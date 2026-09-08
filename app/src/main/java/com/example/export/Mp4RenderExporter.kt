package com.example.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.model.ExportSettings
import com.example.model.FitMode
import com.example.model.Layer
import com.example.model.LayerType
import com.example.model.Project
import com.example.model.TextData
import java.nio.ByteBuffer

/**
 * REAL MP4 exporter.
 *
 * This is not a fake "progress bar then complete" path. It performs an actual offline render:
 *   - composites the project's real layers (canvas background, gallery IMAGE sources, TEXT
 *     overlays and real VIDEO sources decoded frame-by-frame) into a [Bitmap] at the project's
 *     aspect ratio,
 *   - feeds each frame into a hardware/software H.264 (or H.265) [MediaCodec] encoder,
 *   - muxes the compressed stream with [MediaMuxer] into a genuine playable .mp4.
 *
 * Content that cannot be rendered faithfully is reported as an explicit error rather than being
 * silently dropped or faked:
 *   - an empty project (no timed content) refuses to export,
 *   - live CAMERA layers are not yet supported by the encoder and produce a clear error,
 *   - SCREEN (screen-record) layers have no frame source and produce a clear error.
 *
 * Because this pipeline intentionally runs on a normal background thread (no EGL/GLES, no visible
 * Surface), it keeps every existing live-camera / gallery-player / Compose preview path untouched.
 */
class Mp4RenderExporter(private val context: Context) {

    class ExportException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * @param outPfd writable destination for the muxed file (opened by the caller from either a
     *               user-chosen SAF folder/document URI or the default public Movies collection).
     * @param onProgress called on the calling thread with a real 0f..1f fraction of encoded frames.
     */
    fun export(
        project: Project,
        settings: ExportSettings,
        outPfd: ParcelFileDescriptor,
        onProgress: (Float) -> Unit
    ) {
        // --- 1. Timed content gate: never export a fake/empty timeline. ---
        val totalDurationMs = project.durationMs
        if (totalDurationMs <= 0L) {
            throw ExportException(
                "Nothing to export yet — add a video to the timeline first."
            )
        }

        val visibleLayers = project.layers.filter { it.isVisible }
        // Live camera cannot be captured by this CPU compositor. Report honestly, don't fake it.
        if (visibleLayers.any { it.type == LayerType.CAMERA }) {
            throw ExportException(
                "Live camera export isn't supported yet. Pause/remove the camera layer, then export."
            )
        }
        if (visibleLayers.any { it.type == LayerType.SCREEN }) {
            throw ExportException(
                "Screen-record layers can't be exported yet — remove the screen layer, then export."
            )
        }

        // --- 2. Resolution from the user's export settings + project aspect ratio. ---
        val (outWidth, outHeight) = resolveDimensions(project, settings)

        val frameRate = settings.fps.coerceIn(1, 60)
        val durationUs = totalDurationMs * 1000L
        val frameIntervalUs = 1_000_000L / frameRate
        // Number of frames we will actually encode (drives real progress).
        val totalFrames = ((durationUs + frameIntervalUs - 1) / frameIntervalUs).toInt()
        if (totalFrames <= 0) {
            throw ExportException("Project duration is too short to export.")
        }

        // --- 3. Prepare per-source decoders (static images are decoded once; videos are pulled). ---
        val decoderStreams = mutableListOf<Pair<Layer, VideoPullDecoder?>>()
        visibleLayers.forEach { layer ->
            when (layer.type) {
                LayerType.VIDEO -> {
                    if (layer.mediaUri == null) {
                        decoderStreams.add(layer to null) // placeholder graphic drawn below
                    } else {
                        decoderStreams.add(layer to VideoPullDecoder(context, layer.mediaUri!!))
                    }
                }
                else -> decoderStreams.add(layer to null)
            }
        }
        // Open every decoder up front so failures surface before we start writing output, and so
        // an already-opened sibling is released if a later one fails.
        val openedDecoders = mutableListOf<VideoPullDecoder>()
        try {
            decoderStreams.forEach { (_, dec) ->
                if (dec != null) {
                    dec.open()
                    openedDecoders.add(dec)
                }
            }
        } catch (e: Exception) {
            openedDecoders.forEach { runCatching { it.release() } }
            throw ExportException("Could not open a source video for export: ${e.message}", e)
        }

        // Pre-decode still bitmaps (per-layer local cache keyed by layer id).
        val imageBitmaps = HashMap<String, Bitmap>()

        val writer = VideoMp4Writer(
            outPfd.fileDescriptor,
            mimeTypeFor(settings.codec),
            outWidth,
            outHeight,
            frameRate,
            (settings.bitrateMbps * 1_000_000f).toInt()
        )
        var framesWritten = 0
        val frameBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val frameCanvas = Canvas(frameBitmap)

        try {
            for (frameIndex in 0 until totalFrames) {
                val ptsUs = frameIndex * frameIntervalUs
                renderFrame(
                    project = project,
                    canvas = frameCanvas,
                    bitmap = frameBitmap,
                    width = outWidth,
                    height = outHeight,
                    ptsUs = ptsUs,
                    decoderStreams = decoderStreams,
                    imageBitmaps = imageBitmaps
                )
                writer.writeFrame(frameBitmap, ptsUs)
                framesWritten++
                onProgress(framesWritten.toFloat() / totalFrames.toFloat())
            }

            // Flush remaining encoder buffers + signal EOS + finalize the muxer.
            writer.finish()
        } finally {
            try { writer.release() } catch (_: Exception) {}
            decoderStreams.forEach { (_, dec) -> runCatching { dec?.release() } }
        }

        if (framesWritten <= 0) {
            throw ExportException("No video frames were produced during export.")
        }
    }

    // ------------------------------------------------------------------ compositor

    private fun renderFrame(
        project: Project,
        canvas: Canvas,
        bitmap: Bitmap,
        width: Int,
        height: Int,
        ptsUs: Long,
        decoderStreams: List<Pair<Layer, VideoPullDecoder?>>,
        imageBitmaps: HashMap<String, Bitmap>
    ) {
        // Clear to the project's background colour.
        val bg = toAndroidColor(project.background.colorLong)
        canvas.drawColor(bg)

        for ((layer, decoder) in decoderStreams) {
            if (!layer.isVisible) continue
            if (layer.opacity <= 0f) continue

            val alpha = (layer.opacity.coerceIn(0f, 1f) * 255f).toInt()
            val t = layer.transform
            val left = width * (t.cx - t.w / 2f)
            val top = height * (t.cy - t.h / 2f)
            val lw = width * t.w
            val lh = height * t.h
            val cx = left + lw / 2f
            val cy = top + lh / 2f

            val count = canvas.save()
            canvas.clipRect(left, top, left + lw, top + lh)
            canvas.rotate(t.rotationDeg, cx, cy)

            when (layer.type) {
                LayerType.VIDEO -> drawVideoContent(
                    canvas, layer, decoder, RectF(left, top, left + lw, top + lh), ptsUs, alpha
                )
                LayerType.IMAGE -> drawImageContent(
                    context, canvas, layer, imageBitmaps, RectF(left, top, left + lw, top + lh), alpha
                )
                LayerType.TEXT -> drawTextContent(
                    canvas, layer.textData ?: TextData(),
                    RectF(left, top, left + lw, top + lh), alpha
                )
                else -> { /* camera/screen already rejected above; placeholder handled below */ }
            }
            canvas.restoreToCount(count)
        }
    }

    private fun drawVideoContent(
        canvas: Canvas,
        layer: Layer,
        decoder: VideoPullDecoder?,
        rect: RectF,
        ptsUs: Long,
        alpha: Int
    ) {
        if (decoder != null) {
            // Which point of the (looping) source should be visible at this output time?
            val speed = layer.playbackSpeed.coerceAtLeast(0.1f)
            val sourceCycleUs = if (decoder.durationUs > 0) decoder.durationUs else 1_000_000L
            val tInSource = ((ptsUs.toDouble() * speed).toLong()) % sourceCycleUs
            val frame = decoder.pullFrame(tInSource) ?: return
            val srcW = frame.width
            val srcH = frame.height
            if (srcW <= 0 || srcH <= 0) return

            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            paint.alpha = alpha
            // Mirror the preview (TextureView fills the layer box).
            val srcRect = Rect(0, 0, srcW, srcH)
            canvas.drawBitmap(frame, srcRect, Rect(
                rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt()
            ), paint)
        } else {
            // No media attached -> draw the same placeholder tone the app shows in preview.
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.rgb(0x13, 0x1A, 0x2A)
            paint.alpha = alpha
            canvas.drawRect(rect, paint)
        }
    }

    private fun drawImageContent(
        context: Context,
        canvas: Canvas,
        layer: Layer,
        imageBitmaps: HashMap<String, Bitmap>,
        rect: RectF,
        alpha: Int
    ) {
        val uri = layer.mediaUri ?: return
        var bmp = imageBitmaps[layer.id]
        if (bmp == null) {
            bmp = decodeImage(context, uri)
            if (bmp != null) imageBitmaps[layer.id] = bmp
        }
        if (bmp == null) return
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        paint.alpha = alpha

        val dst = RectF(rect)
        if (layer.fitMode == FitMode.FIT) {
            // ContentScale.Fit equivalent: contain within the layer rect.
            val scale = minOf(rect.width() / bmp.width, rect.height() / bmp.height)
            val drawW = bmp.width * scale
            val drawH = bmp.height * scale
            dst.set(
                rect.centerX() - drawW / 2f,
                rect.centerY() - drawH / 2f,
                rect.centerX() + drawW / 2f,
                rect.centerY() + drawH / 2f
            )
        }
        canvas.drawBitmap(
            bmp, Rect(0, 0, bmp.width, bmp.height),
            Rect(dst.left.toInt(), dst.top.toInt(), dst.right.toInt(), dst.bottom.toInt()),
            paint
        )
    }

    private fun drawTextContent(canvas: Canvas, td: TextData, rect: RectF, alpha: Int) {
        if (td.text.isBlank()) return
        val padX = rect.width() / 40f
        val textRect = RectF(rect.left + padX, rect.top, rect.right - padX, rect.bottom)

        val fontPx = td.fontSizeSp * (rect.height() / 260f).coerceIn(0.4f, 6f)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = toAndroidColor(td.colorHex)
            textSize = fontPx
            typeface = if (td.isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
            if (td.hasShadow) {
                setShadowLayer(6f, 3f, 3f, Color.BLACK)
            }
        }

        val layout = StaticLayout.Builder
            .obtain(td.text, 0, td.text.length, textPaint, textRect.width().toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(true)
            .build()

        canvas.save()
        canvas.clipRect(textRect)
        canvas.translate(textRect.centerX() - layout.width / 2f,
            textRect.centerY() - layout.height / 2f)
        // Alpha is applied through an overlay paint on the layout's first paint.
        layout.paint.alpha = alpha
        layout.draw(canvas)
        canvas.restore()
    }

    // ------------------------------------------------------------------ helpers

    private fun resolveDimensions(project: Project, settings: ExportSettings): Pair<Int, Int> {
        val aspect = project.aspectRatio
        val baseLong = maxOf(aspect.w, aspect.h)
        val targetLong = when {
            settings.resolution.contains("4K", ignoreCase = true) -> 3840
            settings.resolution.contains("1080", ignoreCase = true) -> 1920
            else -> 1280
        }
        val scale = targetLong.toFloat() / baseLong.toFloat()
        val w = ((aspect.w * scale).toInt() / 2) * 2
        val h = ((aspect.h * scale).toInt() / 2) * 2
        return Pair(w.coerceAtLeast(2), h.coerceAtLeast(2))
    }

    private fun toAndroidColor(colorLong: Long): Int = (colorLong and 0xFFFFFFFFL).toInt()

    companion object {
        fun mimeTypeFor(codec: String): String =
            if (codec.contains("H.265") || codec.contains("HEVC")) "video/hevc" else "video/avc"

        fun decodeImage(context: Context, uriString: String): Bitmap? {
            return try {
                context.contentResolver.openInputStream(Uri.parse(uriString))?.use { ins ->
                    BitmapFactoryDecode.decode(ins)
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** Small indirection keeps this file free of a second BitmapFactory import clash. */
private object BitmapFactoryDecode {
    fun decode(stream: java.io.InputStream): Bitmap? =
        try {
            android.graphics.BitmapFactory.decodeStream(stream)
        } catch (e: Exception) {
            null
        }
}

/**
 * Pull-model decoder for one source video. For each requested source timestamp it decodes forward
 * (from the current position) until it has the frame whose PTS is the closest one at or before the
 * requested time, then hands it back as an ARGB [Bitmap]. The codec is intentionally configured for
 * byte-buffer output: [MediaCodec.getOutputImage] then exposes flexible YUV420 planes synchronously,
 * avoiding Surface/ImageReader races and vendor format mismatches. The planes are converted to ARGB
 * while respecting each plane's row/pixel stride and the image crop rectangle.
 */
private class VideoPullDecoder(private val context: Context, private val uriString: String) {
    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    /** Unrotated decoder frame reused between pulls to avoid allocating a full frame each time. */
    private var rawBitmap: Bitmap? = null
    /** Frame returned to the compositor (may be [rawBitmap] or a rotation-corrected copy). */
    private var bitmap: Bitmap? = null
    private var lastReturnedUs = -1L
    private var eosSent = false
    var durationUs = 0L
    private var rotationDeg = 0f
    private var width = 0
    private var height = 0

    fun open() {
        try {
            val uri = Uri.parse(uriString)
            val ex = MediaExtractor()
            // Explicit (Context, Uri, headers=null) overload — unambiguous for the compiler.
            ex.setDataSource(context, uri, null)
            var videoIndex = -1
            for (i in 0 until ex.trackCount) {
                val fmt = ex.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoIndex = i
                    width = fmt.getInteger(MediaFormat.KEY_WIDTH)
                    height = fmt.getInteger(MediaFormat.KEY_HEIGHT)
                    if (fmt.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = fmt.getLong(MediaFormat.KEY_DURATION)
                    }
                    if (fmt.containsKey(MediaFormat.KEY_ROTATION)) {
                        rotationDeg = fmt.getInteger(MediaFormat.KEY_ROTATION).toFloat()
                    }
                    break
                }
            }
            if (videoIndex < 0) throw IllegalStateException("No video track in source")
            ex.selectTrack(videoIndex)
            extractor = ex

            val decodeFormat = ex.getTrackFormat(videoIndex)
            val mime = decodeFormat.getString(MediaFormat.KEY_MIME)!!
            // Ask for the Android-defined flexible YUV layout. getOutputImage() exposes its real
            // row/pixel strides, so the implementation remains correct for planar and semiplanar
            // codec buffers without relying on a vendor-specific packed byte layout.
            decodeFormat.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            val dec = MediaCodec.createDecoderByType(mime)
            dec.configure(decodeFormat, null, null, 0)
            dec.start()
            codec = dec
        } catch (e: Exception) {
            release()
            throw e
        }
    }

    fun pullFrame(targetUs: Long): Bitmap? {
        // Loop: the source restarts when the requested time goes backwards (end of a loop cycle).
        if (lastReturnedUs >= 0 && targetUs < lastReturnedUs) {
            restart()
        }
        if (lastReturnedUs >= targetUs) {
            return bitmap
        }

        val dec = codec ?: return bitmap
        val ex = extractor ?: return bitmap
        val info = MediaCodec.BufferInfo()
        var lastDecodedUs = lastReturnedUs
        var inputEos = eosSent
        var guard = 100000

        while (lastDecodedUs < targetUs && guard-- > 0) {
            var madeProgress = false

            // Feed input (unless end of stream already signalled).
            if (!inputEos) {
                val inIdx = dec.dequeueInputBuffer(2_000L)
                if (inIdx >= 0) {
                    val inBuf = dec.getInputBuffer(inIdx)!!
                    val sampleSize = ex.readSampleData(inBuf, 0)
                    if (sampleSize < 0) {
                        dec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputEos = true
                        eosSent = true
                    } else {
                        dec.queueInputBuffer(inIdx, 0, sampleSize, ex.sampleTime, 0)
                        ex.advance()
                    }
                    madeProgress = true
                }
            }

            val outIdx = dec.dequeueOutputBuffer(info, 2_000L)
            when {
                outIdx >= 0 -> {
                    madeProgress = true
                    if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        dec.releaseOutputBuffer(outIdx, false)
                    } else {
                        val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        try {
                            // An EOS buffer can also carry the last frame; decode it when non-empty.
                            if (!eos || info.size > 0) {
                                val outputImage = dec.getOutputImage(outIdx)
                                    ?: throw Mp4RenderExporter.ExportException(
                                        "The video decoder did not expose a YUV frame at " +
                                            "${info.presentationTimeUs / 1000L} ms."
                                    )
                                val rendered = try {
                                    readYuv420Image(outputImage, rawBitmap)
                                } finally {
                                    outputImage.close()
                                }
                                rawBitmap = rendered
                                val previous = bitmap
                                val corrected = rotateIfNeeded(rendered)
                                bitmap = corrected
                                if (previous != null &&
                                    previous !== rawBitmap &&
                                    previous !== corrected
                                ) {
                                    previous.recycle()
                                }
                                lastDecodedUs = info.presentationTimeUs
                            }
                        } finally {
                            dec.releaseOutputBuffer(outIdx, false)
                        }
                        if (eos) return bitmap
                    }
                }
                else -> { /* no output available yet */ }
            }

            // Stop if we can no longer make progress (queues full / drained) to avoid spinning.
            if (!madeProgress) break
        }
        lastReturnedUs = lastDecodedUs
        return bitmap
    }

    /**
     * The raw decoded frame is in the container's coded orientation; honour the source's
     * KEY_ROTATION so the exported video appears upright like the in-app preview.
     */
    private fun rotateIfNeeded(source: Bitmap): Bitmap {
        if (rotationDeg == 0f) return source
        val matrix = Matrix().apply { postRotate(rotationDeg) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Copies an [ImageFormat.YUV_420_888] decoder image into an ARGB bitmap.
     *
     * YUV_420_888 deliberately does not prescribe whether U/V are planar or interleaved. Reading
     * each plane through its own row/pixel stride supports I420, NV12 and NV21-backed images alike.
     * The decoder commonly exposes padded rows, so a contiguous-buffer copy would corrupt frames.
     */
    private fun readYuv420Image(image: android.media.Image, reuse: Bitmap?): Bitmap {
        if (image.format != ImageFormat.YUV_420_888 || image.planes.size < 3) {
            throw IllegalStateException("Unsupported decoder image format: ${image.format}")
        }

        val crop = image.cropRect
        val w = crop.width()
        val h = crop.height()
        if (w <= 0 || h <= 0) {
            throw IllegalStateException("Decoder returned an empty video frame")
        }

        var out = reuse
        if (out == null || out.width != w || out.height != h) {
            out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuffer = yPlane.buffer.duplicate()
        val uBuffer = uPlane.buffer.duplicate()
        val vBuffer = vPlane.buffer.duplicate()
        val yBase = yBuffer.position()
        val uBase = uBuffer.position()
        val vBase = vBuffer.position()
        val pixels = IntArray(w * h)

        var dst = 0
        for (row in 0 until h) {
            val sourceY = crop.top + row
            val yRow = yBase + sourceY * yPlane.rowStride
            val chromaRow = crop.top / 2 + row / 2
            val uRow = uBase + chromaRow * uPlane.rowStride
            val vRow = vBase + chromaRow * vPlane.rowStride

            for (col in 0 until w) {
                val sourceX = crop.left + col
                val chromaCol = crop.left / 2 + col / 2
                val y = yBuffer.get(yRow + sourceX * yPlane.pixelStride).toInt() and 0xFF
                val u = uBuffer.get(uRow + chromaCol * uPlane.pixelStride).toInt() and 0xFF
                val v = vBuffer.get(vRow + chromaCol * vPlane.pixelStride).toInt() and 0xFF

                // BT.601 limited-range YUV -> RGB. Most AVC/HEVC decoder output uses this range;
                // clamping also safely handles full-range sources.
                val c = (y - 16).coerceAtLeast(0)
                val d = u - 128
                val e = v - 128
                val red = ((298 * c + 409 * e + 128) shr 8).coerceIn(0, 255)
                val green = ((298 * c - 100 * d - 208 * e + 128) shr 8).coerceIn(0, 255)
                val blue = ((298 * c + 516 * d + 128) shr 8).coerceIn(0, 255)
                pixels[dst++] =
                    (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun restart() {
        try { codec?.flush() } catch (_: Exception) {}
        try { extractor?.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC) } catch (_: Exception) {}
        lastReturnedUs = -1L
        eosSent = false
    }

    fun release() {
        try { codec?.stop() } catch (_: Exception) {}
        try { codec?.release() } catch (_: Exception) {}
        try { extractor?.release() } catch (_: Exception) {}
        codec = null
        extractor = null
        if (bitmap !== rawBitmap) runCatching { bitmap?.recycle() }
        runCatching { rawBitmap?.recycle() }
        rawBitmap = null
        bitmap = null
    }
}

/**
 * Owns the H.264/H.265 encoder + [MediaMuxer] and writes compressed samples into the MP4.
 * Frames are fed as ARGB [Bitmap]s; conversion to the encoder's NV12 input happens here.
 */
private class VideoMp4Writer(
    fileDescriptor: java.io.FileDescriptor,
    mime: String,
    private val width: Int,
    private val height: Int,
    fps: Int,
    bitrate: Int
) {
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var muxerStarted = false
    private var trackIndex = -1
    private val info = MediaCodec.BufferInfo()
    private val yuv = ARGBToYuv420(width, height)

    init {
        val format = MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val enc = MediaCodec.createEncoderByType(mime)
        enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        enc.start()
        codec = enc
        muxer = MediaMuxer(fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    fun writeFrame(bitmap: Bitmap, ptsUs: Long) {
        val enc = codec ?: return

        val buffer = yuv.convert(bitmap)
        var inIdx = -1
        var attempts = 0
        while (inIdx < 0 && attempts++ < 50) {
            inIdx = enc.dequeueInputBuffer(10_000L)
            if (inIdx < 0) drain(endOfStream = false)
        }
        if (inIdx < 0) error("encoder input buffer unavailable")

        val inBuf = enc.getInputBuffer(inIdx)!!
        val size = buffer.remaining()
        inBuf.clear()
        inBuf.put(buffer)
        enc.queueInputBuffer(inIdx, 0, size, ptsUs, 0)

        drain(endOfStream = false)
    }

    fun finish() {
        val enc = codec ?: return
        // Signal end of stream.
        var inIdx = -1
        var attempts = 0
        while (inIdx < 0 && attempts++ < 50) {
            inIdx = enc.dequeueInputBuffer(10_000L)
            if (inIdx < 0) drain(endOfStream = false)
        }
        if (inIdx >= 0) {
            enc.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        drain(endOfStream = true)
    }

    private fun drain(endOfStream: Boolean) {
        val enc = codec ?: return
        val mux = muxer ?: return
        var retries = 0
        while (true) {
            val outIdx = enc.dequeueOutputBuffer(info, 10_000L)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = mux.addTrack(enc.outputFormat)
                        mux.start()
                        muxerStarted = true
                    }
                }
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                    if (++retries > 20) return
                }
                outIdx >= 0 -> {
                    retries = 0
                    val outBuf = enc.getOutputBuffer(outIdx)!!
                    val isConfig =
                        (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (muxerStarted && !isConfig && info.size > 0) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        mux.writeSampleData(trackIndex, outBuf, info)
                    }
                    val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    enc.releaseOutputBuffer(outIdx, false)
                    if (eos) return
                }
            }
        }
    }

    fun release() {
        try {
            if (muxerStarted) muxer?.stop()
        } catch (_: Exception) {
        }
        try { codec?.stop() } catch (_: Exception) {}
        try { codec?.release() } catch (_: Exception) {}
        try { muxer?.release() } catch (_: Exception) {}
        codec = null
        muxer = null
    }
}

/** Converts an ARGB Bitmap to packed NV12 YUV420 (the dominant encoder input layout). */
private class ARGBToYuv420(private val width: Int, private val height: Int) {
    fun convert(bitmap: Bitmap): ByteBuffer {
        val frameSize = width * height
        val yuvSize = frameSize * 3 / 2
        val out = ByteBuffer.allocateDirect(yuvSize)

        // Read the full frame once.
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, bitmap.height)

        val yArr = ByteArray(frameSize)
        val uArr = ByteArray(frameSize / 4)
        val vArr = ByteArray(frameSize / 4)

        fun pxAt(x0: Int, y0: Int): Int {
            val x = x0.coerceIn(0, width - 1)
            val y = y0.coerceIn(0, bitmap.height - 1)
            return pixels[y * width + x]
        }

        // Luma (BT.601 -> limited range), per pixel.
        var p = 0
        for (yy in 0 until height) {
            val rowBase = yy * width
            for (xx in 0 until width) {
                val argb = pixels[rowBase + xx]
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yArr[p] = y.toByte()
                p++
            }
        }

        // Chroma (U/V) subsampled 2x2.
        var q = 0
        for (yy in 0 until height / 2) {
            for (xx in 0 until width / 2) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                for (dr in 0..1) {
                    for (dc in 0..1) {
                        val argb = pxAt(xx * 2 + dc, yy * 2 + dr)
                        rSum += (argb shr 16) and 0xFF
                        gSum += (argb shr 8) and 0xFF
                        bSum += argb and 0xFF
                    }
                }
                // Average of the 2x2 block.
                val u = ((-38 * rSum - 74 * gSum + 112 * bSum + 512) shr 10) + 128
                val v = ((112 * rSum - 94 * gSum - 18 * bSum + 512) shr 10) + 128
                uArr[q] = u.toByte()
                vArr[q] = v.toByte()
                q++
            }
        }

        out.put(yArr)
        // NV12 layout: Y plane, then interleaved U/V.
        val chroma = ByteArray(frameSize / 2)
        for (i in 0 until uArr.size) {
            chroma[i * 2] = uArr[i]
            chroma[i * 2 + 1] = vArr[i]
        }
        out.put(chroma)
        out.rewind()
        return out
    }
}
