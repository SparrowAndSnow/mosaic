package com.jakewharton.mosaic.tty

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isZero
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Locks the behavior that every [Tty] binding (JNI, FFM, and cinterop) must share.
 *
 * The bindings wrap the same C library, so the contract is mostly about what the wrappers add:
 * argument validation, closed-instance handling, byte-exact buffer ranges, timeout/interrupt
 * results, and the lifetime/visibility rules around callbacks. Running this suite through the JVM
 * test distribution on a modern JDK additionally exercises the FFM binding, which is otherwise not
 * on the default JVM test classpath.
 */
class TtyContractTest {
	private lateinit var testTerminal: TestTerminal
	private lateinit var tty: Tty

	@BeforeTest fun before() {
		testTerminal = TestTerminal.bind()
		tty = testTerminal.tty
		tty.enableRawMode()
	}

	@AfterTest fun after() {
		tty.close()
		testTerminal.close()
	}

	@Test fun readAppliesOffsetAndCount() {
		val input = "hello".encodeToByteArray()
		assertThat(testTerminal.writeTty(input, 0, input.size)).isEqualTo(input.size)

		val buffer = ByteArray(10) { 'x'.code.toByte() }
		assertThat(tty.read(buffer, 5, 3)).isEqualTo(3)
		assertThat(buffer.decodeToString()).isEqualTo("xxxxxhelxx")
	}

	@Test fun writeAppliesOffsetAndCount() {
		val payload = "0123456789".encodeToByteArray()
		assertThat(tty.write(payload, 3, 4)).isEqualTo(4)

		val buffer = ByteArray(4)
		assertThat(testTerminal.readTtyWithTimeout(buffer, 0, 4, 1_000)).isEqualTo(4)
		assertThat(buffer.decodeToString()).isEqualTo("3456")
	}

	@Test fun readAndWritePreserveEveryByteValue() {
		// The Windows console-input harness records only ASCII bytes, so high bytes cannot round-trip
		// there; the JNI/FFM byte handling is still covered on POSIX.
		if (isWindows()) return

		val bytes = ByteArray(256) { it.toByte() }

		assertThat(testTerminal.writeTty(bytes, 0, bytes.size)).isEqualTo(bytes.size)
		val read = ByteArray(bytes.size)
		var total = 0
		while (total < bytes.size) {
			val count = tty.readWithTimeout(read, total, bytes.size - total, 1_000)
			check(count > 0) { "Expected data, got $count" }
			total += count
		}
		assertThat(read.toList()).isEqualTo(bytes.toList())

		assertThat(tty.write(bytes, 0, bytes.size)).isEqualTo(bytes.size)
		val written = ByteArray(bytes.size)
		total = 0
		while (total < bytes.size) {
			val count = testTerminal.readTtyWithTimeout(written, total, bytes.size - total, 1_000)
			check(count > 0) { "Expected data, got $count" }
			total += count
		}
		assertThat(written.toList()).isEqualTo(bytes.toList())
	}

	@Test fun readWithTimeoutReturnsZeroWhenIdle() {
		val buffer = ByteArray(4)
		val elapsed = measureTime {
			assertThat(tty.readWithTimeout(buffer, 0, 4, 20)).isZero()
		}
		assertThat(elapsed >= 10.milliseconds).isEqualTo(true)
	}

	@Test fun readWithTimeoutReturnsAvailableData() {
		val input = "A".encodeToByteArray()
		assertThat(testTerminal.writeTty(input, 0, 1)).isEqualTo(1)

		val buffer = ByteArray(4)
		assertThat(tty.readWithTimeout(buffer, 0, 4, 1_000)).isEqualTo(1)
		assertThat(buffer[0]).isEqualTo('A'.code.toByte())
	}

	@Test fun nonBlockingReadReturnsZero() {
		val buffer = ByteArray(1)
		val elapsed = measureTime {
			assertThat(tty.readWithTimeout(buffer, 0, 1, 0)).isZero()
		}
		assertThat(elapsed < 1.seconds).isEqualTo(true)
	}

	@Test fun interruptReadWakesBlockedRead() = runBlocking {
		val buffer = ByteArray(1)
		val read = async(Dispatchers.Default) {
			tty.read(buffer, 0, 1)
		}
		delay(50)
		tty.interruptRead()
		assertThat(withTimeout(5.seconds) { read.await() }).isZero()
	}

	@Test fun interruptReadMakesNextReadReturnZero() {
		tty.interruptRead()
		assertThat(tty.read(ByteArray(1), 0, 1)).isZero()
	}

	@Test fun readRejectsOutOfBoundsArguments() {
		val buffer = ByteArray(4)
		assertFailsWith<IndexOutOfBoundsException> { tty.read(buffer, -1, 1) }
		assertFailsWith<IndexOutOfBoundsException> { tty.read(buffer, 0, -1) }
		assertFailsWith<IndexOutOfBoundsException> { tty.read(buffer, 2, 3) }
		assertFailsWith<IndexOutOfBoundsException> { tty.read(buffer, 5, 0) }
	}

	@Test fun writeRejectsOutOfBoundsArguments() {
		val buffer = ByteArray(4)
		assertFailsWith<IndexOutOfBoundsException> { tty.write(buffer, -1, 1) }
		assertFailsWith<IndexOutOfBoundsException> { tty.write(buffer, 0, -1) }
		assertFailsWith<IndexOutOfBoundsException> { tty.write(buffer, 2, 3) }
		assertFailsWith<IndexOutOfBoundsException> { tty.write(buffer, 5, 0) }
	}

	@Test fun operationsAfterCloseFailConsistently() {
		tty.close()

		assertFailsWith<IllegalStateException> { tty.read(ByteArray(1), 0, 1) }
		assertFailsWith<IllegalStateException> { tty.readWithTimeout(ByteArray(1), 0, 1, 0) }
		assertFailsWith<IllegalStateException> { tty.write(ByteArray(1), 0, 1) }
		assertFailsWith<IllegalStateException> { tty.interruptRead() }
		assertFailsWith<IllegalStateException> { tty.enableRawMode() }
		assertFailsWith<IllegalStateException> { tty.enableWindowResizeEvents() }
		assertFailsWith<IllegalStateException> { tty.currentSize() }
		assertFailsWith<IllegalStateException> { tty.setCallback(null) }

		// Close and reset stay idempotent, matching the documented AutoCloseable contract.
		tty.close()
		tty.reset()
	}
}
