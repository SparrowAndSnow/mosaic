# showcase

A self-contained demo of the refactored capability set, written on top of the public API only.
The ten pages are, in order:

- **Unicode** — cluster-level rendering: CJK, full-width punctuation, emoji/ZWJ families, flags,
  combining marks, variation selectors, ideographic spaces, box-drawing glyphs.
- **Overlay** — non-modal `Popup`, modal `Dialog`, backdrops, input barriers, and focus.
- **Text** — `textAlign`, cluster-safe soft wrapping, `maxLines` Clip/Ellipsis truncation (incl. an interactive `scroll` window).
- **Layout** — `weight` space splitting, `Filler`, `Spacer`, `BoxWithConstraints` ratio sizing.
- **Input** — mouse press/release/motion/drag, bracketed paste, OSC 52 clipboard read/write,
  per-key echo (repeat / arrows / F-keys / IME).
- **Field** — focused, read-only, and disabled `BasicTextField` states.
- **Animation** — time-driven effects running on the paced frame loop.
- **Scroll** — vertical and horizontal scroll containers with mouse-wheel support.
- **Lazy** — a 500-row `LazyColumn` that only composes the visible window.
- **Border** — border and divider primitives.

## Running

Build the JVM distribution and run its binary from a real terminal:

```bash
./gradlew :samples:showcase:installDist
./samples/showcase/build/install/showcase/bin/showcase
```

Native (release kexe):

```bash
./gradlew :samples:showcase:linkReleaseExecutableLinuxX64
./samples/showcase/build/bin/linuxX64/releaseExecutable/showcase.kexe
```

Controls: `←` / `→` / `Tab` switch pages; `1`–`9` jump directly to the first nine pages;
clicking a bottom tab selects any page, including Border, and highlights it on hover; Ctrl-C exits.
On the Input page, `w` writes the clipboard and `r` reads it (only where the terminal allows
OSC 52).
