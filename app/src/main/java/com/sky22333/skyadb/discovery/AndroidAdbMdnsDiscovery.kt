package com.sky22333.skyadb.discovery

import android.content.Context
import com.flyfishxu.kadb.mdns.KadbMdnsAndroid
import com.flyfishxu.kadb.mdns.MdnsConfig
import com.flyfishxu.kadb.mdns.MdnsServiceType
import com.flyfishxu.kadb.mdns.MdnsStatus
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbEndpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** UI adapter only; NSD lifecycle, resolution and callback ownership belong to Kadb. */
class AndroidAdbMdnsDiscovery(context: Context) : AdbMdnsDiscovery {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(AdbMdnsDiscoveryState())
    override val state = mutableState.asStateFlow()
    private var client: KadbMdnsAndroid? = null
    private var collector: Job? = null

    override fun start(types: Set<AdbMdnsServiceType>) {
        if (client != null || types.isEmpty()) return
        val discovery = createClient(types)
        client = discovery
        collector = scope.launch {
            discovery.state.collect { current ->
                mutableState.value =
                    AdbMdnsDiscoveryState(
                        running =
                            current.status == MdnsStatus.STARTING ||
                                current.status == MdnsStatus.STARTED,
                        endpoints =
                            current.allDevices
                                .map { endpoint ->
                                    AdbMdnsEndpoint(
                                        endpoint.name,
                                        AdbEndpoint.hostForSocket(endpoint.host),
                                        endpoint.port,
                                        endpoint.serviceType.toUiType(),
                                    )
                                }
                                .distinctBy { it.id },
                        error =
                            if (current.status == MdnsStatus.FAILED)
                                appString(R.string.discovery_unavailable)
                            else null,
                    )
            }
        }
        discovery.start()
    }

    override fun stop() {
        collector?.cancel()
        collector = null
        client?.close()
        client = null
        mutableState.value = AdbMdnsDiscoveryState()
    }

    override suspend fun findConnectPort(host: String, timeoutMs: Long): Int? {
        val socketHost = AdbEndpoint.hostForSocket(host)
        if (socketHost.isBlank() || timeoutMs <= 0) return null
        val discovery = createClient(setOf(AdbMdnsServiceType.Connect))
        return try {
            discovery.start()
            withTimeoutOrNull(timeoutMs) {
                discovery.state
                    .first { current -> current.connectDevices.any { AdbEndpoint.sameHost(it.host, socketHost) } }
                    .connectDevices
                    .first { AdbEndpoint.sameHost(it.host, socketHost) }
                    .port
            }
        } finally {
            discovery.close()
        }
    }

    override suspend fun findPairingEndpoint(
        serviceName: String,
        timeoutMs: Long,
    ): AdbMdnsEndpoint? {
        val expectedName = serviceName.trim()
        if (expectedName.isEmpty() || timeoutMs <= 0) return null
        val discovery = createClient(setOf(AdbMdnsServiceType.Pairing))
        return try {
            discovery.start()
            withTimeoutOrNull(timeoutMs) {
                discovery.state
                    .first { current ->
                        current.pairDevices.any { endpoint ->
                            endpoint.name.trim().equals(expectedName, ignoreCase = true)
                        }
                    }
                    .pairDevices
                    .first { endpoint -> endpoint.name.trim().equals(expectedName, ignoreCase = true) }
                    .let { endpoint ->
                        AdbMdnsEndpoint(
                            name = endpoint.name,
                            host = AdbEndpoint.hostForSocket(endpoint.host),
                            port = endpoint.port,
                            type = AdbMdnsServiceType.Pairing,
                        )
                    }
            }
        } finally {
            discovery.close()
        }
    }

    private fun createClient(types: Set<AdbMdnsServiceType>) =
        KadbMdnsAndroid(
            context,
            MdnsConfig(
                serviceTypes =
                    types
                        .map { type ->
                            when (type) {
                                AdbMdnsServiceType.Pairing -> MdnsServiceType.TLS_PAIRING
                                AdbMdnsServiceType.Connect -> MdnsServiceType.TLS_CONNECT
                                AdbMdnsServiceType.Legacy -> MdnsServiceType.ADB
                            }
                        }
                        .toSet()
            ),
        )

    private fun MdnsServiceType.toUiType() =
        when (this) {
            MdnsServiceType.ADB -> AdbMdnsServiceType.Legacy
            MdnsServiceType.TLS_CONNECT -> AdbMdnsServiceType.Connect
            MdnsServiceType.TLS_PAIRING -> AdbMdnsServiceType.Pairing
        }
}
