package com.jakewharton.mosaic

import androidx.compose.runtime.Composable
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import com.jakewharton.mosaic.layout.fillMaxSize
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.height
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.ui.Alignment
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * Regression for a real-terminal-only residue bug: full redraws used to end with a trailing CRLF
 * after the last row, which scrolls the terminal up one line on paint. From then on every
 * absolute-positioned diff lands one row below where the previous frame actually rendered, leaving
 * doubled tab bars, ghost page headers and persistent CJK lines on screen (the trailing newline is
 * silent in the in-memory [VirtualTerminal], which does not model scrolling).
 *
 * These tests pin the "frames are never line-terminated" contract and verify a Unicode-heavy page
 * switch stays clean.
 */
class FullRedrawNoScrollTest {
	private val rendering = AnsiIncrementalRendering(TestTerminal.Capabilities())

	private class FakeMosaic(
		private val surface: TextSurface,
	) : Mosaic {
		override fun setContent(content: @Composable () -> Unit) = Unit
		override fun draw(): TextCanvas = surface
		override fun static(): String? = null
		override fun dumpNodes(): String = ""
		override suspend fun awaitComplete() = Unit
		override fun cancel() = Unit
	}

	private fun textSurface(width: Int, text: String): TextSurface = TextSurface(width, 1).also { surface ->
		for (i in 0 until width) {
			if (i < text.length) {
				surface[0, i].text = text[i].toString()
			}
		}
	}

	private fun String.frameBody(): String = removePrefix(synchronizedOutputEnable).removeSuffix(synchronizedOutputDisable)

	@Test fun fullRedrawFramesAreNeverLineTerminated() {
		val firstFrame = rendering.render(FakeMosaic(textSurface(6, "HELLO"))).toString()
		assertThat(firstFrame.frameBody().endsWith("\r\n")).isFalse()
		assertThat(firstFrame.frameBody().endsWith("\n")).isFalse()

		// A narrower canvas forces the size-changed full-redraw path; it too must not scroll.
		val secondFrame = rendering.render(FakeMosaic(textSurface(4, "AI?"))).toString()
		assertThat(secondFrame.frameBody().endsWith("\r\n")).isFalse()
		assertThat(secondFrame.frameBody().endsWith("\n")).isFalse()
	}

	// ---- Unicode -> Input page switch, mirroring the showcase's real tab layout ----

	@androidx.compose.runtime.Composable
	private fun line(content: String) {
		Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
			Text(content, modifier = Modifier.weight(1f))
			Text("|")
		}
	}

	@androidx.compose.runtime.Composable
	private fun unicodeBody() = Column {
		Text("1. CJK 中文与中日韩混排")
		line("你好，世界！")
		line("中文 English 混合 123 hello")
		line("👍 👍🏽 👍🏻")
		line("👨\u200D👩\u200D👧\u200D👦")
		line("🇨🇳 🇺🇸 🇯🇵")
		Text("")
		line("┌─┐ │ └┘ ─ ═ ⚡ ★")
	}

	@androidx.compose.runtime.Composable
	private fun inputBody() = Column(
		modifier = Modifier.padding(top = 1, bottom = 1).fillMaxSize(),
	) {
		Text("鼠标（press/release/motion/drag）")
		Text("  press   : (none)")
		Text("  release : (none)")
		Text("  motion  : (none)")
		Text("  drag    : (none)")
		Text("", modifier = Modifier.padding(top = 1))
		Text("提示")
		Text("  w=写剪贴板  r=读剪贴板")
	}

	@androidx.compose.runtime.Composable
	private fun shell(page: Int) {
		Column(Modifier.width(80).height(34)) {
			Text("Showcase — mosaic 重构能力演示")
			Text("当前页: ${listOf("Unicode", "Text", "Layout", "Input", "Animation")[page]}")
			Text("══════════════════════════════════════")
			Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 1)) {
				when (page) {
					0 -> unicodeBody()
					else -> inputBody()
				}
			}
			Row(Modifier.fillMaxWidth().padding(top = 1)) {
				listOf("Unicode", "Text", "Layout", "Input", "Animation").forEach {
					Text(it, modifier = Modifier.weight(1f))
				}
			}
			Text("←/→/Tab 切换  |  点击标签  |  Ctrl-C 退出")
		}
	}

	@Test fun unicodeToInputSwitchLeavesNoStaleRows() = runTest {
		val vt = VirtualTerminal(34, 80)
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent { shell(0) }
			vt.feed(awaitSnapshot())
			setContent { shell(3) }
			vt.feed(awaitSnapshot())

			assertThat(vt.hasResidue()).isFalse()
			assertThat(vt.displayRow(1).trimEnd()).isEqualTo("当前页: Input")
			for (row in 0 until 34) {
				assertThat(vt.displayRow(row)).doesNotContain("当前页: Unicode")
			}

			// The tab bar row renders exactly once (its five weighted labels share one row).
			val tabBarRows = (0 until 34).count { row ->
				vt.displayRow(row).contains("Layout") && vt.displayRow(row).contains("Animation")
			}
			assertThat(tabBarRows).isEqualTo(1)
		}
	}
}
