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
}
