package id.ac.stmi.api

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import id.ac.stmi.api.data.api.ApiConfig
import id.ac.stmi.api.data.api.RetrofitClient
import id.ac.stmi.api.databinding.ActivityHazardFormBinding
import kotlinx.coroutines.launch

class activity_hazard_form : AppCompatActivity() {

    private lateinit var binding: ActivityHazardFormBinding
    private var editId: String? = null
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    private var currentLat = "-6.3918"
    private var currentLng = "106.8214"
    private var currentDb = 75

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHazardFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        editId = intent.getStringExtra("EDIT_ID")
        currentLat = intent.getStringExtra("DEFAULT_LAT") ?: "-6.3918"
        currentLng = intent.getStringExtra("DEFAULT_LNG") ?: "106.8214"
        val rawDb = intent.getStringExtra("DEFAULT_DB") ?: "75 dB"
        currentDb = rawDb.replace("dB", "").trim().toIntOrNull() ?: 75

        setupSpinner()
        setupSeekBar()
        setupFormMapWebView()

        if (editId != null) {
            binding.tvFormTitle.text = "Edit Seluruh Laporan (PUT)"
            binding.btnSubmitForm.text = "Simpan Perubahan (PUT)"
            binding.etLocationName.setText(intent.getStringExtra("EDIT_LOCATION"))
            val editNoise = intent.getStringExtra("EDIT_NOISE") ?: "75 dB"
            val dbVal = editNoise.replace("dB", "").trim().toIntOrNull() ?: 75
            binding.sbNoiseLevel.progress = dbVal
            binding.tvNoiseLevelLabel.text = "Tingkat Bising Terukur: $dbVal dB"
        } else {
            binding.sbNoiseLevel.progress = currentDb
            binding.tvNoiseLevelLabel.text = "Tingkat Bising Terukur: $currentDb dB"
        }

        binding.btnSubmitForm.setOnClickListener {
            val location = binding.etLocationName.text.toString().trim()
            val noise = "${binding.sbNoiseLevel.progress} dB"
            val category = binding.spCategory.selectedItem?.toString() ?: "Konstruksi"

            if (location.isEmpty()) {
                Toast.makeText(this, "Isi deskripsi / nama lokasi terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val jsonBody = JsonObject().apply {
                addProperty("location_name", location)
                addProperty("noise_level", noise)
                addProperty("category", category)
                addProperty("latitude", currentLat)
                addProperty("longitude", currentLng)
            }

            if (editId == null) submitPost(jsonBody) else submitPut(editId!!, jsonBody)
        }
    }

    private fun setupSpinner() {
        val categories = arrayOf("Konstruksi", "Klakson", "Kerumunan", "Cahaya Silau")
        val adapter = ArrayAdapter(this, R.layout.spinner_item, categories).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        binding.spCategory.adapter = adapter
    }

    private fun setupSeekBar() {
        binding.sbNoiseLevel.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvNoiseLevelLabel.text = "Tingkat Bising Terukur: $progress dB"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupFormMapWebView() {
        binding.webViewFormMap.settings.javaScriptEnabled = true
        binding.webViewFormMap.settings.domStorageEnabled = true
        val mapboxToken = ApiConfig.MAPBOX_ACCESS_TOKEN
        val lat = currentLat.toDoubleOrNull() ?: -6.3918
        val lng = currentLng.toDoubleOrNull() ?: 106.8214

        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    body, html { margin: 0; padding: 0; height: 100%; background: #121619; }
                    #map { width: 100%; height: 100%; }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    var map = L.map('map', {zoomControl: false, attributionControl: false}).setView([$lat, $lng], 16);
                    var mapboxToken = '$mapboxToken';
                    L.tileLayer('https://api.mapbox.com/styles/v1/mapbox/dark-v11/tiles/{z}/{x}/{y}@2x?access_token=' + mapboxToken, {
                        tileSize: 512,
                        zoomOffset: -1,
                        maxZoom: 19
                    }).addTo(map);
                    L.marker([$lat, $lng]).addTo(map);
                </script>
            </body>
            </html>
        """.trimIndent()

        binding.webViewFormMap.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }

    private fun submitPost(request: JsonObject) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.createHazard(request)
                val body = response.body()
                val jsonText = if (body != null) prettyGson.toJson(body) else "Data berhasil dikirim ke server (Status Code: ${response.code()})"
                AlertDialog.Builder(this@activity_hazard_form)
                    .setTitle("POST Berhasil (${response.code()})")
                    .setMessage(jsonText)
                    .setPositiveButton("Selesai") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@activity_hazard_form, "Error POST: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun submitPut(id: String, request: JsonObject) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.updateHazard(id, request)
                val body = response.body()
                val jsonText = if (body != null) prettyGson.toJson(body) else "Data berhasil diperbarui di server (Status Code: ${response.code()})"
                AlertDialog.Builder(this@activity_hazard_form)
                    .setTitle("PUT Berhasil (${response.code()})")
                    .setMessage(jsonText)
                    .setPositiveButton("Selesai") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@activity_hazard_form, "Error PUT: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
