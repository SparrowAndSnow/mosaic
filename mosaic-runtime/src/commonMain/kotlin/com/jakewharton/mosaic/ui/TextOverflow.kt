package com.jakewharton.mosaic.ui

/**
 * Controls what happens when a [Text][com.jakewharton.mosaic.ui.Text] has more lines than its
 * [maxLines][com.jakewharton.mosaic.ui.Text.maxLines] allows.
 */
public enum class TextOverflow {
	/** Lines beyond [maxLines] are not shown; the last visible line is kept whole. */
	Clip,

	/** Lines beyond [maxLines] are not shown, and the last visible line ends with ellipsis. */
	Ellipsis,
}
