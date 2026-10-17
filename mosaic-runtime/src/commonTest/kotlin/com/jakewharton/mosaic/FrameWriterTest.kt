package com.jakewharton.mosaic

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FrameWriterTest {
	private val chunks = mutableListOf<ByteArray>()
	private var flushes = 0

	private fun writer(chunkSize: Int): FrameWriter {
		chunks.clear()
		flushes = 0
		return FrameWriter(
			chunkSize = chunkSize,
			writeBytes = { bytes, offset, count ->
				chunks += bytes.copyOfRange(offset, offset + count)
			},
			flushBytes = { flushes++ },
		)
	}

	private fun concatenated(): ByteArray {
		val total = chunks.sumOf { it.size }
		val result = ByteArray(total)
		var offset = 0
		for (chunk in chunks) {
			chunk.copyInto(result, offset)
			offset += chunk.size
		}
		return result
	}

	@Test fun asciiFrameWritesSingleChunk() {
		val writer = writer(chunkSize = 8192)
		writer.write("Hello")
		assertThat(chunks.map { it.decodeToString() }).isEqualTo(listOf("Hello"))
		assertThat(flushes).isEqualTo(1)
	}

	@Test fun emptyFrameWritesNothingButStillFlushes() {
		val writer = writer(chunkSize = 4)
		writer.write("")
		assertTrue(chunks.isEmpty())
		assertThat(flushes).isEqualTo(1)
	}

	@Test fun codePointsNeverStraddleChunkBoundaries() {
		val writer = writer(chunkSize = 4)
		writer.write("A🔥B€中")
		// Each emitted chunk must hold only whole code points; "B"+"€" pack into one 4-byte chunk.
		assertThat(chunks.map { it.decodeToString() }).isEqualTo(listOf("A", "🔥", "B€", "中"))
		assertThat(concatenated()).isEqualTo("A🔥B€中".encodeToByteArray())
	}

	@Test fun multiByteCharsDeferAsWholeAcrossFullChunk() {
		val writer = writer(chunkSize = 4)
		writer.write("€€€")
		assertThat(concatenated()).isEqualTo("€€€".encodeToByteArray())
		// "€" is 3 bytes and never fits after a sibling fills the 4-byte chunk.
		assertThat(chunks.map { it.decodeToString() }).isEqualTo(listOf("€", "€", "€"))
	}

	@Test fun surrogatePairReadAheadProducesOneFourByteCodePoint() {
		val writer = writer(chunkSize = 4)
		writer.write("A\uD83D\uDD25") // A + U+1F525 fire
		assertThat(concatenated()).isEqualTo("A🔥".encodeToByteArray())
	}

	@Test fun inputLargerThanChunkWritesMultipleBoundedChunks() {
		val writer = writer(chunkSize = 16)
		val frame = (0 until 200).joinToString("") { "x" }
		writer.write(frame)
		assertThat(concatenated()).isEqualTo(frame.encodeToByteArray())
		assertTrue(chunks.all { it.size <= 16 })
		assertTrue(chunks.isNotEmpty())
	}

	@Test fun loneSurrogateReplaceWithQuestionMark() {
		val writer = writer(chunkSize = 4)
		writer.write("a\uD800b")
		assertThat(concatenated().decodeToString()).isEqualTo("a?b")
	}

	@Test fun chunkSizeBelowFourBytesIsRejected() {
		assertFailsWith<IllegalArgumentException> { writer(chunkSize = 3) }
	}

	@Test fun writerReusesBufferAcrossFrames() {
		val writer = writer(chunkSize = 8)
		writer.write("first")
		val firstChunks = chunks.size
		writer.write("very long second frame content")
		assertTrue(chunks.size > firstChunks)
		assertThat(concatenated()).isEqualTo("firstvery long second frame content".encodeToByteArray())
	}
}
