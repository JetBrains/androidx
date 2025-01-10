/*
 * Copyright 2020 The Android Open Source Project
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
import kotlin.math.abs
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Matrix44

internal fun identityMatrix33() = Matrix33(
    1f, 0f, 0f,
    0f, 1f, 0f,
    0f, 0f, 1f
)

internal fun Matrix.toSkiaMatrix44() = Matrix44(
    this[0, 0],
    this[1, 0],
    this[2, 0],
    this[3, 0],

    this[0, 1],
    this[1, 1],
    this[2, 1],
    this[3, 1],

    this[0, 2],
    this[1, 2],
    this[2, 2],
    this[3, 2],

    this[0, 3],
    this[1, 3],
    this[2, 3],
    this[3, 3]
)

@InternalComposeUiApi
fun prepareTransformationMatrix(
    matrix: Matrix,
    pivotX: Float,
    pivotY: Float,
    translationX: Float,
    translationY: Float,
    rotationX: Float,
    rotationY: Float,
    rotationZ: Float,
    scaleX: Float,
    scaleY: Float,
    cameraDistance: Float,
) {
    matrix.reset()
    matrix.translate(x = -pivotX, y = -pivotY)
    matrix *= Matrix().apply {
        rotateZ(rotationZ)
        rotateY(rotationY)
        rotateX(rotationX)
        scale(scaleX, scaleY)
    }
    // Perspective transform should be applied only in case of rotations to avoid
    // multiply application in hierarchies.
    // See Android's frameworks/base/libs/hwui/RenderProperties.cpp for reference
    if (!rotationX.isZero() || !rotationY.isZero()) {
        matrix *= Matrix().apply {
            // The camera location is passed in inches, set in pt
            val depth = cameraDistance * 72f
            this[2, 3] = -1f / depth
        }
    }
    matrix *= Matrix().apply {
        translate(x = pivotX + translationX, y = pivotY + translationY)
    }

    // Third column and row are irrelevant for 2D space.
    // Zeroing required to get correct inverse transformation matrix.
    matrix[2, 0] = 0f
    matrix[2, 1] = 0f
    matrix[2, 3] = 0f
    matrix[0, 2] = 0f
    matrix[1, 2] = 0f
    matrix[3, 2] = 0f
}

// Copy from Android's frameworks/base/libs/hwui/utils/MathUtils.h
private const val NON_ZERO_EPSILON = 0.001f
private inline fun Float.isZero(): Boolean = abs(this) <= NON_ZERO_EPSILON
