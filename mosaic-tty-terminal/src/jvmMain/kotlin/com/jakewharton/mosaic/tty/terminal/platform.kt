package com.jakewharton.mosaic.tty.terminal

internal actual fun env(name: String): String? {
	return System.getenv(name)
}

internal actual fun isWindowsPlatform(): Boolean {
	return System.getProperty("os.name").contains("windows", ignoreCase = true)
}

internal actual fun stderrPrint(message: String) {
	System.err.println(message)
	System.err.flush()
}
