package io.github.giova.metrofortaleza.ui

import android.Manifest
import android.os.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import io.github.giova.metrofortaleza.data.distanceMeters
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.giova.metrofortaleza.R
import io.github.giova.metrofortaleza.data.Direction
import io.github.giova.metrofortaleza.data.Stop
import io.github.giova.metrofortaleza.data.TripPlan
import io.github.giova.metrofortaleza.data.TripStop
import io.github.giova.metrofortaleza.trip.TripTracker
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import io.github.giova.metrofortaleza.data.BIKE_NEAR_YOU_MAX_METERS
import io.github.giova.metrofortaleza.data.LocationSource
import io.github.giova.metrofortaleza.data.MetroforDatabase
import io.github.giova.metrofortaleza.data.NewsCache
import io.github.giova.metrofortaleza.data.NewsFeed
import io.github.giova.metrofortaleza.data.PinnedStation
import io.github.giova.metrofortaleza.data.ScheduleRepository
import io.github.giova.metrofortaleza.data.ScheduleSource
import io.github.giova.metrofortaleza.data.ScheduleStore
import io.github.giova.metrofortaleza.data.Station
import io.github.giova.metrofortaleza.data.nearestBikeTo
import io.github.giova.metrofortaleza.data.NEARBY_LINES_MAX_METERS
import io.github.giova.metrofortaleza.data.nearestPerRoute
import io.github.giova.metrofortaleza.data.nextDepartures
import io.github.giova.metrofortaleza.data.nowMinutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TICK_MILLIS = 15_000L

/** GPS da tela inicial: a cada 10 s, ou ao andar 30 m. */
private const val HOME_GPS_INTERVAL_MILLIS = 10_000L
private const val HOME_GPS_MIN_METERS = 30f

/** Modo viagem: aviso acima de 1 km da estação de partida; acima de 3 km, some. */
private const val TRIP_WARN_START_METERS = 1_000.0
private const val TRIP_MAX_START_METERS = 3_000.0
private const val USER_POSITION_MAX_AGE_MILLIS = 10L * 60 * 1000

private fun formatKm(meters: Double): String =
    if (meters < 1000) "${meters.toInt()} m" else "%.1f km".format(meters / 1000).replace('.', ',')

/** Quem inicia a viagem já dentro do trem ainda pega a partida de até 2 min atrás. */
private const val TRIP_LATE_BOARDING_MINUTES = 2

/** Manchetes mais velhas que isso são baixadas de novo ao abrir o app. */
private const val NEWS_STALE_MILLIS = 3L * 60 * 60 * 1000

@Composable
fun AppRoot() {
    val context = LocalContext.current

    // O GTFS do Metrofor traz a grade do dia; baixamos uma vez por dia e o
    // repositório é recriado quando chega grade nova ou o dia vira.
    var today by remember { mutableStateOf(ScheduleStore.today()) }
    var scheduleVersion by remember { mutableIntStateOf(0) }
    var scheduleSyncing by remember { mutableStateOf(false) }
    var scheduleSyncFailed by remember { mutableStateOf(false) }
    var scheduleRequest by remember { mutableIntStateOf(0) }

    LaunchedEffect(today, scheduleRequest) {
        if (scheduleRequest == 0 && !ScheduleStore.needsSync(context)) return@LaunchedEffect
        scheduleSyncing = true
        val result = withContext(Dispatchers.IO) {
            runCatching { ScheduleStore.sync(context, MetroforDatabase.bundledFile(context)) }
        }
        scheduleSyncFailed = result.isFailure
        scheduleSyncing = false
        if (result.isSuccess) scheduleVersion++
    }

    // A primeira abertura copia o banco de assets; isso sai da thread principal.
    var repository by remember { mutableStateOf<ScheduleRepository?>(null) }
    LaunchedEffect(context, today, scheduleVersion) {
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
            today = ScheduleStore.today()
        }
    }

    // A estação mais próxima em destaque e, a até 3 km, a mais próxima de cada
    // outra linha — cada uma ganha seu cartão.
    fun nearbyState(stations: List<Station>, lat: Double, lon: Double): HomeStation.Nearby? {
        val perRoute = stations.nearestPerRoute(lat, lon)
        val (main, distance) = perRoute.firstOrNull() ?: return null
        val others = perRoute.drop(1).filter { it.second <= NEARBY_LINES_MAX_METERS }
        return HomeStation.Nearby(main, distance, lat, lon, others)
    }

    suspend fun locate() {
        homeState = HomeStation.Locating
        val position = locationSource.current()
        if (position == null) {
            homeState = HomeStation.Unavailable(HomeStation.Reason.NO_FIX)
            return
        }
        val stations = withContext(Dispatchers.IO) { repo.stations() }
        homeState = nearbyState(stations, position.latitude, position.longitude)
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

    // GPS contínuo enquanto a tela está visível e a estação veio do GPS: ao
    // descer em outra estação, o destaque acompanha. Estação fixada ganha do
    // GPS, então aí não seguimos nada.
    val lifecycleOwner = LocalLifecycleOwner.current
    var visible by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> visible = true
                Lifecycle.Event.ON_STOP -> visible = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val followGps = visible && homeState is HomeStation.Nearby
    LaunchedEffect(followGps, repo) {
        if (!followGps) return@LaunchedEffect
        val stations = withContext(Dispatchers.IO) { repo.stations() }
        locationSource.updates(HOME_GPS_INTERVAL_MILLIS, HOME_GPS_MIN_METERS).collect { position ->
            nearbyState(stations, position.latitude, position.longitude)?.let { homeState = it }
        }
    }

    // ------------------------------------------------------------ viagem ----
    // Onde a pessoa está: a posição do GPS da tela inicial, ou, com estação
    // fixada, a última guardada no aparelho. `null` = não sabemos.
    fun userDistanceTo(lat: Double, lon: Double): Double? {
        val (userLat, userLon) = when (val state = homeState) {
            is HomeStation.Nearby -> state.userLat to state.userLon
            else -> locationSource.lastKnown(USER_POSITION_MAX_AGE_MILLIS)
                ?.let { it.latitude to it.longitude }
        } ?: return null
        return distanceMeters(userLat, userLon, lat, lon)
    }

    /** Longe demais (ou posição desconhecida = deixa tentar). */
    fun tripAllowedFrom(lat: Double, lon: Double): Boolean =
        (userDistanceTo(lat, lon) ?: 0.0) <= TRIP_MAX_START_METERS

    var farTrip by remember { mutableStateOf<Pair<TripPlan, Double>?>(null) }

    val activeTrip by TripTracker.active.collectAsState()
    val tripSound by TripTracker.soundEnabled(context).collectAsState()
    val tripStationAlerts by TripTracker.stationAlerts(context).collectAsState()
    var pendingTrip by remember { mutableStateOf<TripPlan?>(null) }
    var tripMessage by remember { mutableStateOf<String?>(null) }
    val tripPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val plan = pendingTrip
        pendingTrip = null
        // Notificação negada não impede a viagem: o card na tela continua valendo.
        if (plan != null && locationSource.hasPermission()) {
            TripTracker.start(context, plan)
            stopId = null
            routeId = null
        } else if (plan != null) {
            tripMessage = context.getString(R.string.trip_needs_location)
        }
    }

    fun launchTrip(plan: TripPlan) {
        val needed = buildList {
            addAll(LocationSource.PERMISSIONS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        pendingTrip = plan
        tripPermissionLauncher.launch(needed.toTypedArray())
    }

    fun startTrip(routeId: String, direction: Direction, origin: Stop, destination: Stop) {
        val route = repo.route(routeId) ?: return
        val trip = repo.nextTrip(origin.id, routeId, direction.id, nowMinutes() - TRIP_LATE_BOARDING_MINUTES)
        if (trip == null) {
            tripMessage = context.getString(R.string.trip_no_trip_today)
            return
        }
        val (_, times) = trip
        val coords = repo.stations().filter { it.routeId == routeId }.associateBy { it.stopId }
        val path = repo.stops(routeId, direction.id)
            .dropWhile { it.id != origin.id }
            .let { list -> list.take(list.indexOfFirst { it.id == destination.id } + 1) }
        val stops = path.mapNotNull { stop ->
            val station = coords[stop.id] ?: return@mapNotNull null
            val time = times[stop.id] ?: return@mapNotNull null
            TripStop(stop.id, stop.name, station.lat, station.lon, time)
        }
        if (stops.size < 2) {
            tripMessage = context.getString(R.string.trip_no_trip_today)
            return
        }
        val plan = TripPlan(routeId, route.name, direction.headsign, stops)
        val distance = userDistanceTo(plan.origin.lat, plan.origin.lon)
        when {
            distance != null && distance > TRIP_MAX_START_METERS -> tripMessage = context.getString(
                R.string.trip_too_far, formatKm(distance), plan.origin.name,
            )
            distance != null && distance > TRIP_WARN_START_METERS -> farTrip = plan to distance
            else -> launchTrip(plan)
        }
    }

    // Outras linhas a até 3 km (cada uma ganha cartão próprio na tela inicial).
    val otherLines = (homeState as? HomeStation.Nearby)?.others.orEmpty()

    // Modo viagem a partir do topo da tela inicial: origens = estações em
    // destaque (uma por linha, a até 3 km), destinos nos dois sentidos.
    var pickingHomeDestination by remember { mutableStateOf(false) }
    val tripOrigins = buildList {
        homeState.station?.let(::add)
        otherLines.forEach { add(it.first) }
    }.filter { tripAllowedFrom(it.lat, it.lon) }
    if (pickingHomeDestination && tripOrigins.isNotEmpty()) {
        val groups = remember(repo, tripOrigins) {
            tripOrigins.flatMap { origin ->
                repo.directions(origin.routeId).map { direction ->
                    DestinationGroup(
                        title = "${origin.routeName} · ${origin.stopName} → ${direction.headsign}",
                        routeId = origin.routeId,
                        origin = Stop(origin.stopId, origin.stopName, seq = 0),
                        direction = direction,
                        stops = repo.stops(origin.routeId, direction.id)
                            .dropWhile { it.id != origin.stopId }
                            .drop(1),
                    )
                }
            }
        }
        DestinationDialog(
            groups = groups,
            onPick = { group, destination ->
                pickingHomeDestination = false
                startTrip(group.routeId, group.direction, group.origin, destination)
            },
            onDismiss = { pickingHomeDestination = false },
        )
    }

    farTrip?.let { (plan, distance) ->
        AlertDialog(
            onDismissRequest = { farTrip = null },
            title = { Text(stringResource(R.string.trip_far_title)) },
            text = { Text(stringResource(R.string.trip_far_warning, formatKm(distance), plan.origin.name)) },
            confirmButton = {
                TextButton(onClick = {
                    farTrip = null
                    launchTrip(plan)
                }) { Text(stringResource(R.string.trip_far_start)) }
            },
            dismissButton = {
                TextButton(onClick = { farTrip = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    tripMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { tripMessage = null },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { tripMessage = null }) { Text("OK") } },
        )
    }

    fun departuresAt(station: Station): List<HomeDeparture> =
        repo.directions(station.routeId).map { direction ->
            HomeDeparture(
                headsign = direction.headsign,
                departure = nextDepartures(
                    repo.departures(station.stopId, station.routeId, direction.id),
                    now,
                    count = 1,
                    tomorrow = repo.departuresTomorrow(station.stopId, station.routeId, direction.id),
                ).firstOrNull(),
            )
        }

    val homeDepartures = remember(repo, homeState, now) {
        homeState.station?.let(::departuresAt).orEmpty()
    }
    val otherDepartures = remember(repo, homeState, now) {
        otherLines.map { (station, _) -> departuresAt(station) }
    }

    // Com GPS, o Bicicletar mais perto da pessoa; com estação fixada, o da estação.
    val homeBike = remember(homeState, bikeStations) {
        when (val state = homeState) {
            is HomeStation.Nearby -> bikeStations
                .nearestBikeTo(state.userLat, state.userLon, BIKE_NEAR_YOU_MAX_METERS)
                ?.let { (bike, distance) -> NearbyBike(bike, distance) }
                ?.let { HomeBike(it, fromYou = true) }

            is HomeStation.Pinned -> bikeStations
                .nearestBikeTo(state.station.lat, state.station.lon)
                ?.let { (bike, distance) -> NearbyBike(bike, distance) }
                ?.let { HomeBike(it, fromYou = false) }

            else -> null
        }
    }

    // ------------------------------------------------------------ notícias --
    val newsCache = remember(context) { NewsCache(context) }
    var headlines by remember { mutableStateOf(newsCache.load()) }
    var newsSyncedAt by remember { mutableStateOf(newsCache.syncedAt) }
    var newsSyncing by remember { mutableStateOf(false) }

    fun syncNews() {
        if (newsSyncing) return
        newsSyncing = true
        scope.launch {
            // Sem rede ou com o feed fora do ar, fica a lista antiga.
            withContext(Dispatchers.IO) { runCatching { NewsFeed.fetch() } }
                .onSuccess {
                    newsCache.save(it)
                    headlines = it
                    newsSyncedAt = newsCache.syncedAt
                }
            newsSyncing = false
        }
    }

    LaunchedEffect(Unit) {
        if (System.currentTimeMillis() - newsCache.syncedAt > NEWS_STALE_MILLIS) syncNews()
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
                tomorrowFor = { direction ->
                    repo.departuresTomorrow(stop.id, route.id, direction.id)
                },
                destinationsFor = { direction ->
                    repo.stops(route.id, direction.id).dropWhile { it.id != stop.id }.drop(1)
                },
                onStartTrip = { direction, destination -> startTrip(route.id, direction, stop, destination) },
                tripAllowed = remember(stop.id, route.id, homeState) {
                    repo.station(stop.id, route.id)?.let { tripAllowedFrom(it.lat, it.lon) } ?: true
                },
                schedule = repo.source,
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val trip = activeTrip
                    if (trip != null) {
                        TripCard(
                            trip = trip,
                            soundEnabled = tripSound ?: true,
                            onToggleSound = { TripTracker.setSoundEnabled(context, it) },
                            stationAlerts = tripStationAlerts ?: false,
                            onToggleStationAlerts = { TripTracker.setStationAlerts(context, it) },
                            onStop = { TripTracker.stop(context) },
                        )
                    } else {
                        // A mais de 3 km de qualquer estação, o modo viagem não aparece.
                        if (homeState.station == null || tripOrigins.isNotEmpty()) {
                            TripStartCard(
                                origins = tripOrigins,
                                onStart = { pickingHomeDestination = true },
                            )
                        }
                    }
                    ScheduleStatus(
                        source = repo.source,
                        syncing = scheduleSyncing,
                        failed = scheduleSyncFailed,
                        onRefresh = { scheduleRequest++ },
                    )
                    // Durante a viagem, o cartão dela já diz onde a pessoa está.
                    if (trip == null) {
                        HomeStationCard(
                            state = homeState,
                            departures = homeDepartures,
                            scheduleIsToday = repo.source.kind == ScheduleSource.Kind.TODAY,
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
                        otherLines.forEachIndexed { i, (station, distance) ->
                            val nearby = homeState as HomeStation.Nearby
                            HomeStationCard(
                                state = HomeStation.Nearby(station, distance, nearby.userLat, nearby.userLon),
                                departures = otherDepartures.getOrElse(i) { emptyList() },
                                scheduleIsToday = repo.source.kind == ScheduleSource.Kind.TODAY,
                                now = now,
                                onUseLocation = {},
                                onOpenStation = ::openStation,
                                showRefresh = false,
                            )
                        }
                    }
                    homeBike?.let { HomeBikeCard(it) }
                    NewsCard(
                        headlines = headlines,
                        syncedAt = newsSyncedAt,
                        syncing = newsSyncing,
                        onRefresh = ::syncNews,
                    )
                }
            },
        )
    }
}
