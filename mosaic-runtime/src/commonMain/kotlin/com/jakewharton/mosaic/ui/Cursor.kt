package com.jakewharton.mosaic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.jakewharton.mosaic.layout.MosaicNode
import com.jakewharton.mosaic.layout.MosaicNodeAttach
import com.jakewharton.mosaic.layout.isInActiveFocusScope
import com.jakewharton.mosaic.layout.onGloballyPositioned
import com.jakewharton.mosaic.modifier.Modifier

/** The shape the terminal draws for the hardware cursor. */
public enum class CursorShape {
	/** A filled block covering the whole cell. */
	Block,

	/** A horizontal line under the cell. */
	Underline,

	/** A vertical bar at the left edge of the cell. */
	Bar,
}

/**
 * Places the terminal's hardware cursor at this composable's position.
 *
 * The cursor is anchored by layout rather than by screen arithmetic: composing a [Cursor] after a
 * [Text] inside a [Row] puts the caret at the end of that text, while a [Modifier.offset] selects
 * a specific cell. Padding, alignment, and scrolling therefore move the cursor exactly like they
 * move any other child, and a cursor placed outside the composition root is not shown. Clipping
 * applied by a nested scroll container is not consulted, so keep the cursor inside a visible
 * viewport when composing it within one.
 *
 * Unlike the cell-drawn caret used by [BasicTextField], the hardware cursor is drawn by the
 * terminal itself: it can blink, change shape, and serve as the anchor for IME composition. It is
 * only shown when the terminal reports cursor visibility support; on other terminals this
 * composable is a no-op. When the cursor leaves the composition, the renderer hides it again.
 *
 * At most one cursor should be composed at a time. If several are placed during the same frame,
 * the last one placed wins.
 *
 * The reported position is relative to the composition root. Static log lines occupy the top of
 * the screen above that root, so an app that logs through [com.jakewharton.mosaic.LocalStaticLogger]
 * while also showing a hardware cursor may see it one or more rows above the intended cell; prefer
 * the cell-drawn caret in that situation.
 *
 * @param modifier modifiers for the zero-sized marker, typically [Modifier.offset] to choose the
 *   cell within the parent.
 * @param shape the shape the terminal should draw.
 * @param blink whether the terminal should blink the cursor.
 */
@Composable
public fun Cursor(
	modifier: Modifier = Modifier,
	shape: CursorShape = CursorShape.Block,
	blink: Boolean = true,
) {
	val state = LocalCursor.current ?: return
	val token = remember { Any() }
	SideEffect {
		state.updateStyle(token, shape, blink)
	}
	DisposableEffect(state, token) {
		onDispose {
			state.release(token)
		}
	}
	Spacer(
		modifier
			.then(CursorNodeModifier(state, token))
			.onGloballyPositioned { position, _ ->
				state.place(token, position.x, position.y)
			},
	)
}

/**
 * The cursor request shared between [Cursor] and the composition that turns it into terminal
 * output. Placement and style updates are plain fields (not snapshot state): they always happen
 * during composition, layout, or disposal, and each mutation asks the composition for a draw.
 */
private class CursorNodeModifier(
	private val state: CursorState,
	private val token: Any,
) : MosaicNodeAttach {
	override fun attach(node: MosaicNode) {
		state.attachNode(token, node)
	}

	override fun toString(): String = "CursorNode()"
}

internal class CursorState(
	private val onChanged: () -> Unit,
) {
	var active: Boolean = false
		private set

	var column: Int = 0
		private set

	var row: Int = 0
		private set

	var shape: CursorShape = CursorShape.Block
		private set

	var blink: Boolean = true
		private set

	/** Identifies the [Cursor] instance that most recently placed itself. */
	private var token: Any? = null

	/** Node for each attached [Cursor] token, used to check the active focus domain at draw time. */
	private val nodes = HashMap<Any, MosaicNode>()

	fun attachNode(token: Any, node: MosaicNode) {
		nodes[token] = node
	}

	/** True when this cursor's node is inside the current modal focus domain (or no modal). */
	fun isVisible(root: MosaicNode): Boolean {
		val token = token ?: return false
		val node = nodes[token] ?: return false
		return root.isInActiveFocusScope(node)
	}

	fun place(token: Any, column: Int, row: Int) {
		val changed = !active || this.column != column || this.row != row
		this.token = token
		if (changed) {
			active = true
			this.column = column
			this.row = row
			onChanged()
		}
	}

	fun updateStyle(token: Any, shape: CursorShape, blink: Boolean) {
		if (this.token != null && this.token !== token) return
		if (this.shape != shape || this.blink != blink) {
			this.shape = shape
			this.blink = blink
			onChanged()
		}
	}

	fun release(token: Any) {
		nodes.remove(token)
		if (this.token !== token) return
		this.token = null
		if (active) {
			active = false
			onChanged()
		}
	}
}

/**
 * The cursor state of the composition, or null outside of a Mosaic composition (where [Cursor] is
 * intentionally inert).
 */
internal val LocalCursor = staticCompositionLocalOf<CursorState?> { null }
