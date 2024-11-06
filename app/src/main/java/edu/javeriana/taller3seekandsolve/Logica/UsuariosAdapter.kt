package edu.javeriana.taller3seekandsolve.Logica

import android.content.Context
import android.database.Cursor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CursorAdapter
import android.widget.ImageView
import android.widget.TextView
import edu.javeriana.taller3seekandsolve.R
import com.squareup.picasso.Picasso

class UsuariosAdapter(
    private val context: Context,
    private val cursor: Cursor,
    private val onUserFollowClick: (userId: String) -> Unit
) : CursorAdapter(context, cursor, 0) {

    override fun newView(context: Context?, cursor: Cursor?, parent: ViewGroup?): View {
        return LayoutInflater.from(context).inflate(R.layout.layout_usuarios, parent, false)
    }

    override fun bindView(view: View?, context: Context?, cursor: Cursor?) {
        val imagenUsuario = view?.findViewById<ImageView>(R.id.imagen)
        val nombreUsuario = view?.findViewById<TextView>(R.id.nombre)
        val btnSeguir = view?.findViewById<Button>(R.id.btnVerPosicion)

        val userId = cursor?.getString(cursor.getColumnIndexOrThrow("userId"))!!
        val imagen = cursor.getString(cursor.getColumnIndexOrThrow("imagen"))
        val nombre = cursor.getString(cursor.getColumnIndexOrThrow("nombre"))

        Picasso.get()
            .load(imagen)
            .resize(0, 130) // Define solo la altura a 150dp, el ancho será ajustado automáticamente
            .onlyScaleDown() // Solo redimensiona si la imagen es más grande que los límites dados
            .into(imagenUsuario)
        nombreUsuario?.text = nombre

        btnSeguir?.setOnClickListener {
            onUserFollowClick(userId)
        }
    }
}