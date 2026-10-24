package com.jakewharton.mosaic.tty

import assertk.assertThat
import assertk.assertions.isLessThan
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test

/**
 * Regression test for the Windows reader loop dispatching console records it never consumes.
 *
 * WINDOW_BUFFER_SIZE_EVENT records are turned into [Tty.Callback.onResize] calls, but nothing
 * consumed them: only a key press is handed to the ReadFile which translates input to VT
 * sequences. A resize which arrived with no pending key press therefore left its record in the
 * console buffer forever, so the reader spun on the same record: a pegged core and a resize
 * callback storm while no input was read again and an interrupt was never observed. Consuming the
 * dispatched records keeps the blocking wait blocking.
 *
 * A spin is observable as a large number of resize callbacks for a single resize. The bound is
 * loose because Windows Terminal reports one or two records per resize.
 */
class WindowsResizeSpinJvmTest {
	@Test fun resizeWithoutInputDoesNotSpinTheReadLoop() {
		if (!isWindows()) return

		val testTerminal = TestTerminal.bind()
		val tty = testTerminal.tty
		val resizeCount = AtomicInteger()
		try {
			tty.enableRawMode()
			tty.setCallback(
				object : Tty.Callback {
					override fun onFocus(focused: Boolean) {
					}

					override fun onResize(columns: Int, rows: Int, width: Int, height: Int) {
						resizeCount.incrementAndGet()
					}
				},
			)
			tty.enableWindowResizeEvents()

			testTerminal.resize(40, 10, 0, 0)

			// Blocks inside the native read. On Windows the resize record is only dispatched while a
			// read is in progress, so the spinning only happens on this thread.
			val reader = thread(name = "tty-resize-test-reader") {
				runCatching { tty.read(ByteArray(1), 0, 1) }
			}
			Thread.sleep(250)
			tty.interruptRead()
			reader.join(2_000)

			assertThat(resizeCount.get()).isLessThan(10)
		} finally {
			tty.close()
			testTerminal.close()
		}
	}
}
