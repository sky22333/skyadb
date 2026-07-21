package com.sky22333.skyadb.discovery

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import java.util.ArrayDeque
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidAdbMdnsDiscovery(
    context: Context,
) : AdbMdnsDiscovery {
    private val nsdManager = context.applicationContext.getSystemService(NsdManager::class.java)
    private val lock = Any()
    private val callbackExecutor = Executor { command -> command.run() }
    private val listeners = mutableMapOf<AdbMdnsServiceType, NsdManager.DiscoveryListener>()
    private val serviceCallbacks = mutableMapOf<String, NsdManager.ServiceInfoCallback>()
    private val endpoints = linkedMapOf<String, AdbMdnsEndpoint>()
    private val pendingResolves = ArrayDeque<Pair<NsdServiceInfo, AdbMdnsServiceType>>()
    private var resolving = false
    private var active = false

    private val mutableState = MutableStateFlow(AdbMdnsDiscoveryState())
    override val state: StateFlow<AdbMdnsDiscoveryState> = mutableState.asStateFlow()

    override fun start() {
        synchronized(lock) {
            if (active) return
            active = true
            endpoints.clear()
            pendingResolves.clear()
            resolving = false
            mutableState.value = AdbMdnsDiscoveryState(running = true)
        }

        AdbMdnsServiceType.entries.forEach(::startServiceType)
    }

    override fun stop() {
        val currentListeners = synchronized(lock) {
            active = false
            resolving = false
            pendingResolves.clear()
            endpoints.clear()
            listeners.values.toList().also { listeners.clear() }
        }

        currentListeners.forEach { listener ->
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
        stopServiceInfoCallbacks()

        synchronized(lock) {
            mutableState.value = AdbMdnsDiscoveryState(running = false)
        }
    }

    private fun startServiceType(type: AdbMdnsServiceType) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                updateError(null)
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    registerServiceInfoCallback(serviceInfo, type)
                } else {
                    enqueueResolve(serviceInfo, type)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                unregisterServiceInfoCallback(type, serviceInfo)
                removeEndpoint(type = type, name = serviceInfo.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                updateError("自动发现启动失败，可继续使用网段扫描。")
                runCatching { nsdManager.stopServiceDiscovery(this) }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { nsdManager.stopServiceDiscovery(this) }
            }
        }

        synchronized(lock) {
            if (!active) return
            listeners[type] = listener
        }

        runCatching {
            nsdManager.discoverServices(type.nsdType, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            updateError("自动发现不可用，可继续使用网段扫描。")
        }
    }

    private fun enqueueResolve(serviceInfo: NsdServiceInfo, type: AdbMdnsServiceType) {
        synchronized(lock) {
            if (!active) return
            pendingResolves.add(serviceInfo to type)
        }
        resolveNext()
    }

    private fun resolveNext() {
        val next = synchronized(lock) {
            if (!active || resolving || pendingResolves.isEmpty()) return
            resolving = true
            pendingResolves.removeFirst()
        }

        resolveLegacy(next.first, next.second)
    }

    private fun finishResolve() {
        synchronized(lock) {
            resolving = false
        }
        resolveNext()
    }

    @SuppressLint("NewApi")
    private fun registerServiceInfoCallback(serviceInfo: NsdServiceInfo, type: AdbMdnsServiceType) {
        val key = callbackKey(type, serviceInfo)
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                unregisterServiceInfoCallback(key)
            }

            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                addResolvedService(serviceInfo, type, serviceInfo.hostAddresses.firstOrNull()?.hostAddress.orEmpty())
            }

            override fun onServiceLost() {
                unregisterServiceInfoCallback(key)
                removeEndpoint(type = type, name = serviceInfo.serviceName)
            }

            override fun onServiceInfoCallbackUnregistered() = Unit
        }

        synchronized(lock) {
            if (!active || serviceCallbacks.containsKey(key)) return
            serviceCallbacks[key] = callback
        }

        runCatching {
            nsdManager.registerServiceInfoCallback(serviceInfo, callbackExecutor, callback)
        }.onFailure {
            unregisterServiceInfoCallback(key)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveLegacy(serviceInfo: NsdServiceInfo, type: AdbMdnsServiceType) {
        runCatching {
            nsdManager.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        finishResolve()
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        addResolvedService(serviceInfo, type, serviceInfo.host?.hostAddress.orEmpty())
                        finishResolve()
                    }
                },
            )
        }.onFailure {
            finishResolve()
        }
    }

    private fun addResolvedService(serviceInfo: NsdServiceInfo, type: AdbMdnsServiceType, host: String) {
        val port = serviceInfo.port
        if (host.isNotBlank() && port in 1..65535) {
            addEndpoint(
                AdbMdnsEndpoint(
                    name = serviceInfo.serviceName.ifBlank { type.label },
                    host = host,
                    port = port,
                    type = type,
                ),
            )
        }
    }

    private fun stopServiceInfoCallbacks() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val callbacks = synchronized(lock) {
            serviceCallbacks.values.toList().also { serviceCallbacks.clear() }
        }
        callbacks.forEach { callback ->
            runCatching { nsdManager.unregisterServiceInfoCallback(callback) }
        }
    }

    private fun unregisterServiceInfoCallback(type: AdbMdnsServiceType, serviceInfo: NsdServiceInfo) {
        unregisterServiceInfoCallback(callbackKey(type, serviceInfo))
    }

    private fun unregisterServiceInfoCallback(key: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val callback = synchronized(lock) {
            serviceCallbacks.remove(key)
        }
        if (callback != null) {
            runCatching { nsdManager.unregisterServiceInfoCallback(callback) }
        }
    }

    private fun callbackKey(type: AdbMdnsServiceType, serviceInfo: NsdServiceInfo): String {
        return "${type.name}:${serviceInfo.serviceName}"
    }

    private fun addEndpoint(endpoint: AdbMdnsEndpoint) {
        synchronized(lock) {
            if (!active) return
            endpoints[endpoint.id] = endpoint
            publishStateLocked(error = null)
        }
    }

    private fun removeEndpoint(type: AdbMdnsServiceType, name: String) {
        synchronized(lock) {
            if (!active) return
            endpoints.entries.removeAll { (_, endpoint) ->
                endpoint.type == type && endpoint.name == name
            }
            publishStateLocked(error = mutableState.value.error)
        }
    }

    private fun updateError(error: String?) {
        synchronized(lock) {
            if (!active) return
            publishStateLocked(error = error)
        }
    }

    private fun publishStateLocked(error: String?) {
        mutableState.value = AdbMdnsDiscoveryState(
            running = active,
            endpoints = endpoints.values.sortedWith(
                compareBy<AdbMdnsEndpoint> { it.type.ordinal }
                    .thenBy { it.name }
                    .thenBy { it.host }
                    .thenBy { it.port },
            ),
            error = error,
        )
    }
}
