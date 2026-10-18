package com.jakewharton.mosaic.tty.terminal

internal expect fun env(name: String): String?

/**
 * Writes a diagnostic line to the process standard error stream.
 *
 * Used for degraded-capability notices which must never be silently swallowed, since the standard
 * output is the rendering surface and cannot carry diagnostics.
 */
internal expect fun stderrPrint(message: String): Unit
