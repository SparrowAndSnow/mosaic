package com.jakewharton.mosaic

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.text.AnnotatedString
import com.jakewharton.mosaic.ui.Alignment
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class TextAlignRenderingTest {
	private val rendering = AnsiIncrementalRendering(TestTerminal.Capabilities())

	@Test fun centerAlignmentWithinWidth() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text("AA\nB", modifier = Modifier.width(6), textAlign = Alignment.CenterHorizontally)
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|  AA
				|   B
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}

	@Test fun endAlignmentWithinWidth() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text("AB", modifier = Modifier.width(6), textAlign = Alignment.End)
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|    AB
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}

	@Test fun startAlignmentKeepsCompactBox() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text("AB", modifier = Modifier.width(6), textAlign = Alignment.Start)
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|AB
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}

	@Test fun alignmentWithoutBoundedWidthIsNoOp() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text("AB", textAlign = Alignment.CenterHorizontally)
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|AB
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}

	@Test fun softWrapLinesCenterWithinWidth() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text(
					"中A中B中C",
					modifier = Modifier.width(4),
					softWrap = true,
					textAlign = Alignment.CenterHorizontally,
				)
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				| 中A
				| 中B
				| 中C
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}

	@Test fun centerAlignmentExpandsBoxForSiblings() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Row {
					Text("AB", modifier = Modifier.width(6), textAlign = Alignment.CenterHorizontally)
					Text("|")
				}
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|  AB  |
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}

	@Test fun annotatedStringHonorsAlignment() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text(AnnotatedString("AB"), modifier = Modifier.width(6), textAlign = Alignment.CenterHorizontally)
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|  AB
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}
}
