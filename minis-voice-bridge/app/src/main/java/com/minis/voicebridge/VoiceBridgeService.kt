package com.minis.voicebridge

import android.service.voice.VoiceInteractionService
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Registers as Android's default Digital Assistant.
 * Handles: long-press Home, voice assist from keyguard, ACTION_ASSIST.
 */
class VoiceBridgeService : VoiceInteractionService() {

    companion object {
        private const val TAG = "VoiceBridge"
        @Volatile var instance: VoiceBridgeService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "VoiceBridge service created")
    }

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "VoiceBridge service ready — listening for assistant triggers")
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        Log.i(TAG, "Voice assist launched from keyguard")
        showSession(Bundle())
    }

    override fun onShutdown() {
        Log.i(TAG, "VoiceBridge service shutting down")
        super.onShutdown()
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        super.onDestroy()
    }

    /**
     * Show the assistant session UI.
     */
    private fun showSession(args: Bundle?) {
        val intent = Intent(this, VoiceBridgeSession::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (args != null) intent.putExtras(args)
        startActivity(intent)
    }
}
