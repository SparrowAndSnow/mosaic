package com.jakewharton.mosaic

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextStyle
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

private fun goto(line: Int, column: Int): String = "${CSI}$line;${column}H"

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

	@Test fun middleChangeRewritesOnlyChangedSegment() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Column {
					Text("ABCDEFGHIJ")
					Text("KLMNOPQRST")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|ABCDEFGHIJ
				|KLMNOPQRST
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)

			setContent {
				Column {
					Text("ABCDEFXHIJ")
					Text("KLMNOPQRST")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"${goto(1, 7)}X${goto(3, 1)}".wrapWithAnsiSynchronizedUpdate(),
			)
		}
	}

	@Test fun multipleSparseSegmentsRewrittenSeparately() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text("abcdefghij")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|abcdefghij
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)

			setContent {
				Text("abXdefXhij")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"${goto(1, 3)}X${goto(1, 7)}X${goto(2, 1)}".wrapWithAnsiSynchronizedUpdate(),
			)
		}
	}


	@Test fun styleChangeDetectedAndRewritesStyledRow() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text("AAAAA")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|AAAAA
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)

			setContent {
				Text("AAAAA", textStyle = TextStyle.Bold)
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"${goto(1, 1)}${clearLine}${CSI}1mAAAAA${ansiReset}${ansiClosingCharacter}${goto(2, 1)}"
					.wrapWithAnsiSynchronizedUpdate(),
			)
		}
	}

	@Test fun denseChangeRewritesWholeRow() = runTest {
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
				"${goto(2, 1)}${clearLine}TWO!!${goto(4, 1)}".wrapWithAnsiSynchronizedUpdate(),
			)
		}
	}

	@Test fun truncationRewritesRowFromClear() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Column {
					Text("OneTwoThree")
					Text("AAAAAAAAAAA")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|OneTwoThree
				|AAAAAAAAAAA
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)

			setContent {
				Column {
					Text("On")
					Text("AAAAAAAAAAA")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"${goto(1, 1)}${clearLine}On${goto(3, 1)}".wrapWithAnsiSynchronizedUpdate(),
			)
		}
	}

	@Test fun wideClusterHeadChangePatchesSegment() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text("AAAAA中BBBB")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|AAAAA中BBBB
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)

			setContent {
				Text("AAAAA文BBBB")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"${goto(1, 6)}文${goto(2, 1)}".wrapWithAnsiSynchronizedUpdate(),
			)
		}
	}

	@Test fun wideClusterStructureChangeRewritesCluster() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text("AAAAA中BBBB")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|AAAAA中BBBB
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)

			setContent {
				Text("AAAA中ABBBB")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"${goto(1, 5)}中A${goto(2, 1)}".wrapWithAnsiSynchronizedUpdate(),
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
}
