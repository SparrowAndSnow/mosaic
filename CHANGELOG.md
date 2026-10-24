# Change log

## [Unreleased]
[Unreleased]: https://github.com/JakeWharton/mosaic/compare/0.18.0...HEAD

New:
- Grapheme-cluster text: wide CJK, combining sequences, ZWJ families, flags, and variant
  selectors now render in their true cell width (auto-enabled behind DEC 2027 when supported).
- Cell-level incremental rendering with synchronized-output wrapping, plus a snapshot renderer.
- Text soft wrapping, truncation, alignment, and scrolling view windows.
- BoxWithConstraints, layout caching, and fill-fraction modifiers for proportional layouts.
- Pointer interaction (hover/press/release/drag), bracketed paste, clipboard read/write, and the
  kitty keyboard protocol.
- `ClickTracker`, a small helper for double-click detection: call `press(index, nowMillis)` from a
  mouse event handler and it reports whether the same row was pressed again within a configurable
  window, with the caller supplying the clock.
- `Modifier.clickable` accepts an optional `onDoubleClick`: the first release invokes `onClick`, and
  a second release on the same element within 400 ms invokes `onDoubleClick` instead of clicking
  again.
- `TabRow`, a palette-neutral row of clickable tabs: selected, hover, and container colors, an
  optional one-cell divider, and a trailing `RowScope` slot for a right-aligned status hint.
- `OverlayHost` and `Popup`: the host measures a base content slot first and an optional overlay
  slot matching the base size, whose direct children are layers drawn above the base in composition
  order. `Popup` sizes to its content (or fills the base in modal mode), places content at a
  base-relative alignment plus cell offset, shields mouse clicks inside its bounds, and offers
  key/paste/mouse input to the layer before the base.
- Modal `Dialog` and `Popup(modal = true)` with `OverlayBackdrop`: modal layers gate keyboard,
  paste, mouse, and Tab traversal, save and restore base focus, support Escape dismissal, and can
  erase the base (`Solid`, optionally filling a background color) or leave it visible (`None`).
  Layer changes cancel any in-flight press and recompute hover at the last pointer position; hosts
  inside sub-compositions register with the composition root, so modal barriers still apply.
- Focus tracking with Tab/Shift+Tab traversal.
- Hardware cursor control: place the terminal's cursor by layout position with block, underline, or
  bar shapes and optional blinking (`Cursor`); the terminal's default shape is restored on exit.
  While a modal overlay is open, a cursor whose node is outside the active modal domain is hidden
  and restored when that overlay closes.
- Opt-in momentum scrolling: `MomentumScrollState` turns wheel impulses into a decaying fling; the
  application owns the frame loop, calls `advance(dt, maxPosition)` each frame, and maps `position`
  into layout. A default wheel notch travels about three rows.
- Optional alternate-screen support for the `runMosaic*` entry points (`alternateScreen = true`).
  The library enters the alternate screen before capability detection and leaves it during teardown,
  so applications no longer need to emit the control sequences themselves and the shell's scrollback
  is preserved, including on cancellation and signal-driven shutdown.
- Border/divider primitives.
- Animation containers (crossfade, enter/exit, animated visibility).
- JVM support for RISC-V and s390x!

Changed:
- The test-console and process-monitor examples now use neutral paths and identifiers
  (`samples/test-console`, `samples/monitor`), including their Gradle projects, binaries, READMEs,
  and CI sample matrix; behavior is unchanged.
- Terminal teardown now restores every mode it toggled (cursor, mouse, bracketed paste, keyboard,
  grapheme clustering, and so on) before leaving the alternate screen, and writes nothing after the
  leave sequence.
- Use the AndroidX versions of the Compose runtime and lifecycle dependencies which are now fully multiplatform. Dependency constraints to the corresponding JetBrains artifacts (which are now empty) are present to prevent symbol duplication.
- Windows color detection no longer depends on being launched from Windows Terminal. Entering raw
  mode enables VT output processing, and every console that supports it renders 24-bit color, so a
  plain console window (cmd, or a double-clicked executable) now gets color too instead of falling
  back to no color. `NO_COLOR` and an explicit `TERM`/`COLORTERM` still take precedence.
- Remove support for x86 macOS targets. These have been deprecated for a bit, but support has been removed from the Compose runtime so we have no choice but to match.

Fixed:
- Clear the set of states which were read during layout and draw passes before each pass. Previously these sets grew infinitely, which was both a memory leak and a performance problem.
- Close the underlying TTY at the end of the `runMosaic*` family of functions.
- Frame times on Linux and Windows no longer jump backwards once per second.
- Fix arc-based animations on native targets, which built their lookup table incorrectly.
- `Modifier.onGloballyPositioned` now places its content at its own origin. Previously the content
  was measured but never placed, so every descendant drew at the composition origin (for example,
  all rows of a lazy list painted in the top-left corner).
- The JNI and FFM TTY bindings now validate read/write buffer ranges and reject operations on a
  closed instance, matching the native binding instead of risking memory corruption or
  platform-specific failures; all three also install the same POSIX crash-safety net when entering
  raw mode.
- `Modifier.clickable` no longer drops a press when the element's node is replaced or when it lives
  in a sub-composition such as `BoxWithConstraints`. A release now fires while the element remains
  enabled and the pointer is inside the bounds captured at press time; removed elements stay silent.
- A partial `OverlayBackdrop.Solid` rectangle now erases complete wide grapheme clusters: if it
  touches a CJK/emoji head or continuation cell, the head and every continuation cell are cleared,
  so no half glyph remains.
- Modal focus save/restore is keyed by the layer that saved it. Closing dialogs out of order
  discards only the removed layer's entry and never restores a target from a removed or disabled
  layer; when the last modal closes the nearest still-valid saved target is restored.
- `OverlayBackdrop.Solid` now clears at the draw scope's translated origin. A dialog hosted below
  a header (or above a footer) only erases the host area instead of every row from the canvas
  origin through the host height.
- Windows window resizes no longer wedge input. A window-size or focus record which arrived with
  no keystrokes pending was dispatched as a callback but never consumed, so the reader looped on it:
  a pegged core, a resize callback storm which crowded real input out of the event channel, and an
  uninterruptible read which could outlive the terminal when it was closed. Those records are now
  consumed, the reported size is derived from the actual window (so a resize swallowed by a read is
  not lost), and an interrupt is honored even while console records are pending.
- Windows input no longer stalls after a key release or a mouse event. Only a key press makes the
  console's VT translation produce bytes; a release or mouse record left the reader blocked inside
  the read until the next key press, which also froze any timed read (such as the one which
  disambiguates a bare Escape). Records which cannot produce bytes are consumed instead of being
  handed to a read which would never return. Windows mouse events are therefore not delivered yet;
  the console's VT input translation produces no bytes for them.
- Windows input no longer depends on the console keeping the modes Mosaic applied. Anything else in
  the process which resets the console (an audio engine or a driver it loads is the usual culprit)
  left it line-buffered and echoing, so reads blocked until Enter and every keystroke was printed
  onto the screen instead of reaching the application. The reader now compares the console's modes
  with the ones it applied and restores them, waking once a second while idle to repair a reset;
  `enableRawMode()` also re-asserts the modes when raw mode is already enabled instead of doing
  nothing.
- Shrinking the terminal below the content's natural size no longer crashes a weighted `Row` or
  `Column`. Fixed children can need more space than the parent offers (content-sized children keep
  their own size), which made the space left for weights negative and built an empty `coerceIn`
  range: `IllegalArgumentException: Cannot coerce value to an empty range`. The remaining space
  now clamps to zero, so weighted children simply get none.
- The JNI binding now looks up the calling thread's JNI environment when invoking TTY callbacks
  instead of reusing the environment captured when the callback was registered. A JNI environment
  is only valid on its own thread, so callbacks delivered from the reader thread could fail or
  corrupt the VM state.


## [0.18.0] - 2025-08-21
[0.18.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.18.0

New:
- JVM artifacts can now be used as modules in the Java module system.
- On Java 22 and newer, Mosaic now uses the [foreign function & memory API](https://docs.oracle.com/en/java/javase/22/core/foreign-function-and-memory-api.html) to call into its native library instead of JNI.

Changed:
- Unsolicited focus, theme, and resize events are now ignored unless the terminal has reported that it supports each of those modes.
  This should have no real impact on anything, except that now the `Terminal.capabilities` value can now be trusted to indicate whether `Terminal.state` will ever change in the case of focus and theme.
  For terminal size, a platform-specific fallback exists which will attempt to still correctly report the size, but asynchronously.
- Terminal theme is now always queried for an initial value regardless of whether the terminal supports sending theme changes.
- Java 11 is now the minimum-supported JVM version.
- In-development snapshots of Mosaic are now published to the Central Portal Snapshots repository at https://central.sonatype.com/repository/maven-snapshots/.

Fixed:
- Windows now reports the terminal size correctly. Previously it reported the buffer size instead of the window size.
- Windows Terminal and ConEmu are now correctly detected as supporting color.


## [0.17.0] - 2025-04-11
[0.17.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.17.0

New:
- `Terminal` and `LocalTerminal` are now renamed to `TerminalState` and `LocalTerminalState`, respectively, along with some new capabilities:
  - Add `focused` boolean which defaults to true. If the terminal supports sending focus changes, this will be updated in real time.
  - Add `theme` property which defaults to 'unknown'. If the terminal supports querying theme state, this will be 'light' or 'dark' and be updated in real time. Note: this is not the OS theme, but instead reflects the theme of the terminal color scheme.
  - Size property now features the size in pixels in addition to cells. These values will be 0 if unsupported by the terminal.
- Bind `TerminalState.focused` to a `Lifecycle` and expose into the composition as `LocalLifecycleOwner`. This allows using Compose lifecycle helpers such as `LifecycleResumeEffect` and others.
- Fancy underline styles (single, double, dashed, dotted, curved) and colors can now be specified for text and annotated string spans.
- `LocalStaticLogger` composition local provides access to `StaticLogger` which allows logging plain strings at arbitrary points for inclusion in the next frame. This can be used from effects, callback, state classes, etc. Support for logging `AnnotatedString`s will come in a future release.
- `runMosaicMain` function replaces the existing `runMosaicBlocking` for use specifically in `fun main()` or main-like scenarios.

Changed:
- Switched to our own terminal integration and parsing library. Report any issues with keyboard input, incorrect size reporting, or garbled output. This unlocks many of the features listed above, as well as many more planned in the future.
- Only disable the cursor and emit synchronized rendering markers if the terminal reports support for those features.
- `Static` function is now called `StaticEffect` to better indicate that it only renders its content once.
- `runMosaic` and `runMosaicBlocking` now accept a `NonInteractivePolicy` argument which dictates the behavior when Mosaic cannot connect directly to the TTY.

Fixed:
- Prevent final character from being erased when a row writes into the last column of the terminal.
- Do not emit ANSI style reset escape sequence when colors are disabled (such as in testing).
- Do not draw blank spaces at the end of every line.

Removed:
- `renderMosaic` was removed without replacement. As the capabilities of the library grow, supporting a string as a render target was increasingly difficult.


## [0.16.0] - 2025-02-14
[0.16.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.16.0

New:
- Add `setContentAndSnapshot` to 'mosaic-testing' which returns the initial composition snapshot. This avoids a potential problem with calling `setContent` and then `awaitSnapshot` since the latter will trigger a subsequent recomposition (if needed), preventing observation of the initial state.

Changed:
- `Static` composable no longer takes a list. Instead, it behaves like a normal composable which only renders its function once.
- Drop use of Jansi library for supporting ANSI color output on Windows.


## [0.15.0] - 2025-01-07
[0.15.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.15.0

New:
- Create `mosaic-animation` library, that provides various possibilities for animating Mosaic. An analog of [androidx.compose.animation-core](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/package-summary).
- Add `IntrinsicSize` and related `Modifier.width`/`height`/`requiredWidth`/`requiredHeight`.
- New `mosaic-testing` library for deterministically rendering Mosaic composables under test.
- Add `Mosaic.layoutId` which allows identifying the element within its parent during layout.
- Add `Modifier.composed` that allows creating custom reusable modifiers with access to Compose functionality inside.

Changed:
- Rendering now occurs as fast as possible, although still only when necessary. Previously the maximum FPS was capped to 20, which could cause minor visual delays when processing events.

Fixed:
- Fix the handling of custom `Modifier`s that have multiple parents from `LayoutModifier`, `DrawModifier`, `KeyModifier`, `ParentDataModifier`.


## [0.14.0] - 2024-10-07
[0.14.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.14.0

New:
- Support synchronized terminal update for rendering. This should eliminate tearing when rendering updates for terminals that support this feature.
- The terminal cursor is now automatically hidden during rendering and restored afterward.
- Added `Modifier.onKeyEvent` or `Modifier.onPreKeyEvent` to listen to keyboard events.
- Send real frame times into Compose which can be used for things like animations.

Changed:
- The entrypoints (`runMosaic` and `runMosaicBlocking`) have been changed to directly accept a composable lambda. Asynchronous work should now be performed inside Compose's effect system (e.g., `LaunchedEffect`), and rendering will complete when all effects have completed. Check out our samples for more information about how to do this.
- Сhange `DrawScope#drawRect` API with the ability to draw with text characters and specify `DrawStyle` (`Fill` or `Stroke`).
- Drop support for JS target. If you were using this, please file an issue so we can discuss support.


## [0.13.0] - 2024-05-28
[0.13.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.13.0

New:
- Support Kotlin 2.0.0!

Changed:
- Remove our Gradle plugin in favor of JetBrains' (see below for more).

Note: Version 0.12.0 was also released today, but it still supports Kotlin 1.9.24.
Check out [its release entry](https://github.com/JakeWharton/mosaic/releases/tag/0.12.0) for more on what's new.


### Gradle plugin removed

This version of Mosaic removes the custom Gradle plugin in favor of [the official JetBrains Compose compiler plugin](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compiler.html) which ships as part of Kotlin itself.
Each module in which you had previously applied the `com.jakewharton.mosaic` plugin should be changed to apply `org.jetbrains.kotlin.plugin.compose` instead.
The Mosaic runtime will no longer be added as a result of the plugin change, and so any module which references Mosaic APIs should apply the `com.jakewharton.mosaic:mosaic-runtime` dependency.

For posterity, the Kotlin version compatibility table and compiler version customization for our old Mosaic Gradle plugin will be archived here:

<details>
<summary>Mosaic 0.12.0 Gradle plugin Kotlin compatibility table</summary>
<p>

Since Kotlin compiler plugins are an unstable API, certain versions of Mosaic only work with
certain versions of Kotlin.

| Kotlin | Mosaic        |
|--------|---------------|
| 1.9.24 | 0.12.0        |
| 1.9.22 | 0.11.0        |
| 1.9.20 | 0.10.0        |
| 1.9.10 | 0.9.1         |
| 1.9.0  | 0.8.0 - 0.9.0 |
| 1.8.22 | 0.7.1         |
| 1.8.21 | 0.7.0         |
| 1.8.20 | 0.6.0         |
| 1.8.10 | 0.5.0         |
| 1.8.0  | 0.3.0 - 0.4.0 |
| 1.7.10 | 0.2.0         |
| 1.5.10 | 0.1.0         |

</p>
</details>

<details>
<summary>Mosaic 0.12.0 Gradle plugin Compose compiler customization instructions</summary>
<p>

Each version of Mosaic ships with a specific JetBrains Compose compiler version which works with
a single version of Kotlin (see [version table](#usage) above). Newer versions of the Compose
compiler or alternate Compose compilers can be specified using the Gradle extension.

To use a new version of the JetBrains Compose compiler version:
```kotlin
mosaic {
  kotlinCompilerPlugin.set("1.4.8")
}
```

To use an alternate Compose compiler dependency:
```kotlin
mosaic {
  kotlinCompilerPlugin.set("com.example:custom-compose-compiler:1.0.0")
}
```

</p>
</details>


## [0.12.0] - 2024-05-28
[0.12.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.12.0

New:
- Add `linuxArm64` target.
- Add a top-like monitor sample.
- Support true color palette.

Changed:
- Disable klib signature clash checks for JS compilations. These occasionally occur as a result of Compose compiler behavior, and are safe to disable (the first-party JetBrains Compose Gradle plugin also disables them).
- Remove `Terminal$Size` and use `IntSize` instead in `Terminal#size` for optimization purposes.
- Remove `Color.Bright*` constants. Use `Color` function to create the desired color.
- Replace nullable `Color` and `TextStyle` with `Color.Unspecified` and `TextStyle.Unspecified` respectively. Also make `TextStyle` an inline class.

Fixed:
- Use CRLF line endings to fix rendering when a terminal is in raw mode.


## [0.11.0] - 2024-02-27
[0.11.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.11.0

New:
- Support Kotlin 1.9.22 via JetBrains Compose compiler 1.5.10.
- `Filler` composable is like a `Spacer` but fills its area with a character instead of a space.
- `Box` without content provides the ability to render using drawing modifiers without needing an empty chidlren lambda.
- `Modifier.aspectRatio` attempts to constrain a composable to an aspect ratio in either the vertical or horizontal direction.
- `Modifier.offset` offsets the composable in its parent by the given coordinates.
- `Modifier.fillMaxWidth`, `Modifier.fillMaxHeight`, `Modifier.fillMaxSize`, `Modifier.wrapContentWidth`, `Modifier.wrapContentHeight`, `Modifier.wrapContentSize`, and `Modifier.defaultMinSize` help size composable measurement in relation to their parent.
- `Modifier.weight` allows sizing a composable proportionally to others within the same parent.
- `Row` and `Column` each feature an arrangement parameter which controls the placement of children on the main axis of the container.

Changed:
- `Modifier` parameter is now universally called `modifier` in the API.
- Disable decoy generation for JS target to make compatible with JetBrains Compose 1.6. This is an ABI-breaking change, so all Compose-based libraries targeting JS will also need to have been recompiled.

Fix:
- Ensure ANSI control sequences are written properly to Windows terminals.
- Robot sample now correctly moves on Windows.

This version works with Kotlin 1.9.22 by default.


## [0.10.0] - 2023-11-13
[0.10.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.10.0

New:
- Support Kotlin 1.9.20 via JetBrains Compose compiler 1.5.3.

- `@MosaicComposable` annotation restricts composable function usage to those meant for Mosaic
  (e.g., our `Text`) or general-purpose (e.g., Compose's `remember`). In most cases the Compose
  compiler can infer this automatically, but it's available for explicit use if needed.

- `LocalTerminal` composition local provides the size of the terminal if it can be detected.
  If the size changes, your function will automatically be recomposed with the new values.

    ```kotlin
    val size = LocalTerminal.current.size
    Text("Terminal(w=${size.width}, h=${size.height})")
    ```

- `Row`, `Column`, and `Box` now support horizontal and vertical alignment of their children.

    ```kotlin
    Column {
      Text("This is very long")
      Text(
        "On the right",
        modifier = Modifier.align(End),
      )
    }
    ```

- Add `AnnotatedString` with `SpanStyle` for string customization. Instead of writing a series of
  `Text` functions in a `Row`, emit a single `Text` with formatting changes within the string.

    ```kotlin
    Text(buildAnnotatedString {
      append("Plain! ")
      withStyle(SpanStyle(color = BrightGreen)) {
        append("Green! ")
      }
      withStyle(SpanStyle(color = BrightBlue)) {
        append("Blue!")
      }
    })
    ```

- `Spacer` node for occupying space within layouts.

- Constraints and intrinsics are now available in the layout system.

This version works with Kotlin 1.9.20 by default.


## [0.9.1] - 2023-09-14
[0.9.1]: https://github.com/JakeWharton/mosaic/releases/tag/0.9.1

New:
- Support Kotlin 1.9.10 via JetBrains Compose compiler 1.5.2.

This version works with Kotlin 1.9.10 by default.


## [0.9.0] - 2023-08-09
[0.9.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.9.0

New:
- Support for specifying custom Compose compiler versions. This will allow you to use the latest
  version of Molecule with newer versions of Kotlin than it explicitly supports.

  See [the README](https://github.com/JakeWharton/mosaic/#custom-compose-compiler) for more information.


## [0.8.0] - 2023-07-20
[0.8.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.8.0

New:
- Support Kotlin 1.9.0 via JetBrains Compose compiler 1.5.0.


## [0.7.1] - 2023-06-30
[0.7.1]: https://github.com/JakeWharton/mosaic/releases/tag/0.7.1

New:
- Support Kotlin 1.8.22 via JetBrains Compose compiler 1.4.8.


## [0.7.0] - 2023-06-26
[0.7.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.7.0

New:
- Support Kotlin 1.8.21 via JetBrains Compose compiler 1.4.7.
- Add support for modifiers on layouts and built-in components.
  There are two types which are built-in: `DrawModifier` and `LayoutModifier`.
  The built-in functions are `drawBehind`, `background`, `layout`, and `padding`.
- Add Box component.

Changed:
- Frames are now emitted slightly differently relying on Compose for signaling when a change has happened. Please report any problems.


## [0.6.0] - 2023-04-17
[0.6.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.6.0

New:
- Support Kotlin 1.8.20 via JetBrains Compose compiler 1.4.5.

Changed:
- Upgrade JetBrains Compose runtime to 1.4.0.
- Reduce string copies and string allocation required to perform a single frame render.
- Only split text strings on newline when its value changes by caching layout information across recomposition.
- Canvas is no longer clipped for each node. If you notice any overlapping drawing, please report a bug.

Breaking:
- Composables were moved into `ui` subpackage.
- `Layout` and related interfaces were moved into `layout` subpackage.
- `Static` content is now based on `SnapshotStateList` instead of `Flow`.


## [0.5.0] - 2023-03-09
[0.5.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.5.0

 - Support Kotlin 1.8.10 via JetBrains Compose compiler 1.4.2.
 - New: `renderMosaic` function returns a single string of the composition for tools that require only static output.
 - New: Expose a custom `Layout` composable similar to Compose UI. This is just the beginning of a larger change to expose more powerful primitives.
 - Implicit root node is no longer a `Row`. Multiple children at the root will now draw on top of each other. Choose a `Row` or `Column` as the root composable yourself.
 - Each `Static` content is no longer wrapped in a `Row`. Multiple children in a single `Static` composable will draw on top of each other. Choose a `Row` or `Column` if you have multiple items. Multiple `Static` composables will still render in `Column`-like behavior


## [0.4.0] - 2023-02-19
[0.4.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.4.0

 - Mosaic is now multiplatform!

   The following targets are now supported in addition to the JVM:
     - Linux (X64)
     - MacOS (ARM & X64)
     - Windows (X64)
     - JS (experimental)

   Additionally, the JetBrains Compose compiler is now used instead of AndroidX which
   should offer better support for native and JS targets.

 - `runMosaic` is now a suspending function which will return when the composition ends.
   For the previous behavior, a `runMosaicBlocking` function is provided (JVM + native).


## [0.3.0] - 2023-01-17
[0.3.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.3.0

 - Support Kotlin 1.8.0 via Compose compiler 1.4.0.
 - New: `Static` composable for rendering permanent output.
 - Fix: Correct line calculation to prevent output from drifting downward over time when its height changes.


## [0.2.0] - 2022-08-12
[0.2.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.2.0

 - Support Kotlin 1.7.10 via Compose compiler 1.3.0.
 - Migrate from custom build of Compose compiler and Compose runtime to Google's Compose compiler and JetBrains' multiplatform Compose runtime. Note that this will require you have the Google Maven repositories in your Gradle repositories (`google()`).


## [0.1.0] - 2021-06-25
[0.1.0]: https://github.com/JakeWharton/mosaic/releases/tag/0.1.0

Initial release!
