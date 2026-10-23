package com.jakewharton.mosaic.layout

import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Alignment
import com.jakewharton.mosaic.ui.unit.Constraints
import com.jakewharton.mosaic.ui.unit.IntOffset

/**
 * Marker implemented by the modifier element attached to an overlay host root. [MosaicNode.setModifier]
 * calls [attach] so the host state can learn its own node without exposing node identity to user code.
 */
internal interface OverlayHostAttach : MosaicNodeAttach

/**
 * Parent data attached by every overlay layer (Popup, Dialog) so the overlay slot knows whether the
 * layer is modal, whether it fills the base area, and where a content-sized layer goes.
 */
internal class OverlayLayerParentData(
	var alignment: Alignment,
	var offset: IntOffset,
	var modal: Boolean,
	var fillParent: Boolean,
)

internal class OverlayLayerModifier(
	private val alignment: Alignment,
	private val offset: IntOffset,
	private val modal: Boolean,
	private val fillParent: Boolean,
) : ParentDataModifier {
	override fun modifyParentData(parentData: Any?): Any = OverlayLayerParentData(alignment, offset, modal, fillParent)
}

/**
 * Per-host state shared by composition, layout, and root input dispatch.
 *
 * The state is itself a pass-through layout modifier so attaching it to the host's node is enough to
 * learn that node (see [OverlayHostAttach]). It is registered on the composition root lazily on the
 * first measure; root key/mouse/paste dispatch then consults [layers] for ordering and barriers.
 */
internal class OverlayHostState :
	LayoutModifier,
	OverlayHostAttach {
	var hostNode: MosaicNode? = null
		private set
	var baseNode: MosaicNode? = null
		private set
	var slotNode: MosaicNode? = null
		private set

	private var rootNode: MosaicNode? = null
	private var previousLayers: List<MosaicNode> = emptyList()
	private var previousModalLayers: List<MosaicNode> = emptyList()
	private var dirty = true

	/** Live layer list, bottom-most first. Later layers draw and receive input above earlier ones. */
	val layers: List<MosaicNode>
		get() = slotNode?.children ?: emptyList()

	fun isModal(layer: MosaicNode): Boolean = (layer.parentData as? OverlayLayerParentData)?.modal == true

	override fun attach(node: MosaicNode) {
		hostNode = node
	}

	override fun MeasureScope.measure(
		measurable: Measurable,
		constraints: Constraints,
	): MeasureResult {
		register()
		val placeable = measurable.measure(constraints)
		return layout(placeable.width, placeable.height) {
			placeable.place(0, 0)
		}
	}

	override fun toString(): String = "OverlayHost()"

	/** Registers this host with the composition root if it is not already the active host. */
	fun register() {
		val node = hostNode ?: return
		val root = node.root()
		rootNode = root
		if (root.overlayHost !== this) {
			root.overlayHost = this
		}
	}

	/** Marks the layer structure as changed by a composition apply. */
	fun markDirty() {
		dirty = true
	}

	/** Re-reads the host's base/slot/layer structure after composition applies changes. */
	fun syncLayers() {
		if (!dirty) return
		val node = hostNode ?: return
		dirty = false
		register()
		baseNode = node.children.getOrNull(0)
		slotNode = node.children.getOrNull(1)
		val currentLayers = slotNode?.children?.toList().orEmpty()
		val modalLayers = currentLayers.filter(::isModal)
		val layersChanged = currentLayers != previousLayers
		val modalsChanged = modalLayers != previousModalLayers
		if (layersChanged || modalsChanged) {
			rootNode?.resetPointerSessions()
		}
		if (modalsChanged) {
			rootNode?.ensureFocusManager()?.updateModals(modalLayers, rootNode!!)
		}
		if (layersChanged || modalsChanged) {
			rootNode?.rehoverOverlay()
		}
		previousLayers = currentLayers
		previousModalLayers = modalLayers
	}

	/** Detaches this host when its composition leaves, restoring the base focus domain. */
	fun unregister() {
		val root = rootNode
		if (root != null && root.overlayHost === this) {
			root.overlayHost = null
			root.ensureFocusManager().updateModals(emptyList(), root)
			root.resetPointerSessions()
		}
		hostNode = null
		baseNode = null
		slotNode = null
		rootNode = null
		previousLayers = emptyList()
		previousModalLayers = emptyList()
		dirty = false
	}

	/**
	 * Input roots in top-most-first order, stopping at the top-most modal layer (inclusive). When no
	 * modal layer exists the base is appended last, so non-modal layers fall through to it.
	 */
	fun rootsForMouse(): List<MosaicNode> {
		val result = ArrayList<MosaicNode>()
		for (index in layers.indices.reversed()) {
			val layer = layers[index]
			result += layer
			if (isModal(layer)) return result
		}
		baseNode?.let(result::add)
		return result
	}

	/**
	 * Focus roots for the active modal domain in top-most-first order, or `null` when no modal layer
	 * exists (the whole tree remains the focus domain, matching the pre-overlay behavior).
	 */
	fun activeFocusRoots(): List<MosaicNode>? {
		val result = ArrayList<MosaicNode>()
		for (index in layers.indices.reversed()) {
			val layer = layers[index]
			result += layer
			if (isModal(layer)) return result
		}
		return null
	}
}
