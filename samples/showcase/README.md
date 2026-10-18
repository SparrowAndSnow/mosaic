# showcase

A self-contained demo of the refactored capability set, written on top of the public API only:

- **Unicode** — cluster-level rendering: CJK, full-width punctuation, emoji/ZWJ families, flags,
  combining marks, variation selectors, ideographic spaces, box-drawing glyphs.
- **Text** — `textAlign`, cluster-safe soft wrapping, `maxLines` Clip/Ellipsis truncation (incl. an interactive `scroll` window).
- **Layout** — `weight` space splitting, `Filler`, `Spacer`, `BoxWithConstraints` ratio sizing.
- **Input** — mouse press/release/motion/drag, bracketed paste, OSC 52 clipboard read/write,
  per-key echo (repeat / arrows / F-keys / IME).
- **Animation** — time-driven effects running on the paced frame loop.

## Running

JVM:

```bash
./gradlew :samples:showcase:runJvm
```

Native (release kexe):

```bash
./gradlew :samples:showcase:linkReleaseExecutableLinuxX64
./samples/showcase/build/bin/linuxX64/releaseExecutable/showcase.kexe
```

Controls: `←` / `→` / `Tab` / `1`-`5` switch pages; clicking a bottom tab also switches and
highlights it on hover; Ctrl-C exits. On the Input page, `w` writes the clipboard and `r` reads
it (only where the terminal allows OSC 52).
