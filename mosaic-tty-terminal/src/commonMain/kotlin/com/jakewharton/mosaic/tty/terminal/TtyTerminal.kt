package com.jakewharton.mosaic.tty.terminal

import com.jakewharton.finalization.withFinalizationHook
import com.jakewharton.mosaic.terminal.AnsiLevel
import com.jakewharton.mosaic.terminal.CapabilityQueryEvent
import com.jakewharton.mosaic.terminal.ClipboardEvent
import com.jakewharton.mosaic.terminal.CursorPositionEvent
import com.jakewharton.mosaic.terminal.DebugEvent
import com.jakewharton.mosaic.terminal.DecModeReportEvent
import com.jakewharton.mosaic.terminal.DecModeReportEvent.Setting
import com.jakewharton.mosaic.terminal.Event
import com.jakewharton.mosaic.terminal.FocusEvent
import com.jakewharton.mosaic.terminal.KittyGraphicsEvent
import com.jakewharton.mosaic.terminal.KittyKeyboardQueryEvent
import com.jakewharton.mosaic.terminal.KittyNotificationEvent
import com.jakewharton.mosaic.terminal.KittyPointerQueryEvent
import com.jakewharton.mosaic.terminal.OperatingStatusResponseEvent
import com.jakewharton.mosaic.terminal.PrimaryDeviceAttributesEvent
import com.jakewharton.mosaic.terminal.ResizeEvent
import com.jakewharton.mosaic.terminal.SystemThemeEvent
import com.jakewharton.mosaic.terminal.Terminal
import com.jakewharton.mosaic.terminal.TerminalVersionEvent
import com.jakewharton.mosaic.tty.IOException
import com.jakewharton.mosaic.tty.Tty
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow.SUSPEND
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

private class TtyTerminal(
	override val name: String?,
	override val state: Terminal.State,
	override val capabilities: Terminal.Capabilities,
	override val events: ReceiveChannel<Event>,
	private val teardown: () -> Unit,
	private val closeJob: Job,
	private val readerJob: Job,
	private val tty: Tty,
) : Terminal {
	override val interactive get() = true
	override fun close() {
		teardown()
		closeJob.cancel()
		// The reader coroutine is a child of the scope which created the terminal, and the
		// caller frees the tty (and its file descriptors) immediately after close() returns.
		// Until the reader has fully exited it may still be inside a blocking read on those
		// descriptors; freeing them mid-read would surface either EBADF (crash) or, if the
		// descriptor number was reused before the read started, a read which never returns.
		// The reader only stops once it observes an interrupt wake-up, so keep waking it
		// until the job completes, bounded so a stuck reader cannot wedge normal shutdown.
		runBlocking {
			if (withTimeoutOrNull(readerShutdownTimeout) {
					while (!readerJob.isCompleted) {
						runCatching { tty.interruptRead() }
						delay(1)
					}
				} == null
			) {
				// Best effort at the deadline: cancellation unblocks a cooperative reader (for
				// example one suspended on the event channel); the final interrupt wakes one
				// which is blocked inside a native read.
				readerJob.cancel()
				runCatching { tty.interruptRead() }
			}
		}
	}

	override fun requestClipboardRead(selection: ClipboardEvent.Selection) {
		tty.write("${OSC}52;${ClipboardOsc.requestSelection(selection)};?$ST")
	}

	override fun writeClipboard(text: String, selection: ClipboardEvent.Selection) {
		tty.write("${OSC}52;${ClipboardOsc.requestSelection(selection)};${ClipboardOsc.encode(text)}$ST")
	}

	class State(
		override val focused: StateFlow<Boolean>,
		override val theme: StateFlow<Terminal.Theme>,
		override val size: StateFlow<Terminal.Size>,
	) : Terminal.State

	class Capabilities(
		override val ansiLevel: AnsiLevel,
		override val cursorVisibility: Boolean,
		override val focusEvents: Boolean,
		override val inBandResizeEvents: Boolean,
		override val kittyGraphics: Boolean,
		override val kittyKeyboard: Boolean,
		override val kittyNotifications: Boolean,
		override val kittyPointerShape: Boolean,
		override val kittyTextSizingScale: Boolean,
		override val kittyTextSizingWidth: Boolean,
		override val kittyUnderline: Boolean,
		override val graphemeClustering: Boolean,
		override val synchronizedOutput: Boolean,
		override val themeEvents: Boolean,
	) : Terminal.Capabilities
}

internal const val StageDeviceAttributes = 3
internal const val StageCapabilityQueries = 2
internal const val StageDefaultQueries = 1
internal const val StageNormalOperation = 0

@OptIn(ExperimentalAtomicApi::class)
public suspend fun Tty.asTerminalIn(
	scope: CoroutineScope,
	/** When true, each terminal event will be immediately followed by a matching [DebugEvent]. */
	emitDebugEvents: Boolean = false,
	/** When true, mouse button/press events are reported and dispatched to layouts. */
	mouse: Boolean = false,
	/** When true, bracketed paste mode is enabled and paste content is reported as events. */
	paste: Boolean = false,
	/**
	 * When true, the terminal's alternate screen is entered after raw mode is enabled and left
	 * during teardown. Only a real, bound TTY is ever touched; a non-interactive run never writes
	 * these sequences.
	 */
	alternateScreen: Boolean = false,
): Terminal {
	// Entering raw mode can fail, so perform it before any additional control sequences which change
	// settings. We also need to be in character mode to query capabilities with control sequences.
	enableRawMode()

	// The alternate screen must be entered before the first capability probe (and therefore before
	// any rendering), so probing and drawing never overwrite the user's scrollback even if the
	// terminal never repaints the primary screen afterwards.
	if (alternateScreen) {
		write(alternateScreenEnable)
	}

	val focused = MutableStateFlow(true)
	val theme = MutableStateFlow(Terminal.Theme.Unknown)
	val size = MutableStateFlow(Terminal.Size.Default)

	// Bounded buffer with backpressure: the producer blocks when full instead of dropping events,
	// so bursts (large pastes, mouse storms) can never lose discrete events. High-frequency mouse
	// motion is coalesced downstream in the runtime frame listener.
	val events = Channel<Event>(64, onBufferOverflow = SUSPEND)

	setCallback(EventParserTtyCallback(focused, size, events, emitDebugEvents))

	// Each of these will become true when their respective feature is recognized by the terminal
	// and was not already configured to our desired setting. Revert each toggled setting on exit.
	var toggleCursor = false
	var toggleFocus = false
	var toggleInBandResize = false
	var toggleSystemTheme = false
	var toggleMouse = mouse
	var togglePaste = paste
	var toggleKittyKeyboard = false
	var toggleGraphemeClustering = false

	// Revert every setting we toggled and restore the terminal. Runs exactly once regardless of
	// path: normal close() calls it synchronously so the restore is guaranteed to precede
	// Tty.close(), while the finalization hook below re-invokes it on the signal/shutdown path
	// where close() may never run. The guard keeps the two paths from double-firing.
	val teardownRan = AtomicBoolean(false)
	val teardown: () -> Unit = {
		if (teardownRan.compareAndSet(expectedValue = false, newValue = true)) {
			setCallback(null)
			if (toggleSystemTheme) write(systemThemeDisable)
			if (toggleInBandResize) write(inBandResizeDisable)
			if (toggleFocus) write(focusDisable)
			if (toggleCursor) write(cursorEnable)
			if (toggleMouse) write(mouseDisable)
			if (togglePaste) write(pasteDisable)
			if (toggleKittyKeyboard) write(kittyKeyboardPop)
			if (toggleGraphemeClustering) write(graphemeClusteringDisable)
			// Leave the alternate screen only after every other mode has been restored: the leave
			// sequence itself is the final bytes we are willing to write to this terminal. Any write
			// after it (for example re-showing a cursor) would target the restored primary screen and
			// leave the user's scrollback in a modified state. reset() below only restores termios and
			// signal dispositions and emits no terminal output, so it is deliberately the last step.
			if (alternateScreen) write(alternateScreenDisable)
			reset()
		}
	}

	val interruptJob = scope.launch(Unconfined, start = UNDISPATCHED) {
		withFinalizationHook(
			hook = teardown,
			block = {
				try {
					awaitCancellation()
				} finally {
					// When cancelled (from signal or normally), wake up the reader parse loop so it can
					// exit. Best effort: on some shutdown paths the descriptor may already be closing.
					runCatching { interruptRead() }
				}
			},
		)
	}

	write("${CSI}0c")
	if (mouse) {
		// Setting DEC modes does not produce a reply, so this is safe to do synchronously here
		// before the capability queries begin.
		write(mouseEnable)
	}
	if (paste) {
		// Bracketed paste is also a plain DEC mode without a reply.
		write(pasteEnable)
	}
	val debugBootstrap = env("MOSAIC_TTY_TERMINAL_DEBUG") == "true"
	var stage = StageDeviceAttributes

	var cursorVisibility = false
	var focusEvents = false
	var inBandResizeEvents = false
	var receivedInBandSize = false
	var kittyGraphics = false
	var kittyKeyboard = false
	var kittyNotifications = false
	var graphemeClustering = false
	var kittyPointerShape = false
	var kittyTextSizingScale = false
	var kittyTextSizingWidth = false
	var kittyTextSizingCursorPositionCount = 0
	var kittyTextSizingLastCursorPosition: CursorPositionEvent? = null
	var kittyUnderlines = false
	var synchronizedOutput = false
	var terminalName: String? = null
	var themeEvents = false

	val bootstrapDone = CompletableDeferred<Unit>()
	val readerJob = scope.launch(Dispatchers.IO) {
		val parser = EventParser(this@asTerminalIn)
		while (true) {
			// A read can fail while the descriptor is being torn down (or after a terminal hangup).
			// The terminal is gone at that point, so end the reader quietly instead of letting an
			// uncaught exception escape the caller's scope. Normal EOF arrives via the null break.
			val event = try {
				parser.next()
			} catch (_: IOException) {
				break
			} ?: break
			if (stage != StageNormalOperation && debugBootstrap) {
				write("$event\r\n")
			}
			when (event) {
				is PrimaryDeviceAttributesEvent -> {
					if (stage == StageNormalOperation) continue

					if (event.id == 1) {
						// VT100 terminals can't handle most of the other queries so just bail.
						stage = StageNormalOperation
						bootstrapDone.complete(Unit)
						continue
					}

					stage = StageCapabilityQueries
					write(
						"$CSI?$cursorMode\$p" +
							"$CSI?$focusMode\$p" +
							"$CSI?$synchronizedOutputMode\$p" +
							"$CSI?$systemThemeMode\$p" +
							"$CSI?$inBandResizeMode\$p" +
							"$CSI?$graphemeClusteringMode\$p" +
							"$CSI?996n" + // Theme query
							"${APC}Gi=31,s=1,v=1,a=q,t=d,f=24;AAAA$ST" + // Kitty graphics
							"$CSI?u" + // Kitty keyboard
							"${OSC}99;i=1:p=?$ST" + // Kitty notifications
							"${OSC}22;?__current__$ST" + // Kitty pointer shape
							"${CSI}6n${OSC}66;w=1; $ST${CSI}6n${OSC}66;s=2; $ST${CSI}6n\r" + // Kitty text sizing
							"$DCS+q5375$ST" + // Kitty underline ("Su")
							"$CSI>0q" + // Xterm version
							"${CSI}5n", // DSR (end marker)
					)
				}

				is DecModeReportEvent -> {
					if (stage != StageCapabilityQueries) continue

					when (event.mode) {
						cursorMode -> {
							cursorVisibility = event.setting.canBeChanged
							if (event.setting == Setting.Set) {
								toggleCursor = true
								write(cursorDisable)
							}
						}

						focusMode -> {
							if (event.setting.isSupported) {
								focusEvents = true
								toggleFocus = event.setting == Setting.Reset
								// Enabling focus notification (even if already set) _might_ trigger an initial
								// event. There is otherwise no explicit way to request the initial value.
								write(focusEnable)
							}
						}

						synchronizedOutputMode -> {
							synchronizedOutput = event.setting.canBeChanged
						}

						systemThemeMode -> {
							themeEvents = event.setting.isSupported
							if (event.setting == Setting.Reset) {
								toggleSystemTheme = true
								write(systemThemeEnable)
							}
						}

						inBandResizeMode -> {
							if (event.setting.isSupported) {
								inBandResizeEvents = true
								toggleInBandResize = event.setting == Setting.Reset
								// Enabling in-band resize (even if already set) should trigger an initial event.
								write(inBandResizeEnable)
							}
						}

						graphemeClusteringMode -> {
							if (event.setting.isSupported) {
								graphemeClustering = true
								toggleGraphemeClustering = event.setting == Setting.Reset
								// Enable grapheme clustering so multi-codepoint clusters we emit as a
								// single cell are kept whole (already-active terminals need no change).
								if (event.setting == Setting.Reset) write(graphemeClusteringEnable)
							}
						}
					}
				}

				is OperatingStatusResponseEvent -> {
					if (stage == StageCapabilityQueries) {
						if (focusEvents or inBandResizeEvents) {
							// By enabling these modes (or by sending an explicit default value query after
							// enabling the mode) wait for a reply about the default with a second DSR.
							stage = StageDefaultQueries
							write("${CSI}5n")
						} else {
							stage = StageNormalOperation
							bootstrapDone.complete(Unit)
						}
					} else if (stage == StageDefaultQueries) {
						bootstrapDone.complete(Unit)
					}
				}

				is KittyKeyboardQueryEvent -> {
					if (stage == StageCapabilityQueries) {
						kittyKeyboard = true
						// Activate the push-based keyboard protocol so keys are reported
						// unambiguously. The push does not produce a reply, so it is safe to send
						// synchronously here. On exit we always pop to restore the terminal's
						// prior configuration.
						toggleKittyKeyboard = true
						write(kittyKeyboardPush)
					}
				}

				is KittyGraphicsEvent -> {
					if (stage == StageCapabilityQueries) {
						kittyGraphics = true
					}
				}

				is KittyPointerQueryEvent -> {
					if (stage == StageCapabilityQueries) {
						kittyPointerShape = true
					}
				}

				is KittyNotificationEvent -> {
					if (stage == StageCapabilityQueries) {
						kittyNotifications = true
					}
				}

				is CapabilityQueryEvent -> {
					if (stage == StageCapabilityQueries && event.success) {
						if ("Su" in event.data) {
							kittyUnderlines = true
						}
					}
				}

				is CursorPositionEvent -> {
					if (stage == StageCapabilityQueries) {
						when (kittyTextSizingCursorPositionCount++) {
							1 -> kittyTextSizingWidth = event != kittyTextSizingLastCursorPosition
							2 -> kittyTextSizingScale = event != kittyTextSizingLastCursorPosition
						}
						kittyTextSizingLastCursorPosition = event
					}
				}

				is TerminalVersionEvent -> {
					if (stage == StageCapabilityQueries) {
						terminalName = event.data
					}
				}

				is FocusEvent -> {
					if (focusEvents) {
						focused.value = event.focused
					} else {
						// TODO Report unsolicited focus events... somewhere.
					}
				}

				is ResizeEvent -> {
					if (inBandResizeEvents) {
						receivedInBandSize = true
						size.value = Terminal.Size(event.columns, event.rows, event.width, event.height)
					} else {
						// TODO Report unsolicited resize events... somewhere.
					}
				}

				is SystemThemeEvent -> {
					if (themeEvents || stage == StageCapabilityQueries) {
						theme.value = if (event.isDark) {
							Terminal.Theme.Dark
						} else {
							Terminal.Theme.Light
						}
					} else {
						// TODO Report unsolicited theme events... somewhere.
					}
				}

				else -> {}
			}

			events.send(event)
		}
	}

	// Spend at most 1 second bootstrapping capabilities and defaults. In theory, there could
	// exist a terminal which does not respond to DA1 or DSR. Does that terminal actually work?
	// Who knows, but we don't want to hang forever waiting. Take whatever we got so far
	// (if anything) and move on with rendering.
	val bootstrapComplete = withTimeoutOrNull(1.seconds) {
		bootstrapDone.await()
		true
	} ?: false
	// Capture the stuck stage before it is reset: the notice below needs to report where the
	// bootstrap was abandoned rather than pretending every timeout is identical.
	val bootstrapStuckStage = stage
	stage = StageNormalOperation

	if (debugBootstrap) {
		write("\r\n")
	}

	if (!bootstrapComplete) {
		val confirmed = buildList {
			if (cursorVisibility) add("cursor visibility")
			if (focusEvents) add("focus events")
			if (inBandResizeEvents) add("in-band resize")
			if (kittyGraphics) add("kitty graphics")
			if (kittyKeyboard) add("kitty keyboard")
			if (kittyNotifications) add("kitty notifications")
			if (graphemeClustering) add("grapheme clustering")
			if (kittyPointerShape) add("kitty pointer shape")
			if (kittyTextSizingScale) add("kitty text sizing scale")
			if (kittyTextSizingWidth) add("kitty text sizing width")
			if (kittyUnderlines) add("kitty underline")
			if (synchronizedOutput) add("synchronized output")
			if (themeEvents) add("theme events")
		}
		stderrPrint(bootstrapTimeoutNotice(bootstrapStuckStage, confirmed, debugBootstrap))
	}

	if (!inBandResizeEvents) {
		currentSize().let { (columns, rows) ->
			size.value = Terminal.Size(columns, rows)
		}
		enableWindowResizeEvents()
	} else if (!receivedInBandSize) {
		// Seed with the OS-reported size when the terminal advertises in-band resize support but
		// did not emit an initial size report. A genuine in-band event replaces this with the
		// authoritative (pixel) data once one arrives.
		currentSize().let { (columns, rows) ->
			size.value = Terminal.Size(columns, rows)
		}
	}

	val ansiLevel = detectAnsiLevel()

	return TtyTerminal(
		name = terminalName,
		state = TtyTerminal.State(
			focused = focused,
			theme = theme,
			size = size,
		),
		capabilities = TtyTerminal.Capabilities(
			ansiLevel = ansiLevel,
			cursorVisibility = cursorVisibility,
			focusEvents = focusEvents,
			inBandResizeEvents = inBandResizeEvents,
			kittyGraphics = kittyGraphics,
			kittyKeyboard = kittyKeyboard,
			kittyNotifications = kittyNotifications,
			kittyPointerShape = kittyPointerShape,
			kittyTextSizingScale = kittyTextSizingScale,
			kittyTextSizingWidth = kittyTextSizingWidth,
			kittyUnderline = kittyUnderlines,
			graphemeClustering = graphemeClustering,
			synchronizedOutput = synchronizedOutput,
			themeEvents = themeEvents,
		),
		events = events,
		teardown = teardown,
		closeJob = interruptJob,
		readerJob = readerJob,
		tty = this,
	)
}

// How long TtyTerminal.close() keeps waking the reader loop before giving up and force-cancelling
// it. The reader stops on the first interrupt it observes, so this is normally a single wake-up.
private val readerShutdownTimeout = 1.seconds

private fun Tty.write(s: String) {
	val b = s.encodeToByteArray()
	var offset = 0
	while (offset < b.size) {
		val written = write(b, offset, b.size - offset)
		if (written == -1) throw EofException()
		offset += written
	}
}

internal fun detectAnsiLevel(): AnsiLevel {
	if (!env("NO_COLOR").isNullOrEmpty()) {
		return AnsiLevel.NONE
	}
	val term = env("COLORTERM") ?: env("TERM") ?: "dumb"
	if (term.contains("24bit", ignoreCase = true) || term.contains("truecolor", ignoreCase = true)) {
		return AnsiLevel.TRUECOLOR
	}
	if (term.contains("256")) {
		return AnsiLevel.ANSI256
	}
	if (term != "dumb") {
		return AnsiLevel.ANSI16
	}
	// https://github.com/microsoft/terminal/issues/1040#issuecomment-496691842
	if (env("WT_SESSION") != null) {
		return AnsiLevel.TRUECOLOR
	}
	// Per https://conemu.github.io/en/ConEmuEnvironment.html
	if (env("ConEmuANSI") == "ON") {
		return AnsiLevel.TRUECOLOR
	}
	return AnsiLevel.NONE
}

/**
 * Builds the diagnostic printed to standard error when capability detection does not finish within
 * the one-second bootstrap budget. This makes the silent fall-back observable: a non-responsive
 * terminal otherwise runs with extended capabilities quietly disabled.
 *
 * @param stage the bootstrap [StageDeviceAttributes]/[StageCapabilityQueries]/[StageDefaultQueries]
 *   stage at which the budget expired.
 * @param confirmed the capabilities that were confirmed before the budget expired.
 * @param debug when true the notice additionally lists the confirmed capabilities; when false it
 *   points at the debug flag for chasing the cause.
 */
internal fun bootstrapTimeoutNotice(stage: Int, confirmed: List<String>, debug: Boolean): String {
	val stuck = when (stage) {
		StageDeviceAttributes -> "awaiting an initial device-attributes (DA1) reply"
		StageCapabilityQueries -> "awaiting capability-query replies"
		StageDefaultQueries -> "awaiting default-state query replies"
		else -> "awaiting a reply"
	}
	val headline =
		"Terminal capability detection did not respond within the 1 second budget ($stuck); " +
			"unconfirmed capabilities are disabled."
	return if (!debug) {
		"$headline Set MOSAIC_TTY_TERMINAL_DEBUG=true for per-query detail."
	} else {
		val detail = if (confirmed.isEmpty()) {
			"No capabilities were confirmed."
		} else {
			"Confirmed capabilities: ${confirmed.joinToString()}."
		}
		"$headline\n$detail"
	}
}

private val Setting.isSupported get() = when (this) {
	Setting.NotRecognized -> false
	Setting.Set -> true
	Setting.Reset -> true
	Setting.PermanentlySet -> true
	Setting.PermanentlyReset -> false
}

private val Setting.canBeChanged get() = when (this) {
	Setting.NotRecognized -> false
	Setting.Set -> true
	Setting.Reset -> true
	Setting.PermanentlySet -> false
	Setting.PermanentlyReset -> false
}
