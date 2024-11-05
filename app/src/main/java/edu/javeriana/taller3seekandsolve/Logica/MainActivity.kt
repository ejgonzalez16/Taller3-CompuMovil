package edu.javeriana.taller3seekandsolve.Logica

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import edu.javeriana.taller3seekandsolve.Datos.Data.Companion.PATH_USERS_ACTIVOS
import edu.javeriana.taller3seekandsolve.Datos.Data.Companion.auth
import edu.javeriana.taller3seekandsolve.R
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
    private val database = FirebaseDatabase.getInstance()
    private lateinit var myRef: DatabaseReference
    private lateinit var usuario: FirebaseUser
    private var previousSize: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usuario = intent.getParcelableExtra<FirebaseUser>("usuario")!!
        initializeMapView()
        initializeLocationClient()
        checkLocationPermission()
        loadPointsOfInterest()
        servicioDisponibilidad()
    }

    private fun servicioDisponibilidad() {
        myRef = database.getReference(PATH_USERS_ACTIVOS)
        myRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(dataSnapshot: DataSnapshot, previousChildName: String?) {
                val userId = dataSnapshot.key
                userId?.let {
                    mostrarDisponibilidad(it)
                }
            }

            override fun onChildRemoved(dataSnapshot: DataSnapshot) {
                val userId = dataSnapshot.key
                userId?.let {
                    mostrarDisponibilidad(it, isUserLeaving = true)
                }
            }

            override fun onChildChanged(dataSnapshot: DataSnapshot, previousChildName: String?) {}

            override fun onChildMoved(dataSnapshot: DataSnapshot, previousChildName: String?) {}

            override fun onCancelled(databaseError: DatabaseError) {}
        })
    }

    private fun mostrarDisponibilidad(userId: String, isUserLeaving: Boolean = false) {
        val userDatabase = FirebaseDatabase.getInstance().getReference("usuarios/$userId")

        userDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val nombre = dataSnapshot.child("nombre").getValue(String::class.java)
                val apellido = dataSnapshot.child("apellido").getValue(String::class.java)

                if (nombre != null && apellido != null) {
                    val message = if (isUserLeaving) {
                        "$nombre $apellido ha salido."
                    } else {
                        "$nombre $apellido ha llegado."
                    }
                    Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(applicationContext, "Usuario no encontrado.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(applicationContext, "Error: ${databaseError.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun initializeMapView() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu, menu)
        return super.onCreateOptionsMenu(menu) // Llama al método del super
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menuLogOut -> {
                myRef = database.getReference(PATH_USERS_ACTIVOS).child(usuario.uid)
                myRef.removeValue()
                auth.signOut()
                val intent = Intent(this, LoginActivity::class.java)
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
                true
            }
            R.id.menuDisponibilidad -> {
                buscarDisponible(usuario.uid) { disponible ->
                    if (disponible) {
                        myRef = database.getReference(PATH_USERS_ACTIVOS).child(usuario.uid)
                        myRef.removeValue()
                        item.title = "Establecerse disponible"
                    } else {
                        myRef = database.getReference(PATH_USERS_ACTIVOS).child(usuario.uid)
                        myRef.setValue(usuario.uid)
                        item.title = "Establecerse no disponible"
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun buscarDisponible(uid: String, onResult: (Boolean) -> Unit){
        val myRef = database.getReference(PATH_USERS_ACTIVOS)
        myRef.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Verifica si el UID existe
                if (snapshot.exists()) {
                    onResult(true) // UID encontrado
                } else {
                    onResult(false) // UID no encontrado
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Manejo de error si la operación fue cancelada
                Log.e("Firebase", "Error al verificar el UID", error.toException())
                onResult(false) // Retorna false en caso de error
            }
        })
    }
}
