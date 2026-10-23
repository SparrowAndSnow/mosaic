package com.jakewharton.mosaic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.drawBehind
import com.jakewharton.mosaic.layout.focusable
import com.jakewharton.mosaic.layout.onFocusChanged
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.layout.onPaste
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.text.grapheme.forEachGraphemeClusterRange
import com.jakewharton.mosaic.text.unicodewidth.strWidth

/**
 * A single-line editable text field. The value is controlled by the caller (mirroring Compose's
 * `value`/`onValueChange` contract), and the field keeps an internal caret position between the
 * grapheme clusters of the text. Caret movement, character attempts with wide/combining clusters,
 * IME text and bracketed paste all insert at the caret; the caret renders as the terminal-native
 * reverse-video block under the character (or the empty cell after the last one).
 *
 * The field participates in focus: it gains focus by clicking it or via Tab traversal (and
 * programmatically through a [com.jakewharton.mosaic.layout.FocusRequester]), and only edits and
 * shows its caret while focused. Unfocused fields ignore keyboard input, so any number of fields
 * can share a screen.
 *
 * @param value the text to display (owned by the caller).
 * @param onValueChange called whenever an edit changes [value].
 * @param enabled when false the field is inert: it renders without a caret, shows [value] as-is
 *   and does not consume key events.
 * @param readOnly when true the field still shows a caret and allows caret movement, but rejects
 *   edits.
 * @param textStyle optional style to apply to the whole line.
 * @param color optional foreground color of the text.
 * @param background optional background color behind the text.
 * @param onDone optional callback invoked on Enter; when absent Enter is not consumed.
 */
@Composable
@MosaicComposable
public fun BasicTextField(
	value: String,
	onValueChange: (String) -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	readOnly: Boolean = false,
	textStyle: TextStyle = TextStyle.Unspecified,
	color: Color = Color.Unspecified,
	background: Color = Color.Unspecified,
	onDone: (() -> Unit)? = null,
) {
	// The caller owns [value], so edits between recompositions cannot trust the closure-captured
	// value: several key events can be delivered inside a single frame budget, and each one would
	// then be applied against the same stale snapshot. Keep an internal mirror that is updated on
	// every edit (snapshot writes are immediately visible to the next read) and reconcile it with
	// the caller's value whenever a recomposition hands down a different string.
	var editableText by remember { mutableStateOf(value) }
	var cursor by remember { mutableIntStateOf(value.length) }
	var focused by remember { mutableStateOf(false) }
	if (editableText != value) {
		editableText = value
		cursor = cursor.coerceIn(0, value.length)
	}

	fun commit(edit: TextEdit) {
		editableText = edit.text
		cursor = edit.cursor
		onValueChange(edit.text)
	}

	val inputModifier = modifier
		.focusable(enabled = enabled)
		.onFocusChanged { state -> focused = state.isFocused }
		.onKeyEvent { event ->
			// Only the focused field consumes keys, so any number of fields can coexist.
			if (!enabled || !focused) {
				return@onKeyEvent false
			}
			// Re-read the live caret and mirror here: events can arrive in a burst before any
			// recomposition, so both must be sampled at dispatch time, never from the closure.
			val caret = cursor.coerceIn(0, editableText.length)
			when (event.key) {
				"ArrowLeft" -> {
					cursor = TextEditing.caretLeft(editableText, caret)
					true
				}

				"ArrowRight" -> {
					cursor = TextEditing.caretRight(editableText, caret)
					true
				}

				"Home" -> {
					cursor = 0
					true
				}

				"End" -> {
					cursor = editableText.length
					true
				}

				"Backspace" -> {
					if (readOnly) {
						true
					} else {
						commit(TextEditing.backspace(editableText, caret))
						true
					}
				}

				"Delete" -> {
					if (readOnly) {
						true
					} else {
						commit(TextEditing.delete(editableText, caret))
						true
					}
				}

				"Enter" -> {
					onDone?.invoke()
					onDone != null
				}

				// Navigation that belongs to the surrounding application, not the field.
				"ArrowUp", "ArrowDown", "PageUp", "PageDown", "Tab", "Escape" -> false

				else -> {
					// A printable edit (single characters for direct typing, whole strings for IME
					// composition). Ctrl combinations stay unhandled so shortcuts keep working.
					if (!readOnly && !event.ctrl && isEditableText(event.key)) {
						commit(TextEditing.insert(editableText, caret, event.key))
						true
					} else {
						false
					}
				}
			}
		}
		.onPaste { text ->
			if (!enabled || readOnly || !focused) {
				false
			} else {
				val caret = cursor.coerceIn(0, editableText.length)
				commit(TextEditing.insert(editableText, caret, text))
				true
			}
		}

	Layout(
		debugInfo = { "BasicTextField(\"$value\")" },
		measurePolicy = {
			// Reserve one trailing column so the caret past the last character stays inside the box.
			layout(strWidth(value) + if (enabled) 1 else 0, 1)
		},
		modifier = inputModifier.drawBehind {
			drawText(0, 0, value, color, background, textStyle)
			if (enabled && focused) {
				val caret = cursor.coerceIn(0, value.length)
				val caretColumn = strWidth(value, 0, caret)
				val caretStyle = textStyle + TextStyle.Invert
				if (caret < value.length) {
					val end = TextEditing.clusterEndAfter(value, caret)
					drawText(0, caretColumn, value.substring(caret, end), color, background, caretStyle)
				} else {
					drawText(0, caretColumn, " ", color, background, caretStyle)
				}
			}
		},
	)
}

/** The result of an edit: the new text plus the caret offset that must follow it. */
internal data class TextEdit(
	val text: String,
	val cursor: Int,
)

/**
 * The edit operations of a single-line field, split out from the composable so every caret/delta
 * rule is plain, testable logic. Caret offsets are character indices that always sit on grapheme
 * cluster boundaries, so wide and combining clusters move and delete as a unit.
 */
internal object TextEditing {
	/** The start index of the grapheme cluster occupying [cursor], or the cluster before it. */
	fun clusterStartBefore(text: CharSequence, cursor: Int): Int {
		var start = 0
		text.forEachGraphemeClusterRange { clusterStart, _ ->
			if (clusterStart >= cursor) {
				return start
			}
			start = clusterStart
		}
		return start
	}

	/** The end index of the grapheme cluster at [cursor], or the first cluster after it. */
	fun clusterEndAfter(text: CharSequence, cursor: Int): Int {
		text.forEachGraphemeClusterRange { _, clusterEnd ->
			if (clusterEnd > cursor) {
				return clusterEnd
			}
		}
		return text.length
	}

	fun insert(text: String, cursor: Int, inserted: String): TextEdit {
		if (inserted.isEmpty()) {
			return TextEdit(text, cursor)
		}
		val merged = text.substring(0, cursor) + inserted + text.substring(cursor)
		return TextEdit(merged, cursor + inserted.length)
	}

	fun backspace(text: String, cursor: Int): TextEdit {
		if (cursor <= 0) {
			return TextEdit(text, cursor)
		}
		val start = clusterStartBefore(text, cursor)
		val shrunk = text.substring(0, start) + text.substring(cursor)
		return TextEdit(shrunk, start)
	}

	fun delete(text: String, cursor: Int): TextEdit {
		if (cursor >= text.length) {
			return TextEdit(text, cursor)
		}
		val end = clusterEndAfter(text, cursor)
		val shrunk = text.substring(0, cursor) + text.substring(end)
		return TextEdit(shrunk, cursor)
	}

	fun caretLeft(text: CharSequence, cursor: Int): Int {
		if (cursor <= 0) {
			return 0
		}
		return clusterStartBefore(text, cursor)
	}

	fun caretRight(text: CharSequence, cursor: Int): Int {
		if (cursor >= text.length) {
			return text.length
		}
		return clusterEndAfter(text, cursor)
	}
}

/** True when [key] carries renderable text (IME composition, paste, single characters). */
private fun isEditableText(key: String): Boolean {
	if (key.isEmpty()) {
		return false
	}
	for (index in key.indices) {
		val char = key[index]
		if (char < ' ' || char == '\u007F') {
			return false
		}
	}
	return true
}
