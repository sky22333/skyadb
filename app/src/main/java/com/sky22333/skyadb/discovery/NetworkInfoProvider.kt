package com.sky22333.skyadb.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import java.net.Inet4Address
import java.net.NetworkInterface

class NetworkInfoProvider(
    context: Context,
) {
    private val connectivityManager = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    fun currentLocalNetworks(): List<LocalNetwork> {
        val activeRanges = activeLinkRanges()
        if (activeRanges.isNotEmpty()) return activeRanges

        return networkInterfaceAddress()
            ?.let { address ->
                ScanRangeParser.subnetForLocalAddress(address, sourceLabel = "当前网络")
            }
            ?.let(::listOf)
            .orEmpty()
    }

    fun subnetForHost(host: String, sourceLabel: String): LocalNetwork? {
        return ScanRangeParser.subnetForHost(host, sourceLabel)
    }

    private fun activeLinkRanges(): List<LocalNetwork> {
        val network = connectivityManager.activeNetwork ?: return emptyList()
        val linkProperties = connectivityManager.getLinkProperties(network) ?: return emptyList()
        return linkProperties.linkAddresses
            .asSequence()
            .filter { it.address is Inet4Address }
            .mapNotNull { linkAddress -> linkAddress.toLocalNetwork() }
            .filter { it.hostCount > 0 }
            .distinctBy { it.subnetLabel }
            .toList()
    }

    private fun LinkAddress.toLocalNetwork(): LocalNetwork? {
        return ScanRangeParser.subnetForLocalAddress(
            address = address.hostAddress.orEmpty(),
            sourceLabel = "当前网络",
        )
    }

    private fun networkInterfaceAddress(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()
                .asSequence()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { networkInterface -> networkInterface.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .map { it.hostAddress.orEmpty() }
                .firstOrNull { address ->
                    ScanRangeParser.subnetForLocalAddress(address, sourceLabel = "当前网络") != null
                }
        }.getOrNull()
    }
}
