package id.ac.stmi.api

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import id.ac.stmi.api.data.api.RetrofitClient
import id.ac.stmi.api.data.model.Hazard
import id.ac.stmi.api.data.model.HazardRequest
import id.ac.stmi.api.databinding.ActivityHazardDetailBinding
import kotlinx.coroutines.launch

class activity_hazard_detail : AppCompatActivity() {

    companion object {
        const val EXTRA_HAZARD_ID = "extra_hazard_id"
    }

    private lateinit var binding: ActivityHazardDetailBinding
    private var hazardId: String = ""
    private var currentHazard: Hazard? = null
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHazardDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        hazardId = intent.getStringExtra(EXTRA_HAZARD_ID) ?: ""

        binding.btnPatchNoise.setOnClickListener { showPatchDialog() }

        binding.btnEditPut.setOnClickListener {
            val item = currentHazard ?: return@setOnClickListener
            val intent = Intent(this, activity_hazard_form::class.java).apply {
                putExtra("EDIT_ID", item.id)
                putExtra("EDIT_LOCATION", item.location_name)
                putExtra("EDIT_NOISE", item.noise_level)
                putExtra("EDIT_CATEGORY", item.category)
                putExtra("EDIT_LAT", item.latitude)
                putExtra("EDIT_LNG", item.longitude)
            }
            startActivity(intent)
        }

        binding.btnDelete.setOnClickListener { confirmDelete() }
    }

    override fun onResume() {
        super.onResume()
        if (hazardId.isNotEmpty()) fetchDetail()
    }

    private fun fetchDetail() {
        lifecycleScope.launch {
            try {
                val item = RetrofitClient.apiService.getHazardById(hazardId)
                currentHazard = item
                binding.tvDetailId.text = "ID Server: #${item.id}"
                binding.tvDetailLocation.text = item.location_name
                binding.tvDetailNoise.text = "Intensitas Suara: ${item.noise_level}"
                binding.tvDetailCategory.text = "Kategori: ${item.category}"
                binding.tvDetailCoords.text = "Koordinat GPS: ${item.latitude}, ${item.longitude}"
            } catch (e: Exception) {
                Toast.makeText(this@activity_hazard_detail, "Gagal memuat detail: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPatchDialog() {
        val input = EditText(this).apply {
            hint = "Masukkan dB baru (contoh: 90 dB)"
            setText(currentHazard?.noise_level ?: "")
        }

        AlertDialog.Builder(this)
            .setTitle("Update Intensitas Suara (PATCH)")
            .setMessage("Hanya field noise_level yang diubah, data lain tetap utuh.")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val newNoise = input.text.toString().trim()
                if (newNoise.isNotEmpty()) executePatch(newNoise)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun executePatch(newNoise: String) {
        lifecycleScope.launch {
            try {
                val jsonObject = JsonObject().apply {
                    addProperty("noise_level", newNoise)
                }
                val response = RetrofitClient.apiService.patchHazard(
                    id = hazardId,
                    request = jsonObject
                )
                val body = response.body()
                val jsonText = if (body != null) prettyGson.toJson(body) else "PATCH Berhasil (${response.code()})"
                showResponseDialog("PATCH Berhasil (${response.code()})", jsonText) {
                    fetchDetail()
                }
            } catch (e: Exception) {
                Toast.makeText(this@activity_hazard_detail, "Error PATCH: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Laporan?")
            .setMessage("Titik bising ini akan dihapus permanen dari server.")
            .setPositiveButton("Hapus") { _, _ -> executeDelete() }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun executeDelete() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.deleteHazard(hazardId)
                val rawBody = response.body()?.string() ?: "{}"
                showResponseDialog("DELETE Berhasil (${response.code()})", rawBody) {
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@activity_hazard_detail, "Error DELETE: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showResponseDialog(title: String, jsonBody: String, onDismiss: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(jsonBody)
            .setPositiveButton("OK") { _, _ -> onDismiss() }
            .setCancelable(false)
            .show()
    }
}