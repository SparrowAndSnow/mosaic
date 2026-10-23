package com.jakewharton.mosaic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.jakewharton.mosaic.layout.offset
import com.jakewharton.mosaic.layout.size
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.testing.MosaicSnapshots
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Cursor
import com.jakewharton.mosaic.ui.Dialog
import com.jakewharton.mosaic.ui.OverlayHost
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/** Hardware cursors follow the active modal focus domain, not just their layout position. */
class CursorActiveDomainTest {

	@Test fun baseCursorIsHiddenWhileModalOpenAndRestoredAfterClose() = runTest {
		var show by mutableStateOf(false)
		runMosaicTest(MosaicSnapshots) {
			setContent {
				OverlayHost(
					overlay = {
						if (show) {
							Dialog(onDismissRequest = {}) {
								Text("dialog")
							}
						}
					},
				) {
					Box(Modifier.size(10, 3)) {
						Text("abc")
						Cursor(Modifier.offset(x = 1, y = 1))
					}
				}
			}

			val initial = awaitSnapshot().draw() as TextSurface
			assertThat(initial.cursorShape).isNotNull()

			show = true
			val open = awaitSnapshot().draw() as TextSurface
			assertThat(open.cursorShape).isNull()

			show = false
			val restored = awaitSnapshot().draw() as TextSurface
			assertThat(restored.cursorShape).isNotNull()
		}
	}

	@Test fun renderingStopsShowingBaseCursorWhileModalIsOpen() = runTest {
		var show by mutableStateOf(false)
		runMosaicTest(RenderingSnapshots(AnsiIncrementalRendering(TestTerminal.Capabilities()))) {
			setContent {
				OverlayHost(
					overlay = {
						if (show) {
							Dialog(onDismissRequest = {}) {
								Text("dialog")
							}
						}
					},
				) {
					Box(Modifier.size(10, 3)) {
						Text("abc")
						Cursor(Modifier.offset(x = 1, y = 1))
					}
				}
			}

			assertThat(awaitSnapshot().toString()).contains(showCursor)

			show = true
			assertThat(awaitSnapshot().toString()).doesNotContain(showCursor)

			show = false
			assertThat(awaitSnapshot().toString()).contains(showCursor)
		}
	}

	@Test fun cursorInsideModalLayerStaysVisible() = runTest {
		runMosaicTest(MosaicSnapshots) {
			setContent {
				OverlayHost(
					overlay = {
						Dialog(onDismissRequest = {}) {
							Box(Modifier.size(4, 2)) {
								Cursor(Modifier.offset(x = 1))
							}
						}
					},
				) {
					Box(Modifier.size(10, 3)) {
						Text("base")
					}
				}
			}

			val canvas = awaitSnapshot().draw() as TextSurface
			assertThat(canvas.cursorShape).isNotNull()
		}
	}
}
