package com.picturebook.hardware

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class AudioService(context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var currentCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.CHINESE)
                isInitialized = result != TextToSpeech.LANG_MISSING_DATA &&
                                 result != TextToSpeech.LANG_NOT_SUPPORTED
                Log.i(TAG, "TTS initialized: $isInitialized")
            } else {
                Log.e(TAG, "TTS init failed")
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                currentCallback?.invoke()
                currentCallback = null
            }
            override fun onError(utteranceId: String?) {
                currentCallback?.invoke()
                currentCallback = null
            }
        })
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isInitialized || text.isBlank()) {
            onDone?.invoke()
            return
        }
        currentCallback = onDone
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "AudioService"
    }
}