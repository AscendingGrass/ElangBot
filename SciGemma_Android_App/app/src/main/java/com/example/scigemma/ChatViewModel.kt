package com.example.scigemma

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import co.rikin.speechtotext.RealSpeechToText
import co.rikin.speechtotext.SpeechToText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.launch
import java.util.Locale

class ChatViewModel(
    private val application: Application,
    private val inferenceModel: InferenceModel,
    private val stt:SpeechToText
) : ViewModel() {

    // `GemmaUiState()` is optimized for the Gemma model.
    // Replace `GemmaUiState` with `ChatUiState()` if you're using a different model
    private var tts: TextToSpeech? = null
    private val _uiState: MutableStateFlow<GemmaUiState> = MutableStateFlow(GemmaUiState())
    val uiState: StateFlow<UiState> =
        _uiState.asStateFlow()

    private val _textInputEnabled: MutableStateFlow<Boolean> =
        MutableStateFlow(true)
    val isTextInputEnabled: StateFlow<Boolean> =
        _textInputEnabled.asStateFlow()

    var state:AppState = AppState()

    var sttRunning = false

    private fun speakText(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
    }

    fun runTTS(message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            speakText(message)
        }
    }

    init {
        viewModelScope.launch {
            val onInitListener = TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    // Set language (optional, adjust based on your needs)
                    tts?.setLanguage(Locale("in", "ID"))
                } else {
                    Log.e("ChatViewModel", "TTS initialization failed")
                }
            }
            tts = TextToSpeech(application, onInitListener)


            with(stt) {
                text.collect { result ->
                    send(AppAction.Update(result))
                }
            }
        }
    }

    fun send(action: AppAction) {
        when (action) {
            AppAction.StartRecord -> {
                sttRunning=true
                setInputEnabled(false)
                Log.d("ChatViewModel", "Listening")

                stt.start()

            }

            AppAction.EndRecord -> {
                sttRunning=false
                setInputEnabled(true)
                stt.stop()
                viewModelScope.launch {
                    sendMessage(state.display)
                    delay(3000)
                    state = state.copy(
                        display = ""
                    )
                }
            }
            is AppAction.Update -> {
                state = state.copy(
                    display = state.display + action.text
                )
            }
        }
    }

    data class AppState(
        val display: String = ""
    )

    sealed class AppAction {
        object StartRecord : AppAction()
        object EndRecord : AppAction()
        data class Update(val text: String): AppAction()
    }

    fun sendMessage(userMessage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value.addMessage(userMessage, USER_PREFIX)
            var currentMessageId: String? = _uiState.value.createLoadingMessage()
            setInputEnabled(false)
            try {
                val fullPrompt = _uiState.value.fullPrompt

                _uiState.value.appendFirstMessage(currentMessageId ?: throw Error(), "hello ")
                _uiState.value.appendMessage(currentMessageId ?: throw Error(), "world", true)

                runTTS(_uiState.value.messages.first().message)
//                currentMessageId = null
                setInputEnabled(true)

//                inferenceModel.generateResponseAsync(fullPrompt)
//                inferenceModel.partialResults
//                    .collectIndexed { index, (partialResult, done) ->
//                        currentMessageId?.let {
//                            if (index == 0) {
//                                _uiState.value.appendFirstMessage(it, partialResult)
//                            } else {
//                                _uiState.value.appendMessage(it, partialResult, done)
//                            }
//                            if (done) {
//                                currentMessageId = null
//                                // Re-enable text input
//                                setInputEnabled(true)
//                            }
//                        }
//                    }

            } catch (e: Exception) {
                _uiState.value.addMessage(e.localizedMessage ?: "Unknown Error", MODEL_PREFIX)
                setInputEnabled(true)
            }
        }
    }

    private fun setInputEnabled(isEnabled: Boolean) {
        _textInputEnabled.value = isEnabled
    }

    companion object {
        fun getFactory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = context.applicationContext as Application
                val inferenceModel = InferenceModel.getInstance(context)
                val stt = RealSpeechToText(context)
                return ChatViewModel(application, inferenceModel, stt) as T
            }
        }
    }
}
