package com.jakewharton.mosaic.tty.terminal

import com.jakewharton.mosaic.tty.TestTerminal
import kotlin.test.Test
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

class TtyTerminalLifecycleTest {
	// Mirrors the nesting of `withTerminal`: the terminal is created in the caller's scope, its
	// close() runs the synchronous teardown, and the underlying tty is closed right after in the
	// same scope. The teardown must have completed before the tty is freed, so this must neither
	// crash nor hang even though the reader/interrupt coroutines still belong to this scope.
	@Test fun closeOrderingMatchesWithTerminal() = runBlocking {
		TestTerminal.bind().use { testTty ->
			coroutineScope {
				val terminal = testTty.tty.asTerminalIn(this)
				terminal.use { }
				testTty.tty.close()
			}
		}
	}

	@Test fun doubleCloseIsIdempotent() = runBlocking {
		TestTerminal.bind().use { testTty ->
			coroutineScope {
				val terminal = testTty.tty.asTerminalIn(this)
				terminal.use { }
				terminal.close()
				testTty.tty.close()
			}
		}
	}

	// An exception thrown by the application must still run the teardown and free the tty without
	// leaving the scope waiting on the reader/interrupt coroutines.
	@Test fun exceptionInBlockStillCloses() = runBlocking {
		TestTerminal.bind().use { testTty ->
			coroutineScope {
				val terminal = testTty.tty.asTerminalIn(this)
				try {
					terminal.use {
						throw IllegalStateException("boom")
					}
				} catch (_: IllegalStateException) {
					// close() already ran teardown and cancelled the reader/interrupt jobs.
				}
				testTty.tty.close()
			}
		}
	}
}
