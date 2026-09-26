package com.example.engine

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidPath
import com.example.model.ShapeType

fun ShapePaths.createAndroidPath(shapeType: ShapeType, size: Size, cornerRadius: Float): android.graphics.Path {
    return createPath(shapeType, size, cornerRadius).asAndroidPath()
}
