package com.example.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import com.example.model.ShapeType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object ShapePaths {

    private val pathCache = mutableMapOf<String, Path>()

    fun createPath(shapeType: ShapeType, size: Size, cornerRadius: Float): Path {
        val cacheKey = "${shapeType}_${size.width}x${size.height}_$cornerRadius"
        pathCache[cacheKey]?.let { return it }

        val path = Path()
        val w = size.width
        val h = size.height

        when (shapeType) {
            ShapeType.RECTANGLE -> {
                path.addRect(Rect(0f, 0f, w, h))
            }
            ShapeType.ROUNDED_RECT -> {
                val cr = cornerRadius.coerceIn(0f, minOf(w, h) / 2f)
                path.addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = 0f,
                        top = 0f,
                        right = w,
                        bottom = h,
                        radiusX = cr,
                        radiusY = cr
                    )
                )
            }
            ShapeType.CIRCLE -> {
                path.addOval(Rect(0f, 0f, w, h))
            }
            ShapeType.TRIANGLE -> {
                path.moveTo(w / 2f, 0f)
                path.lineTo(w, h)
                path.lineTo(0f, h)
                path.close()
            }
            ShapeType.STAR -> {
                val centerX = w / 2f
                val centerY = h / 2f
                val outerRadius = minOf(w, h) / 2f
                val innerRadius = outerRadius * 0.42f
                val points = 5
                val angleStep = (PI / points).toFloat()

                for (i in 0 until points * 2) {
                    val r = if (i % 2 == 0) outerRadius else innerRadius
                    val angle = (i * angleStep) - (PI.toFloat() / 2f)
                    val x = centerX + r * cos(angle)
                    val y = centerY + r * sin(angle)
                    if (i == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }
                path.close()
            }
            ShapeType.HEART -> {
                val centerX = w / 2f
                val topY = h * 0.25f
                path.moveTo(centerX, topY)
                // Left curve
                path.cubicTo(
                    centerX - w * 0.45f, 0f,
                    0f, h * 0.45f,
                    centerX, h * 0.95f
                )
                // Right curve
                path.cubicTo(
                    w, h * 0.45f,
                    centerX + w * 0.45f, 0f,
                    centerX, topY
                )
                path.close()
            }
            ShapeType.POLYGON -> {
                // Regular Hexagon
                val centerX = w / 2f
                val centerY = h / 2f
                val radius = minOf(w, h) / 2f
                val sides = 6
                val step = (2 * PI / sides).toFloat()
                for (i in 0 until sides) {
                    val angle = i * step - (PI.toFloat() / 6f)
                    val x = centerX + radius * cos(angle)
                    val y = centerY + radius * sin(angle)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
            ShapeType.CAPSULE -> {
                val cr = minOf(w, h) / 2f
                path.addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = 0f,
                        top = 0f,
                        right = w,
                        bottom = h,
                        radiusX = cr,
                        radiusY = cr
                    )
                )
            }
        }
        pathCache[cacheKey] = path
        return path
    }
}
