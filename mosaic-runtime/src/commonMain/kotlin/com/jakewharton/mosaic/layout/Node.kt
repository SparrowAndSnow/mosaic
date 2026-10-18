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

	/** Number of times an actual (non-cached) measurement ran on this node. */
	internal var measureCount: Long = 0
		private set
	/** Number of times a cached measurement result was reused on this node. */
	internal var cacheHitCount: Long = 0
		private set

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
		topLayer = modifier.foldOut(bottomLayer) { element, nextLayer ->
			var nextLayer = nextLayer
			// The Modifier class can inherit from several key Modifier types
			// with different processing logic.
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
	}

	override fun measure(constraints: Constraints): Placeable {
		val cached = cachedResult
		if (cached != null && cachedConstraints == constraints && cachedVersion == layoutVersion) {
			cacheHitCount++
			return cached
		}
		measureCount++
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
		return topLayer.sendKeyEvent(keyEvent)
	}

	/**
	 * Dispatches a [MouseEvent] to the top-most layout under the pointer and, if it is not
	 * consumed, bubbles up to its ancestors. Coordinates are absolute canvas (screen cell)
	 * coordinates, matching a node's [x]/[y] after placement.
	 */
	fun sendMouseEvent(event: MouseEvent): Boolean {
		if (event.x !in x until x + width || event.y !in y until y + height) {
			return false
		}
		// Children draw on top of this node, so give them (in z-order, top-most first) the first
		// chance to handle the event before this node's own modifiers.
		for (index in children.indices.reversed()) {
			if (children[index].sendMouseEvent(event)) {
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
		return topLayer.sendPasteEvent(text)
	}

	override fun minIntrinsicWidth(height: Int): Int {
		return topLayer.minIntrinsicWidth(height)
	}

	override fun maxIntrinsicWidth(height: Int): Int {
		return topLayer.maxIntrinsicWidth(height)
	}

	override fun minIntrinsicHeight(width: Int): Int {
		return topLayer.minIntrinsicHeight(width)
	}

	override fun maxIntrinsicHeight(width: Int): Int {
		return topLayer.maxIntrinsicHeight(width)
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
