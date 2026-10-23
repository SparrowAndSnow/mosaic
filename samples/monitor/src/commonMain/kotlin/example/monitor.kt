package example

import com.jakewharton.mosaic.runMosaicBlocking
import example.palletes.BlackBirdColorsPalette
import example.palletes.DefaultColorsPalette
import example.palletes.DraculaColorsPalette
import example.palletes.NordColorsPalette
import example.palletes.OneDarkColorsPalette
import example.palletes.SolarizedDarkColorsPalette

fun main(vararg args: String) {
	val monitorColorsPalette = parseArgsForColorsPalette(args)
	val monitorViewModel = MonitorViewModel()

	runMosaicBlocking {
		MonitorApp(monitorViewModel, monitorColorsPalette)
	}
}

private fun parseArgsForColorsPalette(args: Array<out String>): MonitorColorsPalette {
	val keyIndex = args.indexOf("-c")
	if (keyIndex < 0 || keyIndex >= args.lastIndex) {
		return DefaultColorsPalette
	}
	return when (args[keyIndex + 1]) {
		"default" -> DefaultColorsPalette
		"blackbird" -> BlackBirdColorsPalette
		"dracula" -> DraculaColorsPalette
		"nord" -> NordColorsPalette
		"one-dark" -> OneDarkColorsPalette
		"solarized-dark" -> SolarizedDarkColorsPalette
		else -> DefaultColorsPalette
	}
}
