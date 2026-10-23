package com.jakewharton.mosaic

import androidx.compose.runtime.Composable
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.height
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.testing.TestMosaic
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Alignment
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Dialog
import com.jakewharton.mosaic.ui.OverlayHost
import com.jakewharton.mosaic.ui.Popup
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextOverflow
import com.jakewharton.mosaic.ui.unit.IntOffset
import kotlin.test.Test
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runTest

/**
 * End-to-end golden tests: a composition runs through the real pipeline (compose -> measure ->
 * draw -> ANSI frames) and each frame is replayed into an in-memory [VirtualTerminal]. Assertions
 * target the *screen state a user actually sees*, not the raw byte stream of a single frame.
 *
 * The terminal is sized to the test default (80x24), so samples below exercise full-width rows and
 * the wide-cluster handling of East-Asian characters and box-drawing glyphs.
 */
internal class EndToEndGoldenTest {
	private val rendering = AnsiIncrementalRendering(TestTerminal.Capabilities())

	private class Screen(val rows: List<String>, val residue: Boolean)

	/** Feeds frames until the on-screen state stops changing (bounded), returning the final screen. */
	private suspend fun TestMosaic<String>.settleInto(
		vt: VirtualTerminal,
		maxFrames: Int = 5,
	): Screen {
		var previous = listOf<String>()
		repeat(maxFrames) { i ->
			// With a fully settled (idle) tree an awaitSnapshot reaches its 1s virtual timeout:
			// that timeout is the harness's "stable" signal, so stop feeding there.
			val frame = try {
				awaitSnapshot()
			} catch (t: TimeoutCancellationException) {
				return Screen(previous, vt.hasResidue())
			}
			vt.feed(frame)
			val rows = (0 until 24).map { vt.displayRow(it).trimEnd() }.dropLastWhile { it.isEmpty() }
			if (i > 0 && rows == previous) return Screen(rows, vt.hasResidue())
			previous = rows
		}
		return Screen(previous, vt.hasResidue())
	}

	private fun assertGolden(
		expected: List<String>,
		content: @Composable () -> Unit,
	) = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent { content() }
			val screen = settleInto(VirtualTerminal(24, 80))
			assertThat(screen.residue).isFalse()
			assertThat(screen.rows).isEqualTo(expected)
		}
	}

	private fun assertGoldenAfter(
		first: @Composable () -> Unit,
		second: @Composable () -> Unit,
		expected: List<String>,
	) = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			val vt = VirtualTerminal(24, 80)
			setContent { first() }
			settleInto(vt)
			setContent { second() }
			val screen = settleInto(vt)
			assertThat(screen.residue).isFalse()
			assertThat(screen.rows).isEqualTo(expected)
		}
	}

	@Test fun wideTextRendersOnScreen() = assertGolden(
		listOf(
			"English 中文 Beijing 北京",
			"Mix ╔╗ box █▓▒░",
		),
	) {
		Column {
			Text("English 中文 Beijing 北京")
			Text("Mix ╔╗ box █▓▒░")
		}
	}

	@Test fun weightedRowTablePlacesBySlot() = assertGolden(
		listOf(
			"Table 表格",
			"ID" + " ".repeat(18) + "名称/Name" + " ".repeat(31) + "进度",
		),
	) {
		Column {
			Text("Table 表格")
			// 80 columns split 1:2:1 -> slots 20/40/20 at x = 0/20/60; the fill=true children are
			// accounted and placed by their slots while their own labels stay content width. The
			// harness measures the root with unbounded constraints, so the row needs an explicit
			// 80-column container to give the weights space to distribute.
			Box(Modifier.width(80)) {
				Row(Modifier.fillMaxWidth()) {
					Text("ID", modifier = Modifier.weight(1f))
					Text("名称/Name", modifier = Modifier.weight(2f))
					Text("进度", modifier = Modifier.weight(1f))
				}
			}
		}
	}

	@Test fun maxLinesClipKeepsVisibleWindow() = assertGolden(
		listOf(
			"line one",
			"line two",
		),
	) {
		Box(Modifier.width(20).height(2)) {
			// The third line exists but is clipped by the Text's maxLines window.
			Text("line one\nline two\nline three", maxLines = 2, overflow = TextOverflow.Clip)
		}
	}

	@Test fun dialogBackdropErasesBaseAndCentersContent() = assertGolden(
		listOf(
			"",
			"",
			"",
			"",
			"",
			"",
			"",
			"",
			"",
			"",
			"",
			"",
			" ".repeat(37) + "Rename",
		),
	) {
		OverlayHost(
			overlay = {
				Dialog(onDismissRequest = {}) {
					Text("Rename")
				}
			},
		) {
			Column(Modifier.width(80).height(24)) {
				Text("base content")
			}
		}
	}

	@Test fun solidDialogOnlyClearsHostAreaBetweenHeaderAndFooter() = assertGolden(
		listOf(
			"HEADER",
			"",
			"",
			"       Dialog",
			"",
			"TAB",
		),
	) {
		Column(Modifier.width(20).height(6)) {
			Text("HEADER")
			Box(Modifier.height(4)) {
				OverlayHost(
					overlay = {
						Dialog(onDismissRequest = {}) {
							Text("Dialog")
						}
					},
				) {
					Box(Modifier.width(20).height(4)) {
						Text("base")
					}
				}
			}
			Text("TAB")
		}
	}

	@Test fun popupDrawsAboveBaseAndLeavesRestVisible() = assertGolden(
		listOf(
			"base",
			"  menu",
		),
	) {
		OverlayHost(
			overlay = {
				Popup(alignment = Alignment.TopStart, offset = IntOffset(2, 1)) {
					Text("menu")
				}
			},
		) {
			Column(Modifier.width(20).height(5)) {
				Text("base")
			}
		}
	}

	@Test fun closingDialogRestoresBaseScreen() = assertGoldenAfter(
		first = {
			OverlayHost(
				overlay = {
					Dialog(onDismissRequest = {}) {
						Text("Rename")
					}
				},
			) {
				Column(Modifier.width(40).height(10)) {
					Text("base content")
				}
			}
		},
		second = {
			OverlayHost {
				Column(Modifier.width(40).height(10)) {
					Text("base content")
				}
			}
		},
		expected = listOf("base content"),
	)

	@Test fun incrementalUpdateAdoptsNewScreen() = assertGoldenAfter(
		first = {
			Column {
				Text("AAAA")
				Text("BBBB")
			}
		},
		second = {
			Column {
				Text("你好")
				Text("CCCC")
			}
		},
		expected = listOf(
			"你好",
			"CCCC",
		),
	)
}
