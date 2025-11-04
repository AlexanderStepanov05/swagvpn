package com.retard.swag.service

import android.content.Context
import android.util.Log
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object XrayManager {

    sealed class XrayState {
        object Stopped : XrayState()
        data class Starting(val message: String) : XrayState()
        object Started : XrayState()
        data class Error(val message: String) : XrayState()
    }

    private val _state = MutableStateFlow<XrayState>(XrayState.Stopped)
    val state = _state.asStateFlow()

    private var coreController: CoreController? = null

    val isRunning: Boolean
        get() = coreController?.isRunning == true

    fun init(context: Context) {
        Log.d("XrayManager", "Init core env at ${context.filesDir.path}")
        Libv2ray.initCoreEnv(context.filesDir.path, "")
    }

    fun startXray(config: String, tunFd: Int): String? {
        if (isRunning) {
            Log.w("XrayManager", "startXray called while running")
            return "Xray is already running"
        }

        val callback = object : CoreCallbackHandler {
            override fun onEmitStatus(code: Long, message: String?): Long {
                Log.d("XrayManager", "onEmitStatus: code=$code, message=$message")
                message?.let { _state.value = XrayState.Starting(it) }
                return 0
            }
            override fun shutdown(): Long {
                Log.d("XrayManager", "shutdown callback")
                _state.value = XrayState.Stopped
                return 0
            }
            override fun startup(): Long {
                Log.d("XrayManager", "startup callback, returning tunFd=$tunFd")
                return tunFd.toLong()
            }
        }

        try {
            Log.d("XrayManager", "Creating core controller and starting loop")
            val controller = Libv2ray.newCoreController(callback)
            controller.startLoop(config)
            coreController = controller
            _state.value = XrayState.Started
            Log.d("XrayManager", "Core started")
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Unknown error"
            _state.value = XrayState.Error(errorMessage)
            Log.e("XrayManager", "Failed to start core: $errorMessage", e)
            return errorMessage
        }

        return null
    }

    fun stopXray() {
        if (!isRunning || coreController == null) return
        try {
            Log.d("XrayManager", "Stopping core loop")
            coreController?.stopLoop()
        } catch (e: Exception) {
            Log.e("XrayManager", "Error stopping core", e)
        }
        coreController = null
        Log.d("XrayManager", "Core stopped")
    }

    fun measureDelay(config: String): Long {
        return try {
            Log.d("XrayManager", "Measuring delay")
            Libv2ray.measureOutboundDelay(config, "https://www.google.com")
        } catch (e: Exception) {
            Log.e("XrayManager", "Measure delay failed", e)
            -1
        }
    }
    
    fun queryStats(tag: String, direct: String): Long {
         if (!isRunning || coreController == null) return 0
         return try {
            coreController?.queryStats(tag, direct) ?: 0
         } catch (e: Exception) {
            Log.e("XrayManager", "Query stats failed", e)
            0
         }
    }
}