package com.jakewharton.mosaic.tty.terminal

import kotlin.experimental.ExperimentalNativeApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.toKString
import platform.posix.STDERR_FILENO
import platform.posix.getenv
import platform.posix.write

internal actual fun env(name: String): String? {
	return getenv(name)?.toKString()
}

@OptIn(ExperimentalNativeApi::class)
internal actual fun isWindowsPlatform(): Boolean {
	return Platform.osFamily == OsFamily.WINDOWS
}

@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
internal actual fun stderrPrint(message: String) {
	val bytes = "$message\n".cstr
	write(STDERR_FILENO, bytes, bytes.size.convert())
}
