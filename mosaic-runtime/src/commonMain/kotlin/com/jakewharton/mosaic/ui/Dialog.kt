@file:Suppress("ktlint:compose:param-order-check")

package com.jakewharton.mosaic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.Measurable
import com.jakewharton.mosaic.layout.MeasurePolicy
import com.jakewharton.mosaic.layout.MeasureResult
import com.jakewharton.mosaic.layout.MeasureScope
import com.jakewharton.mosaic.layout.OverlayLayerModifier
import com.jakewharton.mosaic.layout.Placeable
import com.jakewharton.mosaic.layout.clickable
import com.jakewharton.mosaic.layout.fillMaxSize
import com.jakewharton.mosaic.layout.hoverable
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.layout.onMouseEvent
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.unit.Constraints
import com.jakewharton.mosaic.ui.unit.IntOffset
import com.jakewharton.mosaic.ui.unit.IntSize

/**
 * A modal layer centered above the base content of an [OverlayHost].
 *
 * While the dialog is composed it is an input barrier: keyboard, paste, and mouse events are offered
 * to the dialog first and unhandled events stop at the dialog instead of reaching the base. The base
 * keeps its composition state and stays visible through [backdrop] when [OverlayBackdrop.None] is
 * used. Opening the dialog saves the base focus and clears it; closing the dialog restores the saved
 * target when it is still focusable and attached.
 *
 * [backdrop] defaults to [OverlayBackdrop.Solid], which erases the base so the dialog is readable on
 * its own. The dialog surface itself paints no border or background; add [Modifier.border] or
 * [Modifier.background] to the content when needed.
 *
 * Escape is handled after the content had its chance: content-level key handlers can consume it
 * first, and only otherwise [onDismissRequest] runs. A primary-button click on the backdrop invokes
 * [onDismissRequest] when [dismissOnBackdropClick] is true; clicks on the dialog content (including
 * content with no mouse handler) never reach the backdrop.
 *
 * @param onDismissRequest Invoked when the dialog asks to close (Escape or backdrop click).
 * @param modifier Modifier applied to the modal layer.
 * @param dismissOnBackdropClick Whether a click outside the dialog content dismisses it.
 * @param dismissOnBack Whether Escape dismisses it.
 * @param backdrop What is painted behind the content. Defaults to erasing the base.
 * @param content Dialog content, stacked vertically in a [Column].
 */
@Suppress("ktlint:compose:param-order-check") // Signature follows the overlay design.
@Composable
public fun Dialog(
	onDismissRequest: () -> Unit,
	modifier: Modifier = Modifier,
	dismissOnBackdropClick: Boolean = true,
	dismissOnBack: Boolean = true,
	backdrop: OverlayBackdrop = OverlayBackdrop.Solid(),
	content: @Composable ColumnScope.() -> Unit,
) {
	// Tracks whether the pointer is over the dialog surface at release time. The backdrop's
	// clickable spans the whole base, so this is what keeps a press outside/release inside (and
	// the reverse) from being treated as a backdrop click.
	val contentHovered = remember { mutableStateOf(false) }
	Layout(
		content = {
			Box(
				Modifier
					.fillMaxSize()
					.overlayBackdrop(backdrop)
					.then(
						if (dismissOnBackdropClick && backdrop != OverlayBackdrop.None) {
							Modifier.clickable {
								if (!contentHovered.value) {
									onDismissRequest()
								}
							}
						} else {
							Modifier
						},
					),
			)
			// The content shield is deliberately separate from the dialog backdrop: without it a
			// click on non-interactive dialog content would fall through to the backdrop and dismiss.
			Box(
				Modifier
					.onMouseEvent { true }
					.clickable { }
					.hoverable { contentHovered.value = it },
			) {
				Column {
					content()
				}
			}
		},
		modifier = modifier
			.then(
				if (dismissOnBack) {
					Modifier.onKeyEvent { event: KeyEvent ->
						if (event.key == "Escape") {
							onDismissRequest()
							true
						} else {
							false
						}
					}
				} else {
					Modifier
				},
			)
			.then(OverlayLayerModifier(Alignment.Center, IntOffset.Zero, modal = true, fillParent = true)),
		debugInfo = { "Dialog(backdrop=$backdrop)" },
		measurePolicy = DialogMeasurePolicy,
	)
}

private object DialogMeasurePolicy : MeasurePolicy {
	override fun MeasureScope.measure(
		measurables: List<Measurable>,
		constraints: Constraints,
	): MeasureResult {
		val width = constraints.minWidth
		val height = constraints.minHeight
		val backdrop = measurables.getOrNull(0)
		val content = measurables.getOrNull(1)
		val backdropPlaceable = backdrop?.measure(Constraints.fixed(width, height))
		val contentPlaceable = content?.measure(constraints.copy(minWidth = 0, minHeight = 0))
		return layout(width, height) {
			backdropPlaceable?.let { placeable: Placeable -> placeable.place(0, 0) }
			if (contentPlaceable != null) {
				val position = Alignment.Center.align(
					IntSize(contentPlaceable.width, contentPlaceable.height),
					IntSize(width, height),
				)
				contentPlaceable.place(position.x, position.y)
			}
		}
	}
}
