@file:OptIn(ExperimentalTransitionApi::class, InternalAnimationApi::class)

package com.jakewharton.mosaic.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.jakewharton.mosaic.layout.LayoutModifier
import com.jakewharton.mosaic.layout.Measurable
import com.jakewharton.mosaic.layout.MeasureResult
import com.jakewharton.mosaic.layout.MeasureScope
import com.jakewharton.mosaic.layout.Placeable
import com.jakewharton.mosaic.layout.clipToBounds
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Alignment
import com.jakewharton.mosaic.ui.Layout
import com.jakewharton.mosaic.ui.unit.Constraints
import com.jakewharton.mosaic.ui.unit.constrain
import com.jakewharton.mosaic.ui.unit.IntOffset
import com.jakewharton.mosaic.ui.unit.IntSize
import kotlin.math.max

/**
 * The possible animation states of the content inside an [AnimatedVisibility] transition:
 * [PreEnter] is the initial state of an enter animation, [Visible] is the target of everything
 * entering and the source of everything exiting, and [PostExit] is the target state of an exit
 * animation (after which the content is disposed).
 */
public enum class EnterExitState {
	PreEnter,
	Visible,
	PostExit,
}

/**
 * Scope for [AnimatedVisibility] content, offering access to the underlying
 * [Transition] so custom animations can be driven alongside the built-in enter/exit effects.
 */
public interface AnimatedVisibilityScope {
	/** The [Transition] that drives this [AnimatedVisibility]'s enter/exit animation. */
	public val transition: Transition<EnterExitState>
}

internal class AnimatedVisibilityScopeImpl(
	override var transition: Transition<EnterExitState>,
) : AnimatedVisibilityScope

/**
 * Animates the appearance and disappearance of [content] as [visible] changes. The enter and exit
 * animations are configured through [enter] and [exit]; multiple effects can be combined with `+`
 * and they all start simultaneously.
 *
 * The size the container occupies animates with any expand/shrink effect, so siblings are pushed
 * and pulled by the transition. Content is only removed from the tree once the exit animation has
 * fully finished.
 *
 * @param visible whether [content] should be visible.
 * @param modifier modifier for the layout that contains [content].
 * @param enter the enter transition, an expand from the bottom by default.
 * @param exit the exit transition, a shrink towards the bottom by default.
 * @param label an optional label to distinguish this transition in diagnostics.
 * @param content the content to appear and disappear.
 */
@Composable
public fun AnimatedVisibility(
	visible: Boolean,
	modifier: Modifier = Modifier,
	enter: EnterTransition = expandVertically(),
	exit: ExitTransition = shrinkVertically(),
	label: String = "AnimatedVisibility",
	content: @Composable() AnimatedVisibilityScope.() -> Unit,
) {
	val transition = updateTransition(visible, label)
	AnimatedVisibilityImpl(transition, { it }, modifier, enter, exit, content)
}

/**
 * Variant of [AnimatedVisibility] driven by a [MutableTransitionState]. The transition begins
 * animating immediately upon entering composition when the state's initial state differs from its
 * target, and re-creating the state restarts the transition.
 *
 * @param visibleState the mutable state driving visibility; must be [remember]ed by the caller.
 * @param modifier modifier for the layout that contains [content].
 * @param enter the enter transition, an expand from the bottom by default.
 * @param exit the exit transition, a shrink towards the bottom by default.
 * @param label an optional label to distinguish this transition in diagnostics.
 * @param content the content to appear and disappear.
 */
@Composable
public fun AnimatedVisibility(
	visibleState: MutableTransitionState<Boolean>,
	modifier: Modifier = Modifier,
	enter: EnterTransition = expandVertically(),
	exit: ExitTransition = shrinkVertically(),
	label: String = "AnimatedVisibility",
	content: @Composable() AnimatedVisibilityScope.() -> Unit,
) {
	val transition = rememberTransition(visibleState, label)
	AnimatedVisibilityImpl(transition, { it }, modifier, enter, exit, content)
}


@Composable
internal fun <T> AnimatedVisibilityImpl(
	transition: Transition<T>,
	visible: (T) -> Boolean,
	modifier: Modifier,
	enter: EnterTransition,
	exit: ExitTransition,
	content: @Composable() AnimatedVisibilityScope.() -> Unit,
) {
	AnimatedEnterExitImpl(
		transition = transition,
		visible = visible,
		modifier = modifier,
		enter = enter,
		exit = exit,
		shouldDisposeBlock = { current, target -> current == target && target == EnterExitState.PostExit },
		content = content,
	)
}

@Composable
internal fun <T> AnimatedEnterExitImpl(
	transition: Transition<T>,
	visible: (T) -> Boolean,
	modifier: Modifier,
	enter: EnterTransition,
	exit: ExitTransition,
	shouldDisposeBlock: (EnterExitState, EnterExitState) -> Boolean,
	content: @Composable() AnimatedVisibilityScope.() -> Unit,
) {
	if (visible(transition.targetState) || visible(transition.currentState)) {
		val childTransition = transition.createChildTransition(label = "EnterExitTransition") {
			transition.targetEnterExit(visible, it)
		}

		val shouldDisposeBlockUpdated by rememberUpdatedState(shouldDisposeBlock)

		// Once the exit animation has finished, the content may be disposed. This is observed
		// through snapshot flow rather than during composition so that the removal happens on the
		// frame *after* the exit has fully completed.
		val shouldDisposeAfterExit by produceState(
			initialValue = shouldDisposeBlock(childTransition.currentState, childTransition.targetState),
		) {
			snapshotFlow { childTransition.exitFinished }
				.collect { finished ->
					value =
						if (finished) {
							shouldDisposeBlockUpdated(
								childTransition.currentState,
								childTransition.targetState,
							)
						} else {
							false
						}
				}
		}

		if (!childTransition.exitFinished || !shouldDisposeAfterExit) {
			val scope = remember(transition) { AnimatedVisibilityScopeImpl(childTransition) }
			Layout(
				content = { scope.content() },
				modifier =
					modifier.then(
						childTransition.createModifier(enter, exit, label = "Built-in"),
					),
				measurePolicy = remember { MaxSizeAtOriginMeasurePolicy },
			)
		}
	}
}

private val Transition<EnterExitState>.exitFinished
	get() = currentState == EnterExitState.PostExit && targetState == EnterExitState.PostExit

// Converts the parent transition state to an EnterExitState. A never-visible target becomes
// PreEnter (so nothing composes), while a state that was visible and is now hidden becomes
// PostExit (so the exit animation plays).
@Composable
private fun <T> Transition<T>.targetEnterExit(
	visible: (T) -> Boolean,
	targetState: T,
): EnterExitState {
	val hasBeenVisible = remember { mutableStateOf(false) }
	if (visible(currentState)) {
		hasBeenVisible.value = true
	}
	return if (visible(targetState)) {
		EnterExitState.Visible
	} else {
		if (hasBeenVisible.value) EnterExitState.PostExit else EnterExitState.PreEnter
	}
}

// Measures all children at the incoming constraints and lays the container out at the largest
// width/height among them, aligning every child to the top start.
internal object MaxSizeAtOriginMeasurePolicy : com.jakewharton.mosaic.layout.MeasurePolicy {
	override fun MeasureScope.measure(
		measurables: List<Measurable>,
		constraints: Constraints,
	): MeasureResult {
		var maxWidth = 0
		var maxHeight = 0
		val placeables = ArrayList<Placeable>(measurables.size)
		for (measurable in measurables) {
			val placeable = measurable.measure(constraints)
			maxWidth = max(maxWidth, placeable.width)
			maxHeight = max(maxHeight, placeable.height)
			placeables += placeable
		}
		return layout(maxWidth, maxHeight) {
			for (placeable in placeables) {
				placeable.place(0, 0)
			}
		}
	}
}

private val DefaultSizeAnimationSpec = spring(
	stiffness = Spring.StiffnessMediumLow,
	visibilityThreshold = IntSize.VisibilityThreshold,
)

private val DefaultOffsetAnimationSpec = spring(
	stiffness = Spring.StiffnessMediumLow,
	visibilityThreshold = IntOffset.VisibilityThreshold,
)

// Builds the modifier chain that turns a Transition<EnterExitState> into the animated layout:
// size (expand/shrink) and slide effects are wired to deferred animations set up during measure,
// and the content is clipped to the animated bounds while a size effect is present.
@Composable
internal fun Transition<EnterExitState>.createModifier(
	enter: EnterTransition,
	exit: ExitTransition,
	label: String,
): Modifier {
	val activeEnter = trackActiveEnter(enter)
	val activeExit = trackActiveExit(exit)

	val shouldAnimateSizeChange =
		activeEnter.data.changeSize != null || activeExit.data.changeSize != null
	val shouldAnimateSlide = activeEnter.data.slide != null || activeExit.data.slide != null

	val sizeAnimation =
		if (shouldAnimateSizeChange) {
			createDeferredAnimation(IntSize.VectorConverter, remember { "$label shrink/expand" })
		} else {
			null
		}
	val slideAnimation =
		if (shouldAnimateSlide) {
			createDeferredAnimation(IntOffset.VectorConverter, remember { "$label slide" })
		} else {
			null
		}

	// Clone the data so the modifier holds stable references to the currently-active transitions,
	// matching the values used to set up the deferred animations above.
	val clipped =
		if (shouldAnimateSizeChange) {
			Modifier.clipToBounds()
		} else {
			Modifier
		}
	return clipped.then(
		EnterExitTransitionModifier(
			transition = this,
			sizeAnimation = sizeAnimation,
			slideAnimation = slideAnimation,
			enter = activeEnter,
			exit = activeExit,
		),
	)
}

@Composable
private fun Transition<EnterExitState>.trackActiveEnter(enter: EnterTransition): EnterTransition {
	var activeEnter by remember(this) { mutableStateOf(enter) }
	if (currentState == targetState && currentState == EnterExitState.Visible) {
		activeEnter = EnterTransition.None
	} else if (targetState == EnterExitState.Visible) {
		activeEnter += enter
	}
	return activeEnter
}

@Composable
private fun Transition<EnterExitState>.trackActiveExit(exit: ExitTransition): ExitTransition {
	var activeExit by remember(this) { mutableStateOf(exit) }
	if (currentState == targetState && currentState == EnterExitState.Visible) {
		activeExit = ExitTransition.None
	} else if (targetState != EnterExitState.Visible) {
		activeExit += exit
	}
	return activeExit
}

/**
 * The layout modifier that actually animates the content: it reads both deferred animations set
 * up in composition and returns a layout sized by the animated size with the content slid and
 * aligned accordingly. Reading the animation values during measure registers this node as
 * depending on those states, so every animated frame re-runs layout.
 */
private class EnterExitTransitionModifier(
	private val transition: Transition<EnterExitState>,
	private val sizeAnimation:
		Transition<EnterExitState>.DeferredAnimation<IntSize, AnimationVector2D>?,
	private val slideAnimation:
		Transition<EnterExitState>.DeferredAnimation<IntOffset, AnimationVector2D>?,
	private val enter: EnterTransition,
	private val exit: ExitTransition,
) : LayoutModifier {

	private var currentAlignment: Alignment? = null

	private val alignment: Alignment?
		get() =
			with(transition.segment) {
				if (EnterExitState.PreEnter isTransitioningTo EnterExitState.Visible) {
					enter.data.changeSize?.alignment ?: exit.data.changeSize?.alignment
				} else {
					exit.data.changeSize?.alignment ?: enter.data.changeSize?.alignment
				}
			}

	private val sizeTransitionSpec: Transition.Segment<EnterExitState>.() -> FiniteAnimationSpec<IntSize> =
		{
			when {
				EnterExitState.PreEnter isTransitioningTo EnterExitState.Visible ->
					enter.data.changeSize?.animationSpec
				EnterExitState.Visible isTransitioningTo EnterExitState.PostExit ->
					exit.data.changeSize?.animationSpec
				else -> null
			} ?: DefaultSizeAnimationSpec
		}

	private val slideTransitionSpec: Transition.Segment<EnterExitState>.() -> FiniteAnimationSpec<IntOffset> =
		{
			when {
				EnterExitState.PreEnter isTransitioningTo EnterExitState.Visible ->
					enter.data.slide?.animationSpec
				EnterExitState.Visible isTransitioningTo EnterExitState.PostExit ->
					exit.data.slide?.animationSpec
				else -> null
			} ?: DefaultOffsetAnimationSpec
		}

	private fun sizeByState(targetState: EnterExitState, fullSize: IntSize): IntSize =
		when (targetState) {
			EnterExitState.Visible -> fullSize
			EnterExitState.PreEnter -> enter.data.changeSize?.size?.invoke(fullSize) ?: fullSize
			EnterExitState.PostExit -> exit.data.changeSize?.size?.invoke(fullSize) ?: fullSize
		}

	private fun slideTargetValueByState(targetState: EnterExitState, fullSize: IntSize): IntOffset =
		when (targetState) {
			EnterExitState.Visible -> IntOffset.Zero
			EnterExitState.PreEnter -> enter.data.slide?.slideOffset?.invoke(fullSize) ?: IntOffset.Zero
			EnterExitState.PostExit -> exit.data.slide?.slideOffset?.invoke(fullSize) ?: IntOffset.Zero
		}

	override fun MeasureScope.measure(
		measurable: Measurable,
		constraints: Constraints,
	): MeasureResult {
		if (transition.currentState == transition.targetState) {
			currentAlignment = null
		} else if (currentAlignment == null) {
			currentAlignment = alignment
		}
		val placeable = measurable.measure(constraints)
		val fullSize = IntSize(placeable.width, placeable.height)
		val animatedSize =
			sizeAnimation
				?.animate(sizeTransitionSpec) { state -> sizeByState(state, fullSize) }
				?.value
		val currentSize = if (animatedSize != null) constraints.constrain(animatedSize) else fullSize
		val slideOffset =
			slideAnimation
				?.animate(slideTransitionSpec) { state -> slideTargetValueByState(state, fullSize) }
				?.value
				?: IntOffset.Zero
		val offset = (currentAlignment?.align(fullSize, currentSize) ?: IntOffset.Zero) + slideOffset
		return layout(currentSize.width, currentSize.height) {
			placeable.place(offset.x, offset.y)
		}
	}

	override fun toString(): String = "EnterExitTransition"
}
