package com.jakewharton.mosaic

import com.jakewharton.mosaic.text.grapheme.forEachGraphemeClusterRange
import com.jakewharton.mosaic.text.grapheme.graphemeClusterWidth

/**
 * A minimal in-memory terminal that interprets the ANSI sequences the incremental renderer emits
 * and keeps a screen grid, so tests can assert the *on-screen state* (what a user would actually
 * see) after a sequence of frames rather than inspecting the raw byte stream of a single frame.
 *
 * A wide cluster is stored as a single [Glyph] instance shared by its head and continuation
 * columns. Writing a narrow cluster over a wide cluster's head only replaces that head column,
 * leaving the continuation columns holding the old instance. `displayRow` renders those orphaned
 * pixels as `◌`, exposing the half-cell residue a real terminal would show, while a rewrite that
 * clears the whole extent first renders cleanly.
 */
internal class VirtualTerminal(
	val rows: Int,
	val columns: Int,
) {
	internal class Glyph(val text: String, val width: Int, val row: Int, val column: Int)

	private val grid = Array(rows) { arrayOfNulls<Glyph>(columns) }

	var cursorRow = 0
		private set

	var cursorColumn = 0
		private set

	/** Feeds one renderer output (typically one frame) into the screen. */
	fun feed(output: CharSequence) {
		var i = 0
		while (i < output.length) {
			val c = output[i]
			if (c == '\u001B') {
				if (i + 1 < output.length && output[i + 1] == '[') {
					i = parseCsi(output, i + 1)
				} else {
					i++
				}
				continue
			}
			when (c) {
				'\r' -> {
					cursorColumn = 0
					i++
				}

				'\n' -> {
					cursorRow = (cursorRow + 1).coerceAtMost(rows - 1)
					i++
				}

				else -> {
					// Plain text: styles are carried by the SGR state but for screen-state
					// purposes we only care whether they were active at all, handled above.
					i = consumeText(output, i)
				}
			}
		}
	}

	/** Parses a CSI sequence whose `[` is at [csiBracket]; returns the index past its final byte. */
	private fun parseCsi(output: CharSequence, csiBracket: Int): Int {
		var i = csiBracket + 1
		var params = ""
		while (i < output.length) {
			val c = output[i]
			if (c in '\u0040'..'\u007E') {
				handleCsi(params, c)
				return i + 1
			}
			params += c
			i++
		}
		return i
	}

	private fun handleCsi(params: String, final: Char) {
		val values = params.splitToSequence(';').mapNotNull { it.toIntOrNull() }.toList()
		when (final) {
			'H', 'f' -> { // CUP / HVP
				cursorRow = (values.getOrElse(0) { 1 } - 1).coerceIn(0, rows - 1)
				cursorColumn = (values.getOrElse(1) { 1 } - 1).coerceIn(0, columns - 1)
			}

			'A' -> cursorRow = (cursorRow - values.getOrElse(0) { 1 }).coerceAtLeast(0)
			'B' -> cursorRow = (cursorRow + values.getOrElse(0) { 1 }).coerceAtMost(rows - 1)
			'C' -> cursorColumn = (cursorColumn + values.getOrElse(0) { 1 }).coerceAtMost(columns - 1)
			'D' -> cursorColumn = (cursorColumn - values.getOrElse(0) { 1 }).coerceAtLeast(0)
			'F' -> { // CPL: previous line, back to the first column like the real terminal does
				cursorRow = (cursorRow - values.getOrElse(0) { 1 }).coerceAtLeast(0)
				cursorColumn = 0
			}
			'E' -> { // CNL
				cursorRow = (cursorRow + values.getOrElse(0) { 1 }).coerceAtMost(rows - 1)
				cursorColumn = 0
			}

			'K', 'X' -> { // EL clear line
				when (values.getOrElse(0) { 0 }) {
					0 -> clearToEndOfLine()
					1 -> {
						for (x in 0..cursorColumn) grid[cursorRow][x] = null
					}
					2 -> {
						for (x in 0 until columns) grid[cursorRow][x] = null
					}
				}
			}

			'J' -> { // ED clear display
				when (values.getOrElse(0) { 0 }) {
					0 -> clearFromCursorToBottom()
					1 -> {
						for (r in 0..cursorRow) {
							for (x in 0..if (r == cursorRow) cursorColumn else columns - 1) {
								grid[r][x] = null
							}
						}
					}
					2 -> clearDisplay()
				}
			}

			// SGR is collapsed to a no-op here: the grid records only the glyphs, and style
			// correctness is not part of the screen-state assertions this harness makes.
			'm' -> Unit
			// Mode set/reset and anything else (e.g. scrolling regions) are not produced by the
			// renderer in a way that affects the screen grid.
		}
	}

	private fun consumeText(output: CharSequence, start: Int): Int {
		var end = start
		while (end < output.length) {
			val c = output[end]
			if (c == '\u001B' || c == '\r' || c == '\n') break
			end++
		}
		writeText(output.subSequence(start, end))
		return end
	}

	private fun writeText(text: CharSequence) {
		text.forEachGraphemeClusterRange { start, end ->
			val cluster = text.substring(start, end)
			val width = text.graphemeClusterWidth(start, end)
			writeCluster(cluster, width)
		}
	}

	private fun writeCluster(text: String, width: Int) {
		if (cursorColumn >= columns) {
			cursorColumn += width
			return
		}
		val glyph = Glyph(text, width, cursorRow, cursorColumn)
		val end = (cursorColumn + width).coerceAtMost(columns)
		for (x in cursorColumn until end) {
			grid[cursorRow][x] = null
		}
		for (x in cursorColumn until end) {
			grid[cursorRow][x] = glyph
		}
		cursorColumn += width
	}

	private fun clearToEndOfLine() {
		for (x in cursorColumn until columns) {
			grid[cursorRow][x] = null
		}
	}

	private fun clearFromCursorToBottom() {
		for (x in cursorColumn until columns) {
			grid[cursorRow][x] = null
		}
		for (r in cursorRow + 1 until rows) {
			for (x in 0 until columns) {
				grid[r][x] = null
			}
		}
	}

	private fun clearDisplay() {
		for (r in 0 until rows) {
			for (x in 0 until columns) {
				grid[r][x] = null
			}
		}
	}

	/**
	 * Renders a row as text: each wide glyph appears once, empty columns show as a space, and an
	 * orphaned continuation pixel (whose head column was overwritten without clearing the rest)
	 * shows as `◌`.
	 */
	fun displayRow(row: Int): String {
		val sb = StringBuilder(columns)
		var c = 0
		while (c < columns) {
			val glyph = grid[row][c]
			if (glyph == null) {
				sb.append(' ')
				c++
				continue
			}
			if (glyph.row == row && glyph.column == c) {
				sb.append(glyph.text)
				c += glyph.width
			} else {
				sb.append('\u25CC')
				c++
			}
		}
		return sb.toString()
	}

	/** Whether any orphaned continuation pixel (half-cell residue) is still on screen. */
	fun hasResidue(): Boolean {
		for (r in 0 until rows) {
			for (c in 0 until columns) {
				val glyph = grid[r][c] ?: continue
				if (glyph.row == r && glyph.column == c) continue // head cell: healthy
				// Continuation pixel: healthy only while its head column still holds it.
				if (glyph.row in 0 until rows &&
					glyph.column in 0 until columns &&
					grid[glyph.row][glyph.column] === glyph
				) {
					continue
				}
				return true
			}
		}
		return false
	}
}
