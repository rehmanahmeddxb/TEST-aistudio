package com.example

import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.export.Mp4RenderExporter
import com.example.model.AspectRatio
import com.example.model.CanvasBackground
import com.example.model.ExportSettings
import com.example.model.Layer
import com.example.model.LayerType
import com.example.model.Project
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-only smoke test for the real MediaCodec -> MediaMuxer export path.
 *
 * The first pass creates a tiny, solid-orange source MP4. The second pass imports that real MP4,
 * decodes it through [Mp4RenderExporter], composites it over a black canvas, and exports again.
 * Inspecting the resulting frame catches a particularly dangerous failure mode where a valid MP4
 * container is produced while source-video decoding silently rendered no pixels.
 */
@RunWith(AndroidJUnit4::class)
class Mp4RenderExporterInstrumentedTest {

    @Test
    fun exportsImportedVideoAsPlayableNonEmptyMp4() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workDir = File(context.cacheDir, "mp4-export-smoke-${System.nanoTime()}").apply {
            check(mkdirs())
        }
        val sourceFile = File(workDir, "source.mp4")
        val renderedFile = File(workDir, "rendered.mp4")
        val durationMs = 600L
        val settings = ExportSettings(
            resolution = "720p (HD)",
            fps = 5,
            codec = "H.264 / AVC",
            bitrateMbps = 1f
        )

        try {
            // Pass 1: make a deterministic MP4 fixture using the same real encoder/muxer path.
            exportToFile(
                context = context,
                project = Project(
                    durationMs = durationMs,
                    aspectRatio = AspectRatio.SIXTEEN_NINE,
                    background = CanvasBackground.ORANGE,
                    layers = emptyList()
                ),
                settings = settings,
                output = sourceFile
            )
            assertPlayableVideo(sourceFile, expectedWidth = 1280, expectedHeight = 720)
            assertCenterPixelIsOrange(sourceFile)

            // Pass 2: exercise real MediaExtractor/MediaCodec source decoding and composition.
            exportToFile(
                context = context,
                project = Project(
                    durationMs = durationMs,
                    aspectRatio = AspectRatio.SIXTEEN_NINE,
                    background = CanvasBackground.BLACK,
                    layers = listOf(
                        Layer(
                            name = "source.mp4",
                            type = LayerType.VIDEO,
                            durationMs = durationMs,
                            mediaUri = Uri.fromFile(sourceFile).toString()
                        )
                    )
                ),
                settings = settings,
                output = renderedFile
            )
            assertPlayableVideo(renderedFile, expectedWidth = 1280, expectedHeight = 720)
            assertCenterPixelIsOrange(renderedFile)
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun exportToFile(
        context: android.content.Context,
        project: Project,
        settings: ExportSettings,
        output: File
    ) {
        val progress = mutableListOf<Float>()
        ParcelFileDescriptor.open(
            output,
            ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE or
                ParcelFileDescriptor.MODE_READ_WRITE
        ).use { pfd ->
            Mp4RenderExporter(context).export(project, settings, pfd) { progress += it }
            pfd.fileDescriptor.sync()
        }
        assertTrue("Export should create a non-empty MP4", output.isFile && output.length() > 0L)
        assertTrue("Exporter should report real frame progress", progress.isNotEmpty())
        assertEquals(1f, progress.last(), 0.0001f)
    }

    private fun assertPlayableVideo(file: File, expectedWidth: Int, expectedHeight: Int) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var videoTrack = -1
            var format: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(index)
                if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    videoTrack = index
                    format = candidate
                    break
                }
            }
            assertTrue("Exported MP4 must contain a video track", videoTrack >= 0)
            assertNotNull(format)
            assertEquals("video/avc", format!!.getString(MediaFormat.KEY_MIME))
            assertEquals(expectedWidth, format.getInteger(MediaFormat.KEY_WIDTH))
            assertEquals(expectedHeight, format.getInteger(MediaFormat.KEY_HEIGHT))

            extractor.selectTrack(videoTrack)
            var sampleCount = 0
            while (extractor.sampleTime >= 0L) {
                sampleCount++
                if (!extractor.advance()) break
            }
            assertTrue("Exported MP4 must contain encoded frame samples", sampleCount > 0)
        } finally {
            extractor.release()
        }
    }

    private fun assertCenterPixelIsOrange(file: File) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            assertNotNull("Exported video must be decodable", frame)
            val pixel = frame!!.getPixel(frame.width / 2, frame.height / 2)
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)
            assertTrue(
                "Imported source pixels were not rendered (center rgb=$red,$green,$blue)",
                red > 140 && red > green * 3 / 2 && blue < 80
            )
        } finally {
            retriever.release()
        }
    }
}
