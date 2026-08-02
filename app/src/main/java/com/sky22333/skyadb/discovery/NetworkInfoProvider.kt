package com.sky22333.skyadb.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import androidx.annotation.StringRes
import com.sky22333.skyadb.R
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
                ScanRangeParser.subnetForLocalAddress(address, sourceLabelRes = R.string.discovery_source_current_network)
            }
            ?.let(::listOf)
            .orEmpty()
    }

    fun subnetForHost(host: String, @StringRes sourceLabelRes: Int): LocalNetwork? {
        return ScanRangeParser.subnetForHost(host, sourceLabelRes)
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
            sourceLabelRes = R.string.discovery_source_current_network,
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
                    ScanRangeParser.subnetForLocalAddress(address, sourceLabelRes = R.string.discovery_source_current_network) != null
                }
        }.getOrNull()
    }
}
