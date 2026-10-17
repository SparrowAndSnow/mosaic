package com.jakewharton.mosaic.layout

import com.jakewharton.mosaic.modifier.Modifier

/**
 * A [Modifier.Element] that lets a layout observe bracketed-paste content.
 *
 * Paste events are dispatched root-first: a handler on a parent runs before handlers on its
 * children, and the first handler returning true consumes the paste. Returning false lets the
 * paste continue to the next handler in tree order.
 */
public interface PasteModifier : Modifier.Element {
	public fun onPaste(text: String): Boolean
}

/**
 * Adding this [modifier][Modifier] to the [modifier][Modifier] parameter of a component lets it
 * receive bracketed-paste content. Return true to consume the paste and stop propagation.
 *
 * During a paste, individual keystrokes are not dispatched as key events; the full pasted text
 * arrives here as a single [String] once the terminal closes the paste.
 */
public fun Modifier.onPaste(
	onPaste: (text: String) -> Boolean,
): Modifier = this then PasteModifierElement(onPaste)

private class PasteModifierElement(
	private val handler: (String) -> Boolean,
) : PasteModifier {
	override fun onPaste(text: String): Boolean = handler(text)
}
