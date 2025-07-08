package com.example.happyplacesapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
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

// ViewModel hält hier alle Marker-Daten und Methoden, um Marker hinzuzufügen und zu bearbeiten.
// Vorteil: ViewModel bleibt am Leben, wenn UI neu erstellt wird (z.B. bei Bildschirmdrehung).
// So gehen keine Marker verloren und der Code ist sauberer getrennt.
class MapViewModel : ViewModel() {
    val items: SnapshotStateList<OverlayItem> = mutableStateListOf()

    fun addItem(item: OverlayItem) {
        items.add(item)
    }

    fun updateItem(oldItem: OverlayItem, newItem: OverlayItem) {
        val index = items.indexOf(oldItem)
        if (index != -1) {
            items[index] = newItem
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {

        //fragt nach Erlaubnis für Internet
        val internetPermission = Manifest.permission.INTERNET
        if (ContextCompat.checkSelfPermission(this, internetPermission) !=
            PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(internetPermission), 0)
        }

        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                // ViewModel hier mit rein, damit es beim UI-Aufbau geholt wird
                val viewModel: MapViewModel = viewModel()
                OsmdroidMapView(viewModel)
            }
        }
        Configuration.getInstance().load(applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext))
        Configuration.getInstance().userAgentValue = "HappyPlacesApp"
    }
}

@Composable
fun OsmdroidMapView(viewModel: MapViewModel) {
    val context = LocalContext.current

    val openAlertDialog = remember { mutableStateOf(false) }
    val geoPointState = remember { mutableStateOf<GeoPoint?>(null)}

    // wir greifen jetzt auf die Marker aus dem ViewModel zu, die Liste ist dort gespeichert
    val items = viewModel.items

    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val overlayRef = remember { mutableStateOf<ItemizedOverlayWithFocus<OverlayItem>?>(null) }

    // Marker, der gerade ausgewählt wird, um ihn zu bearbeiten
    val selectedOverlayItem = remember { mutableStateOf<OverlayItem?>(null) }
    val editTitle = remember { mutableStateOf("") }
    val editDescription = remember { mutableStateOf("") }
    val openEditDialog = remember { mutableStateOf(false) }

    // Funktion die die Marker im Bezug zur Karte/zum context verwaltet
    fun createOverlay(
        items: ArrayList<OverlayItem>,
        context: Context
    ): ItemizedOverlayWithFocus<OverlayItem> {
        return ItemizedOverlayWithFocus(
            items,
            object : ItemizedIconOverlay.OnItemGestureListener<OverlayItem> {
                override fun onItemSingleTapUp(index: Int, item: OverlayItem?): Boolean {
                    return false
                }
                override fun onItemLongPress(index: Int, item: OverlayItem?): Boolean {
                    item?.let {
                        selectedOverlayItem.value = it
                        editTitle.value = it.title
                        editDescription.value = it.snippet
                        openEditDialog.value = true
                    }
                    return true
                }
            }, context
        ).apply {
            // wenn Marker kurz angeklickt werden, dann werden sie fokussiert
            setFocusItemsOnTap(true)
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val mapView = MapView(ctx)
            mapViewRef.value = mapView

            // Steuerungselemente und Standardmap mit MAPNIK
            mapView.setTileSource(TileSourceFactory.MAPNIK)
            mapView.setMultiTouchControls(true)
            mapView.setBuiltInZoomControls(true)
            mapView.controller.setZoom(15.0)
            mapView.controller.setCenter(GeoPoint(52.5200, 13.4050)) // Berlin
            mapView.minZoomLevel = 4.0

            val rotationGestureOverlay = RotationGestureOverlay(mapView)
            rotationGestureOverlay.isEnabled = true
            mapView.overlays.add(rotationGestureOverlay)

            val compassOverlay =
                CompassOverlay(ctx, InternalCompassOrientationProvider(ctx), mapView)
            compassOverlay.enableCompass()
            mapView.overlays.add(compassOverlay)

            // Overlay mit aktuellen Items aus dem ViewModel erstellen
            val overlay = createOverlay(ArrayList(items), ctx)
            overlayRef.value = overlay
            mapView.overlays.add(overlay)

            val mapEventsReceiver = object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
                override fun longPressHelper(p: GeoPoint?): Boolean {
                    p?.let {
                        geoPointState.value = it
                        openAlertDialog.value = true
                    }
                    return true
                }
            }

            val mapEventsOverlay = MapEventsOverlay(mapEventsReceiver)
            mapView.overlays.add(mapEventsOverlay)

            mapView
        }
    )

    // Dialog um neuen Marker hinzuzufügen
    if (openAlertDialog.value && geoPointState.value != null) {
        AlertDialogExample(
            onDismissRequest = {
                openAlertDialog.value = false
                geoPointState.value = null
            },
            onConfirmation = { title, description ->
                geoPointState.value?.let { point ->
                    viewModel.addItem(OverlayItem(title, description, point))

                    // Overlay mit neuen Items updaten
                    mapViewRef.value?.let { mapView ->
                        overlayRef.value?.let { oldOverlay ->
                            mapView.overlays.remove(oldOverlay)
                            val newOverlay = createOverlay(ArrayList(viewModel.items), context)
                            overlayRef.value = newOverlay
                            mapView.overlays.add(newOverlay)
                            mapView.invalidate()
                        }
                    }
                }

                openAlertDialog.value = false
                geoPointState.value = null
            }
        )
    }

    // Dialog um bestehenden Marker zu bearbeiten
    if (openEditDialog.value && selectedOverlayItem.value != null) {
        AlertDialog(
            onDismissRequest = {
                openEditDialog.value = false
                selectedOverlayItem.value = null
            },
            title = { Text("Edit place") },
            text = {
                Column {
                    TextField(
                        value = editTitle.value,
                        onValueChange = { editTitle.value = it },
                        placeholder = { Text("Edit Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = editDescription.value,
                        onValueChange = { editDescription.value = it },
                        placeholder = { Text("Edit Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedOverlayItem.value?.let { oldItem ->
                        val newItem = OverlayItem(editTitle.value, editDescription.value, oldItem.point)
                        viewModel.updateItem(oldItem, newItem)

                        mapViewRef.value?.let { mapView ->
                            overlayRef.value?.let { oldOverlay ->
                                mapView.overlays.remove(oldOverlay)
                                val newOverlay = createOverlay(ArrayList(viewModel.items), context)
                                overlayRef.value = newOverlay
                                mapView.overlays.add(newOverlay)
                                mapView.invalidate()
                            }
                        }
                    }
                    openEditDialog.value = false
                    selectedOverlayItem.value = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    openEditDialog.value = false
                    selectedOverlayItem.value = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AlertDialogExample(
    onDismissRequest: () -> Unit,
    onConfirmation: (String, String) -> Unit,
) {
    val titleName = remember { mutableStateOf("") }
    val description = remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Add Place") },
        text = {
            Column {
                TextField(
                    value = titleName.value,
                    onValueChange = { titleName.value = it },
                    placeholder = { Text("Place Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = description.value,
                    onValueChange = { description.value = it },
                    placeholder = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirmation(titleName.value, description.value)
            }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    HappyPlacesAppTheme {
    }
}
