package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AspectRatio
import com.example.ui.theme.*

@Composable
fun TopStrip(
    projectName: String,
    aspectRatio: AspectRatio,
    isSidebarOpen: Boolean,
    isDirty: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    showStatsOverlay: Boolean,
    isFullCanvasMode: Boolean = false,
    onToggleSidebar: () -> Unit,
    onRenameClick: () -> Unit,
    onAspectCycleClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onSaveClick: () -> Unit,
    onToggleStatsClick: () -> Unit,
    onToggleFullCanvas: () -> Unit = {},
    onQuickExportClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    onShowSaveExportGuide: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var overflowMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xE60E1016))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ☰ Hamburger Button
        IconButton(
            onClick = onToggleSidebar,
            modifier = Modifier
                .testTag("hamburger_button")
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSidebarOpen) StudioCyan.copy(alpha = 0.2f) else Color.Transparent)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Toggle sidebar menu",
                tint = if (isSidebarOpen) StudioCyan else StudioTextPrimary
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Project Title (clickable to rename)
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(6.dp))
                .clickable { onRenameClick() }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = projectName,
                color = StudioTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Rename project",
                tint = StudioTextMuted,
                modifier = Modifier.size(12.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Aspect Ratio Chip
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = StudioSurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioSurfaceBorder),
            modifier = Modifier
                .testTag("aspect_ratio_chip")
                .clickable { onAspectCycleClick() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AspectRatio,
                    contentDescription = null,
                    tint = StudioCyan,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = when (aspectRatio) {
                        AspectRatio.SIXTEEN_NINE -> "16:9"
                        AspectRatio.NINE_SIXTEEN -> "9:16"
                        AspectRatio.ONE_ONE -> "1:1"
                    },
                    color = StudioCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Undo
        IconButton(
            onClick = onUndoClick,
            enabled = canUndo,
            modifier = Modifier
                .testTag("undo_button")
                .size(36.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo",
                tint = if (canUndo) StudioTextPrimary else StudioTextMuted.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }

        // Redo
        IconButton(
            onClick = onRedoClick,
            enabled = canRedo,
            modifier = Modifier
                .testTag("redo_button")
                .size(36.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Redo,
                contentDescription = "Redo",
                tint = if (canRedo) StudioTextPrimary else StudioTextMuted.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Save project draft button
        Surface(
            color = if (isDirty) StudioAmber.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.07f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(
                1.dp,
                if (isDirty) StudioAmber.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f)
            ),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onSaveClick() }
                .testTag("save_project_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isDirty) StudioAmber else StudioGreen)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Save Project Draft",
                    tint = if (isDirty) StudioAmber else StudioTextSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isDirty) "Save Draft" else "Saved",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDirty) StudioAmber else StudioTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Export MP4 button
        FilledTonalButton(
            onClick = onQuickExportClick,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = StudioCyan,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .height(30.dp)
                .testTag("export_mp4_button")
        ) {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Export MP4",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.width(2.dp))

        // Hide Menu / Go Full Screen button (canvas itself is always full screen;
        // this hides the floating Top Strip + Sidebar chrome overlay).
        IconButton(
            onClick = onToggleFullCanvas,
            modifier = Modifier
                .testTag("full_canvas_button")
                .size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = "Hide Menu (Full Canvas)",
                tint = StudioTextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Overflow ⋮ Menu
        Box {
            IconButton(
                onClick = { overflowMenuExpanded = true },
                modifier = Modifier
                    .testTag("overflow_menu_button")
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = StudioTextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = overflowMenuExpanded,
                onDismissRequest = { overflowMenuExpanded = false },
                modifier = Modifier.background(StudioSurfaceElevated)
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Hide Menu (Full Canvas)",
                            color = StudioTextPrimary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = null,
                            tint = StudioCyan
                        )
                    },
                    onClick = {
                        overflowMenuExpanded = false
                        onToggleFullCanvas()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text("Why Save vs Export?", color = StudioAmber)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = null,
                            tint = StudioAmber
                        )
                    },
                    onClick = {
                        overflowMenuExpanded = false
                        onShowSaveExportGuide()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (showStatsOverlay) "Hide Stats Overlay" else "Show Stats Overlay",
                            color = StudioTextPrimary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = StudioCyan
                        )
                    },
                    onClick = {
                        overflowMenuExpanded = false
                        onToggleStatsClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Studio Settings & Diagnostics", color = StudioTextPrimary) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = StudioTextSecondary
                        )
                    },
                    onClick = {
                        overflowMenuExpanded = false
                        onOpenSettingsClick()
                    }
                )
            }
        }
    }
}
