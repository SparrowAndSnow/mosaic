package com.jakewharton.mosaic.text

import com.jakewharton.mosaic.text.grapheme.graphemeWidth
import com.jakewharton.mosaic.text.grapheme.forEachGraphemeClusterRange
import com.jakewharton.mosaic.text.grapheme.graphemeClusterWidth
import com.jakewharton.mosaic.ui.TextOverflow
import com.jakewharton.mosaic.ui.unit.Constraints

internal abstract class TextLayout<T : CharSequence>(initialValue: T) {

	var value: T = initialValue
		set(value) {
			if (value != field) {
				dirty = true
				field = value
			}
		}

	var width: Int = -1
		private set
		get() {
			check(!dirty) { "Missing call to measure()" }
			return field
		}

	var height: Int = -1
		private set
		get() {
			check(!dirty) { "Missing call to measure()" }
			return field
		}

	var lines: List<T> = emptyList()
		private set
		get() {
			check(!dirty) { "Missing call to measure()" }
			return field
		}

	/**
	 * The width in display columns that [lines] may be aligned within, or [Constraints.Infinity]
	 * when no bounded width is available. Lines narrower than this are drawn inset by their
	 * alignment offset instead of starting at column 0.
	 */
	var alignmentWidth: Int = Constraints.Infinity
		private set

	private var dirty = true

	private var lastMaxWidth = Int.MAX_VALUE
	private var lastMaxLines = Int.MAX_VALUE
	private var lastOverflow = TextOverflow.Clip
	private var lastAlignmentWidth = Int.MAX_VALUE

	/**
	 * Recomputes the laid-out [lines] using at most [maxWidth] display columns per line. Clusters
	 * that fit are accumulated greedily; a cluster is never split, so ZWJ emoji families, combining
	 * marks, variation selectors, and flag pairs stay whole. A cluster wider than [maxWidth] still
	 * occupies a line of its own. A paragraph that fits on one line is emitted as-is, preserving
	 * empty paragraphs (consecutive/leading/trailing newlines). [Constraints.Infinity] means no
	 * constraint, in which case the previous behavior (one line per paragraph) is kept.
	 *
	 * When the wrapped lines exceed [maxLines], [overflow] decides how the surplus is handled:
	 * [TextOverflow.Clip] keeps the first [maxLines] lines whole, while [TextOverflow.Ellipsis]
	 * truncates the last visible line in clusters and appends an ellipsis. Truncation never splits
	 * a cluster. With an unbounded [maxWidth] there is no column budget to ellipsize into, so the
	 * behavior degrades to clipping.
	 */
	fun measure(
		maxWidth: Int = Constraints.Infinity,
		maxLines: Int = Int.MAX_VALUE,
		overflow: TextOverflow = TextOverflow.Clip,
		alignmentWidth: Int = Constraints.Infinity,
	) {
		val effectiveMaxWidth = if (maxWidth == Constraints.Infinity) Int.MAX_VALUE else maxWidth
		val effectiveMaxLines = maxLines.coerceAtLeast(1)
		val effectiveAlignmentWidth = if (alignmentWidth == Constraints.Infinity) Int.MAX_VALUE else alignmentWidth
		if (!dirty &&
			effectiveMaxWidth == lastMaxWidth &&
			effectiveMaxLines == lastMaxLines &&
			overflow == lastOverflow &&
			effectiveAlignmentWidth == lastAlignmentWidth
		) {
			return
		}

		val wrappedLines = buildList {
			for (paragraph in value.splitByLines()) {
				addAll(paragraph.wrapToWidth(effectiveMaxWidth))
			}
		}

		val result = if (wrappedLines.size <= effectiveMaxLines) {
			wrappedLines
		} else {
			when (overflow) {
				TextOverflow.Clip -> wrappedLines.take(effectiveMaxLines)
				TextOverflow.Ellipsis -> {
					val kept = ArrayList<T>(effectiveMaxLines)
					kept.addAll(wrappedLines.subList(0, effectiveMaxLines - 1))
					val last = wrappedLines[effectiveMaxLines - 1]
					kept.add(
						if (effectiveMaxWidth == Int.MAX_VALUE) {
							last
						} else {
							last.truncateForEllipsis(effectiveMaxWidth)
						},
					)
					kept
				}
			}
		}

		width = result.maxOf { it.textWidth() }
		height = result.size
		lines = result
		lastMaxWidth = effectiveMaxWidth
		lastMaxLines = effectiveMaxLines
		lastOverflow = overflow
		this.alignmentWidth = effectiveAlignmentWidth
		lastAlignmentWidth = effectiveAlignmentWidth
		dirty = false
	}

	protected abstract fun T.textWidth(): Int

	protected abstract fun T.splitByLines(): List<T>

	protected abstract fun T.subSlice(from: Int, to: Int): T

	/**
	 * Clips this text to the longest prefix (in whole grapheme clusters) whose width is at most
	 * [maxWidth], never splitting a cluster. The text is returned unchanged when it already fits.
	 */
	protected fun T.clipTo(maxWidth: Int): T {
		if (isEmpty() || textWidth() <= maxWidth) return this
		var clipEnd = 0
		var width = 0
		var done = false
		forEachGraphemeClusterRange { start, end ->
			if (!done) {
				val clusterWidth = graphemeClusterWidth(start, end)
				if (width + clusterWidth > maxWidth) {
					done = true
				} else {
					clipEnd = end
					width += clusterWidth
				}
			}
		}
		return subSlice(0, clipEnd)
	}

	/** Returns this text truncated to fit [maxWidth] columns, with an ellipsis appended. */
	protected abstract fun T.truncateForEllipsis(maxWidth: Int): T

	private fun T.wrapToWidth(maxWidth: Int): List<T> {
		if (isEmpty() || textWidth() <= maxWidth) return listOf(this)

		val wrapped = mutableListOf<T>()
		var lineStart = 0
		var lineFilled = false
		var lineWidth = 0
		forEachGraphemeClusterRange { start, end ->
			val clusterWidth = graphemeClusterWidth(start, end)
			if (lineFilled && lineWidth + clusterWidth > maxWidth) {
				wrapped.add(subSlice(lineStart, start))
				lineStart = start
				lineWidth = 0
			}
			lineWidth += clusterWidth
			lineFilled = true
		}
		wrapped.add(subSlice(lineStart, length))
		return wrapped
	}
}

internal class StringTextLayout : TextLayout<String>(initialValue = "") {

	override fun String.splitByLines(): List<String> {
		return this.split("\n")
	}

	override fun String.textWidth(): Int = graphemeWidth()

	override fun String.subSlice(from: Int, to: Int): String = substring(from, to)

	override fun String.truncateForEllipsis(maxWidth: Int): String = clipTo(maxWidth - 1) + "\u2026"
}

internal class AnnotatedStringTextLayout :
	TextLayout<AnnotatedString>(
		initialValue = emptyAnnotatedString(),
	) {

	override fun AnnotatedString.splitByLines(): List<AnnotatedString> {
		return this.split("\n")
	}

	override fun AnnotatedString.textWidth(): Int = text.graphemeWidth()

	override fun AnnotatedString.subSlice(from: Int, to: Int): AnnotatedString = subSequence(from, to)

	override fun AnnotatedString.truncateForEllipsis(maxWidth: Int): AnnotatedString =
		clipTo(maxWidth - 1) + AnnotatedString("\u2026")
}
