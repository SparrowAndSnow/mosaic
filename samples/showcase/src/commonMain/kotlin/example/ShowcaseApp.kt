package example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.LocalTerminalState
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.MouseEvent
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.height
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.layout.onMouseEvent
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.Color
import example.screens.AnimationScreen
import example.screens.InputScreen
import example.screens.LayoutScreen
import example.screens.ScrollScreen
import example.screens.TextScreen
import example.screens.UnicodeScreen
import kotlinx.coroutines.awaitCancellation

/**
 * The showcase shell: a header naming the current page, a page area, and a clickable tab bar.
 * `←`/`→`/`Tab` or `1`-`6` switch pages, mouse press also switches, Ctrl-C exits. Each page is a
 * plain composable so the same content doubles as snapshot-test fodder.
 */
@Composable
internal fun ShowcaseApp() {
	// Keep the demo resident in an interactive loop. Without a never-ending effect, the runtime's
	// run-until-idle contract (awaitComplete) returns once the first frame settles and the process
	// exits right after one paint. Ctrl-C still works: an unhandled ctrl-C cancels the composition.
	LaunchedEffect(Unit) {
		awaitCancellation()
	}

	// The layout engine measures the root with unbounded constraints, so a full-screen app must
	// declare its own canvas from the terminal size (the same pattern the rrtop sample uses).
	// Without this, fillMaxWidth/weight are no-ops (everything collapses to wrap-content width)
	// and proportional layouts derived from an unbounded max divide an infinite width.
	val terminal = LocalTerminalState.current
	var pageIndex by remember { mutableIntStateOf(0) }
	var hoveredPage by remember { mutableStateOf<Page?>(null) }
	val pages = Page.entries

	fun select(index: Int) {
		pageIndex = ((index % pages.size) + pages.size) % pages.size
	}

	Column(
		modifier = Modifier
			.width(terminal.size.columns)
			.height(terminal.size.rows)
			.onKeyEvent { event ->
				when (event.key) {
					"ArrowLeft" -> {
						select(pageIndex - 1)
						true
					}
					"ArrowRight", "Tab" -> {
						select(pageIndex + 1)
						true
					}
				in listOf("1", "2", "3", "4", "5", "6") -> {
						select(event.key[0] - '1')
						true
					}
					else -> false
				}
			},
	) {
		val page = pages[pageIndex]
		Header(page)
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f)
				.padding(horizontal = 1),
		) {
			when (page) {
				Page.Unicode -> UnicodeScreen()
				Page.Text -> TextScreen()
				Page.Layout -> LayoutScreen()
				Page.Input -> InputScreen()
				Page.Animation -> AnimationScreen()
				Page.Scroll -> ScrollScreen()
			}
		}
		TabBar(pages, pageIndex, hoveredPage, onHover = { hoveredPage = it }, onSelect = ::select)
		Text("←/→/Tab 切换  |  点击标签  |  Ctrl-C 退出", color = ShowcaseTheme.dim)
	}
}

@Composable
private fun Header(page: Page) {
	Column {
		Text("Showcase — mosaic 重构能力演示", color = ShowcaseTheme.title)
		Text("当前页: ${page.label}", color = ShowcaseTheme.section)
		Text("══════════════════════════════════════", color = ShowcaseTheme.dim)
	}
}

@Composable
private fun TabBar(
	pages: List<Page>,
	selected: Int,
	hovered: Page?,
	onHover: (Page?) -> Unit,
	onSelect: (Int) -> Unit,
) {
	Row(Modifier.fillMaxWidth().padding(top = 1)) {
		pages.forEachIndexed { index, page ->
			val active = index == selected
			val hovering = hovered == page
			Tab(
				page = page,
				modifier = Modifier
					.weight(1f)
					.onMouseEvent { event ->
						when (event.type) {
							MouseEvent.Type.Motion -> {
								onHover(page)
								true
							}
							MouseEvent.Type.Press -> {
								onSelect(index)
								true
							}
							else -> false
						}
					},
				active = active,
				hovering = hovering,
			)
		}
	}
}

@Composable
private fun Tab(
	page: Page,
	modifier: Modifier,
	active: Boolean,
	hovering: Boolean,
) {
	val background: Color = when {
		active -> ShowcaseTheme.accent
		hovering -> ShowcaseTheme.tabHover
		else -> ShowcaseTheme.dim
	}
	val foreground: Color = when {
		active -> ShowcaseTheme.tabActive
		else -> ShowcaseTheme.body
	}
	Text(
		" ${page.label} ",
		color = foreground,
		background = background,
		modifier = modifier,
	)
}
