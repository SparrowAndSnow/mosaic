package com.jakewharton.mosaic

import androidx.collection.mutableScatterSetOf
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jakewharton.mosaic.NonInteractivePolicy.Exit
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.MosaicNode
import com.jakewharton.mosaic.layout.toRuntimeMouseEvent
import com.jakewharton.mosaic.terminal.BracketedPasteEvent
import com.jakewharton.mosaic.terminal.ClipboardEvent
import com.jakewharton.mosaic.terminal.KeyboardEvent
import com.jakewharton.mosaic.terminal.Terminal
import com.jakewharton.mosaic.ui.BoxMeasurePolicy
import com.jakewharton.mosaic.ui.platform.LocalClipboard
import com.jakewharton.mosaic.ui.platform.TermClipboard
import de.cketti.codepoints.appendCodePoint
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

public fun runMosaicMain(
	mouse: Boolean = false,
	paste: Boolean = false,
	content: @Composable () -> Unit,
) {
	runMosaicBlocking(mouse = mouse, paste = paste, content = content)
}

public fun runMosaicBlocking(
	onNonInteractive: NonInteractivePolicy = Exit,
	mouse: Boolean = false,
	paste: Boolean = false,
	content: @Composable () -> Unit,
): Boolean {
	return runBlocking {
		runMosaic(onNonInteractive, mouse, paste, content)
	}
}

public suspend fun runMosaic(
	onNonInteractive: NonInteractivePolicy = Exit,
	mouse: Boolean = false,
	paste: Boolean = false,
	content: @Composable () -> Unit,
): Boolean = withTerminal(onNonInteractive, mouse, paste) { terminal ->
	val rendering = if (env("MOSAIC_DEBUG_RENDERING") == "true") {
		DebugRendering(terminal.capabilities)
	} else {
		AnsiIncrementalRendering(terminal.capabilities)
	}

	runMosaicComposition(terminal, rendering, content)
}

internal suspend fun runMosaicComposition(
	terminal: Terminal,
	rendering: Rendering,
	content: @Composable () -> Unit,
) {
	val clock = BroadcastFrameClock()
	val frameWriter = FrameWriter()
	val mosaicComposition = MosaicComposition(
		coroutineContext = coroutineContext + clock,
		onDraw = { rootNode ->
			frameWriter.write(rendering.render(rootNode))
		},
		terminal = terminal,
	)

	mosaicComposition.setContent(content)

	mosaicComposition.scope.launch {
		while (true) {
			clock.sendFrame(nanoTime())

			// "1000 FPS should be enough for anybody"
			// We need to yield in order for other coroutines on this dispatcher to run, otherwise
			// this is effectively a spin loop. We do a delay instead of a yield since dispatchers
			// are not required to support yield, but reasonable delay support is almost a guarantee.
			delay(1)
		}
	}

	mosaicComposition.awaitComplete()
}

public interface Mosaic {
	public fun setContent(content: @Composable () -> Unit)

	public fun draw(): TextCanvas
	public fun static(): String?
	public fun dumpNodes(): String

	public suspend fun awaitComplete()
	public fun cancel()
}

public fun Mosaic(
	coroutineContext: CoroutineContext,
	onDraw: (Mosaic) -> Unit,
	terminal: Terminal,
): Mosaic {
	return MosaicComposition(coroutineContext, onDraw, terminal)
}

internal class MosaicComposition(
	coroutineContext: CoroutineContext,
	private val onDraw: (Mosaic) -> Unit,
	private val terminal: Terminal,
) : Mosaic,
	LifecycleOwner {
	private val externalClock = checkNotNull(coroutineContext[MonotonicFrameClock]) {
		"Mosaic requires an external MonotonicFrameClock in its coroutine context"
	}
	private val internalClock = BroadcastFrameClock()

	private val job = Job(coroutineContext[Job])
	private val composeContext = coroutineContext + job + internalClock
	val scope = CoroutineScope(composeContext)

	private val applier = MosaicNodeApplier { needLayout = true }
	val rootNode = applier.root
	private val recomposer = Recomposer(composeContext)
	private val composition = Composition(applier, recomposer)

	private val staticLogs = Channel<Any>(UNLIMITED)
	private val staticLogger = StaticLogger(staticLogs) { needDraw = true }
	private val clipboard = TermClipboard(terminal)

	override val lifecycle = LifecycleRegistry.createUnsafe(this)

	private var terminalState = mutableStateOf(
		TerminalState(
			terminal.state.focused.value,
			terminal.state.theme.value,
			terminal.state.size.value,
		),
	).also { state ->
		scope.launch(Unconfined, start = UNDISPATCHED) {
			terminal.state.focused.collect { focused ->
				state.value = state.value.copy(focused = focused)

				lifecycle.handleLifecycleEvent(
					if (focused) Lifecycle.Event.ON_RESUME else Lifecycle.Event.ON_PAUSE,
				)
			}
		}
		scope.launch(Unconfined, start = UNDISPATCHED) {
			terminal.state.theme.collect { theme ->
				state.value = state.value.copy(theme = theme)
			}
		}
		scope.launch(Unconfined, start = UNDISPATCHED) {
			terminal.state.size.collect { size ->
				state.value = state.value.copy(size = size)
			}
		}
	}

	private val applyObserverHandle: ObserverHandle

	private val readStatesOnLayout = mutableScatterSetOf<Any>()
	private val readStatesOnLayoutObserver: (Any) -> Unit = readStatesOnLayout::add

	private val readStatesOnDraw = mutableScatterSetOf<Any>()
	private val readStatesOnDrawObserver: (Any) -> Unit = readStatesOnDraw::add

	@Volatile
	private var needLayout = false

	@Volatile
	private var needDraw = false

	init {
		GlobalSnapshotManager().ensureStarted(scope)
		startRecomposer()
		startFrameListener()
		applyObserverHandle = registerSnapshotApplyObserver()
	}

	private fun performLayout() {
		needLayout = false

		// Per-node dependencies are tracked in MosaicNode.measure for the fine-grained layout
		// cache, but the whole-tree observation below additionally covers state reads that happen
		// during placement (e.g. a changeable offset), which must still trigger a layout pass.
		readStatesOnLayout.clear()
		Snapshot.observe(readObserver = readStatesOnLayoutObserver) {
			rootNode.measureAndPlace()
		}

		performDraw()
	}

	private fun performDraw() {
		needDraw = false
		onDraw(this)
	}

	override fun draw(): TextCanvas {
		readStatesOnDraw.clear()
		return Snapshot.observe(readObserver = readStatesOnDrawObserver) {
			rootNode.draw()
		}
	}

	override fun static(): String? {
		var static = staticLogs.tryReceive().getOrNull()
		if (static == null) {
			return null
		}
		return buildString {
			do {
				when (static) {
					is String -> {
						append(static)
						append("\r\n")
					}

					is TextCanvas -> {
						for (row in 0 until static.height) {
							static.appendRowTo(
								appendable = this,
								row = row,
								ansiLevel = terminal.capabilities.ansiLevel,
								supportsKittyUnderlines = terminal.capabilities.kittyUnderline,
							)
							append("\r\n")
						}
					}
				}

				static = staticLogs.tryReceive().getOrNull()
			} while (static != null)

			// Remove trailing "\r\n".
			setLength(length - 2)
		}
	}

	override fun dumpNodes(): String {
		return rootNode.toString()
	}

	private fun registerSnapshotApplyObserver(): ObserverHandle {
		return Snapshot.registerApplyObserver { changedStates, _ ->
			var layoutInvalidated = false
			if (changedStates.isNotEmpty()) {
				layoutInvalidated = invalidateLayoutCaches(changedStates)
			}
			var layoutReadChanged = false
			for (state in changedStates) {
				if (state in readStatesOnLayout) {
					layoutReadChanged = true
					break
				}
			}
			if (layoutInvalidated || layoutReadChanged) {
				needLayout = true
			} else if (!needLayout) {
				var setDraw = needDraw
				for (state in changedStates) {
					if (setDraw) {
						break
					}
					if (state in readStatesOnDraw) {
						setDraw = true
						needDraw = true
					}
				}
			}
		}
	}

	/**
	 * Walks the tree and invalidates the layout cache of every node whose measured dependencies
	 * include any of the changed states, propagating the invalidation to all ancestors. Returns
	 * whether any node was invalidated (and therefore whether a layout pass is required).
	 */
	private fun invalidateLayoutCaches(changedStates: Set<Any>): Boolean {
		var invalidated = false
		fun visit(node: MosaicNode) {
			for (state in changedStates) {
				if (state in node.measureDependencies) {
					node.invalidateLayoutVersion()
					invalidated = true
					break
				}
			}
			for (child in node.children) {
				visit(child)
			}
		}
		visit(rootNode)
		return invalidated
	}

	private fun startRecomposer() {
		scope.launch(start = UNDISPATCHED) {
			recomposer.runRecomposeAndApplyChanges()
		}
	}

	private fun startFrameListener() {
		scope.launch(start = UNDISPATCHED) {
			val ctrlC = KeyEvent("c", ctrl = true)
			var pasteText: StringBuilder? = null

			do {
				externalClock.withFrameNanos { nanos ->
					// Drain any pending events before triggering the frame.
					// Consecutive mouse motion/drag events are coalesced to their latest position:
					// only the final coordinate matters to handlers, and all-motion tracking can
					// otherwise flood the per-frame drain.
					var pendingMotion: com.jakewharton.mosaic.terminal.MouseEvent? = null
					fun flushMotion() {
						val motion = pendingMotion ?: return
						pendingMotion = null
						rootNode.sendMouseEvent(motion.toRuntimeMouseEvent())
					}
					while (true) {
						val event = terminal.events.tryReceive().getOrNull() ?: break
						val terminalMouse = event as? com.jakewharton.mosaic.terminal.MouseEvent
						if (terminalMouse != null &&
							(terminalMouse.type == com.jakewharton.mosaic.terminal.MouseEvent.Type.Motion ||
								terminalMouse.type == com.jakewharton.mosaic.terminal.MouseEvent.Type.Drag)
						) {
							pendingMotion = terminalMouse
							continue
						}
						// A discrete event follows: deliver the final buffered position first.
						flushMotion()
						when (event) {
							is KeyboardEvent -> {
								// While a bracketed paste is active, the terminal reports the pasted
								// content as individual key events; accumulate them instead of
								// dispatching them as key events.
								if (pasteText != null) {
									pasteText!!.append(event.text ?: run {
										buildString { appendCodePoint(event.codepoint) }
									})
									continue
								}
								val keyEvent = event.toKeyEventOrNull() ?: continue
								val keyHandled = rootNode.sendKeyEvent(keyEvent)
								if (!keyHandled && keyEvent == ctrlC) {
									job.cancel()
									return@withFrameNanos
								}
							}
							is com.jakewharton.mosaic.terminal.MouseEvent -> {
								rootNode.sendMouseEvent(event.toRuntimeMouseEvent())
							}
							is BracketedPasteEvent -> {
								if (event.start) {
									pasteText = StringBuilder()
								} else {
									val text = pasteText?.toString() ?: ""
									pasteText = null
									rootNode.sendPasteEvent(text)
								}
							}
							is ClipboardEvent -> {
								clipboard.onEvent(event)
							}
							else -> {}
						}
					}
					flushMotion()

					internalClock.sendFrame(nanos)

					if (needLayout) {
						performLayout()
					} else if (needDraw) {
						performDraw()
					}
				}
			} while (job.isActive)
		}
	}

	override fun setContent(content: @Composable () -> Unit) {
		composition.setContent {
			CompositionLocalProvider(
				LocalTerminalState provides terminalState.value,
				LocalStaticLogger provides staticLogger,
				LocalLifecycleOwner provides this,
				LocalClipboard provides clipboard,
				content = content,
			)
		}
		performLayout()
	}

	override suspend fun awaitComplete() {
		try {
			val effectJob = checkNotNull(recomposer.effectCoroutineContext[Job]) {
				"No Job in effectCoroutineContext of recomposer"
			}
			effectJob.children.forEach { it.join() }
			recomposer.awaitIdle()

			applyObserverHandle.dispose()
			if (needLayout || needDraw) {
				// TODO Re-evaluate this for correctness.
				scope.launch { internalClock.withFrameNanos { } }.join()
			}

			recomposer.close()
			recomposer.join()
		} finally {
			applyObserverHandle.dispose() // if canceled before dispose in the try block
			job.cancel()
		}
	}

	override fun cancel() {
		applyObserverHandle.dispose()
		recomposer.cancel()
		job.cancel()
	}
}

internal class MosaicNodeApplier(
	private val onChanges: () -> Unit = {},
) : AbstractApplier<MosaicNode>(
	root = MosaicNode(
		measurePolicy = BoxMeasurePolicy(),
		debugPolicy = { children.joinToString(separator = "\n") },
		isStatic = false,
	),
) {
	override fun onBeginChanges() {
		super.onBeginChanges()
		// We invoke this here rather than in the end change callback to try and ensure
		// no one relies on it to signal the end of changes.
		onChanges.invoke()
	}

	override fun insertTopDown(index: Int, instance: MosaicNode) {
		// Ignored, we insert bottom-up.
	}

	override fun insertBottomUp(index: Int, instance: MosaicNode) {
		current.children.add(index, instance)
		instance.parent = current
		current.invalidateLayoutVersion()
	}

	override fun remove(index: Int, count: Int) {
		current.children.remove(index, count)
		current.invalidateLayoutVersion()
	}

	override fun move(from: Int, to: Int, count: Int) {
		current.children.move(from, to, count)
		current.invalidateLayoutVersion()
	}

	override fun onClear() {
	}
}

@OptIn(ExperimentalAtomicApi::class)
internal class GlobalSnapshotManager {
	private val started = AtomicBoolean(false)
	private val sent = AtomicBoolean(false)

	fun ensureStarted(scope: CoroutineScope) {
		if (started.compareAndSet(expectedValue = false, newValue = true)) {
			val channel = Channel<Unit>(1)
			scope.launch {
				channel.consumeEach {
					sent.store(false)
					Snapshot.sendApplyNotifications()
				}
			}
			Snapshot.registerGlobalWriteObserver {
				if (sent.compareAndSet(expectedValue = false, newValue = true)) {
					channel.trySend(Unit)
				}
			}
		}
	}
}
