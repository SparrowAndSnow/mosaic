package com.jakewharton.mosaic.ui

import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.Composition
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import com.jakewharton.mosaic.MosaicNodeApplier
import com.jakewharton.mosaic.layout.DebugPolicy
import com.jakewharton.mosaic.layout.Measurable
import com.jakewharton.mosaic.layout.MeasurePolicy
import com.jakewharton.mosaic.layout.MeasureResult
import com.jakewharton.mosaic.layout.MeasureScope
import com.jakewharton.mosaic.layout.MosaicNode
import com.jakewharton.mosaic.layout.Placeable
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.modifier.materialize
import com.jakewharton.mosaic.ui.unit.Constraints
import kotlin.math.max

/**
 * A [Box] whose content can read the constraints handed down by its parent, used for layouts that
 * derive child sizes from the available space (for example, splitting the width in half or at a
 * custom ratio that cannot be expressed with [Modifier.weight] or [Modifier.fillMaxWidth]).
 *
 * Content is composed lazily on the first measure with the incoming [Constraints], and recomposed
 * whenever those constraints change after the incoming constraints change. The composed content is
 * measured and aligned like a [Box] using [contentAlignment].
 *
 * @param modifier The modifier to be applied to the layout.
 * @param contentAlignment The alignment of the content within the [BoxWithConstraints] when its
 *   size is known to be larger than the content.
 * @param propagateMinConstraints When `true`, minimum constraints are propagated to children,
 *   matching [Box]'s `propagateMinConstraints` behavior.
 */
@OptIn(InternalComposeApi::class)
@Composable
public fun BoxWithConstraints(
	modifier: Modifier = Modifier,
	contentAlignment: Alignment = Alignment.TopStart,
	propagateMinConstraints: Boolean = false,
	content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
	val state = remember { BoxWithConstraintsState() }
	state.content = content
	DisposableEffect(state) {
		onDispose { state.dispose() }
	}
	val compositionContext = rememberCompositionContext()
	val measurePolicy = remember(contentAlignment, propagateMinConstraints) {
		BoxWithConstraintsMeasurePolicy(
			alignment = contentAlignment,
			propagateMinConstraints = propagateMinConstraints,
			state = state,
			compositionContext = compositionContext,
		)
	}
	BoxWithConstraintsNode(
		measurePolicy = measurePolicy,
		debugPolicy = { "BoxWithConstraints(alignment=$contentAlignment)" },
		modifier = modifier,
		state = state,
	)
}

/**
 * A scope for the children of [BoxWithConstraints]. Exposes the constraints the parent layout
 * measured this [BoxWithConstraints] with, in pixels.
 */
@LayoutScopeMarker
@Immutable
public interface BoxWithConstraintsScope : BoxScope {

	/** The constraints given by the parent layout. */
	public val constraints: Constraints

	/** The minimum width within these [constraints]. */
	public val minWidth: Int
		get() = constraints.minWidth

	/** The maximum width within these [constraints]. */
	public val maxWidth: Int
		get() = constraints.maxWidth

	/** The minimum height within these [constraints]. */
	public val minHeight: Int
		get() = constraints.minHeight

	/** The maximum height within these [constraints]. */
	public val maxHeight: Int
		get() = constraints.maxHeight

	/** Whether the constraints have a bounded maximum width. */
	public val hasBoundedWidth: Boolean
		get() = constraints.hasBoundedWidth

	/** Whether the constraints have a bounded maximum height. */
	public val hasBoundedHeight: Boolean
		get() = constraints.hasBoundedHeight

	/** Whether the constraints have a fixed (min == max) width. */
	public val hasFixedWidth: Boolean
		get() = constraints.hasFixedWidth

	/** Whether the constraints have a fixed (min == max) height. */
	public val hasFixedHeight: Boolean
		get() = constraints.hasFixedHeight
}

private class BoxWithConstraintsScopeImpl(
	override val constraints: Constraints,
) : BoxWithConstraintsScope {
	override fun Modifier.align(alignment: Alignment): Modifier =
		this.then(AlignModifier(alignment, matchParentSize = false))

	override fun Modifier.matchParentSize(): Modifier =
		this.then(AlignModifier(Alignment.Center, matchParentSize = true))
}

/**
 * Holds the single-slot sub-composition that backs [BoxWithConstraints] and the references needed
 * to wire it into the layout tree on first measure.
 */
internal class BoxWithConstraintsState {
	var layoutNode: MosaicNode? = null
		internal set
	var content: (@Composable BoxWithConstraintsScope.() -> Unit)? = null

	internal var composition: Composition? = null
		internal set
	internal var subRoot: MosaicNode? = null
		internal set
	private var lastConstraints: Constraints? = null

	/**
	 * Incremented whenever the sub-composition applies changes, so the enclosing layout pass
	 * (which reads this value) is invalidated and re-measures the newly applied content.
	 */
	val version = mutableIntStateOf(0)

	fun refreshComposition(
		constraints: Constraints,
		compositionContext: androidx.compose.runtime.CompositionContext,
	) {
		if (lastConstraints == constraints && composition != null) {
			return
		}
		lastConstraints = constraints
		val content = content ?: return
		val scope = BoxWithConstraintsScopeImpl(constraints)
		val current = composition
		if (current == null) {
			val node = layoutNode ?: return
			val newApplier = MosaicNodeApplier(onChanges = { version.intValue++ })
			val root = newApplier.root
			val newComposition = Composition(newApplier, compositionContext)
			composition = newComposition
			subRoot = root
			node.children.add(root)
			newComposition.setContent { scope.content() }
		} else {
			current.setContent { scope.content() }
		}
	}

	fun dispose() {
		composition?.dispose()
		composition = null
		val node = layoutNode
		val root = subRoot
		if (node != null && root != null) {
			node.children.remove(root)
		}
		subRoot = null
		lastConstraints = null
	}
}

internal class BoxWithConstraintsMeasurePolicy(
	private val alignment: Alignment,
	private val propagateMinConstraints: Boolean,
	private val state: BoxWithConstraintsState,
	private val compositionContext: androidx.compose.runtime.CompositionContext,
) : MeasurePolicy {
	override fun MeasureScope.measure(
		measurables: List<Measurable>,
		constraints: Constraints,
	): MeasureResult {
		// Reading this value makes every sub-composition change invalidate this layout pass.
		@Suppress("UNUSED_EXPRESSION")
		state.version.intValue

		state.refreshComposition(constraints, compositionContext)
		// The sub-composition root is a structural container; its composed children are measured
		// directly so (as with [Box]) matchParentSize children match this layout's own size and
		// alignment is applied here.
		val children: List<Measurable> = state.subRoot?.children ?: emptyList()
		if (children.isEmpty()) {
			return layout(constraints.minWidth, constraints.minHeight) {}
		}
		val contentConstraints = if (propagateMinConstraints) {
			constraints
		} else {
			constraints.copy(minWidth = 0, minHeight = 0)
		}
		val placeables = arrayOfNulls<Placeable>(children.size)
		var boxWidth = constraints.minWidth
		var boxHeight = constraints.minHeight
		var hasMatchParentSizeChildren = false
		children.forEachIndexed { index, measurable ->
			if (!measurable.matchesParentSize) {
				val placeable = measurable.measure(contentConstraints)
				placeables[index] = placeable
				boxWidth = max(boxWidth, placeable.width)
				boxHeight = max(boxHeight, placeable.height)
			} else {
				hasMatchParentSizeChildren = true
			}
		}
		if (hasMatchParentSizeChildren) {
			// The infinity check is needed for default intrinsic measurements.
			val matchParentSizeConstraints = Constraints(
				minWidth = if (boxWidth != Constraints.Infinity) boxWidth else 0,
				minHeight = if (boxHeight != Constraints.Infinity) boxHeight else 0,
				maxWidth = boxWidth,
				maxHeight = boxHeight,
			)
			children.forEachIndexed { index, measurable ->
				if (measurable.matchesParentSize) {
					placeables[index] = measurable.measure(matchParentSizeConstraints)
				}
			}
		}
		return layout(boxWidth, boxHeight) {
			placeables.forEachIndexed { index, placeable ->
				placeable as Placeable
				placeInBox(placeable, children[index], boxWidth, boxHeight, alignment)
			}
		}
	}
}

@Composable
private fun BoxWithConstraintsNode(
	measurePolicy: MeasurePolicy,
	debugPolicy: DebugPolicy,
	modifier: Modifier,
	state: BoxWithConstraintsState,
) {
	val materializedModifier = currentComposer.materialize(modifier)
	ComposeNode<MosaicNode, Applier<Any>>(
		factory = {
			MosaicNode(
				measurePolicy = measurePolicy,
				debugPolicy = debugPolicy,
				isStatic = false,
			).also { state.layoutNode = it }
		},
		update = {
			set(measurePolicy, SetMeasurePolicy)
			set(materializedModifier, SetModifier)
			set(debugPolicy, SetDebugPolicy)
		},
	)
}
