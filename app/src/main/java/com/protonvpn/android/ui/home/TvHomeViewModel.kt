/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.android.ui.home

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.tv.TvUpgradeActivity
import com.protonvpn.android.tv.main.TvMapRenderer
import com.protonvpn.android.tv.models.Card
import com.protonvpn.android.tv.models.CountryCard
import com.protonvpn.android.tv.models.DrawableImage
import com.protonvpn.android.tv.models.ProfileCard
import com.protonvpn.android.tv.models.QuickConnectCard
import com.protonvpn.android.tv.models.Title
import com.protonvpn.android.utils.AndroidUtils.launchActivity
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.RecentsManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TvHomeViewModel @Inject constructor(
    val appConfig: AppConfig,
    val serverManager: ServerManager,
    val serverListUpdater: ServerListUpdater,
    val vpnStateMonitor: VpnStateMonitor,
    private val recentsManager: RecentsManager,
    val userData: UserData,
    val authManager: AuthManager
) : ViewModel() {

    val selectedCountry = MutableLiveData<VpnCountry>()
    val connectedCountryFlag = MutableLiveData<String>()
    val mapRegion = MutableLiveData<TvMapRenderer.MapRegion>()
    val logoutEvent get() = authManager.logoutEvent

    // Simplified vpn connection state change stream for UI elements interested in distinct changes between 3 states
    enum class ConnectionState { None, Connecting, Connected }
    val vpnConnectionState = vpnStateMonitor.vpnStatus.asFlow().map {
        it.state
    }.map {
        when {
            it == VpnState.Disabled -> ConnectionState.None
            it.isEstablishingConnection -> ConnectionState.Connecting
            else -> ConnectionState.Connected
        }
    }.distinctUntilChanged().asLiveData()

    init {
        vpnStateMonitor.vpnStatus.observeForever {
            connectedCountryFlag.value = if (isConnected())
                it.server!!.flag else ""
        }
    }

    fun onViewInit(lifecycle: Lifecycle) {
        serverListUpdater.startSchedule(lifecycle, null)
    }

    val haveAccessToStreaming get() = userData.isUserPlusOrAbove

    fun showConnectButtons(card: CountryCard) =
        !isConnectedToThisCountry(card) && card.vpnCountry.hasAccessibleServer(userData)

    fun showConnectToStreamingButton(card: CountryCard) = (showConnectButtons(card) ||
        isFreeUser()) && !isConnectedToThisCountry(card)

    fun isConnectedToThisCountry(card: CountryCard) =
        vpnStateMonitor.isConnectingToCountry(card.vpnCountry.flag)

    fun disconnectText(card: CountryCard) =
        if (!showConnectButtons(card) && vpnStateMonitor.vpnStatus.value?.state?.isEstablishingConnection == true)
            R.string.cancel
        else
            R.string.disconnect

    private fun countryListItemIcon(country: VpnCountry) = when {
        !userData.isFreeUser -> null
        country.hasAccessibleServer(userData) -> R.drawable.ic_free
        else -> R.drawable.ic_lock
    }

    fun shouldDisplayStreamingIcons() = appConfig.getFeatureFlags().displayTVLogos

    fun getCountryCardMap(context: Context): Map<CountryTools.Continent?, List<CountryCard>> {
        return serverManager.getVpnCountries().groupBy({
            val continent = CountryTools.locationMap[it.flag]?.continent
            DebugUtils.debugAssert { continent != null }
            continent
        }, { country ->
            CountryCard(
                countryName = country.countryName,
                hasStreamingService = !streamingServices(country).isNullOrEmpty(),
                backgroundImage = DrawableImage(CountryTools.getFlagResource(context, country.flag)),
                bottomTitleResId = countryListItemIcon(country),
                vpnCountry = country
            )
        }).mapValues { continent ->
            continent.value.sortedWith(compareBy<CountryCard> {
                !it.vpnCountry.hasAccessibleOnlineServer(userData)
            }.thenBy { it.countryName })
        }
    }

    fun getRecentCardList(context: Context): List<Card> {
        val recentsList = mutableListOf<Card>()
        val label = context.getString(when {
            isConnected() -> R.string.disconnect
            isEstablishingConnection() -> R.string.cancel
            serverManager.defaultConnection.isPreBakedProfile -> R.string.tv_quick_connect_recommened
            else -> R.string.tv_quick_connect_favourite
        })
        val quickConnectCard = QuickConnectCard(
            title = Title(
                text = label,
                resId = if (isConnected() || isEstablishingConnection())
                    R.drawable.ic_notification_disconnected else R.drawable.ic_thunder,
                backgroundColorRes = if (isConnected() || isEstablishingConnection())
                    R.color.tvAlert else R.color.tvGridItemOverlay
            ),
            backgroundImage = DrawableImage(
                resId = quickConnectBackground(context),
                tint = if (isConnected() || isEstablishingConnection())
                    R.color.tvDisconnectButtonTint else R.color.transparent)
        )
        recentsList.add(quickConnectCard)
        recentsManager.getRecentConnections().forEach {
            recentsList.add(
                ProfileCard(
                    title = it.name,
                    backgroundImage = CountryTools.getFlagResource(context, it.wrapper.country),
                    profile = it
                )
            )
        }
        return recentsList
    }

    fun disconnect() = vpnStateMonitor.disconnect()

    fun isConnected() = vpnStateMonitor.isConnected

    fun isEstablishingConnection() = vpnStateMonitor.isEstablishingConnection

    fun isFreeUser() = userData.isFreeUser

    fun isPlusUser() = userData.isUserPlusOrAbove

    fun hasAccessibleServers(country: VpnCountry) = country.hasAccessibleServer(userData)

    fun showConnectToFastest(card: CountryCard) = card.vpnCountry.hasAccessibleServer(userData) &&
        !isPlusUser() && !isConnectedToThisCountry(card)

    fun onUpgradeClicked(context: Context) {
        context.launchActivity<TvUpgradeActivity>()
    }

    private fun quickConnectBackground(context: Context): Int {
        val server = if (isConnected())
            vpnStateMonitor.connectingToServer
        else
            serverManager.defaultConnection.server
        return CountryTools.getFlagResource(context, server?.flag)
    }

    fun onQuickConnectAction(activity: Activity) {
        if (vpnStateMonitor.isConnected || vpnStateMonitor.isEstablishingConnection) {
            vpnStateMonitor.disconnect()
        } else {
            vpnStateMonitor.connect(activity, serverManager.defaultConnection)
        }
    }

    fun connect(activity: Activity, card: CountryCard?) {
        val profile = if (card != null) Profile.getTempProfile(
            serverManager.getBestScoreServer(card.vpnCountry), serverManager
        )
        else serverManager.defaultConnection
        vpnStateMonitor.connect(activity, profile)
    }

    fun connect(activity: Activity, card: ProfileCard?) {
        card?.let { vpnStateMonitor.connect(activity, it.profile) }
    }

    fun resetMap() {
        mapRegion.value = TvMapRenderer.MapRegion(0f, 0f, 1f)
    }

    fun isDefaultCountry(vpnCountry: VpnCountry) =
        userData.defaultConnection?.wrapper?.country == vpnCountry.flag

    fun setAsDefaultCountry(checked: Boolean, vpnCountry: VpnCountry) {
        userData.defaultConnection = if (checked) Profile(
            vpnCountry.countryName, "", ServerWrapper.makeFastestForCountry(vpnCountry.flag, serverManager)
        ) else null
    }

    data class StreamingService(val name: String, val iconUrl: String?)
    fun streamingServices(vpnCountry: VpnCountry): List<StreamingService>? =
        serverManager.streamingServices?.let { response ->
            response[vpnCountry.flag]?.map { streamingService ->
                StreamingService(
                    streamingService.name,
                    if (appConfig.getFeatureFlags().displayTVLogos)
                        Uri.parse(response.resourceBaseURL).buildUpon().appendPath(streamingService.iconName).toString()
                    else
                        null
                )
            }
        }

    fun logout() = authManager.logout(false)
}
