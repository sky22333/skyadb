package com.sky22333.skyadb.discovery

import android.content.Context
import com.flyfishxu.kadb.mdns.KadbMdnsAndroid
import com.flyfishxu.kadb.mdns.MdnsStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** kadb-mdns 适配为应用侧发现模型。 */
class AndroidAdbMdnsDiscovery(
    context: Context,
) : AdbMdnsDiscovery {
    private val mdns = KadbMdnsAndroid(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val state: StateFlow<AdbMdnsDiscoveryState> = mdns.state
        .map { upstream ->
            AdbMdnsDiscoveryState(
                running = upstream.status == MdnsStatus.STARTING ||
                    upstream.status == MdnsStatus.STARTED ||
                    upstream.loading,
                endpoints = upstream.allDevices
                    .map { endpoint ->
                        AdbMdnsEndpoint(
                            name = endpoint.name,
                            host = endpoint.host,
                            port = endpoint.port,
                            type = AdbMdnsServiceType.from(endpoint.serviceType),
                        )
                    }
                    .sortedWith(
                        compareBy<AdbMdnsEndpoint> { it.type.ordinal }
                            .thenBy { it.host }
                            .thenBy { it.port },
                    ),
                error = if (upstream.status == MdnsStatus.FAILED) {
                    "自动发现失败"
                } else {
                    null
                },
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, AdbMdnsDiscoveryState())

    override fun start() {
        mdns.start()
    }

    override fun stop() {
        mdns.stop()
    }
}
