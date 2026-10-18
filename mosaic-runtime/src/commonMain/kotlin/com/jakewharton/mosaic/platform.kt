package com.jakewharton.mosaic

internal expect fun env(name: String): String?

internal expect inline fun nanoTime(): Long

internal expect fun nonInteractiveExit(): Nothing

internal expect fun warnNonInteractive(): Unit

internal expect fun writeScreenBytes(bytes: ByteArray, offset: Int, count: Int): Unit

internal expect fun flushScreenBytes(): Unit
