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

package androidx.compose.ui.graphics.layer

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.SkiaBackedCanvas
import androidx.compose.ui.graphics.SkiaGraphicsContext
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.asSkiaColorFilter
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.isIdentity
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.prepareTransformationMatrix
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toSkia
import androidx.compose.ui.graphics.toSkiaMatrix44
import androidx.compose.ui.graphics.toSkiaRRect
import androidx.compose.ui.graphics.toSkiaRect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import org.jetbrains.skia.Canvas as SkCanvas
import org.jetbrains.skia.Paint as SkPaint
import org.jetbrains.skia.Picture
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Point3
import org.jetbrains.skia.RTreeFactory
import org.jetbrains.skia.Rect as SkRect
import org.jetbrains.skia.ShadowUtils

actual class GraphicsLayer internal constructor(
    private val context: SkiaGraphicsContext,
) {
    private val pictureDrawScope = CanvasDrawScope()
    private val pictureRecorder = PictureRecorder()
    private var picture: Picture? = null
    private var placeholder: Picture? = null

    // Use factory for BBoxHierarchy to track real bounds of drawn content
    private val bbhFactory = if (context.measureDrawBounds) RTreeFactory() else null

    private var matrixDirty = true
    private val matrix = Matrix()

    actual var compositingStrategy: CompositingStrategy = CompositingStrategy.Auto

    private var internalOutline: Outline? = null
    private var outlineDirty = true
    private var roundRectOutlineTopLeft: Offset = Offset.Zero
    private var roundRectOutlineSize: Size = Size.Unspecified
    private var roundRectCornerRadius: Float = 0f
    private var outlinePath: Path? = null

    private var parentLayerUsages = 0
    private val childDependenciesTracker = ChildLayerDependenciesTracker()

    actual var topLeft: IntOffset = IntOffset.Zero
        set(value) {
            if (field != value) {
                field = value
                updateLayerConfiguration()
            }
        }

    actual var size: IntSize = IntSize.Zero
        private set(value) {
            if (field != value) {
                field = value
                updatePlaceholder()
            }
        }

    private val bounds: SkRect
        get() = SkRect.makeLTRB(0f, 0f, size.width.toFloat(), size.height.toFloat())

    actual var alpha: Float = 1f

    actual var scaleX: Float = 1f
        set(value) {
            field = value
            invalidateMatrix()
        }
    actual var scaleY: Float = 1f
        set(value) {
            field = value
            invalidateMatrix()
        }
    actual var translationX: Float = 0f
        set(value) {
            field = value
            invalidateMatrix()
        }
    actual var translationY: Float = 0f
        set(value) {
            field = value
            invalidateMatrix()
        }
    actual var shadowElevation: Float = 0f

    actual var rotationX: Float = 0f
        set(value) {
            field = value
            invalidateMatrix()
        }
    actual var rotationY: Float = 0f
        set(value) {
            field = value
            invalidateMatrix()
        }
    actual var rotationZ: Float = 0f
        set(value) {
            field = value
            invalidateMatrix()
        }

    actual var cameraDistance: Float = DefaultCameraDistance
        set(value) {
            field = value
            invalidateMatrix()
        }

    actual var renderEffect: RenderEffect? = null

    private var density: Density = Density(1f)

    private fun invalidateMatrix() {
        matrixDirty = true
    }

    private fun updateLayerConfiguration() {
        this.outlineDirty = true
        invalidateMatrix()
    }

    // Note: Updating of placeholder REQUIRES parent layer invalidation
    private fun updatePlaceholder() {
        context.layerManager.unregisterPlaceholder(placeholder)
        placeholder?.close()
        placeholder = Picture.makePlaceholder(bounds)
        context.layerManager.registerPlaceholder(placeholder!!, this)
    }

    actual fun record(
        density: Density,
        layoutDirection: LayoutDirection,
        size: IntSize,
        block: DrawScope.() -> Unit
    ) {
        // Close previous picture
        picture?.close()
        picture = null

        this.density = density
        this.size = size
        updateLayerConfiguration()
        val measureDrawBounds = !clip || shadowElevation > 0
        val bounds = size.toSize().toRect()
        val canvas = pictureRecorder.beginRecording(
            bounds = if (measureDrawBounds) PICTURE_BOUNDS else bounds.toSkiaRect(),
            bbh = if (measureDrawBounds) bbhFactory else null
        )
        val skiaCanvas = canvas.asComposeCanvas() as SkiaBackedCanvas
        skiaCanvas.alphaMultiplier = if (compositingStrategy == CompositingStrategy.ModulateAlpha) {
            this@GraphicsLayer.alpha
        } else {
            1.0f
        }
        trackRecord {
            pictureDrawScope.draw(
                density,
                layoutDirection,
                skiaCanvas,
                size.toSize(),
                this,
                block
            )
        }
        picture = pictureRecorder.finishRecordingAsPicture()
    }

    private fun trackRecord(block: () -> Unit) {
        childDependenciesTracker.withTracking(
            onDependencyRemoved = { it.onRemovedFromParentLayer() },
            block = block
        )
    }

    private fun addSubLayer(graphicsLayer: GraphicsLayer) {
        if (childDependenciesTracker.onDependencyAdded(graphicsLayer)) {
            graphicsLayer.onAddedToParentLayer()
        }
    }

    actual var clip: Boolean = false

    private inline fun createOutlineWithPosition(
        outlineTopLeft: Offset,
        outlineSize: Size,
        block: (Offset, Size) -> Outline
    ): Outline {
        val targetSize = if (outlineSize.isUnspecified) {
            this.size.toSize()
        } else {
            outlineSize
        }
        return block(outlineTopLeft, targetSize)
    }

    private fun configureOutline(): Outline {
        var tmpOutline = internalOutline
        if (outlineDirty || tmpOutline == null) {
            val tmpPath = outlinePath
            tmpOutline = if (tmpPath != null) {
                Outline.Generic(tmpPath)
            } else {
                createOutlineWithPosition(
                    roundRectOutlineTopLeft,
                    roundRectOutlineSize
                ) { outlineTopLeft, outlineSize ->
                    if (roundRectCornerRadius > 0f) {
                        Outline.Rounded(
                            RoundRect(
                                left = outlineTopLeft.x,
                                top = outlineTopLeft.y,
                                right = outlineTopLeft.x + outlineSize.width,
                                bottom = outlineTopLeft.y + outlineSize.height,
                                cornerRadius = CornerRadius(roundRectCornerRadius)
                            )
                        )
                    } else {
                        Outline.Rectangle(
                            Rect(
                                left = outlineTopLeft.x,
                                top = outlineTopLeft.y,
                                right = outlineTopLeft.x + outlineSize.width,
                                bottom = outlineTopLeft.y + outlineSize.height
                            )
                        )
                    }
                }
            }
            internalOutline = tmpOutline
            outlineDirty = false
        }
        return tmpOutline
    }

    internal actual fun draw(canvas: Canvas, parentLayer: GraphicsLayer?) {
        if (isReleased) return
        parentLayer?.addSubLayer(this)

        // Do any recalculation in [drawPlaceholder] because this block will be run only in case
        // of parent layer invalidation. Things like transform should be applied without it.

        placeholder?.let {
            canvas.nativeCanvas.drawPicture(it, null, null)
        }
    }

    internal fun drawPlaceholder(canvas: SkCanvas) {
        val recordedPicture = picture ?: return
        var restoreCount = 0

        configureOutline()
        updateMatrix()

        canvas.save()
        restoreCount++

        if (!matrix.isIdentity()) {
            canvas.concat(matrix.toSkiaMatrix44())
        }
        canvas.translate(topLeft.x.toFloat(), topLeft.y.toFloat())

        if (shadowElevation > 0) {
            drawShadow(canvas)
        }

        if (clip) {
            canvas.save()
            restoreCount++
            canvas.clipOutline(internalOutline, bounds)
        }

        val useLayer = requiresLayer()
        if (useLayer) {
            canvas.saveLayer(
                bounds,
                SkPaint().apply {
                    setAlphaf(this@GraphicsLayer.alpha)
                    imageFilter = this@GraphicsLayer.renderEffect?.asSkiaImageFilter()
                    colorFilter = this@GraphicsLayer.colorFilter?.asSkiaColorFilter()
                    blendMode = this@GraphicsLayer.blendMode.toSkia()
                }
            )
            restoreCount++
        } else {
            canvas.save()
            restoreCount++
        }

        canvas.drawPicture(recordedPicture, null, null)

        repeat(restoreCount) {
            canvas.restore()
        }
    }

    private fun onAddedToParentLayer() {
        parentLayerUsages++
    }

    private fun onRemovedFromParentLayer() {
        parentLayerUsages--
        discardContentIfReleasedAndHaveNoParentLayerUsages()
    }

    internal fun release() {
        if (!isReleased) {
            isReleased = true
            discardContentIfReleasedAndHaveNoParentLayerUsages()
        }
    }

    actual var pivotOffset: Offset = Offset.Unspecified
        set(value) {
            field = value
            invalidateMatrix()
        }

    actual var blendMode: BlendMode = BlendMode.SrcOver

    actual var colorFilter: ColorFilter? = null

    private fun resetOutlineParams() {
        internalOutline = null
        outlinePath = null
        roundRectOutlineSize = Size.Unspecified
        roundRectOutlineTopLeft = Offset.Zero
        roundRectCornerRadius = 0f
        outlineDirty = true
    }

    actual fun setRoundRectOutline(
        topLeft: Offset,
        size: Size,
        cornerRadius: Float
    ) {
        resetOutlineParams()
        this.roundRectOutlineTopLeft = topLeft
        this.roundRectOutlineSize = size
        this.roundRectCornerRadius = cornerRadius
    }

    actual fun setPathOutline(path: Path) {
        resetOutlineParams()
        this.outlinePath = path
    }

    actual val outline: Outline
        get() = configureOutline()

    actual fun setRectOutline(
        topLeft: Offset,
        size: Size
    ) {
        setRoundRectOutline(topLeft, size, 0f)
    }

    private fun updateMatrix() {
        if (!matrixDirty) return

        val pivotX: Float
        val pivotY: Float
        if (pivotOffset.isUnspecified) {
            pivotX = size.width / 2f
            pivotY = size.height / 2f
        } else {
            pivotX = pivotOffset.x
            pivotY = pivotOffset.y
        }
        prepareTransformationMatrix(
            matrix = matrix,
            pivotX = pivotX,
            pivotY = pivotY,
            translationX = translationX,
            translationY = translationY,
            rotationX = rotationX,
            rotationY = rotationY,
            rotationZ = rotationZ,
            scaleX = scaleX,
            scaleY = scaleY,
            cameraDistance = cameraDistance
        )
        matrixDirty = false
    }

    actual var isReleased: Boolean = false
        private set

    private fun discardContentIfReleasedAndHaveNoParentLayerUsages() {
        if (isReleased && parentLayerUsages == 0) {
            picture?.close()
            pictureRecorder.close()

            // discarding means we don't draw children layer anymore and need to remove dependencies:
            childDependenciesTracker.removeDependencies {
                it.onRemovedFromParentLayer()
            }
        }
    }

    actual var ambientShadowColor: Color = Color.Black

    actual var spotShadowColor: Color = Color.Black

    private fun requiresLayer(): Boolean {
        val alphaNeedsLayer = alpha < 1f && compositingStrategy != CompositingStrategy.ModulateAlpha
        val hasColorFilter = colorFilter != null
        val hasBlendMode = blendMode != BlendMode.SrcOver
        val hasRenderEffect = renderEffect != null
        val offscreenBufferRequested = compositingStrategy == CompositingStrategy.Offscreen
        return alphaNeedsLayer || hasColorFilter || hasBlendMode || hasRenderEffect ||
            offscreenBufferRequested
    }

    private fun drawShadow(canvas: SkCanvas) {
        val path = when (val tmpOutline = internalOutline) {
            is Outline.Rectangle -> Path().apply { addRect(tmpOutline.rect) }
            is Outline.Rounded -> Path().apply { addRoundRect(tmpOutline.roundRect) }
            is Outline.Generic -> tmpOutline.path
            else -> return
        }

        val zParams = Point3(0f, 0f, shadowElevation)
        val ambientAlpha = context.lightInfo.ambientShadowAlpha * alpha
        val spotAlpha = context.lightInfo.spotShadowAlpha * alpha
        val ambientColor = ambientShadowColor.copy(alpha = ambientAlpha)
        val spotColor = spotShadowColor.copy(alpha = spotAlpha)

        return ShadowUtils.drawShadow(
            canvas = canvas,
            path = path.asSkiaPath(),
            zPlaneParams = zParams,
            lightPos = context.lightGeometry.center,
            lightRadius = context.lightGeometry.radius,
            ambientColor = ambientColor.toArgb(),
            spotColor = spotColor.toArgb(),
            transparentOccluder = alpha < 1f,
            geometricOnly = false
        )
    }

    actual suspend fun toImageBitmap(): ImageBitmap =
        ImageBitmap(size.width, size.height).apply { draw(Canvas(this), null) }
}


private fun SkCanvas.clipOutline(outline: Outline?, bounds: SkRect) = when (outline) {
    is Outline.Rectangle -> clipRect(outline.rect.toSkiaRect(), antiAlias = true)
    is Outline.Rounded -> clipRRect(outline.roundRect.toSkiaRRect(), antiAlias = true)
    is Outline.Generic -> clipPath(outline.path.asSkiaPath())
    null -> clipRect(bounds, antiAlias = true)
}

// The goal with selecting the size of the rectangle here is to avoid limiting the
// drawable area as much as possible.
// Due to https://partnerissuetracker.corp.google.com/issues/324465764 we have to
// leave room for scale between the values we specify here and Float.MAX_VALUE.
// The maximum possible scale that can be applied to the canvas will be
// Float.MAX_VALUE divided by the largest value below.
// 2^30 was chosen because it's big enough, leaves quite a lot of room between it
// and Float.MAX_VALUE, and also lets the width and height fit into int32 (just in
// case).
private const val PICTURE_MIN_VALUE = -(1 shl 30).toFloat()
private const val PICTURE_MAX_VALUE = ((1 shl 30)-1).toFloat()
private val PICTURE_BOUNDS = SkRect.makeLTRB(
    l = PICTURE_MIN_VALUE,
    t = PICTURE_MIN_VALUE,
    r = PICTURE_MAX_VALUE,
    b = PICTURE_MAX_VALUE
)
