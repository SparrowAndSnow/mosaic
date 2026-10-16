package com.jakewharton.mosaic

import com.jakewharton.mosaic.terminal.Terminal
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal interface Rendering {
	/**
	 * Render [node] to a single string for display.
	 *
	 * Note: The returned [CharSequence] is only valid until the next call to this function,
	 * as implementations are free to reuse buffers across invocations.
	 */
	fun render(mosaic: Mosaic): CharSequence
}

internal class DebugRendering(
	private val capabilities: Terminal.Capabilities,
	private val systemClock: TimeSource = TimeSource.Monotonic,
) : Rendering {
	private var lastRender: TimeMark? = null

	fun StringBuilder.appendSurface(canvas: TextCanvas) {
		for (row in 0 until canvas.height) {
			canvas.appendRowTo(this, row, capabilities.ansiLevel, capabilities.kittyUnderline)
			append("\r\n")
		}
	}

	override fun render(mosaic: Mosaic): CharSequence {
		var failed = false
		val output = buildString {
			lastRender?.let { lastRender ->
				repeat(50) { append('~') }
				append(" +")
				append(lastRender.elapsedNow())
				append("\r\n")
			}
			lastRender = systemClock.markNow()

			append("NODES:\r\n")
			append(mosaic.dumpNodes().replace("\n", "\r\n"))
			append("\r\n\r\n")

			try {
				mosaic.static()?.let { static ->
					append("STATIC:\r\n")
					append(static)
					append("\r\n\r\n")
				}
			} catch (t: Throwable) {
				failed = true
				append(t.stackTraceToString().replace("\n", "\r\n"))
				append("\r\n")
			}

			append("OUTPUT:\r\n")
			try {
				appendSurface(mosaic.draw())
			} catch (t: Throwable) {
				failed = true
				append(t.stackTraceToString().replace("\n", "\r\n"))
			}
		}
		if (failed) {
			throw RuntimeException("Failed\r\n\r\n$output")
		}
		return output
	}
}

/**
 * Row-level incremental renderer.
 *
 * The rendered row strings from the previous frame are retained so that rows whose content is
 * unchanged can be skipped. Changed rows are reached with an absolute cursor move, cleared with
 * `EL` (`CSI K`) so no stale trailing cells survive, and rewritten in place. Frames whose height
 * differs, or whose static text changed, fall back to redrawing the whole previous area from its
 * top so the screen can never retain stale cells across a layout change.
 */
internal class AnsiIncrementalRendering(
	private val capabilities: Terminal.Capabilities,
) : Rendering {
	private val stringBuilder = StringBuilder(100)
	private val rowBuilder = StringBuilder(200)

	private var lastHeight = 0
	private var lastStatic: String? = null
	private var lastRows: List<String> = emptyList()

	override fun render(mosaic: Mosaic): CharSequence {
		val canvas = mosaic.draw()
		val static = mosaic.static()
		val rows = List(canvas.height) { row -> renderRow(canvas, row) }

		val heightChanged = canvas.height != lastHeight
		val staticChanged = static != lastStatic

		return stringBuilder.apply {
			clear()

			if (capabilities.synchronizedOutput) {
				append(synchronizedOutputEnable)
			}

			if (lastHeight == 0) {
				// First frame: draw everything from the top of the screen, mirroring the full
				// redraw protocol so the initial paint is identical to the previous renderer.
				if (static != null) {
					append(static)
					append("\r\n")
				}
				rows.forEach { append(it).append("\r\n") }
			} else if (heightChanged || staticChanged || static != null) {
				// A layout or static change (or any static text at all, since mixing static and
				// dynamic rows makes absolute cursor positioning unreliable): rewrite the whole
				// previous area from its top.
				var staleLines = lastHeight
				if (staleLines > 0) {
					append(CSI)
					append(staleLines)
					append('F')
				}
				if (static != null) {
					if (staleLines > 0) {
						append(clearDisplay)
						staleLines = 0
					}
					append(static)
					append("\r\n")
				}
				for (row in 0 until canvas.height) {
					if (staleLines-- > 0) {
						append(clearLine)
					}
					append(rows[row])
					append("\r\n")
				}
				if (staleLines > 0) {
					append(clearDisplay)
				}
			} else {
				// Same size, same static text: rewrite only the rows that changed. Absolute cursor
				// positioning means changed rows can be patched in place regardless of the cursor's
				// current location. After writing, the cursor is parked one line below the output
				// (mirroring the trailing carriage returns of a full redraw) so that a later full
				// redraw's relative cursor-up still starts from the same place.
				var changed = false
				for (row in 0 until canvas.height) {
					if (rows[row] != lastRows[row]) {
						changed = true
						append(CSI)
						append(row + 1)
						append(';')
						append(1)
						append('H')
						append(clearLine)
						append(rows[row])
					}
				}
				if (changed) {
					append(CSI)
					append(canvas.height + 1)
					append(';')
					append(1)
					append('H')
				}
			}

			if (capabilities.synchronizedOutput) {
				append(synchronizedOutputDisable)
			}

			lastHeight = canvas.height
			lastStatic = static
			lastRows = rows
		}
	}

	private fun renderRow(canvas: TextCanvas, row: Int): String = rowBuilder.apply {
		setLength(0)
		canvas.appendRowTo(this, row, capabilities.ansiLevel, capabilities.kittyUnderline)
	}.toString()
}

internal class AnsiRendering(
	private val capabilities: Terminal.Capabilities,
) : Rendering {
	private val stringBuilder = StringBuilder(100)
	private var lastHeight = 0

	override fun render(mosaic: Mosaic): CharSequence {
		return stringBuilder.apply {
			clear()

			if (capabilities.synchronizedOutput) {
				append(synchronizedOutputEnable)
			}

			var staleLines = lastHeight
			if (staleLines > 0) {
				// Move to start of previous output.
				append(CSI)
				append(staleLines)
				append('F')
			}

			fun appendSurface(canvas: TextCanvas) {
				for (row in 0 until canvas.height) {
					if (staleLines-- > 0) {
						// We have previously drawn on this line. Clear first to be safe. For terminals which
						// do not support synchronized rendering, this may allow seeing a partial row render.
						append(clearLine)
					}
					canvas.appendRowTo(this, row, capabilities.ansiLevel, capabilities.kittyUnderline)
					append("\r\n")
				}
			}

			mosaic.static()?.let { static ->
				// We don't know the width or height of static strings, and we don't want to waste time
				// parsing them to get an accurate count of each to minimally clear only the lines it will
				// write. Instead, just clear everything to ensure we neither leave old cells nor attempt
				// a clear to end-of-line when the cursor is on the last column (thus clearing that cell).
				if (staleLines > 0) {
					append(clearDisplay)
					staleLines = 0
				}
				append(static)
				append("\r\n")
			}

			val surface = mosaic.draw()
			appendSurface(surface)

			// If the new output contains fewer lines than the last output, clear those old lines.
			if (staleLines > 0) {
				append(clearDisplay)
			}

			if (capabilities.synchronizedOutput) {
				append(synchronizedOutputDisable)
			}

			lastHeight = surface.height
		}
	}
}
