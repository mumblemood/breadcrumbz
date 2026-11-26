package de.breadcrumbz.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import de.breadcrumbz.app.state.BreadcrumbzViewModelFactory
import de.breadcrumbz.app.state.SharedPhotosViewModel
import de.breadcrumbz.app.state.TripMapViewModel
import de.breadcrumbz.app.state.TripsViewModel
import de.breadcrumbz.app.ui.theme.BreadcrumbzTheme
import de.breadcrumbz.app.ui.trips.SharedPhotosDialog
import de.breadcrumbz.app.ui.trips.TripMapScreen
import de.breadcrumbz.app.ui.trips.TripsScreen

class MainActivity : ComponentActivity() {

    private val container: AppContainer by lazy { (application as BreadcrumbzApp).container }

    private val tripsViewModel: TripsViewModel by viewModels {
        BreadcrumbzViewModelFactory(
            container.repository,
            container.photoStore,
            container.placeNameResolver,
            container.settingsStore,
        )
    }

    private val sharedPhotos: SharedPhotosViewModel by viewModels()

    /** Geteilte Bilder, die auf die Antwort zur Foto-Erlaubnis warten. */
    private var urisAwaitingPermission: List<Uri> = emptyList()

    // Mit Galerie-Erlaubnis kommen wir bei Fotos vom Handy an das Original
    // samt GPS. Ohne Erlaubnis lesen wir trotzdem, nur evtl. ohne Ort.
    private val sharePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val uris = urisAwaitingPermission
        urisAwaitingPermission = emptyList()
        sharedPhotos.receive(this, uris)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    /** Fotos aus Google Fotos & Co.: „Teilen“ → Breadcrumbz. */
    private fun handleShare(intent: Intent?) {
        val uris = sharedUris(intent ?: return)
        if (uris.isEmpty()) return
        val missing = galleryPermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            sharedPhotos.receive(this, uris)
        } else {
            urisAwaitingPermission = uris
            sharePermissions.launch(missing.toTypedArray())
        }
    }

    private fun sharedUris(intent: Intent): List<Uri> {
        val found = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(
                intent, Intent.EXTRA_STREAM, Uri::class.java,
            )?.let { found.add(it) }
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(
                intent, Intent.EXTRA_STREAM, Uri::class.java,
            )?.let { found.addAll(it) }
            else -> return emptyList()
        }
        // Manche Apps legen die Bilder nur in ClipData ab.
        intent.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { found.add(it) }
        }
        return found.distinct()
    }

    private fun galleryPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Nach dem Drehen nicht noch einmal einlesen.
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            BreadcrumbzTheme {
                Surface(
                    modifier = Modifier,
                    color = MaterialTheme.colorScheme.background,
                ) {
                    LaunchedEffect(Unit) { container.refreshPlaceNamesOnce() }
                    var openTripId by rememberSaveable { mutableStateOf<String?>(null) }

                    val shareState by sharedPhotos.state.collectAsState()
                    val delivery by sharedPhotos.delivery.collectAsState()
                    shareState?.let { state ->
                        val overviews by tripsViewModel.overviews.collectAsState()
                        SharedPhotosDialog(
                            state = state,
                            trips = overviews,
                            onPickTrip = { id ->
                                sharedPhotos.deliverTo(id)
                                openTripId = id
                            },
                            onNewTrip = {
                                tripsViewModel.createTrip { id ->
                                    sharedPhotos.deliverTo(id)
                                    openTripId = id
                                }
                            },
                            onDismiss = { sharedPhotos.clear() },
                        )
                    }

                    val tripId = openTripId
                    if (tripId == null) {
                        TripsScreen(
                            viewModel = tripsViewModel,
                            backupManager = container.backupManager,
                            shareManager = container.shareManager,
                            onOpenTrip = { openTripId = it },
                            onRefreshPlaces = { container.refreshPlaceNamesNow() },
                            settingsStore = container.settingsStore,
                        )
                    } else {
                        val mapFactory = remember(tripId) {
                            BreadcrumbzViewModelFactory(
                                container.repository,
                                container.photoStore,
                                container.placeNameResolver,
                                container.settingsStore,
                                tripId,
                            )
                        }
                        val mapViewModel: TripMapViewModel = viewModel(
                            key = tripId,
                            factory = mapFactory,
                        )
                        TripMapScreen(
                            viewModel = mapViewModel,
                            routingService = container.routingService,
                            shareManager = container.shareManager,
                            settingsStore = container.settingsStore,
                            onBack = { openTripId = null },
                            incomingPhotos = delivery?.takeIf { it.tripId == tripId }?.photos,
                            onIncomingPhotosHandled = { sharedPhotos.delivered() },
                        )
                    }
                }
            }
        }
    }
}
