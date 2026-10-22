package com.jakewharton.mosaic.layout

import androidx.collection.mutableScatterSetOf
import androidx.compose.runtime.snapshots.Snapshot
import com.jakewharton.mosaic.TextCanvas
import com.jakewharton.mosaic.TextSurface
import com.jakewharton.mosaic.layout.Placeable.PlacementScope
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.unit.Constraints

internal fun interface DebugPolicy {
	fun MosaicNode.renderDebug(): String
}

internal abstract class MosaicNodeLayer :
	Placeable(),
	Measurable,
	PlacementScope,
	MeasureScope {
	abstract val next: MosaicNodeLayer?

	private var measureResult: MeasureResult = NotMeasured

	final override var parentData: Any? = null

	final override val width get() = measureResult.width
	final override val height get() = measureResult.height

	override fun measure(constraints: Constraints): Placeable = apply {
		measureResult = doMeasure(constraints)
	}

	protected open fun doMeasure(constraints: Constraints): MeasureResult {
		val placeable = next!!.measure(constraints)
		return object : MeasureResult {
			override val width: Int get() = placeable.width
			override val height: Int get() = placeable.height

			override fun placeChildren() {
				placeable.place(0, 0)
			}
		}
	}

	final override var x = 0
		private set
	final override var y = 0
		private set

	final override fun placeAt(x: Int, y: Int) {
		this.x = x
		this.y = y
		measureResult.placeChildren()
	}

	open fun drawTo(canvas: TextSurface) {
		next?.drawTo(canvas)
	}

	open fun sendKeyEvent(keyEvent: KeyEvent): Boolean {
		return next?.sendKeyEvent(keyEvent) ?: false
	}

	open fun sendMouseEvent(event: MouseEvent): Boolean {
		return next?.sendMouseEvent(event) ?: false
	}

	open fun sendPasteEvent(text: String): Boolean {
		return next?.sendPasteEvent(text) ?: false
	}

	override fun minIntrinsicWidth(height: Int): Int {
		return next?.minIntrinsicWidth(height) ?: 0
	}

	override fun maxIntrinsicWidth(height: Int): Int {
		return next?.maxIntrinsicWidth(height) ?: 0
	}

	override fun minIntrinsicHeight(width: Int): Int {
		return next?.minIntrinsicHeight(width) ?: 0
	}

	override fun maxIntrinsicHeight(width: Int): Int {
		return next?.maxIntrinsicHeight(width) ?: 0
	}
}

internal object NotMeasured : MeasureResult {
	override val width get() = 0
	override val height get() = 0
	override fun placeChildren() = throw UnsupportedOperationException("Not measured")
}


private const val MIN_WIDTH = 0
private const val MAX_WIDTH = 1
private const val MIN_HEIGHT = 2
private const val MAX_HEIGHT = 3

internal class MosaicNode(
	measurePolicy: MeasurePolicy,
	var debugPolicy: DebugPolicy,
	val isStatic: Boolean,
) : Measurable {
	val children = ArrayList<MosaicNode>()

	internal var measurePolicy: MeasurePolicy = measurePolicy
		set(value) {
			field = value
			invalidateLayoutVersion()
		}

	private val bottomLayer: MosaicNodeLayer = BottomLayer(this)
	var topLayer: MosaicNodeLayer = bottomLayer
		private set

	/**
	 * The enclosing node this node is currently a child of, or `null` for the composition root.
	 * Maintained by the applier, and used to propagate layout version invalidation upward.
	 */
	internal var parent: MosaicNode? = null

	// Layout cache state. `MosaicNode.measure` memoizes the most recent measurement, keyed on the
	// incoming constraints plus a monotonically increasing layout version. The version is bumped
	// (and propagated to all ancestors) whenever this node's modifier chain, measure policy, or
	// children change structurally, or when a snapshot state read by this subtree during
	// measurement changes. A memo hit lets the whole subtree skip re-measurement entirely.
	private var layoutVersion = 0L
	private var cachedConstraints: Constraints? = null
	private var cachedResult: Placeable? = null
	private var cachedVersion = -1L
	private var cachedCanvas: TextSurface? = null

	/** Snapshot states this subtree read during its most recent measurement. */
	internal val measureDependencies = mutableScatterSetOf<Any>()

	/** Pointer-input states (from hoverable/clickable elements) active on this node. */
	internal val pointerInputs = ArrayList<PointerInputState>()

	/** Cross-node pointer session, created lazily on the composition root. */
	private var pointerState: PointerState? = null

	/** Overlay host registered on the composition root, if any. */
	internal var overlayHost: OverlayHostState? = null

	/** Focus target active on this node (null when no focus-related modifier is applied). */
	internal var focusTarget: FocusTarget? = null

	/** Focus requesters attached to this node, so they can be detached when the modifier goes away. */
	internal val focusRequesters = ArrayList<FocusRequester>()

	/** Cross-node focus session, created lazily on the composition root. */
	internal var focusManager: FocusManager? = null
		private set

	/** Number of times an actual (non-cached) measurement ran on this node. */
	internal var measureCount: Long = 0
		private set
	/** Number of times a cached measurement result was reused on this node. */
	internal var cacheHitCount: Long = 0
		private set
	/** Number of times an intrinsic query actually recomputed on this node. */
	internal var intrinsicRecomputeCount: Long = 0
		private set
	/** Number of times a cached intrinsic result was reused on this node. */
	internal var intrinsicCacheHitCount: Long = 0
		private set

	// Intrinsic memo: a single (kind, height/width) slot, mirroring the layout memo above.
	// Intrinsic queries walk this node's entire subtree, so repeated queries with the same kind and
	// parameter at the same layout version are answered from this slot without re-walking. Reads are
	// recorded through the same snapshot observer as [measure], so state changes still invalidate
	// the memo through the regular layout invalidation path. Dropped whenever this node actually
	// re-measures: a measurement may observe different state than a preceding intrinsic query.
	private var cachedIntrinsicVersion = -1L
	private var cachedIntrinsicKind = -1
	private var cachedIntrinsicParam = Int.MIN_VALUE
	private var cachedIntrinsicValue = 0

	private inline fun cachedIntrinsic(kind: Int, param: Int, crossinline compute: () -> Int): Int {
		if (cachedIntrinsicVersion == layoutVersion &&
			cachedIntrinsicKind == kind &&
			cachedIntrinsicParam == param
		) {
			intrinsicCacheHitCount++
			return cachedIntrinsicValue
		}
		intrinsicRecomputeCount++
		val value = Snapshot.observe(readObserver = measureDependencies::add) { compute() }
		cachedIntrinsicVersion = layoutVersion
		cachedIntrinsicKind = kind
		cachedIntrinsicParam = param
		cachedIntrinsicValue = value
		return value
	}

	/** Invalidates the cached layout of this node and all of its ancestors. */
	internal fun invalidateLayoutVersion() {
		var node: MosaicNode? = this
		while (node != null) {
			node.layoutVersion++
			node = node.parent
		}
	}

	override var parentData: Any? = null
		private set

	var testTag: String? = null
		private set

	fun setModifier(modifier: Modifier) {
		invalidateLayoutVersion()
		pointerInputs.clear()
		focusRequesters.forEach { it.detach() }
		focusRequesters.clear()
		val previousTarget = focusTarget
		previousTarget?.observers?.clear()
		var sawFocusElement = false
		focusTarget = null
		topLayer = modifier.foldOut(bottomLayer) { element, nextLayer ->
			var nextLayer = nextLayer
			// The Modifier class can inherit from several key Modifier types
			// with different processing logic.
			if (element is OverlayHostAttach) {
				element.attach(this)
			}
			if (element is LayoutModifier) {
				nextLayer = LayoutLayer(element, nextLayer)
			}
			if (element is DrawModifier) {
				nextLayer = DrawLayer(element, nextLayer)
			}
			if (element is KeyModifier) {
				nextLayer = KeyLayer(element, nextLayer)
			}
			if (element is MouseModifier) {
				nextLayer = MouseLayer(element, nextLayer)
			}
			if (element is PointerInputModifier) {
				pointerInputs += element.state
			}
			if (element is FocusableModifier) {
				sawFocusElement = true
				val target = focusTarget ?: previousTarget ?: FocusTarget().also { focusTarget = it }
				focusTarget = target
				target.enabled = element.enabled
				target.traversable = element.enabled
			}
			if (element is FocusObserverModifier) {
				sawFocusElement = true
				val target = focusTarget ?: previousTarget ?: FocusTarget().also { focusTarget = it }
				focusTarget = target
				target.observers += element.onFocusChanged
			}
			if (element is FocusRequesterModifier) {
				element.requester.attach(this)
				focusRequesters += element.requester
			}
			if (element is PasteModifier) {
				nextLayer = PasteLayer(element, nextLayer)
			}
			if (element is ParentDataModifier) {
				parentData = element.modifyParentData(parentData)
			}
			if (element is TestTagModifier) {
				testTag = element.tag
			}
			nextLayer
		}
		if (!sawFocusElement) {
			previousTarget?.finalize()
			focusTarget = null
		}
	}

	override fun measure(constraints: Constraints): Placeable {
		val cached = cachedResult
		if (cached != null && cachedConstraints == constraints && cachedVersion == layoutVersion) {
			cacheHitCount++
			return cached
		}
		measureCount++
		cachedIntrinsicVersion = -1L
		measureDependencies.clear()
		val result = Snapshot.observe(readObserver = measureDependencies::add) {
			topLayer.measure(constraints)
		}
		cachedResult = result
		cachedConstraints = constraints
		cachedVersion = layoutVersion
		return result
	}

	val width: Int get() = topLayer.width
	val height: Int get() = topLayer.height
	val x: Int get() = topLayer.x
	val y: Int get() = topLayer.y

	fun measureAndPlace() {
		val placeable = measure(Constraints())
		topLayer.run { placeable.place(0, 0) }
	}

	/**
	 * Draw this node to a [TextSurface].
	 * A call to [measureAndPlace] must precede calls to this function.
	 */
	fun draw(): TextCanvas {
		val w = width
		val h = height
		var surface = cachedCanvas
		if (surface == null || surface.width != w || surface.height != h) {
			surface = TextSurface(w, h)
			cachedCanvas = surface
		}
		surface.beginDraw()
		topLayer.drawTo(surface)
		surface.resetCellsNotWritten()
		return surface
	}

	fun sendKeyEvent(keyEvent: KeyEvent): Boolean {
		if (parent == null) {
			overlayHost?.syncLayers()
			// On the composition root, the focus manager first handles Tab / Shift+Tab traversal
			// (consuming them when a focusable exists), then the raw event is dispatched normally.
			if (ensureFocusManager().preKeyEvent(this, keyEvent)) {
				return true
			}
			val host = overlayHost
			if (host != null && host.layers.isNotEmpty()) {
				val layers = host.layers
				for (index in layers.indices.reversed()) {
					val layer = layers[index]
					if (layer.sendKeyEvent(keyEvent)) {
						return true
					}
					if (host.isModal(layer)) {
						return false
					}
				}
				return host.baseNode?.sendKeyEvent(keyEvent) ?: false
			}
		}
		return topLayer.sendKeyEvent(keyEvent)
	}

	internal fun ensureFocusManager(): FocusManager {
		val existing = focusManager
		if (existing != null) return existing
		return FocusManager().also { focusManager = it }
	}

	internal fun ensurePointerState(): PointerState {
		val existing = pointerState
		if (existing != null) return existing
		return PointerState().also { pointerState = it }
	}

	/** Releases any captured pointer press and exits all hover states, used when layers change. */
	internal fun resetPointerSessions() {
		pointerState?.cancelPress()
		pointerState?.clearHover()
	}

	/**
	 * Dispatches a [MouseEvent] to the top-most layout under the pointer and, if it is not
	 * consumed, bubbles up to its ancestors. Coordinates are absolute canvas (screen cell)
	 * coordinates, matching a node's [x]/[y] after placement. On the composition root this first
	 * runs the [PointerState] pre-pass so hover enter/exit and press capture are reconciled before
	 * the raw event is delivered.
	 */
	fun sendMouseEvent(event: MouseEvent): Boolean {
		if (parent == null) {
			overlayHost?.syncLayers()
			ensureFocusManager()
			val host = overlayHost
			if (host != null && host.layers.isNotEmpty()) {
				val roots = host.rootsForMouse()
				ensurePointerState().preDispatch(roots, event)
				for (root in roots) {
					if (root.dispatchMouseEvent(event)) {
						return true
					}
				}
				return false
			}
			ensurePointerState().preDispatch(this, event)
		}
		return dispatchMouseEvent(event)
	}

	internal fun dispatchMouseEvent(event: MouseEvent): Boolean {
		if (event.x !in x until x + width || event.y !in y until y + height) {
			return false
		}
		// Children draw on top of this node, so give them (in z-order, top-most first) the first
		// chance to handle the event before this node's own modifiers.
		for (index in children.indices.reversed()) {
			if (children[index].dispatchMouseEvent(event)) {
				return true
			}
		}
		return topLayer.sendMouseEvent(event)
	}

	/**
	 * Dispatches bracketed-paste [text] through this subtree. Paste follows a downward/tree-order
	 * pass: a node's own paste handlers run before its children, and the first handler returning
	 * true consumes the paste.
	 */
	fun sendPasteEvent(text: String): Boolean {
		if (parent == null) {
			overlayHost?.syncLayers()
			val host = overlayHost
			if (host != null && host.layers.isNotEmpty()) {
				val layers = host.layers
				for (index in layers.indices.reversed()) {
					val layer = layers[index]
					if (layer.sendPasteEvent(text)) {
						return true
					}
					if (host.isModal(layer)) {
						return false
					}
				}
				return host.baseNode?.sendPasteEvent(text) ?: false
			}
		}
		return topLayer.sendPasteEvent(text)
	}

	override fun minIntrinsicWidth(height: Int): Int {
		return cachedIntrinsic(MIN_WIDTH, height) { topLayer.minIntrinsicWidth(height) }
	}

	override fun maxIntrinsicWidth(height: Int): Int {
		return cachedIntrinsic(MAX_WIDTH, height) { topLayer.maxIntrinsicWidth(height) }
	}

	override fun minIntrinsicHeight(width: Int): Int {
		return cachedIntrinsic(MIN_HEIGHT, width) { topLayer.minIntrinsicHeight(width) }
	}

	override fun maxIntrinsicHeight(width: Int): Int {
		return cachedIntrinsic(MAX_HEIGHT, width) { topLayer.maxIntrinsicHeight(width) }
	}

	override fun toString() = debugPolicy.run { renderDebug() }
}

private class BottomLayer(
	private val node: MosaicNode,
) : MosaicNodeLayer() {
	override val next: MosaicNodeLayer? get() = null

	override fun sendKeyEvent(keyEvent: KeyEvent): Boolean {
		for (child in node.children) {
			if (child.sendKeyEvent(keyEvent)) {
				return true
			}
		}
		return false
	}

	// Mouse dispatch is handled by MosaicNode.sendMouseEvent (which walks children with bounds
	// hit-testing), so the bottom layer simply terminates the modifier chain.
	override fun sendMouseEvent(event: MouseEvent): Boolean = false

	override fun sendPasteEvent(text: String): Boolean {
		for (child in node.children) {
			if (child.sendPasteEvent(text)) {
				return true
			}
		}
		return false
	}

	override fun doMeasure(constraints: Constraints): MeasureResult {
		return node.measurePolicy.run { measure(node.children, constraints) }
	}

	override fun drawTo(canvas: TextSurface) {
		for (child in node.children) {
			if (child.width != 0 && child.height != 0) {
				child.topLayer.drawTo(canvas)
			}
		}
	}

	override fun minIntrinsicWidth(height: Int): Int {
		return node.measurePolicy.run { minIntrinsicWidth(node.children, height) }
	}

	override fun maxIntrinsicWidth(height: Int): Int {
		return node.measurePolicy.run { maxIntrinsicWidth(node.children, height) }
	}

	override fun minIntrinsicHeight(width: Int): Int {
		return node.measurePolicy.run { minIntrinsicHeight(node.children, width) }
	}

	override fun maxIntrinsicHeight(width: Int): Int {
		return node.measurePolicy.run { maxIntrinsicHeight(node.children, width) }
	}
}

private class LayoutLayer(
	private val element: LayoutModifier,
	override val next: MosaicNodeLayer,
) : MosaicNodeLayer() {
	override fun doMeasure(constraints: Constraints): MeasureResult {
		return element.run { measure(next, constraints) }
	}

	override fun minIntrinsicWidth(height: Int): Int {
		return element.minIntrinsicWidth(next, height)
	}

	override fun maxIntrinsicWidth(height: Int): Int {
		return element.maxIntrinsicWidth(next, height)
	}

	override fun minIntrinsicHeight(width: Int): Int {
		return element.minIntrinsicHeight(next, width)
	}

	override fun maxIntrinsicHeight(width: Int): Int {
		return element.maxIntrinsicHeight(next, width)
	}
}

private class DrawLayer(
	private val element: DrawModifier,
	override val next: MosaicNodeLayer,
) : MosaicNodeLayer() {
	// Saved clip region restored after drawing, stored on this long-lived layer instead of being
	// allocated per frame. Only meaningful when [element] is [ClipToBoundsElement].
	private var savedClipActive = false
	private var savedClipLeft = 0
	private var savedClipTop = 0
	private var savedClipRight = Int.MAX_VALUE
	private var savedClipBottom = Int.MAX_VALUE

	override fun drawTo(canvas: TextSurface) {
		val oldX = canvas.translationX
		val oldY = canvas.translationY
		canvas.translationX = x
		canvas.translationY = y
		val clip = element is ClipToBoundsElement
		if (clip) {
			// Intersect with any enclosing clip (translation above makes this layer's bounds the
			// current canvas origin, x/y are absolute so the region is in canvas coordinates).
			savedClipActive = canvas.clipActive
			savedClipLeft = canvas.clipLeft
			savedClipTop = canvas.clipTop
			savedClipRight = canvas.clipRight
			savedClipBottom = canvas.clipBottom
			canvas.clipActive = true
			if (x > canvas.clipLeft) canvas.clipLeft = x
			if (y > canvas.clipTop) canvas.clipTop = y
			if (x + width < canvas.clipRight) canvas.clipRight = x + width
			if (y + height < canvas.clipBottom) canvas.clipBottom = y + height
		}
		val scope = object : TextCanvasDrawScope(canvas, width, height), ContentDrawScope {
			override fun drawContent() {
				next.drawTo(canvas)
			}
		}
		element.run { scope.draw() }
		if (clip) {
			canvas.clipActive = savedClipActive
			canvas.clipLeft = savedClipLeft
			canvas.clipTop = savedClipTop
			canvas.clipRight = savedClipRight
			canvas.clipBottom = savedClipBottom
		}
		canvas.translationX = oldX
		canvas.translationY = oldY
	}
}

private class KeyLayer(
	private val element: KeyModifier,
	override val next: MosaicNodeLayer,
) : MosaicNodeLayer() {
	override fun sendKeyEvent(keyEvent: KeyEvent) = element.onPreKeyEvent(keyEvent) ||
		next.sendKeyEvent(keyEvent) ||
		element.onKeyEvent(keyEvent)
}

private class MouseLayer(
	private val element: MouseModifier,
	override val next: MosaicNodeLayer,
) : MosaicNodeLayer() {
	override fun sendMouseEvent(event: MouseEvent): Boolean =
		element.onMouseEvent(event) || next.sendMouseEvent(event)
}

private class PasteLayer(
	private val element: PasteModifier,
	override val next: MosaicNodeLayer,
) : MosaicNodeLayer() {
	override fun sendPasteEvent(text: String): Boolean =
		element.onPaste(text) || next.sendPasteEvent(text)
}
