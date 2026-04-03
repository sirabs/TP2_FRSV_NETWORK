package fr.istic.mob.networkbahcritie.model

import android.graphics.Color
import java.util.UUID

data class Connection(
    val id: String = UUID.randomUUID().toString(),
    val fromId: String,
    val toId: String,
    var label: String = "",
    var color: Int = Color.BLACK,
    var thickness: Float = 5f,
    var curvature: Float = 0f
)