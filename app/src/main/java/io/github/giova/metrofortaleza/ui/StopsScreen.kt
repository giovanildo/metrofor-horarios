package io.github.giova.metrofortaleza.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.giova.metrofortaleza.R
import io.github.giova.metrofortaleza.data.Route
import io.github.giova.metrofortaleza.data.Stop
import io.github.giova.metrofortaleza.ui.theme.routeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopsScreen(
    route: Route,
    stops: List<Stop>,
    onBack: () -> Unit,
    onStopClick: (Stop) -> Unit,
) {
    val color = routeColor(route.colorHex)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(route.name)
                        Text(
                            text = route.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            itemsIndexed(stops, key = { _, stop -> stop.id }) { index, stop ->
                StationRow(
                    name = stop.name,
                    color = color,
                    isFirst = index == 0,
                    isLast = index == stops.lastIndex,
                    onClick = { onStopClick(stop) },
                )
            }
        }
    }
}

/** Uma estação com o trecho de trilho que a liga à anterior e à seguinte. */
@Composable
private fun StationRow(
    name: String,
    color: Color,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // IntrinsicSize.Min da altura ao Row, e e isso que deixa o trilho
            // esticar de ponta a ponta: sem essa altura o fillMaxHeight abaixo
            // nao tem contra o que medir e a linha vira um traco solto.
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Rail(color = color, isFirst = isFirst, isLast = isLast)
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 18.dp),
        )
    }
}

@Composable
private fun Rail(color: Color, isFirst: Boolean, isLast: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // As duas metades existem para poder sumir com a de cima na primeira
            // estação e com a de baixo na última.
            Spacer(
                Modifier
                    .width(3.dp)
                    .weight(1f)
                    .background(if (isFirst) Color.Transparent else color),
            )
            Spacer(
                Modifier
                    .width(3.dp)
                    .weight(1f)
                    .background(if (isLast) Color.Transparent else color),
            )
        }
        Spacer(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}
