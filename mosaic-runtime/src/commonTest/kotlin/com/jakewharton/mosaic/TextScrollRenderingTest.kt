package com.jakewharton.mosaic

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextOverflow
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class TextScrollRenderingTest {
	private val rendering = AnsiIncrementalRendering(TestTerminal.Capabilities())

	@Test fun scrollShowsWindowOfLines() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text("a\nb\nc\nd\ne", maxLines = 2, scroll = 2)
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|${clearDisplay}${CSI}Hc
				|d
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}

	@Test fun scrollPastEndShowsTrailingLines() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text("a\nb\nc", maxLines = 2, scroll = 4)
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|${clearDisplay}${CSI}Hc
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}

	@Test fun scrollWithEllipsisMarksFollowingContent() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text(
					"a\nb\nc\nd\ne",
					modifier = Modifier.width(2),
					softWrap = true,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
					scroll = 2,
				)
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|${clearDisplay}${CSI}Hc
				|d…
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}
}
