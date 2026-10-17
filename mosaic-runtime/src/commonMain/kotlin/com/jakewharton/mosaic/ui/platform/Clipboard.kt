package com.jakewharton.mosaic.ui.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.jakewharton.mosaic.terminal.ClipboardEvent
import com.jakewharton.mosaic.terminal.Terminal
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Manages access to the terminal's system clipboard over OSC 52.
 *
 * This mirrors the shape of the Compose `LocalClipboard` entry point but targets the terminal:
 * both read and write are suspend functions because a read requires an asynchronous
 * OSC 52 request/reply round trip.
 */
public interface Clipboard {
	/**
	 * Reads the current clipboard text. Returns `null` when the terminal denies the read
	 * (or does not reply within one request window).
	 */
	public suspend fun getText(): String?

	/** Writes [text] to the clipboard. An empty [text] clears it. */
	public suspend fun setText(text: String)
}

public val LocalClipboard: ProvidableCompositionLocal<Clipboard> = staticCompositionLocalOf {
	error("No clipboard provided")
}

/**
 * A [Clipboard] backed by OSC 52 on [terminal]. The frame listener forwards incoming
 * [ClipboardEvent]s here via [onEvent], which satisfies the pending [getText] read.
 */
internal class TermClipboard(
	private val terminal: Terminal,
) : Clipboard {
	private val replies = Channel<ClipboardEvent>(UNLIMITED)

	internal fun onEvent(event: ClipboardEvent) {
		replies.trySend(event)
	}

	override suspend fun getText(): String? {
		terminal.requestClipboardRead()
		return withTimeoutOrNull(2.seconds) { replies.receive() }?.text
	}

	override suspend fun setText(text: String) {
		terminal.writeClipboard(text)
	}
}
