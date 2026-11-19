package com.orbitfiles.app.share

import com.orbitfiles.core.LanShareServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

data class LanState(
    val running: Boolean = false,
    val url: String? = null,
    val root: File? = null,
    val allowUpload: Boolean = true,
    val log: List<String> = emptyList(),
    val error: String? = null,
)

class LanShareController {
    private var server: LanShareServer? = null
    private val _state = MutableStateFlow(LanState())
    val state: StateFlow<LanState> = _state.asStateFlow()

    fun start(root: File, allowUpload: Boolean) {
        stop()
        val ip = localIp()
        if (ip == null) {
            _state.value = LanState(error = "Conecte-se a uma rede Wi-Fi (ou ative o roteador do celular) para compartilhar.")
            return
        }
        for (port in listOf(8686, 8687, 8688, 8080, 9090)) {
            val s = LanShareServer(root, port, allowUpload) { msg ->
                _state.update { it.copy(log = (listOf(msg) + it.log).take(50)) }
            }
            try {
                s.start()
                server = s
                _state.value = LanState(true, "http://$ip:$port/?k=${s.key}", root, allowUpload)
                return
            } catch (_: Exception) {
                s.stop()
            }
        }
        _state.value = LanState(error = "Não foi possível abrir uma porta de rede.")
    }

    fun stop() {
        server?.stop()
        server = null
        _state.update { LanState(log = it.log) }
    }

    companion object {
        /** Best LAN IPv4: prefers Wi-Fi / hotspot interfaces. */
        fun localIp(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .sortedBy { i -> if (i.name.startsWith("wlan") || i.name.startsWith("ap") || i.name.startsWith("swlan")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
