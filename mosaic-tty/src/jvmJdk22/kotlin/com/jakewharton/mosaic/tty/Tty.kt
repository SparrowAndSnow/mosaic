package com.jakewharton.mosaic.tty

import com.jakewharton.mosaic.tty.Libmosaic.mosaic_tty_current_terminal_size
import com.jakewharton.mosaic.tty.Libmosaic.mosaic_tty_enable_raw_mode
import com.jakewharton.mosaic.tty.Libmosaic.mosaic_tty_enable_signal_guard
import com.jakewharton.mosaic.tty.Libmosaic.mosaic_tty_enable_window_resize_events
import com.jakewharton.mosaic.tty.Libmosaic.mosaic_tty_free
import com.jakewharton.mosaic.tty.Libmosaic.mosaic_tty_init
import com.jakewharton.mosaic.tty.Libmosaic.mosaic_tty_interrupt_read
import com.jakewharton.mosaic.tty.Libmosaic.mosaic_tty_read
import com.jakewharton.mosaic.tty.Libmosaic.mosaic_tty_read_with_timeout
import com.jakewharton.mosaic.tty.Libmosaic.mosaic_tty_reset
import com.jakewharton.mosaic.tty.Libmosaic.mosaic_tty_set_callback
import com.jakewharton.mosaic.tty.Libmosaic.mosaic_tty_write
import java.io.InputStream
import java.io.OutputStream
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

public class Tty internal constructor(
	private var ptr: MemorySegment,
) : AutoCloseable {
	public companion object {
		@JvmStatic
		@Throws(IOException::class)
		public fun tryBind(): Tty? {
			NativeLibrary.ensureLoaded()

			Arena.ofConfined().use { arena ->
				val result = mosaic_tty_init(arena)
				val ptr = MosaicTtyInitResult.tty(result)
				if (ptr != MemorySegment.NULL) {
					return Tty(ptr)
				}
				if (MosaicTtyInitResult.no_tty(result)) {
					return null
				}
				if (MosaicTtyInitResult.already_bound(result)) {
					throw IllegalStateException("Tty already bound")
				}
				val error = MosaicTtyInitResult.error(result)
				if (error != 0) {
					throwIoe(error)
				}
				throw OutOfMemoryError()
			}
		}
	}

	public fun asInputStream(): InputStream = TtyInputStream(this)

	public fun asOutputStream(): OutputStream = TtyOutputStream(this)

	private var callbackArena: Arena? = null

	private fun requireOpen(): MemorySegment {
		val ptr = ptr
		check(ptr != MemorySegment.NULL) { "Tty is closed" }
		return ptr
	}

	public fun setCallback(callback: Callback?) {
		val ttyPtr = requireOpen()
		// Detach first so the native side can never invoke a callback while its arena is closed.
		mosaic_tty_set_callback(ttyPtr, MemorySegment.NULL)
		callbackArena?.let { arena ->
			callbackArena = null
			arena.close()
		}

		if (callback == null) {
			return
		}

		val arena = Arena.ofShared()
		callbackArena = arena
		val ttyCallback = try {
			MosaicTtyCallback.allocate(arena).also { ttyCallback ->
				MosaicTtyCallback.onFocus(
					ttyCallback,
					MosaicTtyCallbackOnFocus.allocate({ _, focused ->
						callback.onFocus(focused)
					}, arena),
				)
				MosaicTtyCallback.onResize(
					ttyCallback,
					MosaicTtyCallbackOnResize.allocate({ _, columns, rows, width, height ->
						callback.onResize(columns, rows, width, height)
					}, arena),
				)
			}
		} catch (t: Throwable) {
			callbackArena = null
			arena.close()
			throw t
		}
		mosaic_tty_set_callback(ttyPtr, ttyCallback)
	}

	@Throws(IOException::class)
	public fun read(buffer: ByteArray, offset: Int, count: Int): Int {
		checkReadWriteArguments(buffer, offset, count)
		val ptr = requireOpen()
		Arena.ofConfined().use { arena ->
			val segment = arena.allocate(count.toLong())
			val result = mosaic_tty_read(arena, ptr, segment, count)
			val error = MosaicIoResult.error(result)
			if (error == 0) {
				val read = MosaicIoResult.count(result)
				MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, 0L, buffer, offset, read)
				return read
			}
			throwIoe(error)
		}
	}

	@Throws(IOException::class)
	public fun readWithTimeout(buffer: ByteArray, offset: Int, count: Int, timeoutMillis: Int): Int {
		checkReadWriteArguments(buffer, offset, count)
		val ptr = requireOpen()
		Arena.ofConfined().use { arena ->
			val segment = arena.allocate(count.toLong())
			val result = mosaic_tty_read_with_timeout(arena, ptr, segment, count, timeoutMillis)
			val error = MosaicIoResult.error(result)
			if (error == 0) {
				val read = MosaicIoResult.count(result)
				MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, 0L, buffer, offset, read)
				return read
			}
			throwIoe(error)
		}
	}

	@Throws(IOException::class)
	public fun interruptRead() {
		val error = mosaic_tty_interrupt_read(requireOpen())
		if (error == 0) return
		throwIoe(error)
	}

	@Throws(IOException::class)
	public fun write(buffer: ByteArray, offset: Int, count: Int): Int {
		checkReadWriteArguments(buffer, offset, count)
		val ptr = requireOpen()
		Arena.ofConfined().use { arena ->
			val segment = arena.allocate(count.toLong())
			MemorySegment.copy(buffer, offset, segment, ValueLayout.JAVA_BYTE, 0, count)
			val result = mosaic_tty_write(arena, ptr, segment, count)
			val error = MosaicIoResult.error(result)
			if (error == 0) {
				return MosaicIoResult.count(result)
			}
			throwIoe(error)
		}
	}

	@Throws(IOException::class)
	public fun enableRawMode() {
		val ptr = requireOpen()
		val error = mosaic_tty_enable_raw_mode(ptr)
		if (error != 0) throwIoe(error)
		// Match cinterop: install the crash-safety net where the signal disposition is still the
		// default (a runtime with its own handlers is left untouched by the native side).
		mosaic_tty_enable_signal_guard(ptr)
	}

	@Throws(IOException::class)
	public fun enableWindowResizeEvents() {
		val error = mosaic_tty_enable_window_resize_events(requireOpen())
		if (error == 0) return
		throwIoe(error)
	}

	@Throws(IOException::class)
	public fun currentSize(): IntArray {
		val ptr = requireOpen()
		Arena.ofConfined().use { arena ->
			val result = mosaic_tty_current_terminal_size(arena, ptr)
			val error = MosaicTtyTerminalSizeResult.error(result)
			if (error == 0) {
				return intArrayOf(
					MosaicTtyTerminalSizeResult.columns(result),
					MosaicTtyTerminalSizeResult.rows(result),
					MosaicTtyTerminalSizeResult.width(result),
					MosaicTtyTerminalSizeResult.height(result),
				)
			}
			throwIoe(error)
		}
	}

	@Throws(IOException::class)
	public fun reset() {
		val ptr = this.ptr
		if (ptr != MemorySegment.NULL) {
			val error = mosaic_tty_reset(ptr)
			if (error == 0) return
			throwIoe(error)
		}
	}

	@Throws(IOException::class)
	override fun close() {
		val ptr = this.ptr
		if (ptr != MemorySegment.NULL) {
			this.ptr = MemorySegment.NULL

			val error = mosaic_tty_free(ptr)
			callbackArena?.close()
			callbackArena = null

			if (error == 0) return
			throwIoe(error)
		}
	}

	public interface Callback {
		public fun onFocus(focused: Boolean)
		public fun onResize(columns: Int, rows: Int, width: Int, height: Int)
	}
}

internal fun throwIoe(error: Int): Nothing {
	throw IOException(error.toString())
}
