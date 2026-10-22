package com.jakewharton.mosaic.tty.terminal

internal const val ESC = "\u001B"
internal const val ST = "${ESC}\\"

internal const val APC = "${ESC}_"
internal const val CSI = "$ESC["
internal const val DCS = "${ESC}P"
internal const val OSC = "$ESC]"
internal const val SS3 = "${ESC}O"

internal const val cursorMode = 25
internal const val cursorEnable = "$CSI?${cursorMode}h"
internal const val cursorDisable = "$CSI?${cursorMode}l"

// The alternate screen is a separate full-screen buffer: entering it keeps the shell's scrollback
// intact, and leaving it restores the original screen exactly as it was before the TUI started.
internal const val alternateScreenMode = 1049
internal const val alternateScreenEnable = "$CSI?${alternateScreenMode}h"
internal const val alternateScreenDisable = "$CSI?${alternateScreenMode}l"

internal const val focusMode = 1004
internal const val focusEnable = "$CSI?${focusMode}h"
internal const val focusDisable = "$CSI?${focusMode}l"

internal const val synchronizedOutputMode = 2026

internal const val systemThemeMode = 2031
internal const val systemThemeEnable = "$CSI?${systemThemeMode}h"
internal const val systemThemeDisable = "$CSI?${systemThemeMode}l"

internal const val inBandResizeMode = 2048
internal const val inBandResizeEnable = "$CSI?${inBandResizeMode}h"
internal const val inBandResizeDisable = "$CSI?${inBandResizeMode}l"

internal const val graphemeClusteringMode = 2027
internal const val graphemeClusteringEnable = "$CSI?${graphemeClusteringMode}h"
internal const val graphemeClusteringDisable = "$CSI?${graphemeClusteringMode}l"

internal const val mouseSgrMode = 1006
internal const val mouseMode = 1000
internal const val mouseMotionMode = 1003
internal const val mouseEnable = "$CSI?${mouseSgrMode}h$CSI?${mouseMode}h$CSI?${mouseMotionMode}h"
internal const val mouseDisable = "$CSI?${mouseSgrMode}l$CSI?${mouseMode}l$CSI?${mouseMotionMode}l"

internal const val bracketedPasteMode = 2004
internal const val pasteEnable = "$CSI?${bracketedPasteMode}h"
internal const val pasteDisable = "$CSI?${bracketedPasteMode}l"

// Kitty keyboard protocol ("CSI u"). A push activates the protocol and a pop restores the
// terminal to its previous configuration. Flag bits: 1 = disambiguate escape codes,
// 2 = report event types (press/repeat/release), 4 = report alternate keys,
// 16 = report associated text.
internal const val kittyKeyboardFlags = 0b1_0111
internal const val kittyKeyboardPush = "$CSI>${kittyKeyboardFlags}u"
internal const val kittyKeyboardPop = "$CSI<${kittyKeyboardFlags}u"
