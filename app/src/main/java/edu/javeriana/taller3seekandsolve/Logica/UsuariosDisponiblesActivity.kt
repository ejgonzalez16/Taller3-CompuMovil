package edu.javeriana.taller3seekandsolve.Logica

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import edu.javeriana.taller3seekandsolve.Datos.Data.Companion.PATH_USERS
import edu.javeriana.taller3seekandsolve.Datos.Data.Companion.PATH_USERS_ACTIVOS
import edu.javeriana.taller3seekandsolve.databinding.ActivityUsuariosDisponiblesBinding
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale

class UsuariosDisponiblesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUsuariosDisponiblesBinding
    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var userLocation: GeoPoint
    private lateinit var selectedUserLocation: GeoPoint
    private val database = FirebaseDatabase.getInstance()
    private lateinit var myRef: DatabaseReference
    private lateinit var matrixCursor: MatrixCursor
    private lateinit var usuariosAdapter: UsuariosAdapter
    private var rowIdCounter = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsuariosDisponiblesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE))
        initializeMapView()
        initializeLocationClient()
        checkLocationPermission()
        servicioDisponibilidad()
    }

    // Mapas ============================================================================================================
    private fun initializeMapView() {
        mapView = binding.mapView
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(5.0)
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
                    userLocation = GeoPoint(it.latitude, it.longitude)
                    addMyMarker(userLocation)
                }
            }
        }
    }

    private fun addMyMarker(userLocation: GeoPoint) {
        val userMarker = Marker(mapView)
        userMarker.position = userLocation
        userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        userMarker.title = "Tu ubicación"
        userMarker.icon = resources.getDrawable(org.osmdroid.library.R.drawable.person, null)
        mapView.overlays.add(userMarker)
        // Centrar el mapa en la ubicación del usuario y hacer zoom
        mapView.controller.setCenter(userLocation)
        mapView.controller.setZoom(13.0)
    }

    // onResume y onPause ===============================================================================================

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    // Firebase =========================================================================================================
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

        // Configurar la lista de usuarios disponibles
        setListUsuariosDisponibles()
    }

    private fun setListUsuariosDisponibles() {
        val columns = arrayOf("_id", "userId", "imagen", "nombre")
        matrixCursor = MatrixCursor(columns)

        usuariosAdapter = UsuariosAdapter(this, matrixCursor) { userId ->
            seguirUsuario(userId)
        }
        binding.listaUsuariosDisponibles.adapter = usuariosAdapter
    }

    private fun mostrarDisponibilidad(userId: String, isUserLeaving: Boolean = false) {
        val userDatabase = FirebaseDatabase.getInstance().getReference("usuarios/$userId")

        userDatabase.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val nombre = dataSnapshot.child("nombre").getValue(String::class.java)
                val apellido = dataSnapshot.child("apellido").getValue(String::class.java)
                val imagen = dataSnapshot.child("imagen").getValue(String::class.java)

                if (nombre != null && apellido != null && imagen != null) {
                    val fullName = "$nombre $apellido"
                    val message = if (isUserLeaving) {
                        "$fullName ha salido."
                    } else {
                        "$fullName ha llegado."
                    }
                    Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()

                    if (isUserLeaving) {
                        val newCursor = MatrixCursor(matrixCursor.columnNames)
                        for (i in 0 until matrixCursor.count) {
                            matrixCursor.moveToPosition(i)
                            val currentUserId = matrixCursor.getString(matrixCursor.getColumnIndexOrThrow("userId"))
                            if (currentUserId != userId) {
                                val rowData = arrayOf(
                                    matrixCursor.getInt(matrixCursor.getColumnIndexOrThrow("_id")),
                                    matrixCursor.getString(matrixCursor.getColumnIndexOrThrow("userId")),
                                    matrixCursor.getString(matrixCursor.getColumnIndexOrThrow("imagen")),
                                    matrixCursor.getString(matrixCursor.getColumnIndexOrThrow("nombre"))
                                )
                                newCursor.addRow(rowData)
                            }
                        }
                        matrixCursor = newCursor
                        usuariosAdapter.changeCursor(matrixCursor)
                    } else {
                        matrixCursor.addRow(arrayOf(rowIdCounter++, userId, imagen, fullName))
                        usuariosAdapter.notifyDataSetChanged()
                    }
                } else {
//                    Toast.makeText(applicationContext, "Usuario no encontrado.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(applicationContext, "Error: ${databaseError.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun seguirUsuario(userId: String) {
        // Obtiene la referencia de Firebase a la ubicación del usuario
        val userRef = database.getReference("$PATH_USERS/$userId")

        // Listener para actualizar la ubicación en tiempo real
        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val lat = dataSnapshot.child("latitud").getValue(Double::class.java) ?: return
                val lon = dataSnapshot.child("longitud").getValue(Double::class.java) ?: return

                selectedUserLocation = GeoPoint(lat, lon)
                actualizarMapaConUbicacion(selectedUserLocation)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@UsuariosDisponiblesActivity, "Error al obtener la ubicación: ${databaseError.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun actualizarMapaConUbicacion(ubicacion: GeoPoint?) {
        ubicacion?.let {
            mapView.overlays.clear()
            addMyMarker(userLocation)
            addTrackedUserMarker(it)
            calculateAndShowDistance(userLocation, it)
        }
    }

    private fun addTrackedUserMarker(trackedLocation: GeoPoint) {
        val marker = Marker(mapView)
        marker.position = trackedLocation
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "Ubicación del usuario seguido"
        mapView.overlays.add(marker)

        val puntosInteres = listOf(userLocation, trackedLocation)
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

    // Función para calcular la distancia entre la ubicación actual y el marcador
    private fun calculateAndShowDistance(start: GeoPoint, end: GeoPoint) {
        val distance = start.distanceToAsDouble(end)
        val distanceKm = String.format(Locale.getDefault(), "%.2f", distance / 1000)
        Toast.makeText(this, "Distancia recta al otro usuario: $distanceKm km", Toast.LENGTH_LONG).show()
//            isStraightDistanceToastShown = true // Marcamos que ya se mostró
    }

}