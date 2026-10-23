package com.jakewharton.mosaic.text

import com.jakewharton.mosaic.text.grapheme.forEachGraphemeClusterRange
import com.jakewharton.mosaic.text.grapheme.graphemeClusterWidth
import com.jakewharton.mosaic.text.grapheme.graphemeWidth
import com.jakewharton.mosaic.ui.TextOverflow
import com.jakewharton.mosaic.ui.unit.Constraints

/**
 * The grapheme-cluster layout of a single laid-out line, produced once during measurement and
 * reused by drawing so the draw pass does not re-segment or re-measure the line. [clusterStarts]
 * holds the start offset (in code units) of every cluster; the `i`-th cluster spans
 * `[clusterStarts[i], clusterStarts[i+1])` (or to `text.length` for the last one) and occupies
 * [clusterWidths][i] columns.
 */
internal class LineMeasure internal constructor(
	val clusterStarts: IntArray,
	val clusterWidths: IntArray,
	val totalWidth: Int,
)

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
	 * Per-line cluster metadata aligned with [lines], produced during [measure] and consumed by
	 * the draw pass to avoid re-segmenting and re-measuring every line.
	 */
	var lineMeasures: List<LineMeasure> = emptyList()
		private set

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
	private var lastScroll = 0

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
	 *
	 * [scroll] selects the first visible line of the laid-out content, shifting the view window
	 * down by that many lines. At most [maxLines] lines are shown from there; when more content
	 * follows below with [TextOverflow.Ellipsis], the last visible line carries the ellipsis.
	 */
	fun measure(
		maxWidth: Int = Constraints.Infinity,
		maxLines: Int = Int.MAX_VALUE,
		overflow: TextOverflow = TextOverflow.Clip,
		alignmentWidth: Int = Constraints.Infinity,
		scroll: Int = 0,
	) {
		val effectiveMaxWidth = if (maxWidth == Constraints.Infinity) Int.MAX_VALUE else maxWidth
		val effectiveMaxLines = maxLines.coerceAtLeast(1)
		val effectiveAlignmentWidth = if (alignmentWidth == Constraints.Infinity) Int.MAX_VALUE else alignmentWidth
		val effectiveScroll = scroll.coerceAtLeast(0)
		if (!dirty &&
			effectiveMaxWidth == lastMaxWidth &&
			effectiveMaxLines == lastMaxLines &&
			overflow == lastOverflow &&
			effectiveAlignmentWidth == lastAlignmentWidth &&
			effectiveScroll == lastScroll
		) {
			return
		}

		val wrappedLines = buildList {
			for (paragraph in value.splitByLines()) {
				addAll(paragraph.wrapToWidth(effectiveMaxWidth))
			}
		}

		// Clamp to the last line when asked to scroll past the end so the view never goes blank;
		// scrolling further cannot reveal content that does not exist.
		val lastLine = (wrappedLines.size - 1).coerceAtLeast(0)
		val visibleStart = effectiveScroll.coerceAtMost(lastLine)
		val visibleCount = wrappedLines.size - visibleStart
		val result = if (visibleCount <= effectiveMaxLines) {
			if (visibleStart == 0) {
				wrappedLines
			} else {
				wrappedLines.subList(visibleStart, wrappedLines.size)
			}
		} else {
			when (overflow) {
				TextOverflow.Clip -> wrappedLines.subList(visibleStart, visibleStart + effectiveMaxLines)

				TextOverflow.Ellipsis -> {
					val kept = ArrayList<T>(effectiveMaxLines)
					kept.addAll(wrappedLines.subList(visibleStart, visibleStart + effectiveMaxLines - 1))
					val last = wrappedLines[visibleStart + effectiveMaxLines - 1]
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

		val measures = buildList {
			for (line in result) {
				add(line.measureLine())
			}
		}
		width = measures.maxOfOrNull { it.totalWidth } ?: 0
		height = result.size
		lines = result
		lineMeasures = measures
		lastMaxWidth = effectiveMaxWidth
		lastMaxLines = effectiveMaxLines
		lastOverflow = overflow
		this.alignmentWidth = effectiveAlignmentWidth
		lastAlignmentWidth = effectiveAlignmentWidth
		lastScroll = effectiveScroll
		dirty = false
	}

	protected abstract fun T.textWidth(): Int

	/** Segments and measures this line once so the draw pass can reuse [LineMeasure]. */
	protected abstract fun T.measureLine(): LineMeasure

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

	override fun String.measureLine(): LineMeasure = measureClusters()

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

	override fun AnnotatedString.measureLine(): LineMeasure = text.measureClusters()

	override fun AnnotatedString.subSlice(from: Int, to: Int): AnnotatedString = subSequence(from, to)

	override fun AnnotatedString.truncateForEllipsis(maxWidth: Int): AnnotatedString = clipTo(maxWidth - 1) + AnnotatedString("\u2026")
}

/**
 * Segments [this] into grapheme clusters and measures each one, returning a [LineMeasure] that
 * the draw pass can consume to skip its own segmentation and width measurement.
 */
internal fun CharSequence.measureClusters(): LineMeasure {
	val starts = ArrayList<Int>()
	val widths = ArrayList<Int>()
	var total = 0
	forEachGraphemeClusterRange { start, end ->
		val w = graphemeClusterWidth(start, end)
		starts.add(start)
		widths.add(w)
		total += w
	}
	return LineMeasure(starts.toIntArray(), widths.toIntArray(), total)
}
