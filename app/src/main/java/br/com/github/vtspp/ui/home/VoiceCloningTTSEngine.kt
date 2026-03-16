package br.com.github.vtspp.ui.home

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.io.UniversalAudioInputStream
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class VoiceCloningTTSEngine(private val context: Context, private val textToSpeech: TextToSpeech) {

    private var detectedPitch: Float = 1.0f  // Default pitch

    fun setVoiceReference(audioBytes: ByteArray) {
        // Detect pitch
        detectedPitch = detectPitch(audioBytes)
        textToSpeech.setPitch(detectedPitch)
        textToSpeech.setSpeechRate(0.9f)  // You can also adjust speech rate based on pitch if desired
    }

    fun setLocale(locale: Locale) {
        textToSpeech.language = locale
    }

    fun setVoice(voiceGender: String) {
        textToSpeech.setVoice(textToSpeech.voices.firstOrNull { it.name.contains("#$voiceGender") } ?: textToSpeech.defaultVoice)
    }

    fun synthesize(text: String, onSuccess: (File) -> Unit, onFailure: (String) -> Unit) {

        val tempFile = File.createTempFile("tts", ".wav", context.cacheDir)
        val utteranceId = "tts_synth"

        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                try {
                    onSuccess(tempFile)
                } catch (e: Exception) {
                    onFailure("Error reading synthesized audio: ${e.message}")
                }
            }

            override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {
                setVoiceReference(audio!!)
                super.onAudioAvailable(utteranceId, audio)
            }

            override fun onError(utteranceId: String?) {
                onFailure("TTS synthesis error")
                tempFile.delete()
            }
        })

        val params = HashMap<String, String>()
        params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId

        val result = textToSpeech.synthesizeToFile(text, Bundle(), tempFile, utteranceId)
        if (result == TextToSpeech.ERROR) {
            onFailure("Synthesis failed")
            tempFile.delete()
        }
    }

    private fun detectPitch(audioBytes: ByteArray): Float {
        // Assume 16bit PCM, 16000 Hz, mono
        val sampleRate = 16000f
        val bufferSize = 1024
        val overlap = 512

        val shortBuffer = ByteBuffer.wrap(audioBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = FloatArray(shortBuffer.remaining())
        for (i in samples.indices) {
            samples[i] = shortBuffer.get(i) / 32768f  // Normalize to -1..1
        }

        val audioFormat = TarsosDSPAudioFormat(sampleRate, 16, 1, true, false)
        val dispatcher = be.tarsos.dsp.AudioDispatcher(UniversalAudioInputStream(ByteArrayInputStream(audioBytes), audioFormat), bufferSize, overlap)

        var pitch = 1.0f  // Default

        val pitchDetector = PitchProcessor(PitchEstimationAlgorithm.YIN, sampleRate, bufferSize
        ) { result, event ->
            if (result != null && result.pitch != -1f) {
                pitch = result.pitch
            }
        }

        dispatcher.addAudioProcessor(pitchDetector)

        // Process a short segment
        try {
            dispatcher.run()
        } catch (e: Exception) {
            // Ignore
        }

        // Map pitch to TTS pitch. TTS pitch is relative, 1.0 is normal.
        // Assume male pitch around 85-180 Hz, female 165-255 Hz
        // TTS pitch: lower value for lower pitch
        val normalizedPitch = when {
            pitch < 120 -> 0.8f  // Low pitch, more masculine
            pitch > 200 -> 1.2f  // High pitch, feminine
            else -> pitch
        }

        return normalizedPitch
    }

    fun shutdown() {
        textToSpeech.shutdown()
    }
}
