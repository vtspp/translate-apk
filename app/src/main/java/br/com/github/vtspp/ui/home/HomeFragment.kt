package br.com.github.vtspp.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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
import java.io.File
import java.util.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val REQUEST_RECORD_AUDIO_PERMISSION = 200
    private var speechRecognizer: SpeechRecognizer? = null
    private var base64Audio: String? = null
    private var isVoiceRecording = false
    private lateinit var ttsEngine: VoiceCloningTTSEngine

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textHome
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }

        ttsEngine = VoiceCloningTTSEngine(requireContext(), TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsEngine.setLocale(Locale.getDefault())
                ttsEngine.setVoice("male")
            } else {
                Toast.makeText(requireContext(), "Erro ao inicializar TTS", Toast.LENGTH_SHORT).show()
            }
        })

        binding.talkButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
            } else {
                startSpeechRecognition()
            }
        }


        binding.addVoiceButton.setOnClickListener {
            isVoiceRecording = true
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
            } else {
                startVoiceRecording()
            }
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
                if (buffer == null) {
                    Toast.makeText(requireContext(), "Audio não foi capturado corretamente...", Toast.LENGTH_SHORT).show()
                    return
                }
                Toast.makeText(requireContext(), "Audio Recebido...", Toast.LENGTH_SHORT).show()
                base64Audio = Base64.encodeToString(buffer, Base64.NO_WRAP)
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
                ttsEngine.synthesize(spokenText, { playAudio(it) }, { requireActivity().runOnUiThread { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() } })
            }

            override fun onPartialResults(partialResults: Bundle?) {
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
            }
        })
        speechRecognizer?.startListening(intent)
    }

    private fun startVoiceRecording() {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize)

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Toast.makeText(requireContext(), "Erro ao inicializar gravação de áudio", Toast.LENGTH_SHORT).show()
            return
        }

        val totalSamples = 3 * sampleRate  // 3 segundos
        val totalBytes = totalSamples * 2  // 16-bit
        val audioData = ByteArray(totalBytes)

        Toast.makeText(requireContext(), "Gravando voz por 3 segundos...", Toast.LENGTH_SHORT).show()

        Thread {
            audioRecord.startRecording()
            var offset = 0
            while (offset < totalBytes) {
                val read = audioRecord.read(audioData, offset, kotlin.math.min(bufferSize, totalBytes - offset))
                if (read < 0) break
                offset += read
            }
            audioRecord.stop()
            audioRecord.release()

            requireActivity().runOnUiThread {
                ttsEngine.setVoiceReference(audioData)
                Toast.makeText(requireContext(), "Voz natural gravada!", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }


    private fun playAudio(audioData: File) {
        try {
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(audioData.absolutePath)
            mediaPlayer.prepare()
            mediaPlayer.start()
            mediaPlayer.setOnCompletionListener {
                it.release()
                audioData.delete()
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
        ttsEngine.shutdown()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (isVoiceRecording) {
                    startVoiceRecording()
                } else {
                    startSpeechRecognition()
                }
                isVoiceRecording = false
            } else {
                Toast.makeText(requireContext(), "Permissão de áudio negada", Toast.LENGTH_SHORT).show()
            }
        }
    }
}