@file:OptIn(InternalAnimationApi::class)

package com.jakewharton.mosaic.animation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.jakewharton.mosaic.ui.Alignment
import com.jakewharton.mosaic.ui.unit.IntOffset
import com.jakewharton.mosaic.ui.unit.IntSize

/**
 * A description of an enter transition for [com.jakewharton.mosaic.animation.AnimatedVisibility].
 * Different [EnterTransition]s can be combined with `+` into a single transition; the animations
 * all start simultaneously.
 */
public sealed class EnterTransition {
	internal abstract val data: TransitionData

	public companion object {
		/** A no-op enter transition: the content appears instantly without any animation. */
		public val None: EnterTransition = EnterTransitionImpl(TransitionData())
	}
}

/**
 * A description of an exit transition for [com.jakewharton.mosaic.animation.AnimatedVisibility].
 * Different [ExitTransition]s can be combined with `+` into a single transition; the animations
 * all start simultaneously.
 */
public sealed class ExitTransition {
	internal abstract val data: TransitionData

	public companion object {
		/** A no-op exit transition: the content disappears instantly without any animation. */
		public val None: ExitTransition = ExitTransitionImpl(TransitionData())
	}
}

@Immutable
internal data class TransitionData(
	val slide: Slide? = null,
	val changeSize: ChangeSize? = null,
)

@Immutable
internal data class ChangeSize(
	val alignment: Alignment,
	val size: (fullSize: IntSize) -> IntSize = { IntSize(0, 0) },
	val animationSpec: FiniteAnimationSpec<IntSize>,
)

@Immutable
internal data class Slide(
	val slideOffset: (fullSize: IntSize) -> IntOffset,
	val animationSpec: FiniteAnimationSpec<IntOffset>,
)

@Immutable
private class EnterTransitionImpl(override val data: TransitionData) : EnterTransition()

@Immutable
private class ExitTransitionImpl(override val data: TransitionData) : ExitTransition()

internal operator fun EnterTransition.plus(enter: EnterTransition): EnterTransition = if (this == EnterTransition.None) {
	enter
} else if (enter == EnterTransition.None) {
	this
} else {
	EnterTransitionImpl(
		TransitionData(
			slide = data.slide ?: enter.data.slide,
			changeSize = data.changeSize ?: enter.data.changeSize,
		),
	)
}

internal operator fun ExitTransition.plus(exit: ExitTransition): ExitTransition = if (this == ExitTransition.None) {
	exit
} else if (exit == ExitTransition.None) {
	this
} else {
	ExitTransitionImpl(
		TransitionData(
			slide = data.slide ?: exit.data.slide,
			changeSize = data.changeSize ?: exit.data.changeSize,
		),
	)
}

/**
 * Expands the appearance bounds of the content from [expandFrom] to the full size of the content.
 * The bounds animation drives the size of the layout itself, so sibling layouts are pushed aside
 * as the content grows. The content is rendered only within the animated bounds.
 *
 * @param animationSpec the animation used for the expanding bounds, [spring] by default.
 * @param expandFrom where the expansion starts from, [Alignment.BottomEnd] by default.
 * @param initialSize a lambda that takes the full size of the content and returns the initial
 *   size of the bounds, `IntSize(0, 0)` by default.
 */
@Stable
public fun expandIn(
	animationSpec: FiniteAnimationSpec<IntSize> = spring(
		stiffness = Spring.StiffnessMediumLow,
		visibilityThreshold = IntSize.VisibilityThreshold,
	),
	expandFrom: Alignment = Alignment.BottomEnd,
	initialSize: (fullSize: IntSize) -> IntSize = { IntSize(0, 0) },
): EnterTransition {
	return EnterTransitionImpl(
		TransitionData(changeSize = ChangeSize(expandFrom, initialSize, animationSpec)),
	)
}

/**
 * Shrinks the appearance bounds of the disappearing content from the full size to the size
 * returned from [targetSize]. The bounds animation drives the size of the layout itself, so
 * sibling layouts move to close the gap as the content collapses. The content is rendered only
 * within the animated bounds.
 *
 * @param animationSpec the animation used for the shrinking bounds, [spring] by default.
 * @param shrinkTowards where the bounds shrink towards, [Alignment.BottomEnd] by default.
 * @param targetSize a lambda that takes the full size of the content and returns the target size
 *   of the bounds, [IntSize.Zero] by default.
 */
@Stable
public fun shrinkOut(
	animationSpec: FiniteAnimationSpec<IntSize> = spring(
		stiffness = Spring.StiffnessMediumLow,
		visibilityThreshold = IntSize.VisibilityThreshold,
	),
	shrinkTowards: Alignment = Alignment.BottomEnd,
	targetSize: (fullSize: IntSize) -> IntSize = { IntSize(0, 0) },
): ExitTransition {
	return ExitTransitionImpl(
		TransitionData(changeSize = ChangeSize(shrinkTowards, targetSize, animationSpec)),
	)
}

/**
 * Expands the appearance bounds of the content horizontally, from the width returned from
 * [initialWidth] to the full width, anchored at [expandFrom]. [expandFrom] controls which edge of
 * the content is revealed first; the opposite edge moves out as the bounds grow.
 *
 * @param animationSpec the animation used for the expanding bounds, [spring] by default.
 * @param expandFrom the starting edge of the expansion, [Alignment.End] by default.
 * @param initialWidth returns the initial width of the bounds, `0` by default.
 */
@Stable
public fun expandHorizontally(
	animationSpec: FiniteAnimationSpec<IntSize> = spring(
		stiffness = Spring.StiffnessMediumLow,
		visibilityThreshold = IntSize.VisibilityThreshold,
	),
	expandFrom: Alignment.Horizontal = Alignment.End,
	initialWidth: (fullWidth: Int) -> Int = { 0 },
): EnterTransition {
	return expandIn(animationSpec, expandFrom.toAlignment()) {
		IntSize(initialWidth(it.width), it.height)
	}
}

/**
 * Expands the appearance bounds of the content vertically, from the height returned from
 * [initialHeight] to the full height, anchored at [expandFrom]. [expandFrom] controls which edge
 * of the content is revealed first; the opposite edge moves out as the bounds grow.
 *
 * @param animationSpec the animation used for the expanding bounds, [spring] by default.
 * @param expandFrom the starting edge of the expansion, [Alignment.Bottom] by default.
 * @param initialHeight returns the initial height of the bounds, `0` by default.
 */
@Stable
public fun expandVertically(
	animationSpec: FiniteAnimationSpec<IntSize> = spring(
		stiffness = Spring.StiffnessMediumLow,
		visibilityThreshold = IntSize.VisibilityThreshold,
	),
	expandFrom: Alignment.Vertical = Alignment.Bottom,
	initialHeight: (fullHeight: Int) -> Int = { 0 },
): EnterTransition {
	return expandIn(animationSpec, expandFrom.toAlignment()) {
		IntSize(it.width, initialHeight(it.height))
	}
}

/**
 * Shrinks the appearance bounds of the disappearing content horizontally, from the full width to
 * the width returned from [targetWidth], collapsing towards [shrinkTowards].
 *
 * @param animationSpec the animation used for the shrinking bounds, [spring] by default.
 * @param shrinkTowards the edge the bounds collapse towards, [Alignment.End] by default.
 * @param targetWidth returns the target width of the bounds, `0` by default.
 */
@Stable
public fun shrinkHorizontally(
	animationSpec: FiniteAnimationSpec<IntSize> = spring(
		stiffness = Spring.StiffnessMediumLow,
		visibilityThreshold = IntSize.VisibilityThreshold,
	),
	shrinkTowards: Alignment.Horizontal = Alignment.End,
	targetWidth: (fullWidth: Int) -> Int = { 0 },
): ExitTransition {
	return shrinkOut(animationSpec, shrinkTowards.toAlignment()) {
		IntSize(targetWidth(it.width), it.height)
	}
}

/**
 * Shrinks the appearance bounds of the disappearing content vertically, from the full height to
 * the height returned from [targetHeight], collapsing towards [shrinkTowards].
 *
 * @param animationSpec the animation used for the shrinking bounds, [spring] by default.
 * @param shrinkTowards the edge the bounds collapse towards, [Alignment.Bottom] by default.
 * @param targetHeight returns the target height of the bounds, `0` by default.
 */
@Stable
public fun shrinkVertically(
	animationSpec: FiniteAnimationSpec<IntSize> = spring(
		stiffness = Spring.StiffnessMediumLow,
		visibilityThreshold = IntSize.VisibilityThreshold,
	),
	shrinkTowards: Alignment.Vertical = Alignment.Bottom,
	targetHeight: (fullHeight: Int) -> Int = { 0 },
): ExitTransition {
	return shrinkOut(animationSpec, shrinkTowards.toAlignment()) {
		IntSize(it.width, targetHeight(it.height))
	}
}

/**
 * Slides in the content from a starting offset defined in [initialOffset] to `IntOffset(0, 0)`.
 * A positive x slides from right to left, a negative x slides from left to right; positive and
 * negative y correspond to sliding up and down respectively.
 *
 * @param animationSpec the animation used for the slide, [spring] by default.
 * @param initialOffset takes the full size of the content and returns the starting offset.
 */
@Stable
public fun slideIn(
	animationSpec: FiniteAnimationSpec<IntOffset> = spring(
		stiffness = Spring.StiffnessMediumLow,
		visibilityThreshold = IntOffset.VisibilityThreshold,
	),
	initialOffset: (fullSize: IntSize) -> IntOffset,
): EnterTransition {
	return EnterTransitionImpl(TransitionData(slide = Slide(initialOffset, animationSpec)))
}

/**
 * Slides out the content from `IntOffset(0, 0)` to a target offset defined in [targetOffset].
 *
 * @param animationSpec the animation used for the slide, [spring] by default.
 * @param targetOffset takes the full size of the content and returns the target offset.
 */
@Stable
public fun slideOut(
	animationSpec: FiniteAnimationSpec<IntOffset> = spring(
		stiffness = Spring.StiffnessMediumLow,
		visibilityThreshold = IntOffset.VisibilityThreshold,
	),
	targetOffset: (fullSize: IntSize) -> IntOffset,
): ExitTransition {
	return ExitTransitionImpl(TransitionData(slide = Slide(targetOffset, animationSpec)))
}

/**
 * Slides in the content horizontally, from a starting offset defined in [initialOffsetX] to `0`.
 * A positive value slides from right to left, a negative value from left to right.
 *
 * @param animationSpec the animation used for the slide, [spring] by default.
 * @param initialOffsetX takes the full width and returns the starting horizontal offset,
 *   `-fullWidth / 2` by default.
 */
@Stable
public fun slideInHorizontally(
	animationSpec: FiniteAnimationSpec<IntOffset> = spring(
		stiffness = Spring.StiffnessMediumLow,
		visibilityThreshold = IntOffset.VisibilityThreshold,
	),
	initialOffsetX: (fullWidth: Int) -> Int = { -it / 2 },
): EnterTransition {
	return slideIn(animationSpec) { IntOffset(initialOffsetX(it.width), 0) }
}

/**
 * Slides in the content vertically, from a starting offset defined in [initialOffsetY] to `0`.
 * A positive value slides up, a negative value slides down.
 *
 * @param animationSpec the animation used for the slide, [spring] by default.
 * @param initialOffsetY takes the full height and returns the starting vertical offset,
 *   `-fullHeight / 2` by default.
 */
@Stable
public fun slideInVertically(
	animationSpec: FiniteAnimationSpec<IntOffset> = spring(
		stiffness = Spring.StiffnessMediumLow,
		visibilityThreshold = IntOffset.VisibilityThreshold,
	),
	initialOffsetY: (fullHeight: Int) -> Int = { -it / 2 },
): EnterTransition {
	return slideIn(animationSpec) { IntOffset(0, initialOffsetY(it.height)) }
}

/**
 * Slides out the content horizontally, from `0` to a target offset defined in [targetOffsetX].
 * A positive value slides to the right, a negative value to the left.
 *
 * @param animationSpec the animation used for the slide, [spring] by default.
 * @param targetOffsetX takes the full width and returns the target horizontal offset,
 *   `-fullWidth / 2` by default.
 */
@Stable
public fun slideOutHorizontally(
	animationSpec: FiniteAnimationSpec<IntOffset> = spring(
		stiffness = Spring.StiffnessMediumLow,
		visibilityThreshold = IntOffset.VisibilityThreshold,
	),
	targetOffsetX: (fullWidth: Int) -> Int = { -it / 2 },
): ExitTransition {
	return slideOut(animationSpec) { IntOffset(targetOffsetX(it.width), 0) }
}

/**
 * Slides out the content vertically, from `0` to a target offset defined in [targetOffsetY].
 * A positive value slides down, a negative value slides up.
 *
 * @param animationSpec the animation used for the slide, [spring] by default.
 * @param targetOffsetY takes the full height and returns the target vertical offset,
 *   `-fullHeight / 2` by default.
 */
@Stable
public fun slideOutVertically(
	animationSpec: FiniteAnimationSpec<IntOffset> = spring(
		stiffness = Spring.StiffnessMediumLow,
		visibilityThreshold = IntOffset.VisibilityThreshold,
	),
	targetOffsetY: (fullHeight: Int) -> Int = { -it / 2 },
): ExitTransition {
	return slideOut(animationSpec) { IntOffset(0, targetOffsetY(it.height)) }
}

private fun Alignment.Horizontal.toAlignment(): Alignment = when {
	this == Alignment.Start -> Alignment.CenterStart
	this == Alignment.End -> Alignment.CenterEnd
	else -> Alignment.Center
}

private fun Alignment.Vertical.toAlignment(): Alignment = when {
	this == Alignment.Top -> Alignment.TopCenter
	this == Alignment.Bottom -> Alignment.BottomCenter
	else -> Alignment.Center
}
