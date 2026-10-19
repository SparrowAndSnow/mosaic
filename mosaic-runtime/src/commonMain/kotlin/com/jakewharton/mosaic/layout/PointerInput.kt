package com.jakewharton.mosaic.layout

import androidx.compose.runtime.remember
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.modifier.composed

/**
 * Per-element state backing the pointer-driven modifiers ([hoverable], [clickable]). One instance
 * is remembered per modified element so it survives recomposition and can keep tracking whether the
 * pointer is inside its bounds or the primary button is held over it across events and frames.
 */
internal class PointerInputState {
	var enabled = true
	var onHoverChanged: ((Boolean) -> Unit)? = null
	var onClick: (() -> Unit)? = null

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
	val state = remember { PointerInputState() }
	state.enabled = enabled
	state.onHoverChanged = onHoverChanged
	state.onClick = null
	then(PointerInputElement(state))
}

/**
 * Adds a click interaction to a component: pressing the primary button within its bounds and
 * releasing within its bounds invokes [onClick]. Pressing inside and releasing outside (or on
 * another component) cancels the click. When [enabled] is false the element neither hovers nor
 * clicks. [onClickLabel] is accepted for API compatibility and reserved for accessibility tooling.
 */
public fun Modifier.clickable(
	enabled: Boolean = true,
	onClickLabel: String? = null,
	onClick: () -> Unit,
): Modifier = composed {
	val state = remember { PointerInputState() }
	state.enabled = enabled
	state.onClick = onClick
	state.onHoverChanged = null
	then(PointerInputElement(state))
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
internal class PointerState {

	private val hovered = ArrayList<PointerInputState>()

	private class PressedTarget(
		val state: PointerInputState,
		val node: MosaicNode,
	)
	private var pressed: PressedTarget? = null

	/** Runs before normal mouse dispatch on the composition root for [event]. */
	fun preDispatch(root: MosaicNode, event: MouseEvent) {
		val path = ArrayList<PointerEntry>()
		root.collectPointerEntries(event.x, event.y, path)
		reconcileHover(path)
		handleFocus(root, event)
		handleClick(root, event, path)
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

	private fun handleClick(root: MosaicNode, event: MouseEvent, path: List<PointerEntry>) {
		when (event.type) {
			MouseEvent.Type.Press -> {
				if (event.button != MouseEvent.Button.Left) return
				if (pressed != null) return
				val target = path.firstOrNull { it.state.onClick != null && it.state.enabled } ?: return
				pressed = PressedTarget(target.state, target.node)
				target.state.press()
			}
			MouseEvent.Type.Release -> {
				val target = pressed ?: return
				pressed = null
				target.state.release()
				if (target.state.enabled && target.node.isAttachedTo(root)) {
					val inBounds =
						event.x in target.node.x until target.node.x + target.node.width &&
							event.y in target.node.y until target.node.y + target.node.height
					if (inBounds) {
						target.state.onClick?.invoke()
					}
				}
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
