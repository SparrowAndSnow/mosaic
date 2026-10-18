package com.jakewharton.mosaic.layout

import androidx.compose.runtime.Stable
import com.jakewharton.mosaic.modifier.Modifier

/**
 * Clip the content of this layout to its bounds.
 *
 * Drawing is clipped both to the layout's own bounds and to any enclosing clip, so overflowing or
 * offset content never leaks into neighboring cells. This is applied implicitly by the scrolling
 * modifier so scrolled-away content stays hidden; it is exposed for cases (rounded borders, fixed
 * viewports over content that overhangs) that need the same guarantee directly.
 */
@Stable
public fun Modifier.clipToBounds(): Modifier = this then ClipToBoundsElement

internal object ClipToBoundsElement : DrawModifier {
	override fun ContentDrawScope.draw() {
		drawContent()
	}

	override fun toString(): String = "ClipToBounds"
}
