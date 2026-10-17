package com.jakewharton.mosaic

/**
 * Writes frames to the terminal in fixed-size byte chunks instead of materializing the whole
 * frame as one string and writing it in a single call.
 *
 * The renderers hand back a reused [CharSequence] backed by an internal [StringBuilder], so a
 * chunked writer avoids the per-frame copy that a `toString()` plus a single large `print` would
 * otherwise cause. Each frame is encoded to UTF-8 into a reusable buffer, and a sink writes
 * bounded chunks, keeping allocation flat across frames of any size.
 */
internal const val FRAME_CHUNK_SIZE = 8192

internal class FrameWriter(
	chunkSize: Int = FRAME_CHUNK_SIZE,
	private val writeBytes: (bytes: ByteArray, offset: Int, count: Int) -> Unit = ::writeScreenBytes,
	private val flushBytes: () -> Unit = ::flushScreenBytes,
) {
	init {
		// A single code point can encode to up to 4 bytes; a smaller chunk would never fit the
		// widest code point and the fill/flush retry of appendUtf8 would loop forever.
		require(chunkSize >= 4) { "chunkSize must be at least 4, was $chunkSize" }
	}

	private val buffer = ByteArray(chunkSize)
	private var position = 0

	fun write(frame: CharSequence) {
		var index = 0
		while (index < frame.length) {
			var codePoint = frame[index].code
			var advance = 1
			val c = frame[index]
			if (c.isHighSurrogate() && index + 1 < frame.length && frame[index + 1].isLowSurrogate()) {
				val high = c.code - 0xD800
				val low = frame[index + 1].code - 0xDC00
				codePoint = 0x10000 + (high shl 10) + low
				advance = 2
			} else if (c.isSurrogate()) {
				// Unpaired surrogates cannot be encoded as valid UTF-8; substitute the same
				// replacement the String encoder would emit rather than drop the char silently.
				codePoint = '?'.code
			}
			index += advance
			while (!appendUtf8(codePoint)) {
				flushChunk()
			}
		}
		flushChunk()
		flushBytes()
	}

	private fun appendUtf8(codePoint: Int): Boolean {
		val size = when {
			codePoint < 0x80 -> 1
			codePoint < 0x800 -> 2
			codePoint < 0x10000 -> 3
			else -> 4
		}
		if (position + size > buffer.size) {
			return false
		}
		when (size) {
			1 -> buffer[position++] = codePoint.toByte()
			2 -> {
				buffer[position++] = (0xC0 or (codePoint shr 6)).toByte()
				buffer[position++] = (0x80 or (codePoint and 0x3F)).toByte()
			}
			3 -> {
				buffer[position++] = (0xE0 or (codePoint shr 12)).toByte()
				buffer[position++] = (0x80 or ((codePoint shr 6) and 0x3F)).toByte()
				buffer[position++] = (0x80 or (codePoint and 0x3F)).toByte()
			}
			else -> {
				buffer[position++] = (0xF0 or (codePoint shr 18)).toByte()
				buffer[position++] = (0x80 or ((codePoint shr 12) and 0x3F)).toByte()
				buffer[position++] = (0x80 or ((codePoint shr 6) and 0x3F)).toByte()
				buffer[position++] = (0x80 or (codePoint and 0x3F)).toByte()
			}
		}
		return true
	}

	private fun flushChunk() {
		if (position > 0) {
			writeBytes(buffer, 0, position)
			position = 0
		}
	}
}
