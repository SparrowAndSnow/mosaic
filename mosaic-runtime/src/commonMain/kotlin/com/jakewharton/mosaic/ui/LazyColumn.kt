@file:JvmName("LazyColumn")

package com.jakewharton.mosaic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.LocalTerminalState
import com.jakewharton.mosaic.layout.ClipToBoundsElement
import com.jakewharton.mosaic.layout.Measurable
import com.jakewharton.mosaic.layout.MeasurePolicy
import com.jakewharton.mosaic.layout.MeasureResult
import com.jakewharton.mosaic.layout.MeasureScope
import com.jakewharton.mosaic.layout.ParentDataModifier
import com.jakewharton.mosaic.layout.Placeable
import com.jakewharton.mosaic.layout.ScrollState
import com.jakewharton.mosaic.layout.ScrollWheelMouseModifier
import com.jakewharton.mosaic.layout.height
import com.jakewharton.mosaic.layout.rememberScrollState
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.unit.Constraints
import kotlin.jvm.JvmName
import kotlin.math.max
import kotlin.math.min

/**
 * A scrollable, vertically-arranged widget that only composes the items in (and slightly around)
 * its visible viewport instead of the whole list. Combined with [ScrollState] it behaves like the
 * general scroll container, but for a list with arbitrarily many items it keeps both composition
 * and measurement bounded by the window: composition cost is O(viewport/itemHeight + lookahead),
 * independent of the total item count.
 *
 * Items are laid out in fixed-height slots of [itemHeight] cells (default one cell, a text row).
 * The slot width is the widest composed item. Scroll position is managed by [state]; [itemHeight]
 * is the sole mapping between pixels and item indices, so content taller than [itemHeight] must be
 * split or the items must report [itemHeight] as their height (an item wrapped in a fixed-height
 * box clips any overflow).
 *
 * The list scrolls with the mouse wheel (three cells per notch, mirroring the arrow-key
 * direction) and clips its content to the viewport, just like the general scroll modifier.
 *
 * Usage:
 * ```
 * val state = rememberScrollState()
 * LazyColumn(state = state, itemHeight = 1, modifier = Modifier.fillMaxWidth().height(10)) {
 *     items(1000) { index ->
 *         Text("Row $index")
 *     }
 * }
 * ```
 *
 * @param state The scroll state; the offset is clamped into `0..maxValue` where `maxValue` is
 *   `totalHeight - viewport`.
 * @param itemHeight The height, in cells, of every item slot.
 * @param beforeLookahead How many extra items above the viewport to compose (hidden by the clip).
 * @param afterLookahead How many extra items below the viewport to compose.
 * @param content The list contents, built through [LazyListScope.items].
 */
@Composable
public fun LazyColumn(
	modifier: Modifier = Modifier,
	state: ScrollState = rememberScrollState(),
	itemHeight: Int = 1,
	beforeLookahead: Int = 2,
	afterLookahead: Int = 2,
	content: @Composable LazyListScope.() -> Unit,
) {
	require(itemHeight > 0) { "itemHeight must be positive, was $itemHeight" }

	val scope = remember { LazyListScopeImpl() }
	scope.entries.clear()
	scope.totalCount = 0
	content(scope)
	val totalCount = scope.totalCount

	// First measured viewport height, guessed until the first measure teaches the real one. The
	// write-back from the measure policy is snapshot state, so composing the window against the
	// last known viewport stays correct across resizes in the next frame.
	val viewportGuess = LocalTerminalState.current.size.rows.coerceAtLeast(1)
	val viewport = remember(itemHeight) { mutableIntStateOf(viewportGuess) }

	val scroll = state.value
	val knownViewport = viewport.intValue
	val slotsOnScreen = if (knownViewport > 0) knownViewport / itemHeight else 1
	val firstVisible = (scroll / itemHeight - beforeLookahead).coerceAtLeast(0)
	val lastExclusive = minOf(totalCount, firstVisible + slotsOnScreen + beforeLookahead + afterLookahead)

	val measurePolicy = remember(state, itemHeight, totalCount, viewport) {
		LazyColumnMeasurePolicy(state, itemHeight, totalCount, viewport)
	}

	Layout(
		content = {
			for (entry in scope.entries) {
				val from = maxOf(entry.startIndex, firstVisible)
				val to = minOf(entry.startIndex + entry.count, lastExclusive)
				for (index in from until to) {
					Box(
						modifier = Modifier.height(itemHeight).then(LazyIndexModifier(index)),
					) {
						entry.itemContent(index)
					}
				}
			}
		},
		modifier = modifier
			.then(ClipToBoundsElement)
			.then(ScrollWheelMouseModifier(state)),
		debugInfo = { "LazyColumn(total=$totalCount, composed=${(lastExclusive - firstVisible).coerceAtLeast(0)})" },
		measurePolicy = measurePolicy,
	)
}

/**
 * DSL for describing the contents of a [LazyColumn]. Entries are appended in order and each item
 * occupies one [itemHeight] slot, so a multi-line list is written as several [items] blocks.
 */
public interface LazyListScope {
	/** Adds [count] items whose content is produced by [itemContent] for each index. */
	public fun items(count: Int, itemContent: @Composable (index: Int) -> Unit)

	/** Adds one item per element of [items], rendering each with [itemContent]. */
	public fun <T> items(items: List<T>, itemContent: @Composable (item: T) -> Unit)
}

internal class LazyListScopeImpl : LazyListScope {
	internal val entries = ArrayList<LazyListEntry>()
	internal var totalCount = 0

	override fun items(count: Int, itemContent: @Composable (index: Int) -> Unit) {
		require(count >= 0) { "item count must be non-negative, was $count" }
		if (count == 0) return
		entries += LazyListEntry(totalCount, count, itemContent)
		totalCount += count
	}

	override fun <T> items(items: List<T>, itemContent: @Composable (item: T) -> Unit) {
		if (items.isEmpty()) return
		val start = totalCount
		entries += LazyListEntry(start, items.size) { index -> itemContent(items[index - start]) }
		totalCount += items.size
	}
}

internal class LazyListEntry(
	internal val startIndex: Int,
	internal val count: Int,
	internal val itemContent: @Composable (index: Int) -> Unit,
)

/**
 * Carries each composed item's global index on its node so the measure policy knows which slot it
 * occupies without re-deriving the window. Set on the box wrapping [itemContent].
 */
private class LazyIndexModifier(
	private val index: Int,
) : ParentDataModifier {
	override fun modifyParentData(parentData: Any?): Any = ((parentData as? LazyParentData) ?: LazyParentData()).also { it.index = index }

	override fun toString(): String = "LazyIndex($index)"
}

internal class LazyParentData(
	var index: Int = 0,
)

private class LazyColumnMeasurePolicy(
	private val state: ScrollState,
	private val itemHeight: Int,
	private val totalCount: Int,
	private val viewport: androidx.compose.runtime.MutableIntState,
) : MeasurePolicy {
	override fun MeasureScope.measure(
		measurables: List<Measurable>,
		constraints: Constraints,
	): MeasureResult {
		val totalHeight = totalCount.toLong() * itemHeight
		val maxViewport = constraints.maxHeight
		val measuredViewport = if (maxViewport == Constraints.Infinity) {
			// Unbounded parent: there is no window to be lazy against; treat the whole list as the
			// viewport so the composed window covers everything and nothing scrolls.
			totalHeight.coerceAtLeast(1).toInt()
		} else {
			maxViewport
		}
		if (viewport.intValue != measuredViewport) {
			viewport.intValue = measuredViewport
		}
		val maxScroll = max(0L, totalHeight - measuredViewport)
		state.maxValue = maxScroll.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
		val clampedScroll = state.value.coerceIn(0, maxScroll.toInt())
		if (clampedScroll != state.value) {
			state.value = clampedScroll
		}

		var width = 0
		val placeables = ArrayList<Pair<Placeable, Int>>(measurables.size)
		for (measurable in measurables) {
			val placeable = measurable.measure(
				constraints.copy(
					minWidth = 0,
					minHeight = itemHeight,
					maxHeight = itemHeight,
				),
			)
			width = max(width, placeable.width)
			val index = (measurable.parentData as? LazyParentData)?.index ?: 0
			placeables += placeable to index
		}

		return layout(width, measuredViewport) {
			for ((placeable, index) in placeables) {
				placeable.place(0, index * itemHeight - clampedScroll)
			}
		}
	}
}
