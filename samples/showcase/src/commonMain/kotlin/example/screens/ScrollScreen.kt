package example.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.ScrollState
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.height
import com.jakewharton.mosaic.layout.horizontalScroll
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.layout.verticalScroll
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import example.ShowcaseTheme

/**
 * The general scroll container: any content - not just text - can be scrolled vertically or
 * horizontally inside a bounded viewport, with the scrolled-away cells clipped at the viewport.
 * Arrow keys scroll the vertical panes, `[`/`]` scroll the horizontal line; the page's own key
 * handlers consume these before the shell (which only steals ←/→/Tab for page switching).
 */
@Composable
internal fun ScrollScreen() {
	val verticalState = remember { ScrollState() }
	val horizontalState = remember { ScrollState() }
	val mixedState = remember { ScrollState() }

	Column(modifier = Modifier.padding(top = 1, bottom = 1)) {
		Text("1. 纵向滚动：↑/↓ = ±1 行，滚轮可滚", color = ShowcaseTheme.section)
		Box(
			Modifier
				.fillMaxWidth()
				.height(5)
				.onKeyEvent { verticalState.handleVerticalKey(it) },
		) {
			Column(Modifier.verticalScroll(verticalState)) {
				for (n in 1..12) {
					val idx = n.toString().padStart(2, '0')
					val marker = if (n % 4 == 0) "★" else if (n % 3 == 0) "●" else "·"
					Text(
						"L$idx  条目 中文行 $idx  $marker",
						color = if (n % 2 == 0) ShowcaseTheme.body else ShowcaseTheme.dim,
					)
				}
			}
		}
		ScrollStatus(verticalState, "offset ${verticalState.value}/${verticalState.maxValue}")

		Text("2. 横向滚动：[/] = ±4 列，滚轮可滚", color = ShowcaseTheme.section)
		Row(
			Modifier
				.fillMaxWidth()
				.height(1)
				.onKeyEvent { horizontalState.handleHorizontalKey(it) },
		) {
			Text(
				value = "横向滚动 horizontal-scrolling 0123456789 中文中国語 한국어 日本語 ①②③④⑤⑥⑦⑧⑨⑩",
				modifier = Modifier.horizontalScroll(horizontalState),
				color = ShowcaseTheme.accent,
			)
		}
		ScrollStatus(horizontalState, "offset ${horizontalState.value}/${horizontalState.maxValue}")

		Text("3. 混合内容：PageUp/PageDown = ±1 屏，带背景的块也能滚", color = ShowcaseTheme.section)
		Box(
			Modifier
				.fillMaxWidth()
				.height(3)
				.onKeyEvent { mixedState.handlePageKey(it) },
		) {
			Column(Modifier.verticalScroll(mixedState)) {
				for (n in 1..8) {
					Text(
						"块$n  这不是纯文本——带背景色的整行也能滚动 $n  ",
						background = if (n % 2 == 0) ShowcaseTheme.dim else ShowcaseTheme.section,
						color = ShowcaseTheme.tabActive,
					)
				}
			}
		}
		ScrollStatus(mixedState, "offset ${mixedState.value}/${mixedState.maxValue}")
	}
}

private fun ScrollState.handleVerticalKey(event: KeyEvent): Boolean {
	when (event.key) {
		"ArrowUp" -> scrollBy(-1)
		"ArrowDown" -> scrollBy(1)
		else -> return false
	}
	return true
}

private fun ScrollState.handlePageKey(event: KeyEvent): Boolean {
	when (event.key) {
		"PageUp" -> scrollBy(-maxOf(1, maxValue))
		"PageDown" -> scrollBy(maxOf(1, maxValue))
		else -> return false
	}
	return true
}

private fun ScrollState.handleHorizontalKey(event: KeyEvent): Boolean {
	when (event.key) {
		"[" -> scrollBy(-4)
		"]" -> scrollBy(4)
		else -> return false
	}
	return true
}

@Composable
private fun ScrollStatus(state: ScrollState, label: String) {
	Row {
		Text(label, color = ShowcaseTheme.section)
		if (state.maxValue > 0) {
			val width = 16
			val filled = ((state.value.toFloat() / state.maxValue) * width).toInt()
			Text(
				"  [" + "█".repeat(filled) + "·".repeat((width - filled).coerceAtLeast(0)) + "]",
				color = ShowcaseTheme.dim,
			)
		}
	}
}
