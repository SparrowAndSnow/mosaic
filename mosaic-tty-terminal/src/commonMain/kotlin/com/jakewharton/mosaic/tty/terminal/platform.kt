package com.jakewharton.mosaic.tty.terminal

internal expect fun env(name: String): String?

/**
 * Whether the process runs on Windows.
 *
 * The Windows console renders 24-bit color as soon as VT output processing is available, which
 * entering raw mode enables before capability detection runs. Color support therefore does not
 * depend on which terminal emulator (or shell) launched the process.
 */
internal expect fun isWindowsPlatform(): Boolean

/**
 * Writes a diagnostic line to the process standard error stream.
 *
 * Used for degraded-capability notices which must never be silently swallowed, since the standard
 * output is the rendering surface and cannot carry diagnostics.
 */
internal expect fun stderrPrint(message: String): Unit
