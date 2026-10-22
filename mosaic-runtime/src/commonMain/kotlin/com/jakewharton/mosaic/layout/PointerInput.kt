package com.jakewharton.mosaic.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.modifier.composed
import com.jakewharton.mosaic.nanoTime

/**
 * Per-element state backing the pointer-driven modifiers ([hoverable], [clickable]). One instance
 * is remembered per modified element so it survives recomposition and can keep tracking whether the
 * pointer is inside its bounds or the primary button is held over it across events and frames.
 */
internal class PointerInputState {
	var enabled = true
		set(value) {
			field = value
			if (!value) {
				resetDoubleClick()
			}
		}
	var onHoverChanged: ((Boolean) -> Unit)? = null
	var onClick: (() -> Unit)? = null
	var onDoubleClick: (() -> Unit)? = null

	// A tracker per element. The index passed to it is constant because the element identity is
	// this state object, so releases on different elements can never pair with one another.
	private var doubleClickTracker: ClickTracker? = null

	var isHovered = false
		private set
	var isPressed = false
		private set

	fun enter() {
		if (!isHovered) {
			isHovered = true
			onHoverChanged?.invoke(true)
		}
	}

	fun exit() {
		if (isHovered) {
			isHovered = false
			onHoverChanged?.invoke(false)
		}
	}

	fun press() {
		if (!isPressed) {
			isPressed = true
		}
	}

	fun release() {
		if (isPressed) {
			isPressed = false
		}
	}

	/**
	 * Invokes the click callback for a completed press/release pair at [nowMillis].
	 *
	 * The first release of an opted-in pair invokes [onClick]; a second release within the
	 * [ClickTracker] window invokes [onDoubleClick] instead of a second [onClick]. A third quick
	 * release starts a new pair. Without a double-click callback every release is a click.
	 */
	fun click(nowMillis: Long) {
		val onClick = onClick ?: return
		val onDoubleClick = onDoubleClick
		if (onDoubleClick == null) {
			onClick()
			return
		}
		val tracker = doubleClickTracker ?: ClickTracker().also { doubleClickTracker = it }
		if (tracker.press(index = 0, nowMillis = nowMillis)) {
			// Start a fresh pair so a third quick release is again a single click.
			doubleClickTracker = null
			onDoubleClick()
		} else {
			onClick()
		}
	}

	fun resetDoubleClick() {
		doubleClickTracker = null
	}

	fun dispose() {
		enabled = false
	}
}

/**
 * Marker implemented by elements that drive pointer interaction. Recognized during modifier folding
 * so the element's [state] is collected onto its node for hit-testing by [PointerState].
 */
internal interface PointerInputModifier : Modifier.Element {
	val state: PointerInputState
}

private class PointerInputElement(
	override val state: PointerInputState,
) : PointerInputModifier

/**
 * Adds a hover interaction to a component: [onHoverChanged] is called with `true` when the pointer
 * enters its bounds and `false` when it leaves (including when the pointer moves to empty space or
 * the element is removed). Leave is reported on the next mouse event, because a stationary pointer
 * produces no events. The element does not consume mouse events.
 */
public fun Modifier.hoverable(
	enabled: Boolean = true,
	onHoverChanged: (Boolean) -> Unit,
): Modifier = composed {
	val state = rememberPointerInputState()
	state.enabled = enabled
	state.onHoverChanged = onHoverChanged
	state.onClick = null
	state.onDoubleClick = null
	state.resetDoubleClick()
	then(PointerInputElement(state))
}

/**
 * Adds a click interaction to a component: pressing the primary button within its bounds and
 * releasing within its bounds invokes [onClick]. Pressing inside and releasing outside (or on
 * another component) cancels the click. When [enabled] is false the element neither hovers nor
 * clicks. [onClickLabel] is accepted for API compatibility and reserved for accessibility tooling.
 *
 * When [onDoubleClick] is non-null, a second release on the same element within the double-click
 * window (400 milliseconds, measured between releases) invokes [onDoubleClick] instead of a second
 * [onClick]. The first release still invokes [onClick], so a double click reports one single click
 * followed by one double click. Releases farther apart each invoke [onClick]. A third release
 * within the window starts a new pair. When [onDoubleClick] is null every release invokes
 * [onClick].
 *
 * Detection is per element: a release on one clickable never pairs with a release on another, and
 * disabling the element or removing it from the composition suppresses both callbacks and clears
 * any pending pair.
 *
 * Press/release capture does not depend on the node instance holding the element: if layout or
 * recomposition replaces that node while the button is held, the release still invokes the
 * callbacks as long as the element itself is still enabled and the pointer is within the bounds
 * captured at press time. Elements removed from the composition do not fire.
 */
public fun Modifier.clickable(
	enabled: Boolean = true,
	onClickLabel: String? = null,
	onDoubleClick: (() -> Unit)? = null,
	onClick: () -> Unit,
): Modifier = composed {
	val state = rememberPointerInputState()
	state.enabled = enabled
	state.onClick = onClick
	state.onDoubleClick = onDoubleClick
	if (onDoubleClick == null) {
		state.resetDoubleClick()
	}
	state.onHoverChanged = null
	then(PointerInputElement(state))
}

@Composable
private fun rememberPointerInputState(): PointerInputState {
	val state = remember { PointerInputState() }
	DisposableEffect(state) {
		onDispose { state.dispose() }
	}
	return state
}

/**
 * A state and its owning node collected along the hit path under the pointer.
 */
internal class PointerEntry(
	val state: PointerInputState,
	val node: MosaicNode,
)

/**
 * Cross-node pointer input session owned by the composition root. Reconciles hover enter/exit and
 * performs press→release capture for [clickable] across mouse events, both of which need state that
 * outlives a single node's dispatch (leave happens on later events; release can land outside the
 * pressed bounds).
 */
internal class PointerState(
	private val nowMillis: () -> Long = { nanoTime() / 1_000_000 },
) {

	private val hovered = ArrayList<PointerInputState>()

	/** A captured press: the element state plus its bounds at press time. */
	private class PressedTarget(
		val state: PointerInputState,
		val left: Int,
		val top: Int,
		val right: Int,
		val bottom: Int,
	)
	private var pressed: PressedTarget? = null

	/** Runs before normal mouse dispatch on the composition root for [event]. */
	fun preDispatch(root: MosaicNode, event: MouseEvent) {
		val path = ArrayList<PointerEntry>()
		root.collectPointerEntries(event.x, event.y, path)
		reconcileHover(path)
		handleFocus(root, event)
		handleClick(event, path)
	}

	private fun handleFocus(root: MosaicNode, event: MouseEvent) {
		if (event.type != MouseEvent.Type.Press || event.button != MouseEvent.Button.Left) return
		val hitPath = ArrayList<MosaicNode>()
		root.collectFocusHitPath(event.x, event.y, hitPath)
		root.ensureFocusManager().focusFromClick(root, hitPath)
	}

	private fun reconcileHover(path: List<PointerEntry>) {
		// Exit previously-hovered states that are no longer under the pointer or are now disabled.
		var i = hovered.size - 1
		while (i >= 0) {
			val state = hovered[i]
			if (!state.enabled || path.none { it.state === state }) {
				state.exit()
				hovered.removeAt(i)
			}
			i--
		}
		// Enter enabled states now under the pointer.
		for (entry in path) {
			val state = entry.state
			if (state.enabled && !hovered.contains(state)) {
				state.enter()
				hovered += state
			}
		}
	}

	private fun handleClick(event: MouseEvent, path: List<PointerEntry>) {
		when (event.type) {
			MouseEvent.Type.Press -> {
				if (event.button != MouseEvent.Button.Left) return
				if (pressed != null) return
				val target = path.firstOrNull { it.state.onClick != null && it.state.enabled } ?: return
				val node = target.node
				pressed = PressedTarget(
					state = target.state,
					left = node.x,
					top = node.y,
					right = node.x + node.width,
					bottom = node.y + node.height,
				)
				target.state.press()
			}
			MouseEvent.Type.Release -> {
				val target = pressed ?: return
				pressed = null
				target.state.release()
				val inBounds = event.x in target.left until target.right &&
					event.y in target.top until target.bottom
				if (!target.state.enabled || !inBounds) {
					target.state.resetDoubleClick()
					return
				}
				target.state.click(nowMillis())
			}
			else -> {}
		}
	}
}

/** Walks the hit path under [x]/[y], collecting each node's pointer inputs (children first). */
internal fun MosaicNode.collectPointerEntries(x: Int, y: Int, out: MutableList<PointerEntry>) {
	if (x !in this.x until this.x + width || y !in this.y until this.y + height) return
	for (index in children.indices.reversed()) {
		children[index].collectPointerEntries(x, y, out)
	}
	val inputs = pointerInputs
	if (inputs.isNotEmpty()) {
		for (state in inputs) {
			out += PointerEntry(state, this)
		}
	}
}

/** True if this node is still present in the tree rooted at [root]. */
internal fun MosaicNode.isAttachedTo(root: MosaicNode): Boolean {
	var node: MosaicNode? = this
	while (node != null) {
		if (node === root) return true
		node = node.parent
	}
	return false
}
