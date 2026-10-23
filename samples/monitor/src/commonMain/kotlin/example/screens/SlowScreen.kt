package example.screens

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.modifier.Modifier
import example.LocalMonitorColorsPalette
import example.SlowScreenUiState
import example.common.BorderedTitledBox
import example.common.Table
import example.common.TableConfig
import example.common.TableData

@Composable
fun SlowScreen(uiState: SlowScreenUiState, modifier: Modifier = Modifier) {
	BorderedTitledBox(
		title = "slow log",
		titleColor = LocalMonitorColorsPalette.current.slowTitleFg,
		borderColor = LocalMonitorColorsPalette.current.slowBorderFg,
		modifier = modifier,
	) {
		Table(
			TableData(uiState.items),
			TableConfig(
				titleColor = LocalMonitorColorsPalette.current.slowTableHeaderFg,
				columnConfigs = listOf(
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "id",
						stringFromItem = { it.id },
						valueColor = LocalMonitorColorsPalette.current.slowTableRowTop1Fg,
					),
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "time",
						stringFromItem = { it.time },
						valueColor = LocalMonitorColorsPalette.current.slowTableRowTop2Fg,
					),
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "exec time",
						stringFromItem = { it.execTime },
						valueColor = LocalMonitorColorsPalette.current.slowTableRowTop2Fg,
					),
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "command",
						stringFromItem = { it.command },
						valueColor = LocalMonitorColorsPalette.current.slowTableRowTop2Fg,
					),
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "client ip",
						stringFromItem = { it.clientIp },
						valueColor = LocalMonitorColorsPalette.current.slowTableRowTop1Fg,
					),
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "client name",
						stringFromItem = { it.clientName },
						valueColor = LocalMonitorColorsPalette.current.slowTableRowTop1Fg,
					),
				),
			),
		)
	}
}
