package com.example.happyplacesapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.happyplacesapp.ui.theme.HappyPlacesAppTheme
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ItemizedIconOverlay
import org.osmdroid.views.overlay.ItemizedOverlayWithFocus
import org.osmdroid.views.overlay.OverlayItem
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {

        //nach Internetzugriff fragen

        val internetPermission = Manifest.permission.INTERNET
        if (ContextCompat.checkSelfPermission(this, internetPermission) !=
            PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(internetPermission),
                0)
        }
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                OsmdroidMapView()
            }
        }
        Configuration.getInstance().load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
        Configuration.getInstance().userAgentValue = "HappyPlacesApp"
    }
}

@Composable
fun OsmdroidMapView(){
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->

            // die eigentliche Karte wird für osmdroid erzeugt

            val mapView = MapView(context).apply {

                // Kartenquelle wird erzeugt, MAPNIK als Standard von Osmdroid

                setTileSource(TileSourceFactory.MAPNIK)

                //Verschiedene Kontrollmöglichkeiten
                val rotationGestureOverlay = RotationGestureOverlay(this)
                rotationGestureOverlay.isEnabled
                overlays.add(rotationGestureOverlay)
                // mit Finger zoomen oder drehen
                setMultiTouchControls(true)

                setBuiltInZoomControls(true)

                controller.setZoom(15.0) //Standardzoom
                controller.setCenter(GeoPoint(52.5200, 13.4050)) // Berlin

                this.minZoomLevel = 4.0 //Nur so rauszoomen, dass man Länder sieht

                //Kleiner Kompass oben links
                val compassOverlay = CompassOverlay(context, InternalCompassOrientationProvider(context), this)
                compassOverlay.enableCompass()
                this.overlays.add(compassOverlay)

                //your items
                val items = ArrayList<OverlayItem>()
                items.add(OverlayItem("Title", "Description", GeoPoint(0.0, 0.0)))
                items.add(OverlayItem("Title", "Description", GeoPoint(2.0, 2.0)))
                //the overlay
                var overlay = ItemizedOverlayWithFocus<OverlayItem>(items, object:
                    ItemizedIconOverlay.OnItemGestureListener<OverlayItem> {
                    override fun onItemSingleTapUp(index:Int, item:OverlayItem):Boolean {
                        return true
                    }
                    override fun onItemLongPress(index:Int, item:OverlayItem):Boolean {
                        return false
                    }
                }, context)
                overlay.setFocusItemsOnTap(true);

                this.overlays.add(overlay);
            }
            mapView
        }
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    HappyPlacesAppTheme {
        OsmdroidMapView()
    }
}