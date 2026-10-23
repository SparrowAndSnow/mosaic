package example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.jakewharton.mosaic.LocalTerminalState
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.background
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.height
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.layout.padding
import com.jakewharton.mosaic.layout.width
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.text.SpanStyle
import com.jakewharton.mosaic.text.buildAnnotatedString
import com.jakewharton.mosaic.text.withStyle
import com.jakewharton.mosaic.ui.Alignment
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Text
import example.screens.CmdScreen
import example.screens.MainScreen
import example.screens.RawScreen
import example.screens.SlowScreen
import example.screens.StatScreen

@Composable
fun MonitorApp(monitorViewModel: MonitorViewModel, colorsPalette: MonitorColorsPalette) {
	val terminal = LocalTerminalState.current
	val monitorUiState by monitorViewModel.uiStateFlow.collectAsState()
	CompositionLocalProvider(LocalMonitorColorsPalette provides colorsPalette) {
		Box(
			modifier = Modifier
				.width(terminal.size.columns)
				.height(terminal.size.rows - 1) // subtraction of one is necessary, because there is a line with a cursor at the bottom, which moves up all the content
				.background(LocalMonitorColorsPalette.current.mainBg)
				.onKeyEvent {
					when (it) {
						KeyEvent("ArrowLeft") -> monitorViewModel.onArrowLeftPress()

						KeyEvent("ArrowRight") -> monitorViewModel.onArrowRightPress()

						KeyEvent("Tab") -> monitorViewModel.onTabPress()

						KeyEvent("q"),
						KeyEvent("Q"),
						-> monitorViewModel.onQPress()

						else -> return@onKeyEvent false
					}
					true
				},
		) {
			when (monitorUiState.currentScreen) {
				MonitorUiState.Screen.Main -> MainScreen(
					monitorUiState.screenUiState as MainScreenUiState,
					modifier = Modifier.padding(bottom = 1),
				)

				MonitorUiState.Screen.Cmd -> CmdScreen(
					monitorUiState.screenUiState as CmdScreenUiState,
					modifier = Modifier.padding(bottom = 1),
				)

				MonitorUiState.Screen.Stat -> StatScreen(
					monitorUiState.screenUiState as StatScreenUiState,
					modifier = Modifier.padding(bottom = 1),
				)

				MonitorUiState.Screen.Slow -> SlowScreen(
					monitorUiState.screenUiState as SlowScreenUiState,
					modifier = Modifier.padding(bottom = 1),
				)

				MonitorUiState.Screen.Raw -> RawScreen(
					monitorUiState.screenUiState as RawScreenUiState,
					modifier = Modifier.padding(bottom = 1),
				)
			}
			BottomStatusBar(
				currentScreen = monitorUiState.currentScreen,
				commonInfo = monitorUiState.commonInfo,
				modifier = Modifier.fillMaxWidth()
					.height(1)
					.padding(horizontal = 1)
					.align(Alignment.BottomCenter),
			)
		}
	}
}

@Composable
private fun BottomStatusBar(
	currentScreen: MonitorUiState.Screen,
	commonInfo: String,
	modifier: Modifier = Modifier,
) {
	Box(modifier = modifier.background(LocalMonitorColorsPalette.current.menuBg)) {
		Text(
			buildAnnotatedString {
				MonitorUiState.Screen.entries.forEachIndexed { index, screen ->
					val screenName = when (screen) {
						MonitorUiState.Screen.Main -> "Main"
						MonitorUiState.Screen.Cmd -> "Cmd"
						MonitorUiState.Screen.Stat -> "Stat"
						MonitorUiState.Screen.Slow -> "Slow"
						MonitorUiState.Screen.Raw -> "Raw"
					}
					if (screen == currentScreen) {
						withStyle(SpanStyle(LocalMonitorColorsPalette.current.menuHighlightFg)) {
							append(screenName)
						}
					} else {
						append(screenName)
					}
					if (index < MonitorUiState.Screen.entries.lastIndex) {
						withStyle(SpanStyle(LocalMonitorColorsPalette.current.menuDividerFg)) {
							append(" | ")
						}
					}
				}
			},
			modifier = Modifier.align(Alignment.CenterStart),
			color = LocalMonitorColorsPalette.current.menuFg,
		)
		Text(
			commonInfo,
			color = LocalMonitorColorsPalette.current.statusBarFg,
			modifier = Modifier.align(Alignment.CenterEnd),
		)
	}
}
