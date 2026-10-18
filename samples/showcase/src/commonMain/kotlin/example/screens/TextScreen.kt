package example.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.height
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Alignment
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextOverflow
import example.ShowcaseTheme
import kotlinx.coroutines.delay

/**
 * Text layout features: per-line alignment within a bounded width, cluster-safe soft wrapping that
 * never splits a glyph, maxLines clipping/ellipsis, and the scroll view window.
 */
@Composable
internal fun TextScreen() {
	Column(modifier = Modifier.padding(top = 1, bottom = 1)) {
		Section("1. textAlign：Start / Center / End")
		AlignedLine("← Start 中文字 123", Alignment.Start)
		AlignedLine("→ Center 中文字 123", Alignment.CenterHorizontally)
		AlignedLine("→ End 中文字 123", Alignment.End)

		SpacerLine()
		Section("2. 多行各自 Center 对齐（不等宽行）")
		Text(
			"标题\n很短\n一段比较长的中文文本用于观察每行对齐",
			modifier = Modifier.fillMaxWidth(),
			textAlign = Alignment.CenterHorizontally,
			color = ShowcaseTheme.body,
		)

		SpacerLine()
		Section("3. scroll 查看窗口（maxLines=3，12 行内容）")
		Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
			Column(modifier = Modifier.weight(1f)) {
				SmallTitle("scroll=0")
				ScrolledBlock(scroll = 0)
			}
			Column(modifier = Modifier.weight(1f)) {
				SmallTitle("scroll=4")
				ScrolledBlock(scroll = 4)
			}
			Column(modifier = Modifier.weight(1f)) {
				SmallTitle("scroll=8")
				ScrolledBlock(scroll = 8)
			}
		}

		SpacerLine()
		Section("4. 自动滚动 + Ellipsis 标记后续内容")
		AutoScrollingBlock()

		SpacerLine()
		Section("5. 字符串截断：maxLines=2，Clip 直接切断 / Ellipsis 加省略号")
		Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
			Column(modifier = Modifier.weight(1f)) {
				SmallTitle("Clip w=10")
				Truncated(overflow = TextOverflow.Clip, width = 10)
			}
			Column(modifier = Modifier.weight(1f)) {
				SmallTitle("Ellipsis w=10")
				Truncated(overflow = TextOverflow.Ellipsis, width = 10)
			}
			Column(modifier = Modifier.weight(1f)) {
				SmallTitle("Ellipsis w=16")
				Truncated(overflow = TextOverflow.Ellipsis, width = 16)
			}
		}
	}
}

private const val TRUNCATION_TEXT =
	"这一段比较长的中英文混合文本用于演示 Clip 与 Ellipsis 的截断差异，超出的行内容不会破坏字素簇。"

@Composable
private fun Truncated(overflow: TextOverflow, width: Int) {
	Text(
		TRUNCATION_TEXT,
		modifier = Modifier.width(width),
		softWrap = true,
		maxLines = 2,
		overflow = overflow,
		color = ShowcaseTheme.body,
	)
}

private const val SCROLL_CONTENT = "第01行 行号一\n" +
	"第02行 行号二\n" +
	"第03行 行号三\n" +
	"第04行 行号四\n" +
	"第05行 行号五\n" +
	"第06行 行号六\n" +
	"第07行 行号七\n" +
	"第08行 行号八\n" +
	"第09行 行号九\n" +
	"第10行 行号十\n" +
	"第11行 行号十一\n" +
	"第12行 行号十二"

@Composable
private fun AlignedLine(content: String, align: Alignment.Horizontal) {
	Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
		Text(
			content,
			modifier = Modifier.weight(1f),
			textAlign = align,
			color = ShowcaseTheme.body,
		)
	}
}

@Composable
private fun ScrolledBlock(scroll: Int) {
	Text(
		SCROLL_CONTENT,
		modifier = Modifier.width(24),
		softWrap = true,
		maxLines = 3,
		scroll = scroll,
		color = ShowcaseTheme.body,
	)
}

@Composable
private fun AutoScrollingBlock() {
	var scroll by remember { mutableIntStateOf(0) }
	LaunchedEffect(Unit) {
		while (true) {
			delay(500)
			scroll = (scroll + 1) % 9
		}
	}
	Text(
		SCROLL_CONTENT,
		modifier = Modifier.width(24),
		softWrap = true,
		maxLines = 3,
		overflow = TextOverflow.Ellipsis,
		scroll = scroll,
		color = ShowcaseTheme.body,
	)
}

@Composable
private fun Section(text: String) {
	Text(text, color = ShowcaseTheme.section)
}

@Composable
private fun SmallTitle(text: String) {
	Text(text, color = ShowcaseTheme.section, modifier = Modifier.height(1))
}

@Composable
private fun SpacerLine() {
	Text("")
}
