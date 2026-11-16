package com.dermoai.feature.finder

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.dermoai.core.ui.components.GradientHeader
import com.dermoai.core.ui.components.MedicalDisclaimerBar
import com.dermoai.core.ui.components.NeuButton
import com.dermoai.core.ui.components.NeuSurface
import com.dermoai.core.ui.components.NeuSurfaceStyle
import com.dermoai.core.ui.theme.DermoColors
import com.dermoai.feature.finder.data.Clinic
import java.util.Locale

/**
 * Dermatologist finder: an OpenStreetMap map (Leaflet, in-app) with clinics from
 * the Overpass API shown as pins, plus a list and a detail card.
 */
@Composable
fun FinderScreen(
    modifier: Modifier = Modifier,
    viewModel: FinderViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state = viewModel.state

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onPermissionResult(granted) }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.locateAndSearch()
        // otherwise the UI shows the permission prompt
    }

    Column(modifier = modifier.fillMaxSize()) {
        GradientHeader(
            title = stringResource(R.string.finder_title),
            subtitle = stringResource(R.string.finder_subtitle),
        )
        MedicalDisclaimerBar()

        when (val s = state) {
            is FinderUiState.Idle -> PermissionPrompt(
                onRequest = { permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
            )
            is FinderUiState.NoPermission -> PermissionPrompt(
                onRequest = { permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
            )
            is FinderUiState.Locating -> CenterMessage(stringResource(R.string.finder_locating), loading = true)
            is FinderUiState.NoLocation -> CenterMessage(
                stringResource(R.string.finder_no_location),
                retry = { viewModel.locateAndSearch() },
            )
            is FinderUiState.Located -> FinderContent(
                state = s,
                selected = viewModel.selectedClinic,
                onSelect = viewModel::selectClinic,
                onSelectById = viewModel::selectClinicById,
                onClearSelection = viewModel::clearSelection,
                onRetry = { viewModel.search(s.lat, s.lon) },
            )
        }
    }
}

/**
 * Centered status message. Shows a spinner only while work is in flight
 * ([loading]); terminal states show the message plus a retry button.
 */
@Composable
private fun CenterMessage(message: String, retry: (() -> Unit)? = null, loading: Boolean = false) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (loading) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            if (retry != null) {
                Spacer(Modifier.height(16.dp))
                NeuButton(
                    onClick = retry,
                    containerColor = DermoColors.Teal,
                    contentColor = Color.White,
                ) { Text(stringResource(R.string.finder_retry)) }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.finder_permission_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.finder_permission_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            NeuButton(onClick = onRequest, containerColor = DermoColors.Teal, contentColor = Color.White) {
                Text(stringResource(R.string.finder_permission_button))
            }
        }
    }
}

@Composable
private fun FinderContent(
    state: FinderUiState.Located,
    selected: Clinic?,
    onSelect: (Clinic) -> Unit,
    onSelectById: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRetry: () -> Unit,
) {
    // Keep the map and list above the system navigation bar (this screen has no bottom bar of its own).
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            LeafletMap(
                userLat = state.lat,
                userLon = state.lon,
                clinics = state.clinics,
                selectedId = selected?.id,
                onSelect = onSelectById,
                modifier = Modifier.fillMaxSize(),
            )
            SearchStatus(
                state = state,
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.TopCenter).padding(10.dp),
            )
        }

        if (state.clinics.isNotEmpty()) {
            if (state.isBroadFallback) {
                Text(
                    text = stringResource(R.string.finder_broad_note),
                    style = MaterialTheme.typography.labelMedium,
                    color = DermoColors.AmberText,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            if (selected != null) {
                ClinicDetailCard(
                    clinic = selected,
                    userLat = state.lat,
                    userLon = state.lon,
                    onClose = onClearSelection,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.finder_select_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }

            LazyColumn(
                Modifier.fillMaxWidth().height(200.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            ) {
                items(state.clinics, key = { it.id }) { clinic ->
                    ClinicRow(
                        clinic = clinic,
                        selected = clinic.id == selected?.id,
                        onClick = { onSelect(clinic) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Small banner over the map: searching / failed (with retry) / nothing found. */
@Composable
private fun SearchStatus(state: FinderUiState.Located, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val showEmpty = !state.searching && !state.searchFailed && state.clinics.isEmpty()
    if (!state.searching && !state.searchFailed && !showEmpty) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 4.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                state.searching -> {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.finder_searching), style = MaterialTheme.typography.bodyMedium)
                }
                state.searchFailed -> {
                    Text(
                        stringResource(R.string.finder_error),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.finder_retry)) }
                }
                else -> Text(stringResource(R.string.finder_empty), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ClinicRow(clinic: Clinic, selected: Boolean, onClick: () -> Unit) {
    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        style = if (selected) NeuSurfaceStyle.Inset else NeuSurfaceStyle.Raised,
        onClick = onClick,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Place,
                contentDescription = null,
                tint = DermoColors.TealAccent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(clinic.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    clinic.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            clinic.distanceMeters?.let { dist ->
                Text(
                    text = formatDistance(dist),
                    style = MaterialTheme.typography.labelMedium,
                    color = DermoColors.TealText,
                )
            }
        }
    }
}

@Composable
private fun ClinicDetailCard(
    clinic: Clinic,
    userLat: Double,
    userLon: Double,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    NeuSurface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), style = NeuSurfaceStyle.Inset) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, null, tint = DermoColors.TealAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(clinic.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("✕") }
            }
            Text(clinic.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            clinic.distanceMeters?.let {
                Text(formatDistance(it), style = MaterialTheme.typography.labelMedium, color = DermoColors.TealText)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (clinic.phone != null) {
                    NeuButton(
                        onClick = {
                            // OSM phone tags are user-editable — sanitize to dial-safe chars.
                            val digits = clinic.phone.filter { it.isDigit() || it in "+()- " }
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")))
                            }
                        },
                        containerColor = DermoColors.Teal,
                        contentColor = Color.White,
                    ) {
                        Icon(Icons.Outlined.Phone, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.finder_call))
                    }
                }
                NeuButton(
                    onClick = { openDirections(context, clinic, userLat, userLon) },
                    containerColor = DermoColors.Teal,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.finder_directions))
                }
            }
        }
    }
}

/**
 * Opens turn-by-turn directions in the user's maps app. A phone with no maps app
 * used to crash here (startActivity throws ActivityNotFoundException), so fall
 * back to the same route on openstreetmap.org in the browser.
 */
private fun openDirections(context: Context, clinic: Clinic, userLat: Double, userLon: Double) {
    val label = Uri.encode(clinic.name)
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${clinic.lat},${clinic.lon}($label)"))
    try {
        context.startActivity(geo)
    } catch (_: ActivityNotFoundException) {
        val web = Uri.parse(
            "https://www.openstreetmap.org/directions?route=" +
                "${userLat.fmt()}%2C${userLon.fmt()}%3B${clinic.lat.fmt()}%2C${clinic.lon.fmt()}"
        )
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, web)) }
    }
}

private fun Double.fmt(): String = String.format(Locale.US, "%.6f", this)

private fun formatDistance(meters: Double): String = when {
    meters >= 1000 -> String.format(Locale.US, "%.1f km", meters / 1000)
    else -> String.format(Locale.US, "%.0f m", meters)
}
