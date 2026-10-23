package com.jakewharton.mosaic

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * ISO C0/C1 control characters embedded in text are non-printing: they occupy zero columns and are
 * never emitted into the ANSI stream. Emitting their raw bytes would make the terminal interpret
 * them as cursor motions (CR/BS/TAB) or protocol escapes and corrupt the frame, while measuring
 * them as columns would disagree with what is actually drawn. This pins the "control is invisible
 * and takes no space" contract end to end.
 */
class IsoControlRenderingTest {
	private val rendering = AnsiIncrementalRendering(TestTerminal.Capabilities())

	private fun visible(frame: String): String = frame
		.replace(synchronizedOutputEnable, "")
		.replace(synchronizedOutputDisable, "")
		// The first frame also includes the takeover prefix (clear display + home); it is
		// framing, not content, so the visible-text assertions below ignore it.
		.replace("$clearDisplay${CSI}H", "")
		.replace("\r\n", "\n")

	@Test fun tabCrAndBackspaceAreDropped() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent { Text("A\tB") }
			val frame = awaitSnapshot().toString()
			assertThat(frame).doesNotContain("\u0009")
			assertThat(visible(frame)).isEqualTo("AB")
		}
	}

	@Test fun carriageReturnIsDropped() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent { Text("A\rB") }
			val frame = awaitSnapshot().toString()
			assertThat(frame).doesNotContain("\u000D")
			assertThat(visible(frame)).isEqualTo("AB")
		}
	}

	@Test fun backspaceIsDropped() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent { Text("A\bB") }
			val frame = awaitSnapshot().toString()
			assertThat(frame).doesNotContain("\u0008")
			assertThat(visible(frame)).isEqualTo("AB")
		}
	}

	@Test fun c0AndC1EscapesAreDropped() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent { Text("A\u001BB\u0085C") }
			val frame = awaitSnapshot().toString()
			// The frame's own synchronized-output envelope legitimately carries ESC; the emitted
			// content itself must contain neither the source ESC nor C1 NEL bytes.
			assertThat(visible(frame)).isEqualTo("ABC")
			assertThat(visible(frame)).doesNotContain("\u001B")
			assertThat(visible(frame)).doesNotContain("\u0085")
		}
	}

	@Test fun controlOnlyLineStaysEmpty() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent { Text("\t") }
			val frame = awaitSnapshot().toString()
			assertThat(frame).doesNotContain("\u0009")
			assertThat(visible(frame.trimEnd())).isEqualTo("")
		}
	}

	@Test fun crlfStillSplitsLinesButCrIsDropped() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent { Text("A\r\nB") }
			val frame = awaitSnapshot().toString()
			// The \n remains a line separator; the companion \r must not leak as a cursor move.
			assertThat(visible(frame)).isEqualTo("A\nB")
		}
	}

	@Test fun newlineLineSeparatorStillWorks() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent { Text("A\nB", modifier = Modifier.width(1)) }
			assertThat(visible(awaitSnapshot().toString())).isEqualTo("A\nB")
		}
	}
}
