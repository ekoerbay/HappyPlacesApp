package com.example.happyplacesapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp



import org.osmdroid.api.IMapController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.example.happyplacesapp.ui.theme.HappyPlacesAppTheme
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import androidx.compose.foundation.Image
import coil.compose.rememberAsyncImagePainter
import org.osmdroid.views.overlay.ItemizedIconOverlay
import org.osmdroid.views.overlay.ItemizedOverlayWithFocus
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.OverlayItem
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box



// ViewModel hält hier alle Marker-Daten und Methoden, um Marker hinzuzufügen und zu bearbeiten.
// Vorteil: ViewModel bleibt am Leben, wenn UI neu erstellt wird (z.B. bei Bildschirmdrehung).
// So gehen keine Marker verloren und der Code ist sauberer getrennt.
// MarkerData: Hält Marker-Objekt und Bildpfad
data class MarkerData(val item: OverlayItem, val imageUri: String)

class MapViewModel : ViewModel() {
    var items = mutableStateListOf<MarkerData>()
        private set

    fun addItem(item: MarkerData) {
        items.add(item)
    }

    fun updateItem(oldItem: MarkerData, newItem: MarkerData) {
        val index = items.indexOf(oldItem)
        if (index != -1) {
            items[index] = newItem
        }
    }
}





class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val internetPermission = Manifest.permission.INTERNET
        if (ContextCompat.checkSelfPermission(this, internetPermission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(internetPermission), 0)
        }

        val locationPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (locationPermissions.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }) {
            ActivityCompat.requestPermissions(this, locationPermissions, 1)
        }

        Configuration.getInstance().load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
        Configuration.getInstance().userAgentValue = "HappyPlacesApp"

        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val viewModel: MapViewModel = viewModel()
                OsmdroidMapView(viewModel)
            }
        }
    }
}

@Composable
fun OsmdroidMapView(viewModel: MapViewModel) {
    val context = LocalContext.current
    val geoPointState = remember { mutableStateOf<GeoPoint?>(null) }
    val openDialog = remember { mutableStateOf(false) }
    val openEditDialog = remember { mutableStateOf(false) }
    val openListDialog = remember { mutableStateOf(false) } // NEU

    val selectedOverlayItem = remember { mutableStateOf<OverlayItem?>(null) }
    val editTitle = remember { mutableStateOf("") }
    val editDescription = remember { mutableStateOf("") }
    val selectedImageUri = remember { mutableStateOf("") }

    val mapViewRef = remember { mutableStateOf<MapView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val mapView = MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(true)
                    controller.setZoom(15.0)
                    controller.setCenter(GeoPoint(52.5200, 13.4050)) // Berlin
                    minZoomLevel = 4.0

                    val rotationGestureOverlay = RotationGestureOverlay(this)
                    rotationGestureOverlay.isEnabled = true
                    overlays.add(rotationGestureOverlay)

                    val compassOverlay = CompassOverlay(ctx, InternalCompassOrientationProvider(ctx), this)
                    compassOverlay.enableCompass()
                    overlays.add(compassOverlay)

                    viewModel.items.forEach { data ->
                        val overlay = createOverlay(
                            context, data, viewModel, this, controller,
                            selectedOverlayItem, editTitle, editDescription,
                            selectedImageUri, openEditDialog
                        )
                        overlays.add(overlay)
                    }

                    val mapEventsReceiver = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?) = false
                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            geoPointState.value = p
                            openDialog.value = true
                            return true
                        }
                    }
                    val mapEventsOverlay = MapEventsOverlay(mapEventsReceiver)
                    overlays.add(mapEventsOverlay)
                }

                mapViewRef.value = mapView
                mapView
            }
        )
        Button(
            onClick = { openListDialog.value = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text("Alle Orte")
        }

        // 👇 Neuer Button zum Zentrieren auf aktuellen Standort
        Button(
            onClick = {
                val locationManager =
                    context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val provider = LocationManager.GPS_PROVIDER

                if (
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    locationManager.requestSingleUpdate(provider, object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            val geoPoint = GeoPoint(location.latitude, location.longitude)
                            mapViewRef.value?.controller?.setCenter(geoPoint)
                        }

                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }, Looper.getMainLooper())
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Text("Mein Standort")
        }
    }



    // Tutorial-Overlay: Nur anzeigen, wenn noch kein Pin gesetzt wurde
    if (viewModel.items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 100.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
            ) {
                Text(
                    "Tipp: Halte einen Ort auf der Karte lange gedrückt, um ihn als Pin hinzuzufügen.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }


    if (openListDialog.value) {
        AlertDialog(
            onDismissRequest = { openListDialog.value = false },
            title = { Text("Gesetzte Orte") },
            text = {
                Column {
                    if (viewModel.items.isEmpty()) {
                        Text("Keine Orte vorhanden.")
                    } else {
                        viewModel.items.forEach { marker ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(marker.item.title, style = MaterialTheme.typography.titleMedium)
                                Text(marker.item.snippet, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { openListDialog.value = false }) {
                    Text("Schließen")
                }
            }
        )
    }








    // Neuen Marker hinzufügen
    if (openDialog.value) {
        AlertDialogExample(
            onDismissRequest = { openDialog.value = false },
            onConfirmation = { title, description, imageUri ->
                geoPointState.value?.let { point ->
                    val overlayItem = OverlayItem(title, description, point)
                    val markerData = MarkerData(overlayItem, imageUri)
                    viewModel.addItem(markerData)

                    mapViewRef.value?.let { map ->
                        val overlay = createOverlay(
                            context, markerData, viewModel, map, map.controller,
                            selectedOverlayItem, editTitle, editDescription,
                            selectedImageUri, openEditDialog
                        )
                        map.overlays.add(overlay)
                        map.invalidate()
                    }
                }
                openDialog.value = false
            }
        )
    }

    // Marker bearbeiten
    if (openEditDialog.value && selectedOverlayItem.value != null) {
        AlertDialog(
            onDismissRequest = { openEditDialog.value = false },
            title = { Text("Edit Marker") },
            text = {
                Column {
                    TextField(value = editTitle.value, onValueChange = { editTitle.value = it }, label = { Text("Title") })
                    TextField(value = editDescription.value, onValueChange = { editDescription.value = it }, label = { Text("Description") })
                    if (selectedImageUri.value.isNotBlank()) {
                        Image(
                            painter = rememberAsyncImagePainter(selectedImageUri.value),
                            contentDescription = "Marker-Bild",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val oldItem = selectedOverlayItem.value
                    if (oldItem != null) {
                        val newItem = OverlayItem(editTitle.value, editDescription.value, oldItem.point)
                        val newMarkerData = MarkerData(newItem, selectedImageUri.value)
                        val oldMarkerData = viewModel.items.find { it.item == oldItem }
                        if (oldMarkerData != null) {
                            viewModel.updateItem(oldMarkerData, newMarkerData)

                            mapViewRef.value?.let { map ->
                                map.overlays.removeAll { it is ItemizedOverlayWithFocus<*> }
                                viewModel.items.forEach { data ->
                                    val overlay = createOverlay(
                                        context, data, viewModel, map, map.controller,
                                        selectedOverlayItem, editTitle, editDescription,
                                        selectedImageUri, openEditDialog
                                    )
                                    map.overlays.add(overlay)
                                }
                                map.invalidate()
                            }
                        }
                    }
                    openEditDialog.value = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { openEditDialog.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


fun createOverlay(
    context: Context,
    data: MarkerData,
    viewModel: MapViewModel,
    map: MapView,
    mapController: IMapController,
    selectedOverlayItem: MutableState<OverlayItem?>,
    editTitle: MutableState<String>,
    editDescription: MutableState<String>,
    selectedImageUri: MutableState<String>,
    openEditDialog: MutableState<Boolean>
): ItemizedOverlayWithFocus<OverlayItem> {
    val overlayItem = data.item
    val overlay = ItemizedOverlayWithFocus(
        context,
        mutableListOf(overlayItem),
        object : ItemizedIconOverlay.OnItemGestureListener<OverlayItem> {
            override fun onItemSingleTapUp(index: Int, item: OverlayItem?) = true
            override fun onItemLongPress(index: Int, item: OverlayItem?): Boolean {
                item?.let {
                    val matched = viewModel.items.find { it.item == item }
                    if (matched != null) {
                        selectedOverlayItem.value = it
                        editTitle.value = it.title
                        editDescription.value = it.snippet
                        selectedImageUri.value = matched.imageUri
                        openEditDialog.value = true
                    }
                }
                return true
            }
        }
    )
    overlay.setFocusItemsOnTap(true)
    return overlay
}

@Composable
fun AlertDialogExample(
    onDismissRequest: () -> Unit,
    onConfirmation: (String, String, String) -> Unit,
) {
    val titleName = remember { mutableStateOf("") }
    val description = remember { mutableStateOf("") }
    val imageUri = remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        imageUri.value = uri?.toString() ?: ""
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Add Place") },
        text = {
            Column {
                TextField(value = titleName.value, onValueChange = { titleName.value = it }, placeholder = { Text("Place Name") }, modifier = Modifier.fillMaxWidth())
                TextField(value = description.value, onValueChange = { description.value = it }, placeholder = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { imagePickerLauncher.launch("image/*") }) { Text("Bild auswählen") }
                if (imageUri.value.isNotBlank()) {
                    Image(painter = rememberAsyncImagePainter(imageUri.value), contentDescription = "Ausgewähltes Bild", modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirmation(titleName.value, description.value, imageUri.value)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text("Cancel") } }
    )
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    HappyPlacesAppTheme {}
}
