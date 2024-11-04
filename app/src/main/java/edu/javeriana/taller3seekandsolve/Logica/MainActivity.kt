package edu.javeriana.taller3seekandsolve.Logica

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import edu.javeriana.taller3seekandsolve.databinding.ActivityMainBinding
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var puntosInteres: MutableList<GeoPoint>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeMapView()
        initializeLocationClient()
        checkLocationPermission()
        loadPointsOfInterest()
    }

    private fun initializeMapView() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE))
        mapView = binding.mapView
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(5.0)
        puntosInteres = mutableListOf()
    }

    private fun initializeLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else {
            obtenerLocalizacionActual()
        }
    }

    private fun obtenerLocalizacionActual() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val userLocation = GeoPoint(it.latitude, it.longitude)
                    addUserMarker(userLocation)
                    puntosInteres.add(userLocation)
                    mostrarTodosLosPuntos()
                }
            }
        }
    }

    private fun addUserMarker(userLocation: GeoPoint) {
        val userMarker = Marker(mapView)
        userMarker.position = userLocation
        userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        userMarker.title = "Tu ubicación"
        userMarker.icon = resources.getDrawable(org.osmdroid.library.R.drawable.person, null)
        mapView.overlays.add(userMarker)
    }

    private fun loadPointsOfInterest() {
        puntosInteres.addAll(leerPuntosInteres())
    }

    private fun leerPuntosInteres(): List<GeoPoint> {
        val puntosInteres = mutableListOf<GeoPoint>()
        val inputStream: InputStream = assets.open("locations.json")
        val json = inputStream.bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(json)
        val jsonArray = jsonObject.getJSONArray("locationsArray")

        for (i in 0 until jsonArray.length()) {
            val location = jsonArray.getJSONObject(i)
            val latitud = location.getDouble("latitude")
            val longitud = location.getDouble("longitude")
            puntosInteres.add(GeoPoint(latitud, longitud))
        }

        return puntosInteres
    }

    private fun mostrarTodosLosPuntos() {
        for (punto in puntosInteres) {
            if (punto != puntosInteres.last()) {
                val marker = Marker(mapView)
                marker.position = punto
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                mapView.overlays.add(marker)
            }
        }

        val boundingBox = BoundingBox.fromGeoPoints(puntosInteres)
        val margin = 0.02
        val adjustedBoundingBox = BoundingBox(
            boundingBox.latNorth + margin,
            boundingBox.lonEast + margin,
            boundingBox.latSouth - margin,
            boundingBox.lonWest - margin
        )

        mapView.zoomToBoundingBox(adjustedBoundingBox, true)
        mapView.invalidate()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
