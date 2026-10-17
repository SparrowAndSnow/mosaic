package com.jakewharton.mosaic.layout

import androidx.compose.runtime.Immutable
import com.jakewharton.mosaic.modifier.Modifier
import dev.drewhamilton.poko.Poko

/**
 * A mouse event dispatched to the layout that is under the pointer. The [x] and [y] coordinates
 * are in absolute canvas (screen cell) coordinates: they match the position reported by the
 * terminal, and can be compared against a node's [com.jakewharton.mosaic.layout.PlacementScope] x/y.
 */
@[Immutable Poko]
public class MouseEvent(
	public val x: Int,
	public val y: Int,
	public val type: Type,
	public val button: Button = Button.None,
	public val shift: Boolean = false,
	public val alt: Boolean = false,
	public val ctrl: Boolean = false,
) {
	public enum class Type {
		Drag,
		Motion,
		Release,
		Press,
	}

	public enum class Button {
		Left,
		Middle,
		Right,
		None,
		WheelUp,
		WheelDown,
		Button8,
		Button9,
		Button10,
		Button11,
	}
}

/**
 * A [Modifier.Element] that lets a layout observe mouse events that occur within its bounds.
 *
 * Dispatched events target the top-most (most specific) layout under the pointer first, then its
 * ancestors in z-order. A handler returning true consumes the event and stops dispatch; returning
 * false lets it continue to the next region or ancestor.
 */
public interface MouseModifier : Modifier.Element {
	public fun onMouseEvent(event: MouseEvent): Boolean
}

/**
 * Adding this [modifier][Modifier] to the [modifier][Modifier] parameter of a component lets it
 * receive mouse events over its bounds. Return true to consume the event and stop propagation.
 */
public fun Modifier.onMouseEvent(
	onMouseEvent: (MouseEvent) -> Boolean,
): Modifier = this then MouseModifierElement(onMouseEvent)

private class MouseModifierElement(
	private val onEvent: (MouseEvent) -> Boolean,
) : MouseModifier {
	override fun onMouseEvent(event: MouseEvent): Boolean = onEvent(event)
}

/** Converts a terminal mouse event into the runtime [MouseEvent] dispatched to layouts. */
internal fun com.jakewharton.mosaic.terminal.MouseEvent.toRuntimeMouseEvent(): MouseEvent = MouseEvent(
	x = x,
	y = y,
	type = when (type) {
		com.jakewharton.mosaic.terminal.MouseEvent.Type.Drag -> MouseEvent.Type.Drag
		com.jakewharton.mosaic.terminal.MouseEvent.Type.Motion -> MouseEvent.Type.Motion
		com.jakewharton.mosaic.terminal.MouseEvent.Type.Release -> MouseEvent.Type.Release
		com.jakewharton.mosaic.terminal.MouseEvent.Type.Press -> MouseEvent.Type.Press
	},
	button = when (button) {
		com.jakewharton.mosaic.terminal.MouseEvent.Button.Left -> MouseEvent.Button.Left
		com.jakewharton.mosaic.terminal.MouseEvent.Button.Middle -> MouseEvent.Button.Middle
		com.jakewharton.mosaic.terminal.MouseEvent.Button.Right -> MouseEvent.Button.Right
		com.jakewharton.mosaic.terminal.MouseEvent.Button.None -> MouseEvent.Button.None
		com.jakewharton.mosaic.terminal.MouseEvent.Button.WheelUp -> MouseEvent.Button.WheelUp
		com.jakewharton.mosaic.terminal.MouseEvent.Button.WheelDown -> MouseEvent.Button.WheelDown
		com.jakewharton.mosaic.terminal.MouseEvent.Button.Button8 -> MouseEvent.Button.Button8
		com.jakewharton.mosaic.terminal.MouseEvent.Button.Button9 -> MouseEvent.Button.Button9
		com.jakewharton.mosaic.terminal.MouseEvent.Button.Button10 -> MouseEvent.Button.Button10
		com.jakewharton.mosaic.terminal.MouseEvent.Button.Button11 -> MouseEvent.Button.Button11
	},
	shift = shift,
	alt = alt,
	ctrl = ctrl,
)
