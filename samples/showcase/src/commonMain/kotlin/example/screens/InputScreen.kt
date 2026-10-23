package example.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.MouseEvent
import com.jakewharton.mosaic.layout.fillMaxSize
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.layout.onMouseEvent
import com.jakewharton.mosaic.layout.onPaste
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.platform.LocalClipboard
import example.ShowcaseTheme
import kotlinx.coroutines.launch

private const val maxKeyLogLines = 12

private data class MouseLog(
	var press: String = "(none)",
	var release: String = "(none)",
	var motion: String = "(none)",
	var drag: String = "(none)",
)

private fun formatKey(event: KeyEvent): String = buildString {
	if (event.ctrl) append("C-")
	if (event.alt) append("A-")
	if (event.shift) append("S-")
	append(event.key)
	if (event.repeat) append("(repeat)")
}

private fun formatMouse(event: MouseEvent): String = buildString {
	append("${event.x},${event.y} ${event.type}")
	if (event.button != MouseEvent.Button.None) append(" btn=${event.button}")
	if (event.shift) append(" +shift")
	if (event.ctrl) append(" +ctrl")
	if (event.alt) append(" +alt")
}

/**
 * The L5 input path: live mouse events (press/release/motion/drag with modifiers), bracketed-paste
 * content, OSC 52 clipboard read/write, and per-key echo (repeat, arrows, function keys, IME).
 */
@Composable
internal fun InputScreen() {
	val scope = rememberCoroutineScope()
	val clipboard = LocalClipboard.current
	var log by remember { mutableStateOf(MouseLog()) }
	var pasteCount by remember { mutableIntStateOf(0) }
	var lastPaste by remember { mutableStateOf("(none)") }
	var clipboardWrite by remember { mutableStateOf("(none)") }
	var clipboardRead by remember { mutableStateOf("(none)") }
	var clipboardBusy by remember { mutableStateOf(false) }
	var writeCount by remember { mutableIntStateOf(0) }
	var keyLog by remember { mutableStateOf(listOf<String>()) }

	val boxModifier = Modifier
		.fillMaxSize()
		.onMouseEvent { event ->
			when (event.type) {
				MouseEvent.Type.Press -> log = log.copy(press = formatMouse(event))
				MouseEvent.Type.Release -> log = log.copy(release = formatMouse(event))
				MouseEvent.Type.Drag -> log = log.copy(drag = formatMouse(event))
				MouseEvent.Type.Motion -> log = log.copy(motion = formatMouse(event))
			}
			true
		}
		.onPaste { text ->
			pasteCount += 1
			lastPaste = text.take(200)
			true
		}
		.onKeyEvent { event ->
			keyLog = (listOf(formatKey(event)) + keyLog).take(maxKeyLogLines)
			when (event.key) {
				"w" -> {
					val n = writeCount + 1
					writeCount = n
					clipboardBusy = true
					scope.launch {
						clipboard.setText("showcase #$n")
						clipboardWrite = "write #$n ok"
						clipboardBusy = false
					}
					true
				}

				"r" -> {
					clipboardBusy = true
					scope.launch {
						val text = clipboard.getText()
						clipboardRead = text?.let { "read: $it" } ?: "read: (no reply / denied)"
						clipboardBusy = false
					}
					true
				}

				else -> false
			}
		}

	Column(
		modifier = Modifier
			.padding(top = 1, bottom = 1)
			.then(boxModifier),
	) {
		Section("鼠标（press/release/motion/drag）")
		Text("  press   : ${log.press}", color = ShowcaseTheme.body)
		Text("  release : ${log.release}", color = ShowcaseTheme.body)
		Text("  motion  : ${log.motion}", color = ShowcaseTheme.body)
		Text("  drag    : ${log.drag}", color = ShowcaseTheme.body)

		Text("", modifier = Modifier.padding(top = 1))
		Section("粘贴（bracketed paste，Ctrl+Shift+V / 中键）")
		Text("  count : $pasteCount", color = ShowcaseTheme.body)
		Text("  last  : $lastPaste", color = ShowcaseTheme.body)

		Text("", modifier = Modifier.padding(top = 1))
		Section("剪贴板（OSC 52，终端需允许）")
		Text("  write : $clipboardWrite", color = ShowcaseTheme.body)
		Text("  read  : $clipboardRead", color = ShowcaseTheme.body)
		Text("  busy  : $clipboardBusy", color = ShowcaseTheme.body)

		Text("", modifier = Modifier.padding(top = 1))
		Section("按键回显（长按验证 repeat；方向/F 键；IME 中文）")
		if (keyLog.isEmpty()) {
			Text("  (none)", color = ShowcaseTheme.body)
		} else {
			for (key in keyLog) {
				Text("  key : $key", color = ShowcaseTheme.body)
			}
		}

		Text("", modifier = Modifier.padding(top = 1))
		Section("提示")
		Text("  拖动/移动鼠标观察 motion；点击 tab 高亮", color = ShowcaseTheme.body)
		Text("  w=写剪贴板  r=读剪贴板", color = ShowcaseTheme.body)
	}
}

@Composable
private fun Section(text: String) {
	Text(text, color = ShowcaseTheme.section)
}
