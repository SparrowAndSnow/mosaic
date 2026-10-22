@file:Suppress("ktlint:compose:param-order-check")

package com.jakewharton.mosaic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.jakewharton.mosaic.layout.Measurable
import com.jakewharton.mosaic.layout.MeasurePolicy
import com.jakewharton.mosaic.layout.MeasureResult
import com.jakewharton.mosaic.layout.MeasureScope
import com.jakewharton.mosaic.layout.ParentDataModifier
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
 * Mouse events are hit-tested top-down through the layer stack before reaching the base, so a
 * [Popup] receives clicks inside its bounds first. Layers do not gate keyboard or paste input in
 * this version: keys and paste still follow the normal tree order, and the base is always eligible
 * to consume them. Modal input gating, focus scopes, and Escape dismissal are planned for a later
 * phase.
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
	val registry = LocalOverlayHostRegistry.current
	val host = remember { Any() }
	DisposableEffect(registry, host) {
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
		}
	}

	Box(modifier) {
		content()
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
 * Parent data attached by [Popup] (and future layer components) telling the overlay slot where to
 * place the layer inside the base area.
 */
internal class OverlayLayerParentData(
	val alignment: Alignment,
	val offset: IntOffset,
)

internal class OverlayLayerModifier(
	private val alignment: Alignment,
	private val offset: IntOffset,
) : ParentDataModifier {
	override fun modifyParentData(parentData: Any?): Any = OverlayLayerParentData(alignment, offset)
}

/**
 * Sizes the overlay slot to the base area and places every layer at its requested alignment plus
 * offset. The slot itself is transparent; only layers paint.
 */
private object OverlaySlotMeasurePolicy : MeasurePolicy {
	override fun MeasureScope.measure(
		measurables: List<Measurable>,
		constraints: Constraints,
	): MeasureResult {
		val width = constraints.minWidth
		val height = constraints.minHeight
		val contentConstraints = constraints.copy(minWidth = 0, minHeight = 0)
		val placeables = arrayOfNulls<Placeable>(measurables.size)
		val layerData = arrayOfNulls<OverlayLayerParentData>(measurables.size)
		measurables.forEachIndexed { index, measurable ->
			placeables[index] = measurable.measure(contentConstraints)
			layerData[index] = measurable.parentData as? OverlayLayerParentData
		}
		return layout(width, height) {
			val space = IntSize(width, height)
			placeables.forEachIndexed { index, placeable ->
				placeable as Placeable
				val data = layerData[index]
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
