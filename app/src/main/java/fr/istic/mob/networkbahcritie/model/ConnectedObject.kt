package fr.istic.mob.networkbahcritie.model

import android.graphics.Color
import java.util.UUID

data class ConnectedObject(
    val id: String = UUID.randomUUID().toString(),
    var label: String,
    var x: Float,
    var y: Float,
    var color: Int = Color.BLUE,
    var iconRes: Int? = null,
    var iconName: String? = null
)