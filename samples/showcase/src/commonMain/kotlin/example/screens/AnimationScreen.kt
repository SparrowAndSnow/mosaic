package example.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Alignment
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import example.ShowcaseTheme
import kotlinx.coroutines.delay

/**
 * Time-driven effects through the paced frame loop: a tick counter, a block progress bar, and a
 * blinking cursor stripe, all running at the interactive frame rate without busy-spinning.
 */
@Composable
internal fun AnimationScreen() {
	var tick by remember { mutableIntStateOf(0) }
	var frameCount by remember { mutableIntStateOf(0) }
	LaunchedEffect(Unit) {
		while (true) {
			delay(50)
			tick += 1
			frameCount += 1
		}
	}

	val progress = (tick / 10) % 20 // 0..19 loop
	val filled = progress
	val empty = 20 - progress

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
		val on = (tick % 20) < 10
		Text(if (on) "██████████" else "██████████", color = if (on) ShowcaseTheme.section else ShowcaseTheme.dim)

		SpacerLine()
		Section("4. 每秒帧数过滤说明")
		Text(
			"效果由帧节流驱动：活跃时 ~60fps、空闲 ~20fps，不会空转烧 CPU",
			color = ShowcaseTheme.dim,
		)
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
