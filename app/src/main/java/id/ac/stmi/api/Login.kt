package id.ac.stmi.api

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import id.ac.stmi.api.data.api.SpotifyClient
import id.ac.stmi.api.databinding.ActivityLoginBinding

class Login : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleSpotifyCallback(intent)

        val prefs = getSharedPreferences("shield_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_logged_in", false)) {
            navigateNext(prefs.getBoolean("is_calibrated", false))
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val email = binding.etLoginEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Masukkan email terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            completeLogin(email)
        }

        binding.btnRegister.setOnClickListener {
            val email = binding.etLoginEmail.text.toString().trim().ifEmpty { "user@shield.app" }
            Toast.makeText(this, "Akun baru dibuat untuk $email", Toast.LENGTH_SHORT).show()
            completeLogin(email)
        }

        binding.btnLoginGoogle.setOnClickListener {
            completeLogin("google.user@gmail.com")
        }

        binding.btnLoginSpotify.setOnClickListener {
            // Simpan status login & langsung arahkan OAuth Spotify
            prefs.edit().putBoolean("is_logged_in", true).apply()
            startActivity(Intent(Intent.ACTION_VIEW, SpotifyClient.buildAuthUri()))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSpotifyCallback(intent)
    }

    private fun handleSpotifyCallback(intent: Intent?) {
        val uri = intent?.data
        if (uri != null && uri.scheme == "routeshield" && uri.host == "callback") {
            val fragment = uri.fragment
            val token = if (!fragment.isNullOrEmpty()) {
                val params = fragment.split("&").associate {
                    val parts = it.split("=")
                    parts[0] to (parts.getOrNull(1) ?: "")
                }
                params["access_token"]
            } else {
                uri.getQueryParameter("access_token")
            }

            if (!token.isNullOrEmpty()) {
                val prefs = getSharedPreferences("shield_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean("is_logged_in", true)
                    .putString("spotify_token", token)
                    .apply()
                Toast.makeText(this, "Login Spotify Berhasil!", Toast.LENGTH_SHORT).show()
                navigateNext(prefs.getBoolean("is_calibrated", false))
            }
        }
    }

    private fun completeLogin(email: String) {
        val prefs = getSharedPreferences("shield_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_logged_in", true)
            .putString("user_email", email)
            .apply()

        navigateNext(prefs.getBoolean("is_calibrated", false))
    }

    private fun navigateNext(isCalibrated: Boolean) {
        val target = if (isCalibrated) MainActivity::class.java else OnboardingActivity::class.java
        startActivity(Intent(this, target))
        finish()
    }
}
