package example

import com.jakewharton.mosaic.ui.Color

/** Shared, self-contained palette so the showcase has no dependency on other samples. */
internal object ShowcaseTheme {
	val title = Color(114, 159, 207) // soft blue
	val section = Color(138, 226, 52) // green
	val body = Color(211, 215, 207) // light gray
	val dim = Color(104, 106, 113) // dark gray
	val accent = Color(252, 175, 62) // amber
	val danger = Color(239, 41, 41) // red
	val tabActive = Color(24, 26, 29) // dark background
	val tabActiveFg = Color(211, 215, 207)
	val tabInactiveFg = Color(138, 143, 148)
	val tabHover = Color(238, 238, 236) // lighter
}
