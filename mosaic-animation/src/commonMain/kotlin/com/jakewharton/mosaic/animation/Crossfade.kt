@file:OptIn(InternalAnimationApi::class)

package com.jakewharton.mosaic.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.jakewharton.mosaic.layout.LayoutModifier
import com.jakewharton.mosaic.layout.Measurable
import com.jakewharton.mosaic.layout.MeasureResult
import com.jakewharton.mosaic.layout.MeasureScope
import com.jakewharton.mosaic.layout.Placeable
import com.jakewharton.mosaic.layout.clipToBounds
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Layout
import com.jakewharton.mosaic.ui.unit.Constraints
import com.jakewharton.mosaic.ui.unit.IntOffset

/**
 * Crossfades between the [content] corresponding to the current [targetState] and the content of
 * the previous state. Because terminals have no alpha blending, this takes a *horizontal advance*
 * form: the outgoing content slides out to the left while the incoming content slides in from the
 * right, layered over one another inside the container.
 *
 * [targetState] is a key representing the target content: whenever it changes, the animation is
 * triggered. [content] is invoked with the old key (sliding out) and the new key (sliding in)
 * simultaneously during the transition.
 *
 * @param targetState a key for the target content state.
 * @param modifier modifier to apply to the container.
 * @param animationSpec the animation used to drive the slide, [tween] by default.
 * @param label an optional label to distinguish this transition in diagnostics.
 * @param content a mapping of a state to the content for that state.
 */
@Composable
public fun <T> Crossfade(
	targetState: T,
	modifier: Modifier = Modifier,
	animationSpec: FiniteAnimationSpec<IntOffset> = tween(),
	label: String = "Crossfade",
	content: @Composable (T) -> Unit,
) {
	val transition = updateTransition(targetState, label)
	transition.Crossfade(modifier, animationSpec, content = content)
}

/**
 * The [Transition] variant of [Crossfade]: the target state of the given [Transition] is used.
 * When the transition changes target, this Crossfade slides the target content in while sliding
 * the current content out.
 *
 * [contentKey] is used to compare states for equality; when two states resolve to the same key no
 * animation is triggered between them. By default the state object itself is used.
 *
 * @param modifier modifier to apply to the container.
 * @param animationSpec the animation used to drive the slide, [tween] by default.
 * @param contentKey a mapping of a state to an identity key.
 * @param content a mapping of a state to the content for that state.
 */
@Composable
public fun <T> Transition<T>.Crossfade(
	modifier: Modifier = Modifier,
	animationSpec: FiniteAnimationSpec<IntOffset> = tween(),
	contentKey: (targetState: T) -> Any? = { it },
	content: @Composable (targetState: T) -> Unit,
) {
	val currentlyVisible = remember { mutableStateListOf<T>().apply { add(currentState) } }
	val contentMap = remember { mutableMapOf<T, @Composable () -> Unit>() }
	if (currentState == targetState) {
		// Steady state: collapse back down to the single target layer so that re-entering the
		// same state does not accumulate layers.
		if (currentlyVisible.size != 1 || currentlyVisible[0] != targetState) {
			currentlyVisible.removeAll { it != targetState }
			contentMap.clear()
		}
	}
	if (targetState !in contentMap) {
		// Replace any layer sharing the new target's content key, then rebuild the layer map with
		// the latest target so enter/exit directions stay consistent for the whole transition.
		val replacementId =
			currentlyVisible.indexOfFirst { contentKey(it) == contentKey(targetState) }
		if (replacementId == -1) {
			currentlyVisible.add(targetState)
		} else {
			currentlyVisible[replacementId] = targetState
		}
		contentMap.clear()
		val transition = this
		for (stateForContent in currentlyVisible) {
			contentMap[stateForContent] = {
				val entering = stateForContent == transition.targetState
				AnimatedLayer(
					transition = transition,
					stateForContent = stateForContent,
					entering = entering,
					animationSpec = animationSpec,
					content = { content(stateForContent) },
				)
			}
		}
	}

	Layout(
		content = {
			for (stateForContent in currentlyVisible) {
				key(contentKey(stateForContent)) {
					contentMap[stateForContent]?.invoke()
				}
			}
		},
		modifier = modifier.then(Modifier.clipToBounds()),
		measurePolicy = remember { MaxSizeAtOriginMeasurePolicy },
	)
}

/**
 * One crossfade layer: own content plus the deferred offset animation that advances this layer
 * across the container during a transition.
 */
@Suppress("UNCHECKED_CAST")
@Composable
private fun <T> AnimatedLayer(
	transition: Transition<T>,
	stateForContent: T,
	entering: Boolean,
	animationSpec: FiniteAnimationSpec<IntOffset>,
	content: @Composable () -> Unit,
) {
	val anyTransition = transition as Transition<Any?>
	val offsetAnimation = anyTransition.createDeferredAnimation(
		IntOffset.VectorConverter,
		remember { "Crossfade slide" },
	)
	Layout(
		content = content,
		modifier = Modifier.then(
			CrossfadeOffsetModifier(
				transition = anyTransition,
				stateForContent = stateForContent as Any?,
				entering = entering,
				offsetAnimation = offsetAnimation,
				animationSpec = animationSpec,
			),
		),
		measurePolicy = remember { IdentityMeasurePolicy },
	)
}

/**
 * Slides a crossfade layer horizontally. The mapping drives the outgoing layer (no longer the
 * target) off to the left and brings the entering layer in from the right, both relative to this
 * layer's own width.
 */
private class CrossfadeOffsetModifier(
	private val transition: Transition<Any?>,
	private val stateForContent: Any?,
	private val entering: Boolean,
	private val offsetAnimation: Transition<Any?>.DeferredAnimation<IntOffset, AnimationVector2D>,
	private val animationSpec: FiniteAnimationSpec<IntOffset>,
) : LayoutModifier {
	override fun MeasureScope.measure(
		measurable: Measurable,
		constraints: Constraints,
	): MeasureResult {
		val placeable = measurable.measure(constraints)
		val width = placeable.width
		val offsetState = offsetAnimation.animate({ animationSpec }) { parentState ->
			when {
				parentState == stateForContent -> IntOffset.Zero
				entering -> IntOffset(width, 0)
				else -> IntOffset(-width, 0)
			}
		}
		val offset = offsetState.value
		return layout(placeable.width, placeable.height) {
			placeable.place(offset.x, offset.y)
		}
	}

	override fun toString(): String = "Crossfade"
}

private object IdentityMeasurePolicy : com.jakewharton.mosaic.layout.MeasurePolicy {
	override fun MeasureScope.measure(
		measurables: List<Measurable>,
		constraints: Constraints,
	): MeasureResult {
		val placeable = measurables[0].measure(constraints)
		return layout(placeable.width, placeable.height) {
			placeable.place(0, 0)
		}
	}
}
