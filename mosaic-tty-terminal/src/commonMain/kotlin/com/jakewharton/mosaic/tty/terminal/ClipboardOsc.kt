package com.jakewharton.mosaic.tty.terminal

import com.jakewharton.mosaic.terminal.ClipboardEvent
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
internal object ClipboardOsc {
	/** Encodes [text] as the base64 payload of an OSC 52 reply/request. */
	fun encode(text: String): String = Base64.encode(text.encodeToByteArray())

	/**
	 * Decodes an OSC 52 payload into text. Returns `""` for an empty payload (empty clipboard),
	 * `null` for content that is not valid base64/UTF-8.
	 */
	fun decode(payload: String): String? = runCatching {
		if (payload.isEmpty()) {
			""
		} else {
			Base64.decode(payload).decodeToString()
		}
	}.getOrNull()

	fun parseSelection(value: String): ClipboardEvent.Selection = when (value) {
		"c" -> ClipboardEvent.Selection.Clipboard
		"p" -> ClipboardEvent.Selection.Primary
		else -> ClipboardEvent.Selection.Unknown
	}

	/** The selection code used in an OSC 52 request; [ClipboardEvent.Selection.Unknown] falls back to clipboard. */
	fun requestSelection(selection: ClipboardEvent.Selection): String = when (selection) {
		ClipboardEvent.Selection.Clipboard -> "c"
		ClipboardEvent.Selection.Primary -> "p"
		ClipboardEvent.Selection.Unknown -> "c"
	}
}
