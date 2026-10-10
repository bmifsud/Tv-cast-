package com.example.apppresentation.network

import kotlinx.coroutines.flow.StateFlow

interface Discoverer {
    val isDiscovering: StateFlow<Boolean>
    val discoveredDevices: StateFlow<List<DiscoveredDevice>>
    fun startDiscovery()
    fun stopDiscovery()
}
