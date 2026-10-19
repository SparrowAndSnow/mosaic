package example.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.layout.FocusRequester
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.focusRequester
import com.jakewharton.mosaic.layout.onFocusChanged
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.BasicTextField
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import example.ShowcaseTheme

/**
 * The single-line text field plus the focus system: two editable fields that share a screen.
 * The first is auto-focused on load; click a field or Tab / Shift+Tab to move focus between them.
 * Only the focused field shows its caret and accepts typing / IME / paste, and each row reports
 * its own focus state below. readOnly/disabled remain inert.
 */
@Composable
internal fun FieldScreen() {
	var name by remember { mutableStateOf("你好，mosaic") }
	var note by remember { mutableStateOf("") }
	var nameFocused by remember { mutableStateOf(false) }
	var noteFocused by remember { mutableStateOf(false) }
	val nameRequester = remember { FocusRequester() }

	LaunchedEffect(Unit) {
		nameRequester.requestFocus()
	}

	Column(Modifier.padding(top = 1, bottom = 1)) {
		Text(
			"BasicTextField：可编辑单行输入（受控）。点击字段或 Tab/Shift+Tab 切换焦点，只有聚焦的字段响应输入。",
			color = ShowcaseTheme.section,
		)

		Text("", modifier = Modifier.padding(top = 1))
		Row {
			Text(" 姓名 : ", color = ShowcaseTheme.body)
			BasicTextField(
				value = name,
				onValueChange = { name = it },
				modifier = Modifier
					.fillMaxWidth()
					.focusRequester(nameRequester)
					.onFocusChanged { nameFocused = it.isFocused },
			)
		}
		Text(" 姓名状态 : ${if (nameFocused) "聚焦中" else "未聚焦"}", color = ShowcaseTheme.body)

		Text("", modifier = Modifier.padding(top = 1))
		Row {
			Text(" 备注 : ", color = ShowcaseTheme.body)
			BasicTextField(
				value = note,
				onValueChange = { note = it },
				modifier = Modifier
					.fillMaxWidth()
					.onFocusChanged { noteFocused = it.isFocused },
			)
		}
		Text(" 备注状态 : ${if (noteFocused) "聚焦中" else "未聚焦"}", color = ShowcaseTheme.body)

		Text(" 当前值 : <<$name>> / <<$note>>", color = ShowcaseTheme.body)

		Text("", modifier = Modifier.padding(top = 1))
		Text("只读（readOnly，可聚焦不可编辑）：", color = ShowcaseTheme.dim)
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
