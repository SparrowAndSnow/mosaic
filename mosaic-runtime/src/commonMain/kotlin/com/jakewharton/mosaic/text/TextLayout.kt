package com.jakewharton.mosaic.text

import com.jakewharton.mosaic.text.grapheme.graphemeWidth
import com.jakewharton.mosaic.text.grapheme.forEachGraphemeClusterRange
import com.jakewharton.mosaic.text.grapheme.graphemeClusterWidth
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

	private var dirty = true

	private var lastMaxWidth = Int.MAX_VALUE

	/**
	 * Recomputes the laid-out [lines] using at most [maxWidth] display columns per line. Clusters
	 * that fit are accumulated greedily; a cluster is never split, so ZWJ emoji families, combining
	 * marks, variation selectors, and flag pairs stay whole. A cluster wider than [maxWidth] still
	 * occupies a line of its own. A paragraph that fits on one line is emitted as-is, preserving
	 * empty paragraphs (consecutive/leading/trailing newlines). [Constraints.Infinity] means no
	 * constraint, in which case the previous behavior (one line per paragraph) is kept.
	 */
	fun measure(maxWidth: Int = Constraints.Infinity) {
		val effectiveMaxWidth = if (maxWidth == Constraints.Infinity) Int.MAX_VALUE else maxWidth
		if (!dirty && effectiveMaxWidth == lastMaxWidth) {
			return
		}

		val wrappedLines = buildList {
			for (paragraph in value.splitByLines()) {
				addAll(paragraph.wrapToWidth(effectiveMaxWidth))
			}
		}
		width = wrappedLines.maxOf { it.textWidth() }
		height = wrappedLines.size
		lines = wrappedLines
		lastMaxWidth = effectiveMaxWidth
		dirty = false
	}

	protected abstract fun T.textWidth(): Int

	protected abstract fun T.splitByLines(): List<T>

	protected abstract fun T.subSlice(from: Int, to: Int): T

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
}
