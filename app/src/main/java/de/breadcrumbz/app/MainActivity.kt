package de.breadcrumbz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import de.breadcrumbz.app.state.BreadcrumbzViewModelFactory
import de.breadcrumbz.app.state.TripsViewModel
import de.breadcrumbz.app.ui.theme.BreadcrumbzTheme
import de.breadcrumbz.app.ui.trips.TripsScreen

class MainActivity : ComponentActivity() {

    private val container: AppContainer by lazy { (application as BreadcrumbzApp).container }

    private val viewModel: TripsViewModel by viewModels {
        BreadcrumbzViewModelFactory(container.repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BreadcrumbzTheme {
                Surface(
                    modifier = Modifier,
                    color = MaterialTheme.colorScheme.background,
                ) {
                    TripsScreen(viewModel)
                }
            }
        }
    }
}
