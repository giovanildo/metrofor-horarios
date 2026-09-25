package io.github.giova.metrofortaleza.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.giova.metrofortaleza.data.LocationSource
import io.github.giova.metrofortaleza.data.PinnedStation
import io.github.giova.metrofortaleza.data.ScheduleRepository
import io.github.giova.metrofortaleza.data.Station
import io.github.giova.metrofortaleza.data.nearestBikeTo
import io.github.giova.metrofortaleza.data.nearestTo
import io.github.giova.metrofortaleza.data.nextDepartures
import io.github.giova.metrofortaleza.data.nowMinutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TICK_MILLIS = 15_000L

@Composable
fun AppRoot() {
    val context = LocalContext.current

    // A primeira abertura copia o banco de assets; isso sai da thread principal.
    var repository by remember { mutableStateOf<ScheduleRepository?>(null) }
    LaunchedEffect(context) {
        repository = withContext(Dispatchers.IO) { ScheduleRepository(context) }
    }

    val repo = repository
    if (repo == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Guardamos apenas os ids para a navegação sobreviver à rotação de tela.
    var routeId by rememberSaveable { mutableStateOf<String?>(null) }
    var stopId by rememberSaveable { mutableStateOf<String?>(null) }

    val routes = remember(repo) { repo.routes() }
    val bikeStations = remember(repo) { repo.bikeStations() }
    val sameScheduleWarning = remember(repo) { repo.meta("same_schedule_every_day") == "1" }

    val route = routeId?.let { id -> remember(id) { repo.route(id) } }
    val stop = stopId?.let { id -> remember(id) { repo.stop(id) } }

    // ---------------------------------------------------------- tela inicial --
    val scope = rememberCoroutineScope()
    val pinned = remember(context) { PinnedStation(context) }
    val locationSource = remember(context) { LocationSource(context) }
    var homeState by remember { mutableStateOf<HomeStation>(HomeStation.Idle) }
    var now by remember { mutableIntStateOf(nowMinutes()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MILLIS)
            now = nowMinutes()
        }
    }

    suspend fun locate() {
        homeState = HomeStation.Locating
        val position = locationSource.current()
        if (position == null) {
            homeState = HomeStation.Unavailable(HomeStation.Reason.NO_FIX)
            return
        }
        val nearest = withContext(Dispatchers.IO) {
            repo.stations().nearestTo(position.latitude, position.longitude)
        }
        homeState = nearest
            ?.let { (station, distance) -> HomeStation.Nearby(station, distance) }
            ?: HomeStation.Unavailable(HomeStation.Reason.NO_FIX)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            scope.launch { locate() }
        } else {
            homeState = HomeStation.Unavailable(HomeStation.Reason.PERMISSION_DENIED)
        }
    }

    fun refreshHome() {
        // Uma estação fixada é uma escolha explícita: ela ganha do GPS.
        val chosen = pinned.get()
        if (chosen != null) {
            val station = repo.station(chosen.first, chosen.second)
            if (station != null) {
                homeState = HomeStation.Pinned(station)
                return
            }
            // A estação fixada sumiu do feed; esquecemos e seguimos.
            pinned.clear()
        }
        if (locationSource.hasPermission()) {
            scope.launch { locate() }
        } else if (homeState is HomeStation.Pinned) {
            homeState = HomeStation.Idle
        }
    }

    LaunchedEffect(repo) { refreshHome() }

    val homeDepartures = remember(homeState, now) {
        homeState.station?.let { station ->
            repo.directions(station.routeId).map { direction ->
                HomeDeparture(
                    headsign = direction.headsign,
                    departure = nextDepartures(
                        repo.departures(station.stopId, station.routeId, direction.id),
                        now,
                        count = 1,
                    ).firstOrNull(),
                )
            }
        }.orEmpty()
    }

    val homeBike = remember(homeState, bikeStations) {
        homeState.station?.let { station ->
            bikeStations.nearestBikeTo(station.lat, station.lon)
                ?.let { (bike, distance) -> NearbyBike(bike, distance) }
        }
    }

    fun openStation(station: Station) {
        routeId = station.routeId
        stopId = station.stopId
    }

    // --------------------------------------------------------------- telas ----
    when {
        route != null && stop != null -> {
            val directions = remember(route.id) { repo.directions(route.id) }
            val isPinned = pinned.get() == (stop.id to route.id)
            // O Stop da navegação não carrega coordenadas; o Station sim.
            val bike = remember(stop.id, route.id, bikeStations) {
                repo.station(stop.id, route.id)
                    ?.let { bikeStations.nearestBikeTo(it.lat, it.lon) }
                    ?.let { (station, distance) -> NearbyBike(station, distance) }
            }
            BackHandler { stopId = null }
            DeparturesScreen(
                route = route,
                stop = stop,
                directions = directions,
                departuresFor = { direction ->
                    repo.departures(stop.id, route.id, direction.id)
                },
                showSameScheduleWarning = sameScheduleWarning,
                bike = bike,
                isPinned = isPinned,
                onTogglePin = {
                    if (isPinned) pinned.clear() else pinned.set(stop.id, route.id)
                    refreshHome()
                },
                onBack = { stopId = null },
            )
        }

        route != null -> {
            // O sentido 0 define a ordem em que a lista de estações é mostrada.
            val stops = remember(route.id) { repo.stops(route.id, directionId = 0) }
            BackHandler { routeId = null }
            StopsScreen(
                route = route,
                stops = stops,
                onBack = { routeId = null },
                onStopClick = { stopId = it.id },
            )
        }

        else -> RoutesScreen(
            routes = routes,
            stationCount = { repo.stationCount(it.id) },
            onRouteClick = { routeId = it.id },
            header = {
                HomeStationCard(
                    state = homeState,
                    departures = homeDepartures,
                    bike = homeBike,
                    now = now,
                    onUseLocation = {
                        if (locationSource.hasPermission()) {
                            scope.launch { locate() }
                        } else {
                            permissionLauncher.launch(LocationSource.PERMISSIONS)
                        }
                    },
                    onOpenStation = ::openStation,
                )
            },
        )
    }
}
