package com.jakewharton.mosaic.tty.terminal

import com.jakewharton.mosaic.tty.TestTerminal
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

/**
 * A terminal which never answers the capability queries must not be silently accepted: the
 * one-second budget elapses and a diagnostic is written to standard error so the degradation is
 * observable instead of presenting as a normal run with features quietly missing.
 */
class TtyTerminalTimeoutNoticeJvmTest {
	@Test fun noReplyPrintsTimeoutNoticeToStderr() {
		val originalErr = System.err
		val captured = ByteArrayOutputStream()
		try {
			System.setErr(PrintStream(captured, true, Charsets.UTF_8))
			runBlocking {
				TestTerminal.bind().use { testTty ->
					coroutineScope {
						val terminal = testTty.tty.asTerminalIn(this)
						terminal.use { }
						testTty.tty.close()
					}
				}
			}
		} finally {
			System.setErr(originalErr)
		}

		val stderr = captured.toString(Charsets.UTF_8)
		assertTrue(
			stderr.contains("1 second budget") && stderr.contains("MOSAIC_TTY_TERMINAL_DEBUG"),
			"stderr should carry the bootstrap timeout notice, got: $stderr",
		)
	}
}
