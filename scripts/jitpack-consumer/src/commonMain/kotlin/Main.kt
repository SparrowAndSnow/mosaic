package consumer

import com.jakewharton.mosaic.testing.runMosaicTest
import com.jakewharton.mosaic.tty.TestTerminal
import com.jakewharton.mosaic.ui.Text
import kotlinx.coroutines.runBlocking

fun main() {
	runBlocking {
		TestTerminal.bind().use { terminal ->
			val bytes = "JitPack TTY".encodeToByteArray()
			terminal.tty.enableRawMode()
			check(terminal.tty.write(bytes, 0, bytes.size) == bytes.size)
			terminal.tty.reset()
			val received = ByteArray(bytes.size)
			check(terminal.readTtyWithTimeout(received, 0, received.size, 999) == bytes.size)
			check(received.contentEquals(bytes))
		}
		runMosaicTest {
			setContent { Text("JitPack 中文 👋") }
			check(awaitSnapshot() == "JitPack 中文 👋")
		}
		println("JitPack consumer: TTY roundtrip and Unicode rendering passed")
	}
}
