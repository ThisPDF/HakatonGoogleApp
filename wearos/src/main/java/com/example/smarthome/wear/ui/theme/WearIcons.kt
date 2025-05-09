package com.example.smarthome.wear.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.LocalContentAlpha
import androidx.wear.compose.material.LocalContentColor
import com.example.smarthome.wear.R

object WearIcons {
    val Light = R.drawable.ic_light
    val Lock = R.drawable.ic_lock
    val Thermostat = R.drawable.ic_thermostat
    val Device = R.drawable.ic_device
}

@Composable
fun IconFromDrawable(
    @DrawableRes drawableId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current.copy(alpha = LocalContentAlpha.current)
) {
    Icon(
        painter = painterResource(id = drawableId),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}
