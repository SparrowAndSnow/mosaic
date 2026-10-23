package example.screens

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.modifier.Modifier
import example.LocalMonitorColorsPalette
import example.StatScreenUiState
import example.common.BorderedTitledBox
import example.common.Table
import example.common.TableConfig
import example.common.TableData

@Composable
fun StatScreen(uiState: StatScreenUiState, modifier: Modifier = Modifier) {
	BorderedTitledBox(
		title = "stat",
		titleColor = LocalMonitorColorsPalette.current.statTitleFg,
		borderColor = LocalMonitorColorsPalette.current.statBorderFg,
		modifier = modifier,
	) {
		Table(
			TableData(uiState.items),
			TableConfig(
				titleColor = LocalMonitorColorsPalette.current.statTableHeaderFg,
				columnConfigs = listOf(
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "time",
						stringFromItem = { it.time },
						valueColor = LocalMonitorColorsPalette.current.statTableRowTop1Fg,
						weight = 2,
					),
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "op/s",
						stringFromItem = { it.operationsPerSecond },
						valueColor = LocalMonitorColorsPalette.current.statTableRowTop2Fg,
					),
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "user",
						stringFromItem = { it.userCpuUtilization },
						valueColor = LocalMonitorColorsPalette.current.statTableRowTop2Fg,
					),
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "sys",
						stringFromItem = { it.sysCpuUtilization },
						valueColor = LocalMonitorColorsPalette.current.statTableRowTop2Fg,
					),
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "mem use",
						stringFromItem = { it.memoryUsage },
						valueColor = LocalMonitorColorsPalette.current.statTableRowTop1Fg,
					),
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "mem rss",
						stringFromItem = { it.memoryRss },
						valueColor = LocalMonitorColorsPalette.current.statTableRowTop1Fg,
					),
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "fr rati",
						stringFromItem = { it.fragmentationRatio },
						valueColor = LocalMonitorColorsPalette.current.statTableRowTop2Fg,
					),
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "hit rate",
						stringFromItem = { it.hitRateFormatted },
						valueColor = LocalMonitorColorsPalette.current.statTableRowTop2Fg,
					),
					TableConfig.ColumnConfig.ProgressColumnConfig(
						filledColor = LocalMonitorColorsPalette.current.statTableRowGaugeFg,
						emptyColor = LocalMonitorColorsPalette.current.statTableRowGaugeBg,
						progressFromItem = { it.hitRate / 100.0f },
					),
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "keys",
						stringFromItem = { it.totalKeys },
						valueColor = LocalMonitorColorsPalette.current.statTableRowTop1Fg,
					),
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "exp",
						stringFromItem = { it.keysToExpire },
						valueColor = LocalMonitorColorsPalette.current.statTableRowTop1Fg,
					),
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "exp/s",
						stringFromItem = { it.keysToExpirePerSecond },
						valueColor = LocalMonitorColorsPalette.current.statTableRowTop2Fg,
					),
					TableConfig.ColumnConfig.StringColumnConfig(
						title = "evt/s",
						stringFromItem = { it.evictedKeysPerSecond },
						valueColor = LocalMonitorColorsPalette.current.statTableRowTop2Fg,
					),
				),
			),
		)
	}
}
