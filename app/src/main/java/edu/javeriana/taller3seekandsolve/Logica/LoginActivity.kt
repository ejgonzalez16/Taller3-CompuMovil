package edu.javeriana.taller3seekandsolve.Logica

import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import edu.javeriana.taller3seekandsolve.Datos.Data.Companion.PATH_USERS_ACTIVOS
import edu.javeriana.taller3seekandsolve.Datos.Data.Companion.auth
import edu.javeriana.taller3seekandsolve.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val database = FirebaseDatabase.getInstance()
    private lateinit var myRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = Firebase.auth
        eventoRegistrate()
        eventoLogin()
    }

    private fun eventoLogin(){
        binding.iniciarSesionButton.setOnClickListener {
            if(binding.emailEditText.text.toString().isEmpty() ||
                binding.contrasenia.text.toString().isEmpty()){
                Toast.makeText(this@LoginActivity, "Digite todos los campos", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if(!validarCorreo(binding.emailEditText.text.toString())){
                Toast.makeText(this@LoginActivity, "Digite un correo válido", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            iniciarSesion()
        }
    }

    private fun iniciarSesion(){
        auth.signInWithEmailAndPassword(binding.emailEditText.text.toString(),binding.contrasenia.text.toString())
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmail:success:")
                    myRef = database.getReference(PATH_USERS_ACTIVOS).child(auth.currentUser!!.uid)
                    myRef.setValue(auth.currentUser!!.uid)
                    val intentMain = Intent(this, MainActivity::class.java)
                    val usuario = auth.currentUser
                    intentMain.putExtra("usuario", usuario)
                    startActivity(intentMain)
                } else {
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    Toast.makeText(this, "Authentication failed.",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun validarCorreo(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
        return email.isNotEmpty() && email.matches(emailRegex.toRegex())
    }

    private fun eventoRegistrate(){
        binding.registrateButtonText.setOnClickListener {
            startActivity(Intent(this@LoginActivity, RegistroActivity::class.java))
        }
    }
}