package example.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.animation.AnimatedVisibility
import com.jakewharton.mosaic.animation.Crossfade
import com.jakewharton.mosaic.animation.expandVertically
import com.jakewharton.mosaic.animation.shrinkVertically
import com.jakewharton.mosaic.animation.tween
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import example.ShowcaseTheme
import kotlinx.coroutines.delay

/**
 * Time-driven effects through the paced frame loop: a tick counter, a block progress bar, a
 * blinking stripe, plus the animation containers — a crossfade-style page advance and an
 * expand/collapse panel — all running at the interactive frame rate without busy-spinning.
 */
@Composable
internal fun AnimationScreen() {
	var tick by remember { mutableIntStateOf(0) }
	LaunchedEffect(Unit) {
		while (true) {
			delay(50)
			tick += 1
		}
	}

	val progress = (tick / 10) % 20 // 0..19 loop
	val filled = progress
	val empty = 20 - progress
	val on = (tick % 20) < 10
	// Auto-advance: the crossfade page changes every 1.5s, the visibility toggle every 2s.
	val crossfadePage = (tick / 30) % 3
	val expanded = (tick % 80) < 40

	Column(modifier = Modifier.padding(top = 1, bottom = 1)) {
		Section("1. 帧驱动计数器")
		Text("tick = $tick", color = ShowcaseTheme.body)

		SpacerLine()
		Section("2. 进度条（每 500ms 步进 10%）")
		Row(Modifier.fillMaxWidth()) {
			Text(
				"█".repeat(filled) + "░".repeat(empty) + " ${(progress * 5).toString().padStart(3)}%",
				color = ShowcaseTheme.accent,
				modifier = Modifier.weight(1f),
			)
		}

		SpacerLine()
		Section("3. 闪烁光标条（底部）")
		Text("██████████", color = if (on) ShowcaseTheme.section else ShowcaseTheme.dim)

		SpacerLine()
		Section("4. 页面推进（Crossfade，1.5s/页）")
		Crossfade(targetState = crossfadePage, animationSpec = tween(400)) { page ->
			Text(
				when (page) {
					1 -> "页面 2：水平推进切换"
					2 -> "页面 3：旧层滑出、新层滑入"
					else -> "页面 1：帧驱动渲染"
				},
				color = ShowcaseTheme.accent,
			)
		}

		SpacerLine()
		Section("5. 展开 / 收起（AnimatedVisibility，2s 周期）")
		AnimatedVisibility(
			visible = expanded,
			enter = expandVertically(tween(300)),
			exit = shrinkVertically(tween(300)),
		) {
			Column {
				Text("内容面板 ▸ 纵向展开", color = ShowcaseTheme.body)
				Text("收起时高度收缩到 0 并移除", color = ShowcaseTheme.dim)
			}
		}
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
