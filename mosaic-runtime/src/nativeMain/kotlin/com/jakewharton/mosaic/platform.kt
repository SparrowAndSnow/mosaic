package com.jakewharton.mosaic

import kotlin.system.exitProcess
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.STDERR_FILENO
import platform.posix.STDOUT_FILENO
import platform.posix.getenv
import platform.posix.write

internal actual fun env(name: String): String? {
	return getenv(name)?.toKString()
}

@OptIn(UnsafeNumber::class)
internal actual fun nonInteractiveExit(): Nothing {
	val message = "$NonInteractiveMessage\n".cstr
	write(STDERR_FILENO, message, message.size.convert())
	exitProcess(1)
}

@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
internal actual fun writeScreenBytes(bytes: ByteArray, offset: Int, count: Int) {
	var remaining = count
	var currentOffset = offset
	while (remaining > 0) {
		val written = memScoped {
			write(STDOUT_FILENO, bytes.refTo(currentOffset), remaining.convert())
		}.toInt()
		if (written < 0) {
			throw IllegalStateException("Failed to write to stdout")
		}
		currentOffset += written
		remaining -= written
	}
}

internal actual fun flushScreenBytes() {
	// Native stdout writes are unbuffered at the language level; nothing to flush.
}
