package example.screens

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.layout.HorizontalDivider
import com.jakewharton.mosaic.layout.VerticalDivider
import com.jakewharton.mosaic.layout.border
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.height
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import example.ShowcaseTheme

/**
 * Border and divider primitives: single/thick/colored box borders that inset their content, a
 * border that composes with padding, nesting, and horizontal/vertical dividers including
 * indented rules that span the full available width/height.
 */
@Composable
internal fun BorderScreen() {
	Column(modifier = Modifier.padding(top = 1, bottom = 1)) {
		Section("1. 边框变体：border() / border(2)（加宽）/ 指定颜色")
		Row(Modifier.fillMaxWidth()) {
			Bordered("常规", Modifier.border())
			SpacerWidth()
			Bordered("加宽", Modifier.border(2))
			SpacerWidth()
			Bordered("彩色", Modifier.border(color = ShowcaseTheme.danger))
		}

		SpacerLine()
		Section("2. 边框 + 内边距（内容不贴边）")
		Bordered("Hello mosaic", Modifier.border().padding(horizontal = 2, vertical = 1))

		SpacerLine()
		Section("3. 嵌套边框")
		Box(Modifier.border().padding(1)) {
			Text(
				"里外两层配色",
				color = ShowcaseTheme.body,
				modifier = Modifier.border(color = ShowcaseTheme.accent).padding(horizontal = 1),
			)
		}

		SpacerLine()
		Section("4. 水平分隔线：整宽 / 两端缩进")
		Column(Modifier.width(30)) {
			Text("上行", color = ShowcaseTheme.body)
			HorizontalDivider(color = ShowcaseTheme.dim)
			Text("缩进分隔", color = ShowcaseTheme.body)
			HorizontalDivider(startIndent = 2, endIndent = 4, color = ShowcaseTheme.dim)
			Text("下行", color = ShowcaseTheme.body)
		}

		SpacerLine()
		Section("5. 垂直分隔线（Row 高度固定，分隔线撑满）")
		Row(Modifier.fillMaxWidth().height(4)) {
			Column(Modifier.weight(1f)) {
				Text("左列内容", color = ShowcaseTheme.body)
				Text("跨两行", color = ShowcaseTheme.dim)
			}
			VerticalDivider(color = ShowcaseTheme.dim)
			Column(Modifier.weight(1f).padding(left = 1)) {
				Text("右列", color = ShowcaseTheme.body)
				Text("完全分隔", color = ShowcaseTheme.dim)
			}
			VerticalDivider(startIndent = 1, endIndent = 1, color = ShowcaseTheme.dim)
			Column(Modifier.weight(1f).padding(left = 1)) {
				Text("缩进列", color = ShowcaseTheme.body)
				Text("顶部留空", color = ShowcaseTheme.dim)
			}
		}
	}
}

@Composable
private fun Bordered(text: String, modifier: Modifier = Modifier) {
	Box(modifier) {
		Text(text, modifier = Modifier.padding(horizontal = 1, vertical = 1), color = ShowcaseTheme.body)
	}
}

@Composable
private fun SpacerWidth() {
	Box(Modifier.width(2)) {}
}

@Composable
private fun Section(text: String) {
	Text(text, color = ShowcaseTheme.section)
}

@Composable
private fun SpacerLine() {
	Text("")
}
