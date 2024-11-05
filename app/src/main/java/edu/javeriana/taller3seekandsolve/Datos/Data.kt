package edu.javeriana.taller3seekandsolve.Datos

import com.google.firebase.auth.FirebaseAuth

class Data {
    companion object {
        const val MY_PERMISSION_REQUEST_CAMERA = 0
        const val MY_PERMISSION_REQUEST_GALLERY = 1
        lateinit var auth: FirebaseAuth
        const val PATH_USERS = "usuarios/"
        const val PATH_USERS_ACTIVOS = "usuariosActivos/"
    }
}