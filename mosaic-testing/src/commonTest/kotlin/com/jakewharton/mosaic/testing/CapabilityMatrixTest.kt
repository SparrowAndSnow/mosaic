package com.jakewharton.mosaic.testing

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * The capability matrix runs the same composition across representative combinations of ANSI
 * level, kitty underline, synchronized output, and grapheme clustering. Whatever a real terminal
 * exposes, layout must stay stable — the matrix turns each plastered capability switch into a
 * regression check on the default plain-text snapshot.
 */
class CapabilityMatrixTest {

	@Test fun layoutIsStableAcrossCapabilityMatrix() = runTest {
		var reference: String? = null
		var iterations = 0
		runMosaicTestForEach { _ ->
			setContent {
				Text("\u4E2De\u0301\uD83D\uDE00  A") // CJK + combining + emoji + ascii
				Text("\u7B2C\u4E8C\u884C wide")
				Text("plain ascii row")
			}
			val snapshot = awaitSnapshot()
			if (reference == null) {
				reference = snapshot
			}
			// Every capability combination must paint the exact same plain-text layout.
			assertThat(snapshot).isEqualTo(reference)
			iterations++
		}
		// The whole matrix was actually exercised, not silently reduced to one combo.
		assertThat(iterations).isEqualTo(CapabilityMatrix.defaults.size)
	}
}
