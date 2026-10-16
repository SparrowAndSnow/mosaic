package com.jakewharton.mosaic

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

private fun gotoRow(line: Int): String = "${CSI}${line};1H"

class AnsiIncrementalRenderingTest {
	private val rendering = AnsiIncrementalRendering(TestTerminal.Capabilities())

	@Test fun firstFramePaintsEverything() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Column {
					Text("Hello")
					Text("World!")
				}
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|Hello
				|World!
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}

	@Test fun unchangedFrameEmitsOnlySynchronizedWrapper() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Column {
					Text("Hello")
					Text("World!")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|Hello
				|World!
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)

			setContent {
				Column {
					Text("Hello")
					Text("World!")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"".wrapWithAnsiSynchronizedUpdate(),
			)
		}
	}

	@Test fun changedLineRewritesOnlyThatRow() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Column {
					Text("Hello")
					Text("World!")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|Hello
				|World!
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)

			setContent {
				Column {
					Text("Hello")
					Text("Wide")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"${gotoRow(2)}${clearLine}Wide${gotoRow(3)}"
					.wrapWithAnsiSynchronizedUpdate(),
			)
		}
	}

	@Test fun mixedChangedLinesMoveAbsoluteAndParkBelow() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Column {
					Text("One")
					Text("Two")
					Text("Three")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|One
				|Two
				|Three
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)

			setContent {
				Column {
					Text("One")
					Text("TWO!!")
					Text("Three")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"${gotoRow(2)}${clearLine}TWO!!${gotoRow(4)}"
					.wrapWithAnsiSynchronizedUpdate(),
			)
		}
	}

	@Test fun shorterChangedLineClearsTrailingCells() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text("OneTwoThree")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|OneTwoThree
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)

			setContent {
				Text("On")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"${gotoRow(1)}${clearLine}On${gotoRow(2)}"
					.wrapWithAnsiSynchronizedUpdate(),
			)
		}
	}

	@Test fun tallerFrameRedrawsWholeArea() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Column {
					Text("Hello")
					Text("World!")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|Hello
				|World!
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)

			setContent {
				Column {
					Text("Hel")
					Text("lo")
					Text("Wor")
					Text("ld!")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|${cursorUp(2)}${clearLine}Hel
				|${clearLine}lo
				|Wor
				|ld!
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}

	@Test fun shorterFrameClearsOldLines() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Column {
					Text("Hel")
					Text("lo")
					Text("Wor")
					Text("ld!")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|Hel
				|lo
				|Wor
				|ld!
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)

			setContent {
				Column {
					Text("Hello")
					Text("World!")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|${cursorUp(4)}${clearLine}Hello
				|${clearLine}World!
				|$clearDisplay
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}

	@Test fun staticFramesRedrawFromTop() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				StaticEffect {
					Text("One")
				}
				Text("Two")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|One
				|Two
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)

			setContent {
				StaticEffect {
					Text("Three")
				}
				Text("Four")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|${cursorUp(1)}${clearDisplay}Three
				|Four
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}

	@Test fun staticWithHeightChangeClearsWholeArea() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				StaticEffect {
					Text("S")
				}
				Column {
					Text("A")
					Text("B")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|S
				|A
				|B
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)

			setContent {
				StaticEffect {
					Text("S")
				}
				Text("A")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|${cursorUp(2)}${clearDisplay}S
				|A
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}

	@Test fun changedWideCharacterRowRewritesOnlyThatRow() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Column {
					Text("ABC")
					Text("A中B")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|ABC
				|A中B
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)

			setContent {
				Column {
					Text("ABC")
					Text("中中中")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"${gotoRow(2)}${clearLine}中中中${gotoRow(3)}"
					.wrapWithAnsiSynchronizedUpdate(),
			)
		}
	}
}
