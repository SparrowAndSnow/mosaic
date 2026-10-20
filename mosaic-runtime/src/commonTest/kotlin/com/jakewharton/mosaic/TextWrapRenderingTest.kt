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

class TextWrapRenderingTest {
	private val rendering = AnsiIncrementalRendering(TestTerminal.Capabilities())

	@Test fun softWrapWrapsWithinParentConstraint() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text("中A中B中C", modifier = Modifier.width(4), softWrap = true)
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|${clearDisplay}${CSI}H中A
				|中B
				|中C
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}

	@Test fun noWrapByDefaultPreservesExistingBehavior() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text("中A中B中C", modifier = Modifier.width(4))
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|${clearDisplay}${CSI}H中A中B中C
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}

	@Test fun maxLinesClipsRemainingLines() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text("中A中B中C", modifier = Modifier.width(4), softWrap = true, maxLines = 2)
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|${clearDisplay}${CSI}H中A
				|中B
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}

	@Test fun maxLinesWithEllipsisTruncatesLastLine() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text("中A中B中C", modifier = Modifier.width(4), softWrap = true, maxLines = 2, overflow = TextOverflow.Ellipsis)
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|${clearDisplay}${CSI}H中A
				|中B…
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}
}
