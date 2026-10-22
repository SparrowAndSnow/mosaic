package com.jakewharton.mosaic.ui

import androidx.compose.runtime.Immutable
import com.jakewharton.mosaic.layout.ContentDrawScope
import com.jakewharton.mosaic.layout.DrawModifier
import com.jakewharton.mosaic.layout.TextCanvasDrawScope
import com.jakewharton.mosaic.modifier.Modifier

/**
 * What a modal overlay paints behind its content.
 *
 * The terminal has no alpha, so these are the only two useful behaviors: leave the base fully
 * visible, or erase it. Use [Solid] when the base must not show through (the default for [Dialog]).
 */
@Immutable
public sealed interface OverlayBackdrop {
	/** Paints nothing; the base remains visible behind the modal content. */
	public data object None : OverlayBackdrop

	/**
	 * Erases the base area behind the modal content.
	 *
	 * When [background] is [Color.Unspecified] the cells are reset to the terminal default, clearing
	 * characters, foreground/background colors, text styles, and underlines alike. A specified color
	 * also fills the cleared cells with that background.
	 */
	public data class Solid(
		public val background: Color = Color.Unspecified,
	) : OverlayBackdrop
}

internal fun Modifier.overlayBackdrop(backdrop: OverlayBackdrop): Modifier = this.then(OverlayBackdropModifier(backdrop))

private class OverlayBackdropModifier(
	private val backdrop: OverlayBackdrop,
) : DrawModifier {
	override fun ContentDrawScope.draw() {
		when (backdrop) {
			OverlayBackdrop.None -> {}

			is OverlayBackdrop.Solid -> {
				val scope = this as? TextCanvasDrawScope
				if (scope != null) {
					scope.clear(backdrop.background)
				} else {
					// Fallback for non-surface draw scopes: this cannot reset style fields, but the
					// regular composition always draws through a TextCanvasDrawScope.
					drawRect(
						background = backdrop.background,
						textStyle = TextStyle.Empty,
					)
				}
			}
		}
		drawContent()
	}

	override fun toString(): String = "OverlayBackdrop($backdrop)"
}
