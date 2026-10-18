package com.jakewharton.mosaic

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The capability notice for a degraded run must not be silent: on the JVM, `AssumeAndIgnore`
 * prints [NonInteractiveNotice] to stderr before running the composition against the inert
 * [NonInteractiveTerminal].
 */
class NonInteractiveNoticeTest {

	@Test fun assumeAndIgnorePrintsCapabilityNoticeToStderr() {
		val originalErr = System.err
		val captured = ByteArrayOutputStream()
		try {
			System.setErr(PrintStream(captured, true, Charsets.UTF_8))
			runBlocking {
				withTerminal(
					onNonInteractive = NonInteractivePolicy.AssumeAndIgnore,
					mouse = false,
					paste = false,
				) { }
			}
		} finally {
			System.setErr(originalErr)
		}
		val stderr = captured.toString(Charsets.UTF_8)
		assertTrue(
			stderr.contains(NonInteractiveNotice),
			"stderr should carry the capability notice, got: $stderr",
		)
	}
}
