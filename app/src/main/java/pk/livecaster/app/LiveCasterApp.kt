package pk.livecaster.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.ui.theme.StudioDark
import pk.livecaster.app.core.di.AppContainer
import pk.livecaster.app.studio.presentation.SplitCamStudioScreen
import pk.livecaster.app.studio.presentation.SplitCamStudioViewModel

@Composable
fun LiveCasterApp(
    appContainer: AppContainer,
    modifier: Modifier = Modifier
) {
    val studioViewModel = remember {
        SplitCamStudioViewModel(appContainer)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = StudioDark
    ) {
        SplitCamStudioScreen(
            viewModel = studioViewModel
        )
    }
}
