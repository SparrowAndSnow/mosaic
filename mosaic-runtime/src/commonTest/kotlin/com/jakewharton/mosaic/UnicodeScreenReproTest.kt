package com.jakewharton.mosaic

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
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

@androidx.compose.runtime.Composable
private fun Section(text: String) = Text(text)

@androidx.compose.runtime.Composable
private fun SpacerLine() = Text("")

@androidx.compose.runtime.Composable
private fun Line(content: String) {
	Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
		Text(content, modifier = Modifier.weight(1f))
		Text("|")
	}
}

@androidx.compose.runtime.Composable
private fun UnicodeBody() = Column {
	Section("1. CJK 中文与中日韩混排")
	Line("你好，世界！")
	Line("中文 English 混合 123 hello")
	Line("全角标点：，。！？「」『』（）【】")
	Line("a中b日c韩d汉e鲜")

	SpacerLine()
	Section("2. 全角 / 半角对照")
	Line("（全角）(半角)  ０１２３ 0123")
	Line("，，，。中文，，。")

	SpacerLine()
	Section("3. Emoji / ZWJ / 旗帜")
	Line("👍 👍🏽 👍🏻")
	Line("👨\u200D👩\u200D👧\u200D👦")
	Line("🇨🇳 🇺🇸 🇯🇵")
	Line("❤\uFE0F ☕\uFE0F ✈\uFE0F")

	SpacerLine()
	Section("4. 组合字符 / 变体选择符")
	Line("e\u0301 a\u0302 n\u0303 o\u0308")
	Line("中\u0301 国\u0300 文\u0301")

	SpacerLine()
	Section("5. 制表 / 空白 / 其它图形")
	Line("A\u3000B  C")
	Line("┌─┐ │ └┘ ─ ═ ⚡ ★")
}

class UnicodeScreenReproTest {
	private val rendering = AnsiIncrementalRendering(TestTerminal.Capabilities())

	@Test
	fun overhangingPageContentDoesNotFaultCanvas() = runTest {
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Column(Modifier.width(80).height(24)) {
					Text("title")
					Text("subtitle")
					Text("======")
					Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 1)) {
						UnicodeBody()
					}
					Text("tabbar")
					Text("hint")
				}
			}
			val snapshot = awaitSnapshot()
			assertThat(snapshot).contains("1. CJK")
		}
	}
}
