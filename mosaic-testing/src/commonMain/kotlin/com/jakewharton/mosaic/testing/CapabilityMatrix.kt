package com.jakewharton.mosaic.testing

import com.jakewharton.mosaic.terminal.AnsiLevel
import com.jakewharton.mosaic.terminal.Terminal

/**
 * Representative capability combinations for the testing capability matrix. Each render-affecting
 * dimension — ANSI level, kitty underline, synchronized output, grapheme clustering — is exercised
 * in its on and off states, plus a conservative all-off terminal, so tests can prove a behavior
 * holds regardless of which capabilities a real terminal exposes.
 */
public object CapabilityMatrix {

	public val defaults: List<Terminal.Capabilities> = listOf(
		TestTerminal.Capabilities(),
		TestTerminal.Capabilities(graphemeClustering = false),
		TestTerminal.Capabilities(kittyUnderline = false),
		TestTerminal.Capabilities(synchronizedOutput = false),
		TestTerminal.Capabilities(ansiLevel = AnsiLevel.ANSI16),
		TestTerminal.Capabilities(ansiLevel = AnsiLevel.ANSI256),
		TestTerminal.Capabilities(
			ansiLevel = AnsiLevel.NONE,
			kittyUnderline = false,
			synchronizedOutput = false,
			graphemeClustering = false,
		),
	)
}

/**
 * Runs [block] once per capability combination in [capabilities] with a fresh mosaic each time,
 * threading the combination through so tests can branch on it. Defaults to [CapabilityMatrix.defaults].
 */
public suspend fun runMosaicTestForEach(
	capabilities: List<Terminal.Capabilities> = CapabilityMatrix.defaults,
	block: suspend TestMosaic<String>.(Terminal.Capabilities) -> Unit,
) {
	for (capability in capabilities) {
		runMosaicTest(snapshotStrategy = PlainTextSnapshots, capabilities = capability) {
			block(capability)
		}
	}
}
