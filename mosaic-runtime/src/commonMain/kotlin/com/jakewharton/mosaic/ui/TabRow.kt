package com.jakewharton.mosaic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.layout.clickable
import com.jakewharton.mosaic.layout.hoverable
import com.jakewharton.mosaic.modifier.Modifier

/**
 * Colors used by [TabRow]. Every field defaults to [Color.Unspecified], which leaves the terminal's
 * own palette in effect, so the component carries no application-specific colors.
 *
 * @param container Background of a tab that is neither selected nor hovered.
 * @param selectedBackground Background of the selected tab.
 * @param selectedContent Text color of the selected tab.
 * @param content Text color of every unselected tab.
 * @param hoverBackground Background of an unselected tab under the pointer.
 * @param divider Text color of the divider drawn between tabs.
 */
@Immutable
public data class TabRowColors(
	val container: Color = Color.Unspecified,
	val selectedBackground: Color = Color.Unspecified,
	val selectedContent: Color = Color.Unspecified,
	val content: Color = Color.Unspecified,
	val hoverBackground: Color = Color.Unspecified,
	val divider: Color = Color.Unspecified,
)

/**
 * A single row of clickable tabs, optionally separated by a one-cell [divider].
 *
 * Each tab renders its label surrounded by one column of horizontal padding, and its background
 * follows the selected, hovered, and container colors from [colors] in that order of precedence.
 * Clicking a tab invokes [onSelect] with that tab's index, including when it is already selected.
 *
 * [trailing] is appended after the tabs in the same [RowScope], so its content may use
 * [Modifier.weight] (for example a leading `Spacer(Modifier.weight(1f))` followed by a status
 * text) to occupy the rest of the row and right-align a hint. The row itself is transparent; only
 * the tabs paint [TabRowColors.container].
 *
 * This component manages no selection, scrolling, or keyboard logic; callers own
 * [selectedIndex] and update it from [onSelect].
 *
 * @param tabs Labels to render, in order.
 * @param selectedIndex Index of the selected tab. Out-of-range values select nothing.
 * @param onSelect Invoked with the index of the tab that was clicked.
 * @param modifier Modifier applied to the containing row.
 * @param colors Colors for the tabs and the divider.
 * @param divider Text drawn in one cell between tabs, or `null` to omit it.
 * @param trailing Optional content appended after the tabs in the row's scope.
 */
@Composable
public fun TabRow(
	tabs: List<String>,
	selectedIndex: Int,
	onSelect: (Int) -> Unit,
	modifier: Modifier = Modifier,
	colors: TabRowColors = TabRowColors(),
	divider: String? = "│",
	trailing: (@Composable RowScope.() -> Unit)? = null,
) {
	// A single hovered index avoids one remembered state per tab, which would be positionally
	// unstable when tabs are added or removed or when the divider toggles.
	var hoveredIndex by remember { mutableStateOf(-1) }

	Row(modifier = modifier) {
		tabs.forEachIndexed { index, label ->
			if (divider != null && index > 0) {
				Text(divider, color = colors.divider)
			}

			val selected = index == selectedIndex
			Text(
				value = " " + label + " ",
				modifier = Modifier
					.clickable { onSelect(index) }
					.hoverable { hovered ->
						if (hovered) {
							hoveredIndex = index
						} else if (hoveredIndex == index) {
							hoveredIndex = -1
						}
					},
				color = if (selected) colors.selectedContent else colors.content,
				background = when {
					selected -> colors.selectedBackground
					hoveredIndex == index -> colors.hoverBackground
					else -> colors.container
				},
			)
		}
		trailing?.invoke(this)
	}
}
