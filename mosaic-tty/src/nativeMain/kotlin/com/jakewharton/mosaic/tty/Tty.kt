package com.jakewharton.mosaic.tty

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.NativePlacement
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned

public actual class Tty internal constructor(
	ptr: CPointer<MosaicTty>,
) : AutoCloseable {
	public actual companion object {
		public actual fun tryBind(): Tty? {
			mosaic_tty_init().useContents {
				tty?.let { ptr ->
					return Tty(ptr)
				}
				if (no_tty) {
					return null
				}
				if (already_bound) {
					throw IllegalStateException("Tty already bound")
				}
				if (error != 0U) {
					throwIoe(error)
				}
				throw OutOfMemoryError()
			}
		}
	}

	private var ptr: CPointer<MosaicTty>? = ptr
	private var callbackPtrAndRef: Pair<CPointer<MosaicTtyCallback>, StableRef<Callback>>? = null

	private fun requireOpen(): CPointer<MosaicTty> {
		return checkNotNull(ptr) { "Tty is closed" }
	}

	public actual fun setCallback(callback: Callback?) {
		val ttyPtr = requireOpen()
		// Detach first so the native side can never invoke a callback while it is being freed.
		mosaic_tty_set_callback(ttyPtr, null)
		callbackPtrAndRef?.let { (callbackPtr, callbackRef) ->
			nativeHeap.free(callbackPtr)
			callbackRef.dispose()
			callbackPtrAndRef = null
		}

		callback?.let { callback ->
			val callbackRef = StableRef.create(callback)
			val callbackPtr = callbackRef.toNativeAllocationIn(nativeHeap).ptr

			callbackPtrAndRef = callbackPtr to callbackRef
			mosaic_tty_set_callback(ttyPtr, callbackPtr)
		}
	}

	public actual fun read(buffer: ByteArray, offset: Int, count: Int): Int {
		checkReadWriteArguments(buffer, offset, count)
		val ttyPtr = requireOpen()
		buffer.asUByteArray().usePinned {
			mosaic_tty_read(ttyPtr, it.addressOf(offset), count).useContents {
				if (error == 0U) return this.count
				throwIoe(error)
			}
		}
	}

	public actual fun readWithTimeout(buffer: ByteArray, offset: Int, count: Int, timeoutMillis: Int): Int {
		checkReadWriteArguments(buffer, offset, count)
		val ttyPtr = requireOpen()
		buffer.asUByteArray().usePinned {
			mosaic_tty_read_with_timeout(ttyPtr, it.addressOf(offset), count, timeoutMillis).useContents {
				if (error == 0U) return this.count
				throwIoe(error)
			}
		}
	}

	public actual fun interruptRead() {
		val error = mosaic_tty_interrupt_read(requireOpen())
		if (error == 0U) return
		throwIoe(error)
	}

	public actual fun write(buffer: ByteArray, offset: Int, count: Int): Int {
		checkReadWriteArguments(buffer, offset, count)
		val ttyPtr = requireOpen()
		buffer.asUByteArray().usePinned {
			mosaic_tty_write(ttyPtr, it.addressOf(offset), count).useContents {
				if (error == 0U) return this.count
				throwIoe(error)
			}
		}
	}

	public actual fun enableRawMode() {
		val ttyPtr = requireOpen()
		val error = mosaic_tty_enable_raw_mode(ttyPtr)
		if (error != 0U) throwIoe(error)
		// With the terminal in raw mode, install the crash/termination safety net which restores
		// the original settings if the process is killed by an unhandled signal. Failure to
		// install the guard is not fatal: raw mode is still active, just without the fallback.
		mosaic_tty_enable_signal_guard(ttyPtr)
	}

	public actual fun enableWindowResizeEvents() {
		val error = mosaic_tty_enable_window_resize_events(requireOpen())
		if (error == 0U) return
		throwIoe(error)
	}

	public actual fun currentSize(): IntArray {
		mosaic_tty_current_terminal_size(requireOpen()).useContents {
			if (error == 0U) {
				return intArrayOf(columns, rows, width, height)
			}
			throwIoe(error)
		}
	}

	public actual fun reset() {
		ptr?.let { ttyPtr ->
			val error = mosaic_tty_reset(ttyPtr)
			if (error != 0U) throwIoe(error)
		}
	}

	actual override fun close() {
		ptr?.let { ptr ->
			this.ptr = null

			val error = mosaic_tty_free(ptr)

			callbackPtrAndRef?.let { (callbackPtr, callbackRef) ->
				nativeHeap.free(callbackPtr)
				callbackRef.dispose()
				callbackPtrAndRef = null
			}

			if (error == 0U) return
			throwIoe(error)
		}
	}

	public actual interface Callback {
		public actual fun onFocus(focused: Boolean)
		public actual fun onResize(columns: Int, rows: Int, width: Int, height: Int)
	}
}

internal fun throwIoe(error: UInt): Nothing {
	throw IOException(error.toString())
}

internal fun StableRef<Tty.Callback>.toNativeAllocationIn(memory: NativePlacement): MosaicTtyCallback {
	return memory.alloc<MosaicTtyCallback> {
		opaque = asCPointer()
		onFocus = staticCFunction(::onFocusCallback)
		onResize = staticCFunction(::onResizeCallback)
	}
}

private fun onFocusCallback(opaque: COpaquePointer?, focused: Boolean) {
	val callback = opaque!!.asStableRef<Tty.Callback>().get()
	callback.onFocus(focused)
}

private fun onResizeCallback(opaque: COpaquePointer?, columns: Int, rows: Int, width: Int, height: Int) {
	val callback = opaque!!.asStableRef<Tty.Callback>().get()
	callback.onResize(columns, rows, width, height)
}
