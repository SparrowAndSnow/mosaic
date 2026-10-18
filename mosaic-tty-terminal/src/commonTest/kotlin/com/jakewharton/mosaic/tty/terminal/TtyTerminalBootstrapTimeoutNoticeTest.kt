package com.jakewharton.mosaic.tty.terminal

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import kotlin.test.Test

/**
 * The one-second bootstrap budget must never be silently exceeded: when a terminal does not finish
 * answering capability queries, [bootstrapTimeoutNotice] produces a diagnostic so the degraded run
 * is observable instead of looking normal with features quietly missing.
 */
class TtyTerminalBootstrapTimeoutNoticeTest {
	@Test fun defaultNoticeExplainsTimeoutAndPointsAtDebugFlag() {
		val notice = bootstrapTimeoutNotice(StageDeviceAttributes, emptyList(), debug = false)

		assertThat(notice).contains("did not respond within the 1 second budget")
		assertThat(notice).contains("awaiting an initial device-attributes (DA1) reply")
		assertThat(notice).contains("MOSAIC_TTY_TERMINAL_DEBUG=true")
		assertThat(notice).doesNotContain("Confirmed capabilities")
	}

	@Test fun debugNoticeListsConfirmedCapabilitiesWhenPresent() {
		val notice = bootstrapTimeoutNotice(
			StageCapabilityQueries,
			listOf("cursor visibility", "kitty keyboard", "synchronized output"),
			debug = true,
		)

		assertThat(notice).contains("awaiting capability-query replies")
		assertThat(notice).contains("Confirmed capabilities: cursor visibility, kitty keyboard, synchronized output.")
	}

	@Test fun debugNoticeSaysNoneWhenNothingConfirmed() {
		val notice = bootstrapTimeoutNotice(StageDefaultQueries, emptyList(), debug = true)

		assertThat(notice).contains("awaiting default-state query replies")
		assertThat(notice).contains("No capabilities were confirmed.")
	}

	@Test fun unknownStageFallsBackToGenericWording() {
		val notice = bootstrapTimeoutNotice(StageNormalOperation, emptyList(), debug = false)

		assertThat(notice).contains("awaiting a reply")
	}
}
