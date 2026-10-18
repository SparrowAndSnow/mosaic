package com.jakewharton.mosaic

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isTrue
import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.height
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Alignment
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.BoxWithConstraints
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * Regression for the showcase Layout screen section 4 rendering blank. The [BoxWithConstraints]
 * sub-composition root was measured 0x0 and the draw walk skips any zero-sized container, so the
 * whole proportional row subtree never painted even though every node size measured correctly.
 */
class BoxWithConstraintsRenderingTest {
	private val rendering = AnsiIncrementalRendering(TestTerminal.Capabilities())

	private fun visible(frame: String): String =
		frame.replace(synchronizedOutputEnable, "").replace(synchronizedOutputDisable, "").replace("\r\n", "\n")

	@Composable
	private fun centeredCell(width: Int, content: String) {
		Box(Modifier.width(width.coerceAtLeast(0)).padding(1), contentAlignment = Alignment.Center) {
			Text(content)
		}
	}

	@Composable
	private fun proportionalRow() {
		BoxWithConstraints(Modifier.fillMaxWidth()) {
			val width = maxWidth
			val half = width / 2
			val third = width / 3
			Row(Modifier.fillMaxWidth()) {
				centeredCell(half, "1/2")
				centeredCell(third, "1/3")
				centeredCell(width - half - third, "余量")
			}
		}
	}

	@Test fun proportionalRowInsideBoxWithConstraintsIsPainted() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Column(Modifier.width(80).height(6)) {
					proportionalRow()
				}
			}

			val body = visible(awaitSnapshot().toString())
			assertThat(body).contains("1/2")
			assertThat(body).contains("1/3")
			assertThat(body).contains("余量")
			assertThat(body.lines().any { it.contains("1/2") && it.contains("1/3") && it.contains("余量") }).isTrue()
		}
	}
}
