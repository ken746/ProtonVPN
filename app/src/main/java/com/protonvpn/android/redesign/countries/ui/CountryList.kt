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

package com.protonvpn.android.redesign.countries.ui

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.protonvpn.android.redesign.stubs.ButtonList

@Composable
fun CountryListRoute(
    onCountryClick: (String) -> Unit,
    viewModel: CountryListViewModel = hiltViewModel(),
) {
    CountryList(
        viewModel.countryToCities.keys.toList(),
        onCountryClick
    )
}

@Composable
fun CountryList(countries: List<String>, onCountryClick: (String) -> Unit) {
    ButtonList(countries, onCountryClick)
}
