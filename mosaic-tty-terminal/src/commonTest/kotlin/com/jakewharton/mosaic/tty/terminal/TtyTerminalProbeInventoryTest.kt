package com.jakewharton.mosaic.tty.terminal

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.io.bytestring.ByteString

// Locks the startup probe inventory so a capability query can never be dropped without intent.
// Every probe below feeds a field of Terminal.Capabilities; removing any one silently degrades
// that capability across all terminals.
class TtyTerminalProbeInventoryTest {
	@Test fun startupQueryInventory() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal { setup ->
			// DECRQM (CSI ?Ps$p, reported as CSI ?Ps;Pm$y): one query per probed DEC private mode.
			assertThat(setup).contains("$CSI?25\$p") // Cursor visibility.
			assertThat(setup).contains("$CSI?1004\$p") // Focus events.
			assertThat(setup).contains("$CSI?2026\$p") // Synchronized output.
			assertThat(setup).contains("$CSI?2031\$p") // System theme events.
			assertThat(setup).contains("$CSI?2048\$p") // In-band resize.
			assertThat(setup).contains("$CSI?2027\$p") // Grapheme clustering.
			// Current theme value: CSI ?996n query, reported as CSI ?997;{1|2}n.
			assertThat(setup).contains("$CSI?996n")
			// Kitty graphics APC probe.
			assertThat(setup).contains("${APC}Gi=31,s=1,v=1,a=q,t=d,f=24;AAAA$ST")
			// Kitty keyboard protocol ("CSI u").
			assertThat(setup).contains("$CSI?u")
			// Kitty notifications (OSC 99).
			assertThat(setup).contains("${OSC}99;i=1:p=?$ST")
			// Kitty pointer shape (OSC 22).
			assertThat(setup).contains("${OSC}22;?__current__$ST")
			// Kitty text sizing probes (DSR cursor-position around OSC 66 toggles).
			assertThat(setup).contains("${CSI}6n")
			// Kitty underline ("Su" capability via DCS +q).
			assertThat(setup).contains("$DCS+q5375$ST")
			// Xterm version ("CSI > 0 q").
			assertThat(setup).contains("$CSI>0q")
			// DSR batch end marker, so a missing reply is distinguishable from a dropped query.
			assertThat(setup).contains("${CSI}5n")
		}
		assertThat(teardown).isEqualTo(ByteString())
	}
}
