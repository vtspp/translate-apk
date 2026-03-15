package br.com.github.vtspp.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import br.com.github.vtspp.databinding.FragmentHomeBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var client: OkHttpClient
    private val REQUEST_RECORD_AUDIO_PERMISSION = 200
    private var selectedLocale = "pt-BR"
    private var speechRecognizer: SpeechRecognizer? = null
    private var audioBuffer: ByteArray? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textHome
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }

        client = OkHttpClient()

        binding.talkButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
            } else {
                startSpeechRecognition()
            }
        }

        binding.localeButton.setOnClickListener {
            selectedLocale = if (selectedLocale == "pt-BR") "ar-SA" else "pt-BR"
            binding.localeButton.text = "Locale: $selectedLocale"
        }

        return root
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra("android.speech.extra.GET_AUDIO", true)
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(requireContext(), "Fale agora...", Toast.LENGTH_SHORT).show()
            }

            override fun onBeginningOfSpeech() {
                Toast.makeText(requireContext(), "Capturando audio. Aguarde...", Toast.LENGTH_SHORT).show()
            }

            override fun onRmsChanged(rmsdB: Float) {
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                audioBuffer = buffer
            }

            override fun onEndOfSpeech() {
                Toast.makeText(requireContext(), "Audio Capturado...", Toast.LENGTH_SHORT).show()
            }

            override fun onError(error: Int) {
                Toast.makeText(requireContext(), "Erro no reconhecimento de fala", Toast.LENGTH_SHORT).show()
            }

            override fun onResults(results: Bundle?) {
                val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = data?.get(0) ?: ""
                binding.textHome.text = spokenText
                val audioBytes = audioBuffer

                if (audioBytes != null) {
                    val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
                    sendToAPI(spokenText, base64Audio)
                } else {
                    Toast.makeText(requireContext(), "Erro ao ler áudio", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
            }
        })
        speechRecognizer?.startListening(intent)
    }

    private fun sendToAPI(text: String, voiceReference: String) {
        val json = JSONObject()
            .put("action", "text-to-speech")
            .put("targetText", text)
            .put("promptBoost", true)
            //.put("voicePromptId", "eedd9a83-eccc-4c66-b8aa-1d9eb490e57e_prompt-reading-neutral")
            .put("model", "dd-etts-3.0")
            .put("locale", selectedLocale)
            .put("mode", "rest")
            .put("voiceReference", voiceReference)
            .toString()
        val body = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://restapi.deepdub.ai/api/v1/tts")
            .post(body)
            .addHeader("x-api-key", "dd-WoTDqml3jBPEQiEVECOsBNq8OhgYF1ZE8c1e2811")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Erro na requisição: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val audioData = response.body?.bytes()
                    if (audioData != null) {
                        playAudio(audioData)
                    }
                } else {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Erro na API: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun playAudio(audioData: ByteArray) {
        try {
            val tempFile = File.createTempFile("audio", ".mp3", requireContext().cacheDir)
            tempFile.writeBytes(audioData)
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(tempFile.absolutePath)
            mediaPlayer.prepare()
            mediaPlayer.start()
            mediaPlayer.setOnCompletionListener {
                it.release()
                tempFile.delete()
            }
        } catch (e: Exception) {
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "Erro ao reproduzir áudio: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSpeechRecognition()
            } else {
                Toast.makeText(requireContext(), "Permissão de áudio negada", Toast.LENGTH_SHORT).show()
            }
        }
    }
}