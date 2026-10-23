package example.screens

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.layout.fillMaxSize
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Alignment
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.BoxWithConstraints
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Filler
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Spacer
import com.jakewharton.mosaic.ui.Text
import example.ShowcaseTheme

/**
 * Layout primitives: weight splits spare space, Filler fills remaining space, Spacer reserves
 * fixed space, and BoxWithConstraints reads the incoming constraints to size children by ratio.
 */
@Composable
internal fun LayoutScreen() {
	Column(modifier = Modifier.padding(top = 1, bottom = 1)) {
		Section("1. weight 等宽列（3 列各占 1/3）")
		Row(Modifier.fillMaxWidth()) {
			Cell("W1", Modifier.weight(1f))
			Cell("W2", Modifier.weight(1f))
			Cell("W3", Modifier.weight(1f))
		}

		SpacerLine()
		Section("2. weight 2:1 非等分")
		Row(Modifier.fillMaxWidth()) {
			Cell("两倍宽", Modifier.weight(2f))
			Cell("一倍", Modifier.weight(1f))
		}

		SpacerLine()
		Section("3. Filler 撑满剩余空间 / Spacer 固定宽度")
		Row(Modifier.fillMaxWidth()) {
			Cell("前", Modifier.width(6))
			Spacer(Modifier.width(2))
			Filler('·', modifier = Modifier.weight(1f))
			Spacer(Modifier.width(2))
			Cell("后", Modifier.width(6))
		}

		SpacerLine()
		Section("4. BoxWithConstraints 按 maxWidth 比例（1/2 / 1/3）")
		ProportionalBoxes()
	}
}

@Composable
private fun Cell(text: String, modifier: Modifier = Modifier) {
	Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
		Text(text, color = ShowcaseTheme.body)
	}
}

@Composable
private fun ProportionalBoxes() {
	BoxWithConstraints(Modifier.fillMaxWidth()) {
		// Nested scope lambdas are layout-scope-marked, so read the constraints into locals first.
		val width = maxWidth
		val half = width / 2
		val third = width / 3
		Row(Modifier.fillMaxWidth()) {
			ProportionalCell(half, Alignment.Center) {
				Text("1/2", color = ShowcaseTheme.accent)
			}
			ProportionalCell(third, Alignment.Center) {
				Text("1/3", color = ShowcaseTheme.section)
			}
			ProportionalCell(width - half - third, Alignment.Center) {
				Text("余量", color = ShowcaseTheme.dim)
			}
		}
	}
}

@Composable
private fun ProportionalCell(
	width: Int,
	contentAlignment: Alignment,
	content: @Composable () -> Unit,
) {
	Box(
		Modifier
			.width(width.coerceAtLeast(0))
			.padding(1),
		contentAlignment = contentAlignment,
	) {
		content()
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
