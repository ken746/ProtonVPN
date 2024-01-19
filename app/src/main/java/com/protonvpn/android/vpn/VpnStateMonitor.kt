/*
 * Copyright (c) 2019 Proton Technologies AG
 * 
 * This file is part of ProtonVPN.
 * 
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.protonvpn.android.vpn

import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.vpn.VpnState.Connected
import com.protonvpn.android.vpn.VpnState.Disabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

abstract class VpnStatusProvider {

    abstract val status: StateFlow<VpnStateMonitor.Status>

    val state get() = status.value.state
    val connectionParams get() = status.value.connectionParams

    val isConnected get() = state == Connected && connectionParams != null
    val isEstablishingConnection get() = state.isEstablishingConnection
    val isEstablishingOrConnected get() = isConnected || state.isEstablishingConnection
    val isDisabled get() = state == Disabled

    val connectingToServer
        get() = connectionParams?.server?.takeIf {
            state == Connected || state.isEstablishingConnection
        }

    val connectionIntent
        get() = connectionParams?.connectIntent

    val connectionProtocol
        get() = connectionParams?.protocolSelection

    fun isConnectedTo(server: Server?) =
        isConnected && connectionParams?.server?.serverId == server?.serverId

    fun isConnectedTo(connectIntent: ConnectIntent) =
        isConnected && connectionIntent == connectIntent

    fun isConnectingToCountry(country: String) =
        connectingToServer?.exitCountry == country
}

@Singleton
class VpnStateMonitor @Inject constructor() : VpnStatusProvider() {

    private val statusInternal = MutableStateFlow(Status(Disabled, null))
    private val lastKnownExitIp = MutableStateFlow<String?>(null)

    override val status: StateFlow<Status> = statusInternal
    val exitIp: StateFlow<String?> = lastKnownExitIp
    val onDisconnectedByUser = MutableSharedFlow<Unit>()
    val onDisconnectedByReconnection = MutableSharedFlow<Unit>()
    val vpnConnectionNotificationFlow = MutableSharedFlow<VpnFallbackResult>()
    val newSessionEvent = MutableSharedFlow<Unit>()

    fun updateStatus(newStatus: Status) {
        statusInternal.value = newStatus
    }

    fun updateLastKnownExitIp(exitIp: String?) {
        lastKnownExitIp.value = exitIp
    }

    data class Status(
        val state: VpnState,
        val connectionParams: ConnectionParams?
    ) {
        val connectIntent: AnyConnectIntent? get() = connectionParams?.connectIntent
        val server get() = connectionParams?.server
    }
}

// Status provider that ignores Guest Hole connections as those should be ignored in UI
// TODO: reimplement all these three classes, remove inheritance on VpnStatusProvider (then uiStatus can be named just
//  status again) and clean it up.
@Singleton
class VpnStatusProviderUI @Inject constructor(
    scope: CoroutineScope,
    monitor: VpnStateMonitor
) : VpnStatusProvider() {
    data class Status(
        val state: VpnState,
        val connectionParams: ConnectionParams?
    ) {
        val connectIntent: ConnectIntent? get() = connectionParams?.connectIntent as? ConnectIntent
        val server get() = connectionParams?.server
    }

    override val status: StateFlow<VpnStateMonitor.Status> = monitor.status

    val uiStatus: StateFlow<Status> = monitor.status
        .map {
            if (it.connectionParams?.connectIntent is ConnectIntent)
                Status(it.state, it.connectionParams)
            else
                Status(Disabled, null)
        }
        .stateIn(scope, SharingStarted.Eagerly, Status(Disabled, null))
}
