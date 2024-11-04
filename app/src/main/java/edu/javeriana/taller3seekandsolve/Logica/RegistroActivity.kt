package edu.javeriana.taller3seekandsolve.Logica

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.transition.Visibility
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.UploadTask
import edu.javeriana.taller3seekandsolve.Datos.Data.Companion.MY_PERMISSION_REQUEST_CAMERA
import edu.javeriana.taller3seekandsolve.Datos.Data.Companion.MY_PERMISSION_REQUEST_GALLERY
import edu.javeriana.taller3seekandsolve.Datos.Data.Companion.PATH_USERS
import edu.javeriana.taller3seekandsolve.Datos.Data.Companion.auth
import edu.javeriana.taller3seekandsolve.Datos.Usuario
import edu.javeriana.taller3seekandsolve.databinding.ActivityRegistroBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RegistroActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegistroBinding
    private lateinit var photoUri: Uri
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var storage: FirebaseStorage
    private val database = FirebaseDatabase.getInstance()
    private lateinit var myRef: DatabaseReference

    // El onRequestPermissionsResult se reemplaza por ActivityResultContracts.RequestMultiplePermissions
    // Porque se tienen que solicitar y aceptar varios permisos a la vez
    private val requestGalleryPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        when {
            permissions.all { it.value } -> {
                // Todos los permisos concedidos -> seleccionar una imagen de la galería
                seleccionarDeGaleria()
            }
            permissions.any { !it.value } -> {
                // Algún permiso fue denegado
                Toast.makeText(this, "Permisos de Galería denegados", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistroBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        storage = FirebaseStorage.getInstance()
        setupPasswordVisibility()
        eventoRegistrarse()
        eventoImagenContacto()
        eventoIniciarSesion()
    }

    private fun setupPasswordVisibility() {

        binding.contraseniaLayout.setEndIconOnClickListener {
            val isPasswordVisible = binding.contrasenia.inputType == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            val newInputType = if (isPasswordVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            }
            binding.contrasenia.inputType = newInputType
            binding.confirmarContrasenia.inputType = newInputType
            binding.contrasenia.setSelection(binding.contrasenia.text?.length ?: 0)
            binding.confirmarContrasenia.setSelection(binding.confirmarContrasenia.text?.length ?: 0)
        }
    }

    private fun eventoRegistrarse(){
        binding.registrarseBtn.setOnClickListener {
            if(binding.nombre.editText?.text.toString().isEmpty() ||
                binding.apellido.editText?.text.toString().isEmpty() ||
                binding.email.editText?.text.toString().isEmpty() ||
                binding.contraseniaLayout.editText?.text.toString().isEmpty() ||
                binding.confirmarContraseniaLayout.editText?.text.toString().isEmpty() ||
                binding.numeroIdentificacion.editText?.text.toString().isEmpty() ||
                binding.contraseniaLayout.editText?.text.toString().isEmpty() ||
                binding.imagenContacto.visibility == View.GONE){
                Toast.makeText(this@RegistroActivity, "Complete todos los campos y seleccione una imagen para continuar", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if(!validarCorreo(binding.email.editText?.text.toString())){
                Toast.makeText(this@RegistroActivity, "Digite un correo válido", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if(!binding.contrasenia.text.toString().equals(binding.confirmarContrasenia.text.toString())){
                Toast.makeText(this@RegistroActivity, "Las contraseñas deben ser iguales", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if(binding.contrasenia.text.toString().length < 6){
                Toast.makeText(this@RegistroActivity, "La contraseña debe tener al menos 6 carácteres", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            crearUsuario()
        }
    }

    private fun crearUsuario(){
        auth.createUserWithEmailAndPassword(binding.email.editText?.text.toString(), binding.contraseniaLayout.editText?.text.toString())
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "createUserWithEmail:onComplete:" + task.isSuccessful)
                    val user = auth.currentUser
                    if (user != null) {

                        subirImagenUsuario()
                    }
                } else {
                    Toast.makeText(this, "createUserWithEmail:Failure: " + task.exception.toString(),
                        Toast.LENGTH_SHORT).show()
                    task.exception?.message?.let { Log.e(TAG, it) }
                }
            }
    }

    private fun subirImagenUsuario(){
        val drawable = binding.imagenContacto.drawable
        if (drawable is BitmapDrawable) {
            val bitmap = drawable.bitmap

            // Guardar el Bitmap en un archivo temporal
            val file = File(cacheDir, "temp_image.jpg")
            try {
                val outputStream = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                outputStream.flush()
                outputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
                return
            }

            // Convertir el archivo a Uri
            val fileUri = Uri.fromFile(file)
            val imageRef =
                storage.reference.child("Usuarios/${auth.currentUser?.uid}/imagen_contacto.jpg")
            imageRef.putFile(fileUri)
                .addOnSuccessListener { taskSnapshot ->
                    // Obtener la URL de descarga después de que la imagen se suba con éxito
                    imageRef.downloadUrl.addOnSuccessListener { uri ->
                        val downloadUrl = uri.toString()
                        // Guardar el URL en la base de datos o usarlo como desees
                        escribirUsuarioBD(downloadUrl)
                    }.addOnFailureListener { exception ->
                        Toast.makeText(
                            this@RegistroActivity,
                            "No fue posible obtener la URL de descarga",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(
                        this@RegistroActivity,
                        "No fue posible subir la imagen",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
        else{
            Toast.makeText(this, "La imagen no es un BitmapDrawable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun escribirUsuarioBD(urlImagen: String){
        // Verificar y solicitar permisos
        var latitud = 0.0
        var longitud = 0.0
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else {
            fusedLocationClient.lastLocation.addOnSuccessListener(this, OnSuccessListener { location ->
                if (location != null) {
                    latitud = location.latitude
                    longitud = location.longitude
                    Toast.makeText(this, "Latitud: $latitud, Longitud: $longitud", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "No se pudo obtener la ubicación", Toast.LENGTH_SHORT).show()
                }
            })
        }
        val usuario = Usuario(binding.nombreEditText.text.toString(),
            binding.apellidoEditText.text.toString(), binding.emailEditText.text.toString(),
            binding.contrasenia.text.toString(), urlImagen, binding.numeroIdentificacionEditText.text.toString().toInt(), latitud, longitud)
        myRef = database.getReference(PATH_USERS+auth.currentUser!!.uid)
        myRef.setValue(usuario)
        Toast.makeText(this, "usuario creado con éxito!",
            Toast.LENGTH_SHORT).show()
        startActivity(Intent(this@RegistroActivity, LoginActivity::class.java))
    }

    private fun eventoIniciarSesion(){
        binding.iniciaSesionButtonText.setOnClickListener {
            startActivity(Intent(this@RegistroActivity, LoginActivity::class.java))
        }
    }

    private fun validarCorreo(email: String): Boolean{

        if(email.isEmpty() || !email.contains(".") || !email.contains("@") ||
            email.indexOf("@") > email.indexOf(".") || email.count{ it == '@'} > 1){
            return false
        }
        return true
    }

    private fun eventoImagenContacto() {
        binding.seleccionarImagenBtn.setOnClickListener {
            pedirPermisosGaleria("Necesitamos acceder a la galería para seleccionar y mostrar una imagen en la app")
        }
        binding.tomarImagenBtn.setOnClickListener {
            pedirPermiso(this, android.Manifest.permission.CAMERA,
                "Necesitamos acceder a la cámara para tomar la foto", MY_PERMISSION_REQUEST_CAMERA
            )
        }
    }

    // Función para solicitar los permisos de galería y mostrar justificación si es necesario
    private fun pedirPermisosGaleria(justificacion: String) {
        // Array de permisos a solicitar basado en la versión de Android del dispositivo
        val permisos = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO
            )
            else -> arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // Verificar si se debe mostrar una justificación para cualquiera de los permisos
        if (permisos.any { shouldShowRequestPermissionRationale(it) }) {
            mostrarJustificacion(
                justificacion
            ) {
                // Lanzar la solicitud de permisos después de la justificación
                requestGalleryPermissions.launch(permisos)
            }
        } else {
            // Lanzar la solicitud de permisos sin justificación
            requestGalleryPermissions.launch(permisos)
        }
    }

    // Función para mostrar la justificación con un diálogo y volver a solicitar el permiso
    private fun mostrarJustificacion(mensaje: String, onAccept: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Justificación de permisos")
            .setMessage(mensaje)
            .setPositiveButton("Aceptar") { dialog, _ ->
                onAccept()
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun pedirPermiso(context: Context, permiso: String, justificacion: String,
                             idCode: Int){
        if(ContextCompat.checkSelfPermission(context, permiso) !=
            PackageManager.PERMISSION_GRANTED){
            if (shouldShowRequestPermissionRationale(permiso)) {
                // Explicar al usuario por qué necesitamos el permiso
                mostrarJustificacion(justificacion) {
                    requestPermissions(arrayOf(permiso), idCode)
                }
            } else {
                requestPermissions(arrayOf(permiso), idCode)
            }
        }
        else{
            // Permiso ya concedido, tomar la foto
            takePicture()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            MY_PERMISSION_REQUEST_CAMERA -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permiso concedido, tomar la foto
                    takePicture()
                } else {
                    Toast.makeText(this, "Funcionalidades reducidas", Toast.LENGTH_LONG).show()
                }
                return
            }
        }
    }

    private fun takePicture() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            val photoFile: File = createImageFile()
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            startActivityForResult(takePictureIntent, MY_PERMISSION_REQUEST_CAMERA)
        } catch (e: ActivityNotFoundException) {
            e.message?. let{ Log.e("PERMISSION_APP",it) }
            Toast.makeText(this, "No es posible abrir la cámara", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageFile(): File {
        // El timestamp se usa para que el nombre del archivo sea único
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        // El directorio donde se guardará la imagen
        val storageDir: File = getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        // Crear el archivo con el nombre "JPEG_YYYYMMDD_HHMMSS.jpg" en el directorio storageDir
        return File.createTempFile(
            "JPEG_${timestamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Guardar la URI del archivo para usarla en el Intent de la cámara
            photoUri = FileProvider.getUriForFile(this@RegistroActivity, "edu.javeriana.taller3seekandsolve.fileprovider", this)
        }
    }

    private fun seleccionarDeGaleria() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        try {
            startActivityForResult(intent, MY_PERMISSION_REQUEST_GALLERY)
        } catch (e: ActivityNotFoundException) {
            e.message?. let{ Log.e("PERMISSION_APP",it) }
            Toast.makeText(this, "No es posible abrir la galería", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when(requestCode) {
            MY_PERMISSION_REQUEST_CAMERA -> {
                if (resultCode == RESULT_OK) {
                    binding.imagenContacto.setImageURI(photoUri)
                    binding.imagenContacto.visibility = View.VISIBLE
                }
            }
            MY_PERMISSION_REQUEST_GALLERY -> {
                if (resultCode == RESULT_OK) {
                    try {
                        val imageUri = data?.data
                        val imageStream: InputStream? = contentResolver.openInputStream(imageUri!!)
                        val selectedImage = BitmapFactory.decodeStream(imageStream)
                        binding.imagenContacto.setImageBitmap(selectedImage)
                        binding.imagenContacto.visibility = View.VISIBLE
                    } catch (e: Exception) {
                        e.message?. let{ Log.e("PERMISSION_APP",it) }
                        Toast.makeText(this, "No fue posible seleccionar la imagen (exc.)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}