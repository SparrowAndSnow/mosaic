package example.screens

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.layout.clickable
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Text
import example.ShowcaseTheme

/**
 * Overlay demo controls. The actual layers live in the shell's [com.jakewharton.mosaic.ui.OverlayHost]
 * so the modal dialog can cover the header and tab bar too; this screen only renders the buttons and
 * the base click counter that shows the modal barrier.
 */
@Composable
internal fun OverlayScreen(
	onOpenPopup: () -> Unit,
	onOpenDialog: () -> Unit,
	baseClicks: Int,
	onBaseClick: () -> Unit,
) {
	Column(modifier = Modifier.padding(top = 1, bottom = 1)) {
		Section("1. 非模态 Popup：只遮挡自己，外部输入回落到 base")
		Text("[打开 Popup]", modifier = Modifier.clickable(onClick = onOpenPopup), color = ShowcaseTheme.accent)
		Text("Esc 关闭；点击 Popup 外部仍可操作 base。", color = ShowcaseTheme.body)

		SpacerLine()
		Section("2. 模态 Dialog：输入屏障 + backdrop 清屏 + 焦点恢复")
		Text("[打开 Dialog]", modifier = Modifier.clickable(onClick = onOpenDialog), color = ShowcaseTheme.accent)
		Text("Esc / 点击 backdrop 关闭；打开时 base 的 key/mouse/paste/Tab 均不可达。", color = ShowcaseTheme.body)

		SpacerLine()
		Section("3. base 点击计数（Dialog 打开时不会增加）")
		Text(
			"[点击 base: $baseClicks]",
			modifier = Modifier.clickable(onClick = onBaseClick),
			color = ShowcaseTheme.body,
		)

		SpacerLine()
		Section("4. 默认 backdrop")
		Text("Dialog 默认 Solid：清字符/背景/下划线；Popup 默认 None：base 保持可见。", color = ShowcaseTheme.dim)
	}
}

@Composable
private fun Section(text: String) {
	Text(text, color = ShowcaseTheme.section)
}

@Composable
private fun SpacerLine() {
	Text("")
}
