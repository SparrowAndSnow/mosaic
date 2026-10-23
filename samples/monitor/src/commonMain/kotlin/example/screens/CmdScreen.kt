package example.screens

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.modifier.Modifier
import example.CmdScreenUiState
import example.LocalMonitorColorsPalette
import example.common.BorderedTitledBox
import example.common.Table
import example.common.TableConfig
import example.common.TableData

@Composable
fun CmdScreen(uiState: CmdScreenUiState, modifier: Modifier = Modifier) {
	BorderedTitledBox(
		title = "by calls",
		titleColor = LocalMonitorColorsPalette.current.callsTitleFg,
		borderColor = LocalMonitorColorsPalette.current.callsBorderFg,
		modifier = modifier,
	) {
		Table(
			TableData(uiState.items),
			TableConfig(
				titleColor = LocalMonitorColorsPalette.current.callsTableHeaderFg,
				columnConfigs = listOf(
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "command",
						stringFromItem = { it.command },
						valueColor = LocalMonitorColorsPalette.current.callsTableRowTop1Fg,
					),
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "calls",
						stringFromItem = { it.calls },
						valueColor = LocalMonitorColorsPalette.current.callsTableRowTop2Fg,
						valueAlignment = TableConfig.ColumnConfig.ColumnAligment.END,
					),
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "",
						stringFromItem = { it.callsPercentFormatted },
						valueColor = LocalMonitorColorsPalette.current.callsTableRowTop2Fg,
						valueAlignment = TableConfig.ColumnConfig.ColumnAligment.END,
					),
					TableConfig.ColumnConfig.ProgressColumnConfig(
						filledColor = LocalMonitorColorsPalette.current.callsTableRowGaugeFg,
						emptyColor = LocalMonitorColorsPalette.current.callsTableRowGaugeBg,
						progressFromItem = { it.callsPercent / 100.0f },
					),
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "usec",
						stringFromItem = { it.usec },
						valueColor = LocalMonitorColorsPalette.current.callsTableRowTop2Fg,
						valueAlignment = TableConfig.ColumnConfig.ColumnAligment.END,
					),
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "",
						stringFromItem = { it.usecPercentFormatted },
						valueColor = LocalMonitorColorsPalette.current.callsTableRowTop2Fg,
						valueAlignment = TableConfig.ColumnConfig.ColumnAligment.END,
					),
					TableConfig.ColumnConfig.ProgressColumnConfig(
						filledColor = LocalMonitorColorsPalette.current.callsTableRowGaugeFg,
						emptyColor = LocalMonitorColorsPalette.current.callsTableRowGaugeBg,
						progressFromItem = { it.usecPercent / 100.0f },
					),
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "usec per call",
						stringFromItem = { it.usecPerCall },
						valueColor = LocalMonitorColorsPalette.current.callsTableRowTop2Fg,
						valueAlignment = TableConfig.ColumnConfig.ColumnAligment.END,
					),
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "",
						stringFromItem = { it.usecPerCallPercentFormatted },
						valueColor = LocalMonitorColorsPalette.current.callsTableRowTop2Fg,
						valueAlignment = TableConfig.ColumnConfig.ColumnAligment.END,
					),
					TableConfig.ColumnConfig.ProgressColumnConfig(
						filledColor = LocalMonitorColorsPalette.current.callsTableRowGaugeFg,
						emptyColor = LocalMonitorColorsPalette.current.callsTableRowGaugeBg,
						progressFromItem = { it.usecPerCallPercent / 100.0f },
					),
				),
			),
		)
	}
}
