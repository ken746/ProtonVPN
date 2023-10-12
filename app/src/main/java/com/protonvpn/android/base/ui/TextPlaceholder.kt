/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.base.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun TextPlaceholder(
    modifier: Modifier = Modifier,
    width: Dp = 64.dp,
    textStyle: TextStyle = LocalTextStyle.current
) {
    val textHeight = with(LocalDensity.current) {
        textStyle.lineHeight.takeOrElse { 24.sp }.toDp()
    }
    val rectHeight = 14.dp
    Box(
        modifier
            .size(width = width, height = textHeight)
            .padding(vertical = (textHeight - rectHeight).coerceAtLeast(0.dp) / 2)
            .background(color = ProtonTheme.colors.textDisabled, shape = RoundedCornerShape(size = 4.dp))
    )
}
