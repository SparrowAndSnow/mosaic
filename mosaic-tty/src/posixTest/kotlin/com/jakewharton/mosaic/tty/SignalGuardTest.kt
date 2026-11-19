package com.jakewharton.mosaic.tty

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.posix.ECHO
import platform.posix.ICANON
import platform.posix.SIGILL
import platform.posix.SIGPIPE
import platform.posix.SIGSEGV
import platform.posix.SIG_DFL
import platform.posix.SIG_IGN
import platform.posix._exit
import platform.posix.fork
import platform.posix.raise
import platform.posix.signal
import platform.posix.waitpid

@OptIn(ExperimentalForeignApi::class)
class SignalGuardTest {
	private val testTerminal = TestTerminal.bind()
	private val tty = testTerminal.tty

	@AfterTest fun after() {
		// Restore the process-global dispositions so later tests start from the default state.
		signal(SIGSEGV, SIG_DFL)
		signal(SIGILL, SIG_DFL)
		signal(SIGPIPE, SIG_DFL)
		tty.close()
		testTerminal.close()
	}

	private val cookedMask: UInt get() = (ECHO or ICANON).toUInt()

	/**
	 * The guard must restore the original terminal settings and then terminate the process by the
	 * exact signal that was delivered, preserving its default semantics (waitpid status, core
	 * dumps, exit codes). The child restores the terminal on the pty shared with the parent, which
	 * is how the parent observes both the restore and the preserved waitpid status.
	 */
	private fun assertGuardedSignalRestoresAndTerminates(signal: Int, expectedSignal: Int) {
		signal(signal, SIG_DFL) // Only default dispositions are guarded; make the precondition explicit.
		tty.enableRawMode()
		assertThat(testTerminal.ttyTermiosCookedBits()).isEqualTo(0u)

		val pid = fork()
		if (pid == 0) {
			// Keep the child to async-signal-safe C calls only; the child must not run Kotlin code.
			raise(signal)
			_exit(0) // Unreachable when the default disposition terminates the process.
		}
		assertThat(pid).isGreaterThan(0)

		memScoped {
			val status = alloc<IntVar>()
			val reaped = waitpid(pid, status.ptr, 0)
			assertThat(reaped).isEqualTo(pid)
			// The low 7 bits of the wait status encode the terminating signal (0 for normal exit).
			assertThat(status.value and 0x7f).isEqualTo(expectedSignal)
		}

		// The child's guard restored the shared terminal to its pre-raw (cooked) settings.
		assertThat(testTerminal.ttyTermiosCookedBits()).isEqualTo(cookedMask)
		// ...and woke any reader blocked in read via the interrupt pipe.
		val buffer = ByteArray(16)
		assertThat(tty.read(buffer, 0, buffer.size)).isEqualTo(0)
	}

	@Test fun segfaultRestoresTerminalAndPreservesTermination() {
		assertGuardedSignalRestoresAndTerminates(SIGSEGV, SIGSEGV)
	}

	@Test fun illegalInstructionRestoresTerminalAndPreservesTermination() {
		assertGuardedSignalRestoresAndTerminates(SIGILL, SIGILL)
	}

	@Test fun pipeSignalRestoresTerminalAndPreservesTermination() {
		signal(SIGPIPE, SIG_DFL)
		assertGuardedSignalRestoresAndTerminates(SIGPIPE, SIGPIPE)
	}

	@Test fun guardSkipsSignalIgnoredBeforeEnable() {
		// The application deliberately ignores SIGPIPE before entering raw mode.
		signal(SIGPIPE, SIG_IGN)
		tty.enableRawMode()
		raise(SIGPIPE) // Ignored; the guard must not have hijacked it into restore-and-kill.
		assertThat(testTerminal.ttyTermiosCookedBits()).isEqualTo(0u)
	}

	@Test fun guardDoesNotClobberDispositionChangedAfterEnable() {
		tty.enableRawMode() // SIGPIPE is guarded here (default disposition at enable).
		signal(SIGPIPE, SIG_IGN) // The application changes the disposition afterwards.
		raise(SIGPIPE)
		assertThat(testTerminal.ttyTermiosCookedBits()).isEqualTo(0u)
	}

	@Test fun repeatedRawModeEnableIsIdempotent() {
		tty.enableRawMode()
		tty.enableRawMode() // Must not throw or install a second conflicting guard.
		assertThat(testTerminal.ttyTermiosCookedBits()).isEqualTo(0u)
	}
}
