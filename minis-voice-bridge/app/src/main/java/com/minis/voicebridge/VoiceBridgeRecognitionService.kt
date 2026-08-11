package com.minis.voicebridge

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Required companion component for VoiceInteractionService.
 * The actual speech recognition happens in VoiceBridgeSession,
 * so this service rejects direct SpeechRecognizer clients.
 */
class VoiceBridgeRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        listener.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onStopListening(listener: Callback) = Unit
    override fun onCancel(listener: Callback) = Unit
}
