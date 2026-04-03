package fr.istic.mob.networkbahcritie.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import fr.istic.mob.networkbahcritie.model.Graph

class GraphView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var floorPlanBitmap: Bitmap? = null
    private var graph: Graph = Graph()
    private var drawableGraph: DrawableGraph? = null

    var tempConnectionCoords: Pair<Pair<Float, Float>, Pair<Float, Float>>? = null

    private val tempLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.GRAY
        strokeWidth = 3f
        style = Paint.Style.STROKE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    fun setFloorPlan(bitmap: Bitmap) {
        floorPlanBitmap = bitmap
        requestLayout()
        invalidate()
    }

    fun setGraph(newGraph: Graph) {
        graph = newGraph
        if (drawableGraph == null) {
            drawableGraph = DrawableGraph(context, newGraph)
        } else {
            drawableGraph?.updateGraph(newGraph)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)


        floorPlanBitmap?.let { bitmap ->

            canvas.drawBitmap(bitmap, 0f, 0f, null)
        }


        tempConnectionCoords?.let { (from, to) ->
            canvas.drawLine(from.first, from.second, to.first, to.second, tempLinePaint)
        }


        drawableGraph?.let {
            it.bounds = Rect(0, 0, width, height)
            it.draw(canvas)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val bitmap = floorPlanBitmap
        if (bitmap != null) {

            setMeasuredDimension(bitmap.width, bitmap.height)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}