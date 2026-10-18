package com.jakewharton.mosaic

import com.jakewharton.mosaic.terminal.AnsiLevel
import com.jakewharton.mosaic.terminal.Terminal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Locks in the non-interactive policy dispatch: `Ignore` with no bindable TTY and
 * `AssumeAndIgnore` both run the composition against the inert [NonInteractiveTerminal]
 * (no colors, no input, no extended capabilities) instead of touching a real TTY, and the caller
 * still sees `true` from [withTerminal]. The stderr capability notice itself is asserted on the
 * JVM in `NonInteractiveNoticeTest`.
 */
class NonInteractiveTest {

	@Test fun assumeAndIgnoreSkipsBindingAndUsesInertTerminal() = runBlocking {
		var blockRan = false
		val result = withTerminal(
			onNonInteractive = NonInteractivePolicy.AssumeAndIgnore,
			mouse = false,
			paste = false,
		) { terminal ->
			blockRan = true
			assertFalse(terminal.interactive)
			assertSame(NonInteractiveTerminal, terminal)
			// The inert terminal owns a closed event channel: nothing can ever be received.
			assertTrue(terminal.events.tryReceive().isFailure)
		}
		// The block ran and withTerminal reports success even though no TTY was ever touched.
		assertTrue(blockRan)
		assertTrue(result)
	}

	@Test fun inertTerminalHasNoCapabilities() {
		assertSame(NonInteractiveTerminal, NonInteractiveTerminal.state)
		assertSame(NonInteractiveTerminal, NonInteractiveTerminal.capabilities)
		assertEquals(AnsiLevel.NONE, NonInteractiveTerminal.capabilities.ansiLevel)
		assertFalse(NonInteractiveTerminal.capabilities.kittyKeyboard)
		assertFalse(NonInteractiveTerminal.capabilities.graphemeClustering)
		assertFalse(NonInteractiveTerminal.capabilities.synchronizedOutput)
		assertTrue(NonInteractiveTerminal.state.focused.value) // Inert terminal pretends to be focused.
		assertEquals(Terminal.Size.Default, NonInteractiveTerminal.state.size.value)
	}
}
