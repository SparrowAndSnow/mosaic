package com.jakewharton.mosaic.tty

/**
 * Validates the buffer range shared by the [Tty] read/write entry points.
 *
 * Every binding performs this check before touching native memory so an invalid range fails with
 * the same [IndexOutOfBoundsException] on JNI, FFM, and cinterop instead of corrupting memory or
 * surfacing a platform-specific exception.
 */
internal fun checkReadWriteArguments(buffer: ByteArray, offset: Int, count: Int) {
	if (offset < 0) {
		throw IndexOutOfBoundsException("offset < 0: $offset")
	}
	if (count < 0) {
		throw IndexOutOfBoundsException("count < 0: $count")
	}
	if (offset > buffer.size - count) {
		throw IndexOutOfBoundsException("offset + count > size: $offset + $count > ${buffer.size}")
	}
}
