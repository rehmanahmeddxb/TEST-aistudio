package com.example.viewmodel

import com.example.model.AspectRatio
import com.example.model.LayerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Guards the acceptance criteria of the empty-project change:
 * a new project starts with zero layers, no pre-selected source, and the startup
 * aspect-ratio dialog still offered — no demo video/camera/text/image sources.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudioViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `new project starts with zero layers and no selection`() {
        val viewModel = StudioViewModel()
        val state = viewModel.uiState.value

        assertTrue("A new project must not contain demo sources", state.project.layers.isEmpty())
        assertNull("A new project must not pre-select a source", state.selectedLayerId)
        assertFalse("A new project must not be dirty", state.project.isDirty)
        assertTrue(
            "Startup aspect-ratio dialog should be offered on launch",
            state.showStartupAspectRatioDialog
        )
    }

    @Test
    fun `adding a source selects only that source`() {
        val viewModel = StudioViewModel()
        viewModel.addSource(LayerType.VIDEO)

        val state = viewModel.uiState.value
        assertEquals(1, state.project.layers.size)
        assertEquals(state.project.layers.first().id, state.selectedLayerId)
        // No hidden demo layers lingering behind the source just added.
        assertEquals(1, state.project.layers.size)
    }

    @Test
    fun `aspect ratio dialog selection sets canvas format`() {
        val viewModel = StudioViewModel()
        viewModel.setAspectRatio(AspectRatio.NINE_SIXTEEN)
        viewModel.setShowStartupAspectRatioDialog(false)

        val state = viewModel.uiState.value
        assertEquals(AspectRatio.NINE_SIXTEEN, state.project.aspectRatio)
        assertFalse(state.showStartupAspectRatioDialog)
    }

    // --- Full Canvas workspace = STUDIO CHROME visibility, never source visibility ---

    @Test
    fun `full canvas mode only toggles studio chrome, never sources`() {
        val viewModel = StudioViewModel()
        viewModel.addSource(LayerType.VIDEO)
        viewModel.addSource(LayerType.IMAGE)
        val hiddenImageId = viewModel.uiState.value.project.layers.last().id
        viewModel.toggleLayerVisibility(hiddenImageId) // image source hidden BEFORE full canvas

        val before = viewModel.uiState.value
        assertTrue(before.showStudioChrome)

        viewModel.toggleFullCanvasMode()
        val inWorkspace = viewModel.uiState.value
        assertFalse("Entering the canvas workspace hides Studio chrome", inWorkspace.showStudioChrome)
        // Source visibility preserved exactly: video visible, image hidden.
        assertEquals(before.project.layers, inWorkspace.project.layers)
        assertTrue(inWorkspace.project.layers.first { it.type == LayerType.VIDEO }.isVisible)
        assertFalse(inWorkspace.project.layers.first { it.id == hiddenImageId }.isVisible)

        viewModel.toggleFullCanvasMode()
        val exited = viewModel.uiState.value
        assertTrue("Exiting the workspace restores Studio chrome", exited.showStudioChrome)
        assertEquals(before.project.layers, exited.project.layers)
    }

    @Test
    fun `eye toggle shows and hides studio chrome without touching project or recording`() {
        val viewModel = StudioViewModel()
        viewModel.addSource(LayerType.CAMERA)
        viewModel.toggleFullCanvasMode()
        viewModel.startRecording()
        val recorded = viewModel.uiState.value

        viewModel.toggleStudioChrome()
        val restored = viewModel.uiState.value
        assertTrue("Eye restores Studio chrome", restored.showStudioChrome)
        assertTrue("Eye stays inside the canvas workspace", restored.isFullCanvasMode)
        assertEquals("Project untouched", recorded.project, restored.project)
        assertTrue("Recording not stopped by chrome visibility", restored.isRecording)

        viewModel.toggleStudioChrome()
        assertFalse("Eye hides Studio chrome again", viewModel.uiState.value.showStudioChrome)
        assertEquals(recorded.project, viewModel.uiState.value.project)
    }

    @Test
    fun `full canvas mode preserves the pre-existing sidebar open state on exit`() {
        val viewModel = StudioViewModel()
        assertTrue(viewModel.uiState.value.isSidebarOpen)

        viewModel.toggleFullCanvasMode()
        assertFalse(viewModel.uiState.value.isSidebarOpen)

        viewModel.toggleFullCanvasMode()
        assertTrue(viewModel.uiState.value.isSidebarOpen)

        viewModel.setSidebarOpen(false)
        viewModel.toggleFullCanvasMode()
        viewModel.toggleFullCanvasMode()
        assertFalse("A sidebar that was closed stays closed", viewModel.uiState.value.isSidebarOpen)
    }
}
