package com.jakewharton.mosaic.layout

import androidx.compose.runtime.Immutable
import com.jakewharton.mosaic.modifier.Modifier
import dev.drewhamilton.poko.Poko

/**
 * Describes the focus of a node as reported to [Modifier.onFocusChanged]. [hasFocus] is true when
 * the node itself or any descendant holds focus; [isFocused] is true only when the node itself is
 * the focused target. [isCaptured] is always false here (there is no focus-capture concept).
 */
@[Immutable Poko]
public class FocusState(
	public val hasFocus: Boolean,
	public val isFocused: Boolean = hasFocus,
	public val isCaptured: Boolean = false,
)

/**
 * Per-node focus state. Created lazily on the node when the first focus-related modifier appears
 * and reused across recompositions so [hasFocus] survives (the applier reuses the same [MosaicNode]
 * for the same slot). Observers are rebuilt every modifier application to pick up fresh lambdas.
 */
internal class FocusTarget {
	var enabled = true
	var traversable = false
	val observers = mutableListOf<(FocusState) -> Unit>()

	var hasFocus = false
		private set
	var selfFocused = false
		private set

	/** Applies [down] (has-focus-in-subtree) and [self] (is the focused target) in one atomic update,
	 *  notifying observers at most once when either changed. */
	fun setFocus(down: Boolean, self: Boolean) {
		if (hasFocus != down || selfFocused != self) {
			hasFocus = down
			selfFocused = self
			val state = FocusState(hasFocus = hasFocus, isFocused = selfFocused)
			for (observer in observers) {
				observer(state)
			}
		}
	}

	/** Releases focus when the element disappeared from the modifier chain. */
	fun finalize() {
		hasFocus = false
		selfFocused = false
	}
}

internal interface FocusableModifier : Modifier.Element {
	val enabled: Boolean
}

private class FocusableElement(override val enabled: Boolean) : FocusableModifier

internal interface FocusObserverModifier : Modifier.Element {
	val onFocusChanged: (FocusState) -> Unit
}

private class FocusObserverElement(
	override val onFocusChanged: (FocusState) -> Unit,
) : FocusObserverModifier

/**
 * Makes the component participate in focus traversal: Tab / Shift+Tab move focus between focusable
 * components in tree order, and pressing the primary mouse button on one focuses it. When
 * [enabled] is false the component is neither traversable nor click-focusable.
 */
public fun Modifier.focusable(enabled: Boolean = true): Modifier = this then FocusableElement(enabled)

/**
 * Invoked when the component's focus changes: [FocusState.hasFocus] is true when it or a descendant
 * gains focus (and false when fully blurred); [FocusState.isFocused] is true only when this
 * component itself is the focused target. Combine with [focusable] for self focus, or place on an
 * ancestor to observe descendant focus.
 */
public fun Modifier.onFocusChanged(onFocusChanged: (FocusState) -> Unit): Modifier =
	this then FocusObserverElement(onFocusChanged)

/**
 * Requests focus for the component it is applied to, typically from a side effect such as
 * [androidx.compose.runtime.LaunchedEffect] after first composition. Combine with [focusable] on
 * the same component; a request on a non-focusable target is ignored.
 */
public class FocusRequester {
	internal var node: MosaicNode? = null
		private set

	public fun requestFocus() {
		val target = node?.focusTarget ?: return
		if (!target.enabled || !target.traversable) return
		val root = node!!.root()
		root.ensureFocusManager().focus(node)
	}

	internal fun attach(n: MosaicNode) {
		node = n
	}

	internal fun detach() {
		node = null
	}
}

internal interface FocusRequesterModifier : Modifier.Element {
	val requester: FocusRequester
}

private class FocusRequesterElement(override val requester: FocusRequester) : FocusRequesterModifier

/**
 * Requests [FocusRequester.requestFocus] for the component it is applied to. Use together with
 * [focusable].
 */
public fun Modifier.focusRequester(focusRequester: FocusRequester): Modifier =
	this then FocusRequesterElement(focusRequester)

/**
 * Composition-level focus state, created lazily on the composition root. Tracks the single focused
 * target and implements Tab / Shift+Tab traversal, plus clearing focus when the focused node leaves
 * the tree or stops being focusable.
 */
internal class FocusManager {

	var focusedNode: MosaicNode? = null
		private set

	/** Focuses [node], or clears focus when null. Fires [Modifier.onFocusChanged] on each affected target at most once. */
	fun focus(node: MosaicNode?) {
		val old = focusedNode
		if (old === node) return
		focusedNode = node

		// Release the previously focused branch: its own self-focus, plus has-focus for ancestors
		// that are not also ancestors of the new target (those stay lit for the new branch).
		if (old != null) {
			old.focusTarget?.setFocus(down = false, self = false)
			var ancestor = old.parent
			while (ancestor != null && !ancestor.isOnPathTo(node)) {
				ancestor.focusTarget?.setFocus(down = false, self = false)
				ancestor = ancestor.parent
			}
		}

		// Light up the new branch, stopping at the first ancestor that already reports focus.
		if (node != null) {
			node.focusTarget?.setFocus(down = true, self = true)
			var ancestor = node.parent
			while (ancestor != null) {
				val target = ancestor.focusTarget
				if (target != null) {
					if (target.hasFocus) break // shared with the released branch (or already lit)
					target.setFocus(down = true, self = false)
				}
				ancestor = ancestor.parent
			}
		}
	}

	/** True when the event is a focus-navigation key that this manager should consume. */
	fun preKeyEvent(root: MosaicNode, event: KeyEvent): Boolean {
		reconcile(root)
		if (event.key != "Tab" || event.ctrl || event.alt) return false
		if (event.repeat) return true // swallow auto-repeat without spinning through the cycle
		val nodes = ArrayList<MosaicNode>()
		root.collectFocusable(nodes)
		if (nodes.isEmpty()) return false // no focusable: keep legacy Tab behavior
		val current = focusedNode?.let { nodes.indexOf(it) } ?: -1
		val next = when {
			current == -1 -> if (event.shift) nodes.size - 1 else 0
			event.shift -> (current - 1 + nodes.size) % nodes.size
			else -> (current + 1) % nodes.size
		}
		focus(nodes[next])
		return true
	}

	fun focusFromClick(root: MosaicNode, hitPath: List<MosaicNode>) {
		reconcile(root)
		val target = hitPath.firstOrNull {
			it.focusTarget?.let { t -> t.traversable && t.enabled } == true
		} ?: return
		focus(target)
	}

	private fun reconcile(root: MosaicNode) {
		val node = focusedNode ?: return
		val target = node.focusTarget
		val valid = target != null && target.enabled && target.traversable && node.isAttachedTo(root)
		if (!valid) {
			focus(null)
		}
	}
}

/** Pre-order tree walk collecting traversable focus targets. */
internal fun MosaicNode.collectFocusable(out: MutableList<MosaicNode>) {
	val target = focusTarget
	if (target != null && target.traversable && target.enabled) {
		out += this
	}
	for (child in children) {
		child.collectFocusable(out)
	}
}

/** Walks the top-most hit path under [x]/[y], collecting each node (children first, leaf most specific). */
internal fun MosaicNode.collectFocusHitPath(x: Int, y: Int, out: MutableList<MosaicNode>) {
	if (x !in this.x until this.x + width || y !in this.y until this.y + height) return
	for (index in children.indices.reversed()) {
		children[index].collectFocusHitPath(x, y, out)
	}
	out += this
}

/** Returns this tree's root node. */
internal fun MosaicNode.root(): MosaicNode {
	var node: MosaicNode = this
	while (node.parent != null) {
		node = node.parent!!
	}
	return node
}

/** True when [target] is null, this node, or a descendant of this node. */
internal fun MosaicNode.isOnPathTo(target: MosaicNode?): Boolean {
	if (target == null) return false
	var node: MosaicNode? = target
	while (node != null) {
		if (node === this) return true
		node = node.parent
	}
	return false
}
