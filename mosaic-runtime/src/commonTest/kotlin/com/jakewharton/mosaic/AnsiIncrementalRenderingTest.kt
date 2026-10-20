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
				|${clearDisplay}${CSI}HHello
				|World!
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
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
				|${clearDisplay}${CSI}HHello
				|World!
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
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
				|${clearDisplay}${CSI}HABCDEFGHIJ
				|KLMNOPQRST
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
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
				|${clearDisplay}${CSI}Habcdefghij
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
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
				|${clearDisplay}${CSI}HAAAAA
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
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
				|${clearDisplay}${CSI}HOne
				|Two
				|Three
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
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
				|${clearDisplay}${CSI}HOneTwoThree
				|AAAAAAAAAAA
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
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
				|${clearDisplay}${CSI}HAAAAA中BBBB
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
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
				|${clearDisplay}${CSI}HAAAAA中BBBB
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)

			setContent {
				Text("AAAA中ABBBB")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"${goto(1, 5)}中A${goto(2, 1)}".wrapWithAnsiSynchronizedUpdate(),
			)
		}
	}


	@Test fun changeAfterUnchangedWideClusterStaysAfterIt() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text("中B")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|${clearDisplay}${CSI}H中B
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)

			setContent {
				Text("中C")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"${goto(1, 3)}C${goto(2, 1)}".wrapWithAnsiSynchronizedUpdate(),
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
				|${clearDisplay}${CSI}HHello
				|World!
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
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
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
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
				|${clearDisplay}${CSI}HHel
				|lo
				|Wor
				|ld!
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
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
				|${clearLine}
				|${clearLine}
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
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
				|${clearDisplay}${CSI}HOne
				|Two
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)

			setContent {
				StaticEffect {
					Text("Three")
				}
				Text("Four")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|${cursorUp(1)}${clearLine}Three
				|Four
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
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
				|${clearDisplay}${CSI}HS
				|A
				|B
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)

			setContent {
				StaticEffect {
					Text("S")
				}
				Text("A")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|${cursorUp(2)}${clearLine}S
				|${clearLine}A
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}

	@Test fun multiLineStaticClearsItsLinesIndividually() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Column {
					Text("A")
					Text("B")
					Text("C")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|${clearDisplay}${CSI}HA
				|B
				|C
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)

			setContent {
				StaticEffect {
					Column {
						Text("One")
						Text("Two")
					}
				}
				Text("X")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|${cursorUp(3)}${clearLine}One
				|${clearLine}Two
				|${clearLine}X
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}

	@Test fun staticAndShorterDynamicClearLeftoverRowsPerLine() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Column {
					Text("A")
					Text("B")
					Text("C")
					Text("D")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|${clearDisplay}${CSI}HA
				|B
				|C
				|D
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)

			setContent {
				StaticEffect {
					Text("One")
				}
				Text("X")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|${cursorUp(4)}${clearLine}One
				|${clearLine}X
				|${clearLine}
				|${clearLine}
				|
				""".trimMargin().dropLast(1).wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}

	@Test fun framesWithoutSynchronizedOutputAreWrappedInCursorHideShow() = runTest {
		val noSyncRendering = AnsiIncrementalRendering(TestTerminal.Capabilities(synchronizedOutput = false))
		runMosaicTest(RenderingSnapshots(noSyncRendering)) {
			setContent {
				Column {
					Text("Hello")
					Text("World!")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|${clearDisplay}${CSI}HHello
				|World!
				|
				""".trimMargin().dropLast(1).replaceLineEndingsWithCRLF().wrapWithAnsiCursorHideShow(),
			)

			// An unchanged frame draws nothing, so no hide/show is needed and none is emitted.
			setContent {
				Column {
					Text("Hello")
					Text("World!")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("")
		}
	}

	@Test fun incrementalChangeWithoutSynchronizedOutputWrapsThePatch() = runTest {
		val noSyncRendering = AnsiIncrementalRendering(TestTerminal.Capabilities(synchronizedOutput = false))
		runMosaicTest(RenderingSnapshots(noSyncRendering)) {
			setContent {
				Text("ABCDEFGHIJ")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|${clearDisplay}${CSI}HABCDEFGHIJ
				|
				""".trimMargin().dropLast(1).replaceLineEndingsWithCRLF().wrapWithAnsiCursorHideShow(),
			)

			setContent {
				Text("ABCDEFXHIJ")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"${hideCursor}${goto(1, 7)}X${goto(2, 1)}${showCursor}",
			)
		}
	}

	@Test fun framesWithoutCursorTogglingAreNotWrapped() = runTest {
		val noCursorRendering = AnsiIncrementalRendering(
			TestTerminal.Capabilities(synchronizedOutput = false, cursorVisibility = false),
		)
		runMosaicTest(RenderingSnapshots(noCursorRendering)) {
			setContent {
				Text("Hi")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"""
				|${clearDisplay}${CSI}HHi
				|
				""".trimMargin().dropLast(1).replaceLineEndingsWithCRLF(),
			)

			setContent {
				Text("Hi")
			}
			assertThat(awaitSnapshot()).isEqualTo("")
		}
	}
}
