package com.jakewharton.mosaic.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.unit.Constraints

/**
 * State for [verticalScroll] and [horizontalScroll].
 *
 * The [value] is the current scroll offset in cells along the scroll axis, backed by snapshot
 * state: mutating it invalidates the scrolling layout so the content shifts without recomposition.
 * [maxValue] is updated by the scroll modifier each measure and is the largest offset that still
 * shows content (content size minus viewport size, clamped at zero).
 */
@Stable
public class ScrollState(
	initial: Int = 0,
) {
	/** The current scroll offset in cells; always clamped into `0..maxValue`. */
	public var value: Int by mutableIntStateOf(initial)
		internal set

	/**
	 * The largest offset that still shows content (content size minus viewport size, clamped at
	 * zero), updated by the scroll modifier during measure. A scroll bar shares the viewport with
	 * the scrolled content, so it can render the [value]/[maxValue] ratio.
	 */
	public var maxValue: Int by mutableIntStateOf(0)
		internal set

	/**
	 * Scrolls by [delta] cells, clamping to the valid range, and returns how much was consumed
	 * (may be less than [delta] at the bounds).
	 */
	public fun scrollBy(delta: Int): Int {
		val target = delta.toLong() + value
		val clamped = target.coerceIn(0, maxValue.coerceAtLeast(0).toLong()).toInt()
		val consumed = clamped - value
		value = clamped
		return consumed
	}

	/** Scrolls so [value] becomes [target], clamped to the valid range. */
	public fun scrollTo(target: Int) {
		value = target.coerceIn(0, maxValue.coerceAtLeast(0))
	}

	override fun toString(): String = "ScrollState(value=$value, maxValue=$maxValue)"
}

@Composable
public fun rememberScrollState(initial: Int = 0): ScrollState = remember { ScrollState(initial) }

/**
 * Scroll the content vertically within its container, offsetting the content by the [state] scroll
 * value and clipping it to the container's viewport. The child is measured without a height bound
 * so it lays out at its natural full height; the modifier reports the constrained viewport height
 * (the full content height when the parent leaves it unbounded, in which case nothing scrolls).
 *
 * When [enabled] is false the modifier passes its content through untouched.
 */
@Stable
public fun Modifier.verticalScroll(
	state: ScrollState,
	enabled: Boolean = true,
): Modifier {
	if (!enabled) return this
	// The clip is the outer element so it stays fixed at the scroll container's viewport while the
	// inner offset element shifts the content up; clipping content to itself would scroll the clip
	// along with the content and defeat the effect.
	return this
		.then(ClipToBoundsElement)
		.then(ScrollingLayoutModifier(state, vertical = true))
		.then(ScrollWheelMouseModifier(state))
}

/**
 * Scroll the content horizontally within its container. See [verticalScroll] for the details.
 */
@Stable
public fun Modifier.horizontalScroll(
	state: ScrollState,
	enabled: Boolean = true,
): Modifier {
	if (!enabled) return this
	return this
		.then(ClipToBoundsElement)
		.then(ScrollingLayoutModifier(state, vertical = false))
		.then(ScrollWheelMouseModifier(state))
}

/**
 * Rolls the mouse wheel over a scroll container into scroll movement: each notch advances the
 * [state] by [MouseWheelLines] along the scroll axis (wheel up scrolls toward the start, wheel
 * down toward the end, mirroring the direction of the arrow keys). The handler is attached to the
 * scroll container itself, so any content - a list, a single long line - scrolls under the wheel
 * without the caller wiring up mouse plumbing.
 */
internal class ScrollWheelMouseModifier(
	private val state: ScrollState,
) : MouseModifier {
	override fun onMouseEvent(event: MouseEvent): Boolean {
		if (event.type != MouseEvent.Type.Press) {
			return false
		}
		val delta = when (event.button) {
			MouseEvent.Button.WheelUp -> -MouseWheelLines
			MouseEvent.Button.WheelDown -> MouseWheelLines
			else -> return false
		}
		state.scrollBy(delta)
		return true
	}

	override fun toString(): String = "ScrollWheel"
}

internal const val MouseWheelLines = 3

private class ScrollingLayoutModifier(
	private val state: ScrollState,
	private val vertical: Boolean,
) : LayoutModifier {
	override fun MeasureScope.measure(
		measurable: Measurable,
		constraints: Constraints,
	): MeasureResult {
		val content = if (vertical) {
			measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
		} else {
			measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
		}
		val (viewport, contentSize) = if (vertical) {
			val maxViewport = constraints.maxHeight
			val viewport = if (maxViewport == Constraints.Infinity) content.height else maxViewport
			viewport to content.height
		} else {
			val maxViewport = constraints.maxWidth
			val viewport = if (maxViewport == Constraints.Infinity) content.width else maxViewport
			viewport to content.width
		}
		val maxScroll = maxOf(0, contentSize - viewport)
		state.maxValue = maxScroll
		val scroll = state.value.coerceIn(0, maxScroll)
		if (scroll != state.value) {
			state.value = scroll
		}
		return if (vertical) {
			layout(content.width, viewport) {
				content.place(0, -scroll)
			}
		} else {
			layout(viewport, content.height) {
				content.place(-scroll, 0)
			}
		}
	}

	override fun toString(): String = "Scroll(${if (vertical) "vertical" else "horizontal"})"
}
