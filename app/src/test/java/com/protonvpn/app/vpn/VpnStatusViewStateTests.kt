/*
 * Copyright (c) 2023 Proton AG
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
package com.protonvpn.app.vpn

import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.netshield.NetShieldStats
import com.protonvpn.android.redesign.vpn.ui.VpnStatusViewState
import com.protonvpn.android.redesign.vpn.ui.VpnStatusViewStateFlow
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.test.shared.mockVpnUser
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VpnStatusViewStateFlowTest {

    @MockK
    private lateinit var vpnStateMonitor: VpnStateMonitor

    @MockK
    private lateinit var serverListUpdaterPrefs: ServerListUpdaterPrefs

    @MockK
    private lateinit var vpnConnectionManager: VpnConnectionManager

    @RelaxedMockK
    private lateinit var mockCurrentUser: CurrentUser

    @MockK
    lateinit var vpnUser: VpnUser

    private lateinit var vpnStatusViewStateFlow: VpnStatusViewStateFlow
    private val server: Server = mockk()
    private val connectionParams = ConnectionParams(mockk(), server, mockk(), mockk())
    private val statusFlow =
        MutableStateFlow(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
    private val ipAddressFlow = MutableStateFlow("1.1.1.1")
    private val countryFlow = MutableStateFlow("US")
    private val netShieldStatsFlow = MutableStateFlow(NetShieldStats())

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { vpnStateMonitor.status } returns statusFlow
        every { serverListUpdaterPrefs.ipAddressFlow } returns ipAddressFlow
        every { serverListUpdaterPrefs.lastKnownCountryFlow } returns countryFlow
        every { vpnConnectionManager.netShieldStats } returns netShieldStatsFlow
        every { server.isSecureCoreServer } returns false
        every { vpnUser.isFreeUser } returns false
        every { mockCurrentUser.vpnUserFlow } returns flowOf(vpnUser)
        mockCurrentUser.mockVpnUser {
            vpnUser
        }

        vpnStatusViewStateFlow = VpnStatusViewStateFlow(
            vpnStateMonitor,
            serverListUpdaterPrefs,
            vpnConnectionManager,
            mockCurrentUser
        )
    }

    @Test
    fun `change in vpnStatus changes StatusViewState flow`() = runTest {
        statusFlow.emit(VpnStateMonitor.Status(VpnState.Disabled, null))
        assert(vpnStatusViewStateFlow.first() is VpnStatusViewState.Disabled)
        statusFlow.emit(VpnStateMonitor.Status(VpnState.Connecting, connectionParams))
        assert(vpnStatusViewStateFlow.first() is VpnStatusViewState.Connecting)
        statusFlow.emit(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        assert(vpnStatusViewStateFlow.first() is VpnStatusViewState.Connected)
    }

    @Test
    fun `change in netShield stats are reflected in StatusViewState flow`() = runTest {
        statusFlow.emit(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        assert(vpnStatusViewStateFlow.first() is VpnStatusViewState.Connected)
        netShieldStatsFlow.emit(NetShieldStats(3, 3, 3000))
        val netShieldStats =
            (vpnStatusViewStateFlow.first() as VpnStatusViewState.Connected).netShieldStats
        assert(netShieldStats.adsBlocked == 3L)
        assert(netShieldStats.trackersBlocked == 3L)
        assert(netShieldStats.savedBytes == 3000L)
    }

    @Test
    fun `free user produces correct state with greyed out netshield stats`() = runTest {
        statusFlow.emit(VpnStateMonitor.Status(VpnState.Connected, connectionParams))
        assert(vpnStatusViewStateFlow.first() is VpnStatusViewState.Connected)
        assertEquals(
            (vpnStatusViewStateFlow.first() as VpnStatusViewState.Connected).netShieldStatsGreyedOut,
            false
        )
        every { vpnUser.isFreeUser } returns true
        assertEquals(
            (vpnStatusViewStateFlow.first() as VpnStatusViewState.Connected).netShieldStatsGreyedOut,
            true
        )
    }
}