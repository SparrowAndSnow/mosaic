package example.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.BasicTextField
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import example.ShowcaseTheme

/**
 * The single-line text field: a controlled value, an internal caret (rendered as a reverse-video
 * block — terminal-native, no color needed), grapheme-aware movement over CJK and combining
 * clusters, IME composition and bracketed paste insert at the caret, and a readOnly/disabled pair
 * below to show the inert states.
 */
@Composable
internal fun FieldScreen() {
	var value by remember { mutableStateOf("你好，mosaic") }

	Column(Modifier.padding(top = 1, bottom = 1)) {
		Text(
			"BasicTextField：可编辑单行输入。光标=反显块；支持中文/IME/粘贴；" +
				"←/→ 按字素移动（CJK 一次跳一个字符）、Home/End 到头/尾、Backspace/Delete 删除。",
			color = ShowcaseTheme.section,
		)

		Text("", modifier = Modifier.padding(top = 1))
		Row {
			Text(" 输入 : ", color = ShowcaseTheme.body)
			BasicTextField(
				value = value,
				onValueChange = { value = it },
				modifier = Modifier.fillMaxWidth(),
			)
		}
		Text("", modifier = Modifier.padding(top = 1))
		Text(" 当前值 : <<$value>>", color = ShowcaseTheme.body)

		Text("", modifier = Modifier.padding(top = 1))
		Text("只读（readOnly，仅展示）：", color = ShowcaseTheme.dim)
		Row {
			Text(" ", color = ShowcaseTheme.body)
			BasicTextField("只读内容，不能编辑", onValueChange = {}, readOnly = true)
		}
		Text("禁用（disabled，不响应）：", color = ShowcaseTheme.dim)
		Row {
			Text(" ", color = ShowcaseTheme.body)
			BasicTextField("禁用内容，无光标", onValueChange = {}, enabled = false)
		}
	}
}
