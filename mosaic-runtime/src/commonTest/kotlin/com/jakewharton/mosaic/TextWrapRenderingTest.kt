package com.jakewharton.mosaic

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Text
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
				|中A
				|中B
				|中C
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
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
				|中A中B中C
				|
				""".trimMargin().wrapWithAnsiSynchronizedUpdate().replaceLineEndingsWithCRLF(),
			)
		}
	}
}
