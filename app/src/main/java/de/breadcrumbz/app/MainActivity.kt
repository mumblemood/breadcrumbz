package de.breadcrumbz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import de.breadcrumbz.app.state.BreadcrumbzViewModelFactory
import de.breadcrumbz.app.state.TripMapViewModel
import de.breadcrumbz.app.state.TripsViewModel
import de.breadcrumbz.app.ui.theme.BreadcrumbzTheme
import de.breadcrumbz.app.ui.trips.TripMapScreen
import de.breadcrumbz.app.ui.trips.TripsScreen

class MainActivity : ComponentActivity() {

    private val container: AppContainer by lazy { (application as BreadcrumbzApp).container }

    private val tripsViewModel: TripsViewModel by viewModels {
        BreadcrumbzViewModelFactory(
            container.repository,
            container.photoStore,
            container.placeNameResolver,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BreadcrumbzTheme {
                Surface(
                    modifier = Modifier,
                    color = MaterialTheme.colorScheme.background,
                ) {
                    LaunchedEffect(Unit) { container.refreshPlaceNamesOnce() }
                    var openTripId by rememberSaveable { mutableStateOf<String?>(null) }

                    val tripId = openTripId
                    if (tripId == null) {
                        TripsScreen(
                            viewModel = tripsViewModel,
                            backupManager = container.backupManager,
                            onOpenTrip = { openTripId = it },
                            onRefreshPlaces = { container.refreshPlaceNamesNow() },
                        )
                    } else {
                        val mapFactory = remember(tripId) {
                            BreadcrumbzViewModelFactory(
                                container.repository,
                                container.photoStore,
                                container.placeNameResolver,
                                tripId,
                            )
                        }
                        val mapViewModel: TripMapViewModel = viewModel(
                            key = tripId,
                            factory = mapFactory,
                        )
                        TripMapScreen(
                            viewModel = mapViewModel,
                            onBack = { openTripId = null },
                        )
                    }
                }
            }
        }
    }
}
