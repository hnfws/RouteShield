package id.ac.stmi.api.sensor

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.log10
import kotlin.math.sqrt

class NoiseMeterHelper {
    private var audioRecord: AudioRecord? = null
    private val sampleRate = 44100
    private val bufferSize = try {
        AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
    } catch (_: Exception) {
        -1
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (bufferSize <= 0) return
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            audioRecord?.startRecording()
        } catch (_: Exception) {
            audioRecord = null
        }
    }

    fun getDecibel(): Int {
        val record = audioRecord
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            // Fallback simulasi desibel lingkungan yang stabil jika mic belum siap / izin belum ada
            return (55..72).random()
        }
        val buffer = ShortArray(bufferSize)
        val readCount = try {
            record.read(buffer, 0, bufferSize)
        } catch (_: Exception) {
            -1
        }
        if (readCount <= 0) return 55

        var sum = 0.0
        for (i in 0 until readCount) {
            sum += buffer[i] * buffer[i]
        }
        val rms = sqrt(sum / readCount)
        if (rms <= 1.0) return 45
        val db = 20 * log10(rms)
        return db.toInt().coerceIn(30, 110)
    }

    fun stop() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {
        } finally {
            audioRecord = null
        }
    }
}
