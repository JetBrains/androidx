/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.graphics

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.LayerManager
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Picture
import org.jetbrains.skia.Point3

@InternalComposeUiApi
class SkiaGraphicsContext(
    internal val measureDrawBounds: Boolean = false,
): GraphicsContext {
    internal val layerManager = LayerManager()
    internal val lightGeometry = LightGeometry()
    internal val lightInfo = LightInfo()

    fun setLightingInfo(
        centerX: Float = Float.MIN_VALUE,
        centerY: Float = Float.MIN_VALUE,
        centerZ: Float = Float.MIN_VALUE,
        radius: Float = 0f,
        ambientShadowAlpha: Float = 0f,
        spotShadowAlpha: Float = 0f
    ) {
        lightGeometry.center = Point3(centerX, centerY, centerZ)
        lightGeometry.radius = radius
        lightInfo.ambientShadowAlpha = ambientShadowAlpha
        lightInfo.spotShadowAlpha = spotShadowAlpha
    }

    fun drawIntoCanvas(canvas: Canvas, block: (Canvas) -> Unit) {
        val pictureFilterCanvas = PictureFilterCanvas(canvas)
        block(pictureFilterCanvas.asComposeCanvas())
        pictureFilterCanvas.close()
    }

    override fun createGraphicsLayer() = GraphicsLayer(this)

    override fun releaseGraphicsLayer(layer: GraphicsLayer) {
        layer.release()
    }

    private inner class PictureFilterCanvas(
        canvas: Canvas
    ) : org.jetbrains.skia.PictureFilterCanvas(canvas.nativeCanvas) {
        override fun onDrawPicture(
            picture: Picture,
            matrix: Matrix33?,
            paint: Paint?
        ): Boolean = layerManager.drawPlaceholder(picture, this)
    }
}

// Adoption of frameworks/base/libs/hwui/Lighting.h
internal data class LightGeometry(
    var center: Point3 = Point3(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE),
    var radius: Float = 0f
)

internal data class LightInfo(
    var ambientShadowAlpha: Float = 0f,
    var spotShadowAlpha: Float = 0f
)
