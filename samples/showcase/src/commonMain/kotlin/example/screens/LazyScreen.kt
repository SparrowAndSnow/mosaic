package example.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.ScrollState
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.height
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.LazyColumn
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import example.ShowcaseTheme

/**
 * A lazy list: 500 rows are declared but only the window around the visible viewport is ever
 * composed, so cost stays O(viewport) regardless of the total. ↑/↓ scroll one line, PageUp/
 * PageDown flip a screen, mouse wheel works; the status line shows the offset and a scroll bar
 * against the 500-row total. (The window itself is asserted by the runtime tests - the demo just
 * scrolls a huge list at a constant cost.)
 */
// The box wrapping the LazyColumn pins its viewport to this many rows (see Modifier.height below);
// one "page" of scrolling is a viewport minus a one-row overlap so reading keeps context.
private const val LazyViewportRows = 10

@Composable
internal fun LazyScreen() {
	val state = remember { ScrollState() }

	Column(modifier = Modifier.padding(top = 1, bottom = 1)) {
		Text("LazyColumn：500 行，只组合可视窗口（↑/↓ = ±1 行，PageUp/PageDown = ±1 屏，滚轮可滚；Page 键长按只翻一页）", color = ShowcaseTheme.section)
		Box(
			Modifier
				.fillMaxWidth()
				.height(LazyViewportRows)
				.onKeyEvent { event ->
					when (event.key) {
						"ArrowUp" -> {
							state.scrollBy(-1)
							true
						}

						"ArrowDown" -> {
							state.scrollBy(1)
							true
						}

						// Page keys act once per physical press: the keyboard-protocol auto-repeat
						// (a held Fn+arrow on compact keyboards) arrives tagged as a repeat and must
						// not stack whole pages on a single hold. Fresh presses always page.
						"PageUp", "PageDown" -> {
							if (event.repeat) {
								true
							} else {
								state.scrollBy(if (event.key == "PageUp") -(LazyViewportRows - 1) else (LazyViewportRows - 1))
								true
							}
						}

						else -> false
					}
				},
		) {
			LazyColumn(state = state, itemHeight = 1) {
				items(500) { index ->
					val marker = when (index % 7) {
						0 -> "★"
						3 -> "●"
						else -> "·"
					}
					Text(
						"第${index.toString().padStart(3, ' ')}行  lazy 条目 $index  $marker",
						color = if (index % 2 == 0) ShowcaseTheme.body else ShowcaseTheme.dim,
					)
				}
			}
		}
		Row {
			Text("offset ${state.value}/500", color = ShowcaseTheme.section)
			if (state.maxValue > 0) {
				val width = 20
				val filled = ((state.value.toFloat() / state.maxValue) * width).toInt()
				Text(
					"  [" + "█".repeat(filled) + "·".repeat((width - filled).coerceAtLeast(0)) + "]",
					color = ShowcaseTheme.dim,
				)
			}
		}
	}
}
