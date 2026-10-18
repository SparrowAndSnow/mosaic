package example.screens

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Alignment
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import example.ShowcaseTheme

/**
 * Cluster-level rendering: wide CJK glyphs, full-width punctuation, emoji/ZWJ families, flags,
 * combining marks, variation selectors, ideographic spaces, and box-drawing glyphs all land on the
 * measured columns, one grapheme cluster per cell.
 */
@Composable
internal fun UnicodeScreen() {
	Column(modifier = Modifier.padding(top = 1, bottom = 1)) {
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
}

@Composable
private fun Section(text: String) {
	Text("$text", color = ShowcaseTheme.section)
}

@Composable
private fun SpacerLine() {
	Text("")
}

@Composable
private fun Line(content: String) {
	Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
		Text(content, modifier = Modifier.weight(1f), color = ShowcaseTheme.body)
		Text("│", color = ShowcaseTheme.dim)
	}
}
