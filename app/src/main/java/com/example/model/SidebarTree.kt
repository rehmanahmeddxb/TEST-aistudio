package com.example.model

import androidx.compose.ui.graphics.vector.ImageVector

data class SidebarItem(
    val id: String,
    val label: String,
    val icon: ImageVector? = null,
    val badge: String? = null,
    val active: Boolean = false,
    val danger: Boolean = false,
    val enabled: Boolean = true,
    val children: List<SidebarItem>? = null, // null = leaf action, non-null = sub-menu
    val action: (() -> Unit)? = null,
    val trailingAction: (() -> Unit)? = null,
    val trailingIcon: ImageVector? = null,
    val isInlineControl: Boolean = false,
    val isHeader: Boolean = false
)

data class SidebarSection(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val badge: String? = null,
    val items: List<SidebarItem>
)
