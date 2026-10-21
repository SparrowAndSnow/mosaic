package com.jakewharton.mosaic

import com.jakewharton.mosaic.terminal.Terminal
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal interface Rendering {
	/**
	 * Whether any frame displayed the hardware cursor. The composition uses this to restore the
	 * terminal's default cursor shape on the way out, since the shape is terminal state that
	 * outlives the last frame.
	 */
	val usedHardwareCursor: Boolean get() = false

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
 * Cell-diff incremental renderer.
 *
 * The previous frame's cell grid is retained so the current frame can be diffed cell by cell.
 * Rows whose content is unchanged are skipped entirely. For changed rows, changed columns are
 * grouped into runs that are expanded to whole grapheme clusters and patched in place with an
 * absolute cursor move, so an unchanged prefix and suffix are never re-emitted. When a change
 * spans most of a row (notably a truncation that would otherwise emit a long run of spaces), the
 * row is instead rewritten whole from the clear. Frames whose size differs, or whose static text
 * appears or changes, fall back to redrawing the whole previous area from its top so the screen
 * can never retain stale cells across a layout change.
 * Each frame is bracketed by a synchronized-output update when the terminal supports it; otherwise
 * it is bracketed by a cursor hide/show so the cursor's travel across rows during a live repaint
 * does not add its own flicker.
 */
internal class AnsiIncrementalRendering(
	private val capabilities: Terminal.Capabilities,
) : Rendering {
	private val stringBuilder = StringBuilder(100)
	private val runs = ArrayList<Int>(16)

	private var prevWidth = 0
	private var prevHeight = 0
	private var prevCells = emptyArray<TextPixel>()
	private var lastStatic: String? = null

	// Hardware-cursor bookkeeping. Once an app requests a cursor, this renderer owns the cursor's
	// visibility: when the request disappears the cursor stays hidden instead of being parked at
	// the renderer's usual resting position. Apps that never request a cursor keep the old behavior.
	private var cursorManaged = false
	private var cursorUsed = false
	private var cursorShown = false
	private var cursorRow = -1
	private var cursorColumn = -1
	private var lastCursorShapeCode = 0

	override val usedHardwareCursor: Boolean get() = cursorUsed

	override fun render(mosaic: Mosaic): CharSequence {
		val surface = mosaic.draw() as TextSurface
		val static = mosaic.static()
		val width = surface.width
		val height = surface.height

		val cursorShape = if (capabilities.cursorVisibility) surface.cursorShape else null
		val cursorBlink = surface.cursorBlink
		val cursorRequested = cursorShape != null
		if (cursorRequested) {
			cursorManaged = true
		}
		var targetRow = surface.cursorRow
		var targetColumn = surface.cursorColumn
		val cursorTarget = cursorRequested &&
			targetRow in 0 until height &&
			targetColumn in 0 until width
		if (cursorTarget) {
			// A hardware cursor cannot sit on the continuation column of a wide cluster; snap left
			// to the cell that owns the glyph.
			while (targetColumn > 0 && surface.cells[targetRow * width + targetColumn].isPlaceholder) {
				targetColumn--
			}
		}

		val firstFrame = prevHeight == 0
		val sizeChanged = prevWidth != width || prevHeight != height
		val staticChanged = static != lastStatic

		return stringBuilder.apply {
			clear()

			val sync = capabilities.synchronizedOutput
			if (sync) {
				append(synchronizedOutputEnable)
			}

			// When the terminal cannot synchronize output it repaints live as we draw, so the
			// cursor's travel across rows adds its own flicker on top of the repaint. Wrapping
			// the frame in a hide/show keeps the cursor invisible until the frame is complete.
			val wrapCursor = !sync && capabilities.cursorVisibility
			val frameStart = length
			val contentStart = if (wrapCursor) {
				append(hideCursor)
				length
			} else {
				length
			}

			if (firstFrame) {
				// First frame: the app takes over the whole screen. Real terminals rarely have the
				// cursor parked at the top-left (a shell prompt usually leaves it lower down), so
				// clear any pre-existing content and move to the top-left before drawing; otherwise
				// the initial paint overlaps whatever the shell left on screen. Subsequence frames
				// then diff incrementally within the now-owned display.
				append(clearDisplay)
				append(CSI)
				append('H')
				if (static != null) {
					append(static)
					append("\r\n")
				}
				// Rows are CRLF-separated but never CRLF-terminated: a trailing newline after the
				// last row scrolls the terminal up a line, desyncing every later absolute-positioned
				// diff (stale rows, doubled headers, orphaned tab bars).
				for (row in 0 until height) {
					if (row > 0) {
						append("\r\n")
					}
					surface.appendRowTo(this, row, capabilities.ansiLevel, capabilities.kittyUnderline)
				}
			} else if (sizeChanged || staticChanged || static != null) {
				// A layout or static change (or any static text at all, since mixing static and
				// dynamic rows makes absolute cursor positioning unreliable): rewrite the whole
				// previous dynamic area from its top, moving relative to the current cursor
				// because printed static output may have scrolled the screen between frames.
				var rowsReplaced = 0
				var rowsEmitted = 0
				if (prevHeight > 0) {
					append(CSI)
					append(prevHeight)
					append('F')
				}
				static?.let { staticText ->
					// Clear each line the static text occupies before rewriting it, charging it
					// against the old rows being replaced so shorter content never leaves residue.
					// These lines are CRLF-separated but never CRLF-terminated (see first frame).
					staticText.lineSequence().forEach { staticLine ->
						if (rowsEmitted > 0) {
							append("\r\n")
						}
						if (rowsReplaced < prevHeight) {
							append(clearLine)
							rowsReplaced++
						}
						append(staticLine.trimEnd('\r'))
						rowsEmitted++
					}
				}
				for (row in 0 until height) {
					if (rowsEmitted > 0) {
						append("\r\n")
					}
					if (rowsReplaced < prevHeight) {
						append(clearLine)
						rowsReplaced++
					}
					surface.appendRowTo(this, row, capabilities.ansiLevel, capabilities.kittyUnderline)
					rowsEmitted++
				}
				// Clear any leftover old rows below the redrawn block line by line instead of
				// erasing the whole display, so unrelated content off-screen stays intact.
				while (rowsReplaced < prevHeight) {
					if (rowsEmitted > 0) {
						append("\r\n")
					}
					append(clearLine)
					rowsEmitted++
					rowsReplaced++
				}
			} else {
				// Same size, no static text: rewrite only the cells that changed, leaving the
				// cursor parked one line below the output so a later full redraw's relative
				// cursor-up still starts from the same place.
				var changed = false
				for (row in 0 until height) {
					if (renderRowDiff(this, surface, row, width)) {
						changed = true
					}
				}
				if (changed) {
					append(CSI)
					append(height + 1)
					append(';')
					append(1)
					append('H')
				}
			}

			if (cursorManaged) {
				if (cursorRequested && cursorTarget) {
					val shapeCode = cursorShapeCode(cursorShape!!, cursorBlink)
					val moved = !cursorShown || targetRow != cursorRow || targetColumn != cursorColumn
					// The drawing pass leaves the cursor wherever the last write landed, so a frame
					// that wrote anything must re-assert the position. In the non-synchronized path
					// the frame is bracketed by hide/show, which also requires re-showing every time.
					if (moved || length > contentStart || wrapCursor) {
						append(CSI)
						append(targetRow + 1)
						append(';')
						append(targetColumn + 1)
						append('H')
					}
					if (shapeCode != lastCursorShapeCode) {
						append(cursorShapeSequence(shapeCode))
						lastCursorShapeCode = shapeCode
					}
					if (!cursorShown || wrapCursor) {
						append(showCursor)
					}
					cursorShown = true
					cursorUsed = true
					cursorRow = targetRow
					cursorColumn = targetColumn
				} else if (cursorShown) {
					append(hideCursor)
					if (lastCursorShapeCode != 0) {
						append(resetCursorShape)
						lastCursorShapeCode = 0
					}
					cursorShown = false
				}
			}

			if (sync) {
				append(synchronizedOutputDisable)
			} else if (wrapCursor) {
				if (cursorManaged) {
					if (length == contentStart && !cursorShown) {
						// Only the leading hide was emitted (the requested cursor is off-screen and
						// was never shown); drop it so an empty frame never makes the cursor blink.
						setLength(frameStart)
					}
				} else if (length > contentStart) {
					append(showCursor)
				} else {
					// Nothing was drawn for this frame; drop the stray hide so an empty frame
					// never makes the cursor blink.
					setLength(frameStart)
				}
			}

			ensurePrevBuffer(width, height)
			surface.copyCellsTo(prevCells)
			prevWidth = width
			prevHeight = height
			lastStatic = static
		}
	}

	/**
	 * Emits the incremental update for one row into [appendable] and returns whether anything
	 * changed. [base] is the row's first cell index in the current and previous grids.
	 */
	private fun renderRowDiff(appendable: StringBuilder, surface: TextSurface, row: Int, width: Int): Boolean {
		val base = row * width

		// Collect maximal runs of columns whose cell differs from the previous frame.
		runs.clear()
		var column = 0
		while (column < width) {
			while (column < width && cellsEqual(surface.cells[base + column], prevCells[base + column])) {
				column++
			}
			if (column == width) break
			val start = column
			do {
				column++
			} while (column < width && !cellsEqual(surface.cells[base + column], prevCells[base + column]))
			runs.add(start)
			runs.add(column)
		}
		if (runs.isEmpty()) {
			return false
		}

		// Grow each run to whole grapheme clusters: a rewrite must never begin or end mid-cluster,
		// otherwise the placeholder columns of a wide cluster would be left half-overwritten.
		var i = 0
		while (i < runs.size) {
			var start = runs[i]
			var end = runs[i + 1]
			// Only back up to a cluster head when the run begins inside the placeholder region of a
			// changed cluster. A run that follows an unchanged wide cluster must not reach into it,
			// otherwise the rewrite would land in the middle of that cluster's placeholder columns.
			while (start > 0 && surface.cells[base + start].isPlaceholder) {
				start--
			}
			var c = start
			while (c < end) {
				val cell = surface.cells[base + c]
				if (!cell.isPlaceholder && cell.width > 1) {
					val clusterEnd = c + cell.width
					if (clusterEnd > end) {
						end = clusterEnd.coerceAtMost(width)
					}
				}
				c++
			}
			runs[i] = start
			runs[i + 1] = end
			i += 2
		}

		// Merge overlapping runs so each changed region is rewritten only once.
		var out = 0
		i = 0
		while (i < runs.size) {
			var start = runs[i]
			var end = runs[i + 1]
			var next = i + 2
			while (next < runs.size && runs[next] <= end) {
				if (runs[next + 1] > end) {
					end = runs[next + 1]
				}
				next += 2
			}
			runs[out++] = start
			runs[out++] = end
			i = next
		}
		val runCount = out / 2

		// When the changed region dominates the row, rewriting it whole after a clear is cheaper
		// than patching sparse segments (a truncation would otherwise emit a long run of spaces).
		var changedCells = 0
		i = 0
		while (i < runCount) {
			changedCells += runs[i * 2 + 1] - runs[i * 2]
			i++
		}
		var newStop = 0
		var prevStop = 0
		var c = width - 1
		while (c >= 0) {
			if (newStop == 0 && !surface.cells[base + c].isEmpty()) {
				newStop = c + 1
			}
			if (prevStop == 0 && !prevCells[base + c].isEmpty()) {
				prevStop = c + 1
			}
			if (newStop != 0 && prevStop != 0) {
				break
			}
			c--
		}
		val contentStop = maxOf(newStop, prevStop)
		if (changedCells * 2 >= contentStop) {
			appendable.append(CSI)
			appendable.append(row + 1)
			appendable.append(';')
			appendable.append(1)
			appendable.append('H')
			appendable.append(clearLine)
			surface.appendRowTo(appendable, row, capabilities.ansiLevel, capabilities.kittyUnderline)
			return true
		}

		// Patch sparse segments in place, left to right.
		i = 0
		while (i < runCount) {
			val start = runs[i * 2]
			val end = runs[i * 2 + 1]
			appendable.append(CSI)
			appendable.append(row + 1)
			appendable.append(';')
			appendable.append(start + 1)
			appendable.append('H')
			surface.appendRowRange(
				appendable,
				base + start,
				base + end,
				base + width,
				capabilities.ansiLevel,
				capabilities.kittyUnderline,
			)
			i++
		}
		return true
	}

	private fun cellsEqual(a: TextPixel, b: TextPixel): Boolean {
		return sliceEquals(a, b) &&
			a.width == b.width &&
			a.isPlaceholder == b.isPlaceholder &&
			a.foreground == b.foreground &&
			a.background == b.background &&
			a.textStyle == b.textStyle &&
			a.underlineStyle == b.underlineStyle &&
			a.underlineColor == b.underlineColor
	}

	private fun sliceEquals(a: TextPixel, b: TextPixel): Boolean {
		val aLength = a.textEnd - a.textStart
		if (aLength != b.textEnd - b.textStart) {
			return false
		}
		var i = 0
		while (i < aLength) {
			if (a.textSource[a.textStart + i] != b.textSource[b.textStart + i]) {
				return false
			}
			i++
		}
		return true
	}

	private fun ensurePrevBuffer(width: Int, height: Int) {
		val size = width * height
		if (prevCells.size != size) {
			prevCells = Array(size) { TextPixel() }
		}
	}
}
