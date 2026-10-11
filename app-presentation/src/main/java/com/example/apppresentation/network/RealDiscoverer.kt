package com.example.apppresentation.network

import com.example.networktransport.UdpDiscoverer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class RealDiscoverer(
    private val udpDiscoverer: UdpDiscoverer = UdpDiscoverer(),
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : Discoverer {

    override val isDiscovering: StateFlow<Boolean> = udpDiscoverer.isDiscovering

    override val discoveredDevices: StateFlow<List<DiscoveredDevice>> =
        udpDiscoverer.discoveredDevices
            .map { list ->
                list.map { DiscoveredDevice(it.id, it.name, it.ipAddress) }
            }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun startDiscovery() {
        udpDiscoverer.startDiscovery()
    }

    override fun stopDiscovery() {
        udpDiscoverer.stopDiscovery()
    }
}
