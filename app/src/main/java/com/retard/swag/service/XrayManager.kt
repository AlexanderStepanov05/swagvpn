package com.retard.swag.service

import android.content.Context
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

    var coreController: CoreController? = null
        private set

    val isRunning: Boolean
        get() = coreController?.isRunning == true

    fun init(context: Context) {
        Libv2ray.initCoreEnv(context.filesDir.path, "")
    }

    fun startXray(config: String, tunFd: Int): String? {
        if (isRunning) {
            return "Xray is already running"
        }

        val callback = object : CoreCallbackHandler {
            override fun onEmitStatus(code: Long, message: String?): Long {
                message?.let { _state.value = XrayState.Starting(it) }
                return 0
            }
            override fun shutdown(): Long {
                _state.value = XrayState.Stopped
                return 0
            }
            override fun startup(): Long {
                return 0
            }
        }

        try {
            val controller = Libv2ray.newCoreController(callback)
            controller.startLoop(config)
            coreController = controller
            _state.value = XrayState.Started
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Unknown error"
            _state.value = XrayState.Error(errorMessage)
            return errorMessage
        }

        return null
    }

    fun stopXray() {
        if (!isRunning || coreController == null) return
        try {
            coreController?.stopLoop()
        } catch (e: Exception) {
        }
        coreController = null
    }

    fun measureDelay(config: String): Long {
        return try {
            Libv2ray.measureOutboundDelay(config, "https://www.google.com")
        } catch (e: Exception) {
            -1
        }
    }
    
    fun queryStats(tag: String, direct: String): Long {
         if (!isRunning || coreController == null) return 0
         return try {
            coreController?.queryStats(tag, direct) ?: 0
         } catch (e: Exception) {
            0
         }
    }
}