package fr.istic.mob.networkbahcritie.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import fr.istic.mob.networkbahcritie.model.Graph

class DrawableGraph(
    private val context: Context,
    private var graph: Graph
) : Drawable() {

    private val objectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val connectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }

    private val iconSize = 80

    fun updateGraph(newGraph: Graph) {
        graph = newGraph
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        // Dessiner les connexions
        graph.connections.forEach { connection ->
            val fromObj = graph.objects.find { it.id == connection.fromId }
            val toObj = graph.objects.find { it.id == connection.toId }
            if (fromObj != null && toObj != null) {
                connectionPaint.color = connection.color
                connectionPaint.strokeWidth = connection.thickness
                val path = Path()
                val midX = (fromObj.x + toObj.x) / 2
                val midY = (fromObj.y + toObj.y) / 2
                val dx = toObj.x - fromObj.x
                val dy = toObj.y - fromObj.y
                val len = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                val ctrlX = if (len > 1f) midX + (-dy / len) * connection.curvature * 2 else midX
                val ctrlY = if (len > 1f) midY + (dx / len) * connection.curvature * 2 else midY
                path.moveTo(fromObj.x, fromObj.y)
                path.quadTo(ctrlX, ctrlY, toObj.x, toObj.y)
                canvas.drawPath(path, connectionPaint)

                // Étiquette au milieu
                if (connection.label.isNotEmpty()) {
                    val pm = PathMeasure(path, false)
                    val pos = FloatArray(2)
                    val tan = FloatArray(2)
                    pm.getPosTan(pm.length / 2, pos, tan)
                    val lx = pos[0] - tan[1] * 25f
                    val ly = pos[1] + tan[0] * 25f
                    labelPaint.textSize = 28f
                    canvas.drawText(connection.label, lx, ly, labelPaint)
                }
            }
        }

        // Dessiner les objets
        graph.objects.forEach { obj ->
            val icon = obj.iconRes
            if (icon != null) {
                val drawable = ContextCompat.getDrawable(context, icon)
                if (drawable != null) {
                    val left = (obj.x - iconSize / 2).toInt()
                    val top = (obj.y - iconSize / 2).toInt()
                    drawable.setBounds(left, top, left + iconSize, top + iconSize)
                    drawable.draw(canvas)
                }
                labelPaint.textSize = 32f
                labelPaint.color = Color.BLACK
                canvas.drawText(obj.label, obj.x, obj.y + iconSize / 2 + 35f, labelPaint)
            } else {
                objectPaint.color = obj.color
                canvas.drawRoundRect(
                    obj.x - 50f, obj.y - 50f,
                    obj.x + 50f, obj.y + 50f,
                    20f, 20f, objectPaint
                )
                labelPaint.textSize = 32f
                labelPaint.color = Color.WHITE
                canvas.drawText(obj.label, obj.x, obj.y + 12f, labelPaint)
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        objectPaint.alpha = alpha
        connectionPaint.alpha = alpha
        labelPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        objectPaint.colorFilter = colorFilter
        connectionPaint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}