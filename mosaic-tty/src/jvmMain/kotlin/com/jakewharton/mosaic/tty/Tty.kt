package com.jakewharton.mosaic.tty

import java.io.InputStream
import java.io.OutputStream

public actual class Tty internal constructor(
	private var ptr: Long,
) : AutoCloseable {
	public actual companion object {
		@JvmStatic
		@Throws(IOException::class)
		public actual fun tryBind(): Tty? {
			val ttyPtr = Jni.ttyInit()
			if (ttyPtr != 0L) {
				return Tty(ttyPtr)
			}
			return null
		}
	}

	/** Read from the TTY using a regular [InputStream]. */
	public fun asInputStream(): InputStream = TtyInputStream(this)

	/** Write to the TTY using a regular [OutputStream]. */
	public fun asOutputStream(): OutputStream = TtyOutputStream(this)

	private var callbackPtr = 0L

	private fun requireOpen(): Long {
		val ptr = ptr
		check(ptr != 0L) { "Tty is closed" }
		return ptr
	}

	public actual fun setCallback(callback: Callback?) {
		val ttyPtr = requireOpen()
		// Detach first so the native side can never invoke a callback while it is being freed.
		Jni.ttySetCallback(ttyPtr, 0L)
		if (callbackPtr != 0L) {
			Jni.ttyCallbackFree(callbackPtr)
			callbackPtr = 0
		}

		if (callback != null) {
			val newCallbackPtr = Jni.ttyCallbackInit(callback)
			if (newCallbackPtr == 0L) {
				throw OutOfMemoryError()
			}
			callbackPtr = newCallbackPtr
			Jni.ttySetCallback(ttyPtr, newCallbackPtr)
		}
	}

	@Throws(IOException::class)
	public actual fun read(buffer: ByteArray, offset: Int, count: Int): Int {
		checkReadWriteArguments(buffer, offset, count)
		return Jni.ttyRead(requireOpen(), buffer, offset, count)
	}

	@Throws(IOException::class)
	public actual fun readWithTimeout(buffer: ByteArray, offset: Int, count: Int, timeoutMillis: Int): Int {
		checkReadWriteArguments(buffer, offset, count)
		return Jni.ttyReadWithTimeout(requireOpen(), buffer, offset, count, timeoutMillis)
	}

	@Throws(IOException::class)
	public actual fun interruptRead() {
		Jni.ttyInterruptRead(requireOpen())
	}

	@Throws(IOException::class)
	public actual fun write(buffer: ByteArray, offset: Int, count: Int): Int {
		checkReadWriteArguments(buffer, offset, count)
		return Jni.ttyWrite(requireOpen(), buffer, offset, count)
	}

	@Throws(IOException::class)
	public actual fun enableRawMode() {
		val ttyPtr = requireOpen()
		Jni.ttyEnableRawMode(ttyPtr)
		// Match cinterop: install the crash-safety net on POSIX where the signal disposition is
		// still the default (a runtime with its own handlers is left untouched by the native side).
		Jni.ttyEnableSignalGuard(ttyPtr)
	}

	@Throws(IOException::class)
	public actual fun enableWindowResizeEvents() {
		Jni.ttyEnableWindowResizeEvents(requireOpen())
	}

	@Throws(IOException::class)
	public actual fun currentSize(): IntArray {
		return Jni.ttyCurrentSize(requireOpen())
	}

	@Throws(IOException::class)
	public actual fun reset() {
		val ttyPtr = ptr
		if (ttyPtr != 0L) {
			Jni.ttyReset(ttyPtr)
		}
	}

	@Throws(IOException::class)
	actual override fun close() {
		val ttyPtr = ptr
		if (ttyPtr != 0L) {
			ptr = 0
			try {
				Jni.ttyFree(ttyPtr)
			} finally {
				if (callbackPtr != 0L) {
					Jni.ttyCallbackFree(callbackPtr)
					callbackPtr = 0
				}
			}
		}
	}

	public actual interface Callback {
		public actual fun onFocus(focused: Boolean)
		public actual fun onResize(columns: Int, rows: Int, width: Int, height: Int)
	}
}
