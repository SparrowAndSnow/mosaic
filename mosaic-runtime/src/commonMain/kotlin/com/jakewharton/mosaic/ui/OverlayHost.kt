@file:Suppress("ktlint:compose:param-order-check")

package com.jakewharton.mosaic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.jakewharton.mosaic.layout.Measurable
import com.jakewharton.mosaic.layout.MeasurePolicy
import com.jakewharton.mosaic.layout.MeasureResult
import com.jakewharton.mosaic.layout.MeasureScope
import com.jakewharton.mosaic.layout.OverlayHostState
import com.jakewharton.mosaic.layout.OverlayLayerParentData
import com.jakewharton.mosaic.layout.Placeable
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.unit.Constraints
import com.jakewharton.mosaic.ui.unit.IntOffset
import com.jakewharton.mosaic.ui.unit.IntSize

/**
 * Hosts an optional overlay above [content].
 *
 * The base [content] is measured first and draws first; the [overlay] slot is measured against the
 * base size (like [BoxScope.matchParentSize]) and draws second, so it can paint over the base.
 * Each direct child composed by [overlay] is one layer, and later children paint on top of earlier
 * ones. The host does not change the incoming constraints and the base still declares the canvas
 * size: put [content] in a sized container (for example the terminal size) as before.
 *
 * Input follows the layer stack: mouse events are hit-tested top-down through the layers, and key
 * and paste events are offered to later layers first. A modal layer (see [Dialog] and
 * [Popup] with `modal = true`) is a barrier: unhandled input stops there instead of falling
 * through to layers below or to the base. Non-modal layers fall through to the base when they do
 * not consume an event, so menus and hint popups do not swallow application shortcuts.
 *
 * While a modal layer is present, the base loses keyboard focus and Tab traversal is restricted to
 * the top-most modal layer and the non-modal layers above it. Opening a modal saves the base focus
 * and closing it restores that target when it is still focusable; layers that disappear also cancel
 * any in-flight pointer press and clear hover state.
 *
 * Only one host is allowed per composition. Composing a second host, or nesting one inside the
 * base or overlay content of another, throws [IllegalStateException]. A null [overlay] composes no
 * overlay slot and renders exactly like [content] alone (the extra host node is still present).
 *
 * @param overlay Layers to draw above [content], or `null` for no overlay. Each direct child is a
 *   layer; later children are drawn and hit-tested above earlier ones.
 * @param modifier Modifier applied to the host layout. It participates in sizing the host.
 * @param content Base content, measured and drawn first.
 */
@Suppress("ktlint:compose:param-order-check") // Signature follows the overlay design.
@Composable
public fun OverlayHost(
	overlay: (@Composable () -> Unit)? = null,
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit,
) {
	val state = remember { OverlayHostState() }
	val registry = LocalOverlayHostRegistry.current
	val host = remember { Any() }
	DisposableEffect(registry, host, state) {
		check(registry.host == null || registry.host === host) {
			"Only one OverlayHost is allowed per composition, and OverlayHosts cannot be nested. " +
				"Compose a single OverlayHost at the top of the content and put all dialogs and popups " +
				"in its overlay slot."
		}
		registry.host = host
		onDispose {
			if (registry.host === host) {
				registry.host = null
			}
			state.unregister()
		}
	}
	SideEffect {
		state.syncLayers()
	}

	Box(modifier.then(state)) {
		// A stable base slot lets the host identify its base even when the application content is
		// empty or emits several roots.
		Box {
			content()
		}
		if (overlay != null) {
			Layout(
				content = overlay,
				modifier = Modifier.matchParentSize(),
				debugInfo = { "OverlaySlot()" },
				measurePolicy = OverlaySlotMeasurePolicy,
			)
		}
	}
}

/**
 * Per-composition registry used to enforce the single-overlay-host rule. Provided by the
 * composition owner; reading it without a provider is a programming error.
 */
internal class OverlayHostRegistry {
	var host: Any? = null
}

internal val LocalOverlayHostRegistry: ProvidableCompositionLocal<OverlayHostRegistry> =
	staticCompositionLocalOf {
		throw AssertionError("No OverlayHost registry provided")
	}

/**
 * Sizes the overlay slot to the base area, measures each layer, and places it. Modal layers fill the
 * base; non-modal layers are content-sized and placed at their requested alignment plus offset.
 * The slot itself is transparent; only layers paint.
 */
private object OverlaySlotMeasurePolicy : MeasurePolicy {
	override fun MeasureScope.measure(
		measurables: List<Measurable>,
		constraints: Constraints,
	): MeasureResult {
		val width = constraints.minWidth
		val height = constraints.minHeight
		val contentConstraints = constraints.copy(minWidth = 0, minHeight = 0)
		val fillConstraints = if (width == Constraints.Infinity || height == Constraints.Infinity) {
			contentConstraints
		} else {
			Constraints.fixed(width, height)
		}
		val placeables = arrayOfNulls<Placeable>(measurables.size)
		val layerData = arrayOfNulls<OverlayLayerParentData>(measurables.size)
		measurables.forEachIndexed { index, measurable ->
			val data = measurable.parentData as? OverlayLayerParentData
			layerData[index] = data
			placeables[index] = measurable.measure(
				if (data?.fillParent == true) fillConstraints else contentConstraints,
			)
		}
		return layout(width, height) {
			val space = IntSize(width, height)
			placeables.forEachIndexed { index, placeable ->
				placeable as Placeable
				val data = layerData[index]
				if (data?.fillParent == true) {
					placeable.place(0, 0)
				} else {
					val aligned = data?.alignment?.align(
						IntSize(placeable.width, placeable.height),
						space,
					) ?: IntOffset.Zero
					placeable.place(
						aligned.x + (data?.offset?.x ?: 0),
						aligned.y + (data?.offset?.y ?: 0),
					)
				}
			}
		}
	}
}
