package example

import com.jakewharton.mosaic.NonInteractivePolicy.Ignore
import com.jakewharton.mosaic.runMosaicBlocking

fun main() {
	runMosaicBlocking(onNonInteractive = Ignore, mouse = true, paste = true) {
		ShowcaseApp()
	}
}
