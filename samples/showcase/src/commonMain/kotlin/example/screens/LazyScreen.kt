package example.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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
// A page key may auto-repeat right after a press (long-press on compact keyboards without
// dedicated PgUp/PgDn). Gate page steps on a short window so a single press lands on one
// page whether or not the terminal tags the repeat; holding the key still pages at this
// rate instead of stacking whole pages in one burst.
private val PageKeyRepeatWindow = 600.milliseconds

@Composable
internal fun LazyScreen() {
	val state = remember { ScrollState() }
	// Timestamp of the last accepted PageUp/PageDown, used to swallow auto-repeat.
	var lastPageKey by remember { mutableStateOf<Pair<String, TimeMark>?>(null) }

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
						// Page keys act once per physical press: auto-repeat (holding Fn+arrow on compact
						// keyboards) would otherwise stack whole pages on a single hold. The repeat
						// flag covers terminals with the keyboard protocol; the time window covers
						// terminals that just stream the key again.
						"PageUp", "PageDown" -> {
							val last = lastPageKey
							// Only the same key repeating counts: flipping between PageUp and PageDown
							// stays immediate. The repeat flag covers terminals with the keyboard
							// protocol; the elapsed-time window covers terminals that just stream the
							// key again without tagging it.
							val repeating = event.repeat ||
								(last != null && last.first == event.key && last.second.elapsedNow() < PageKeyRepeatWindow)
							if (repeating) {
								true
							} else {
								lastPageKey = event.key to TimeSource.Monotonic.markNow()
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
