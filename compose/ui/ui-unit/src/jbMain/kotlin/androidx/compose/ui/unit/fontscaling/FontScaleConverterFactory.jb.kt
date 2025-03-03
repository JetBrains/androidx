/*
 * Copyright 2025 The Android Open Source Project
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
package androidx.compose.ui.unit.fontscaling

import androidx.collection.SparseArrayCompat
import androidx.compose.ui.unit.NonLinearFontSizeAnchors
import androidx.compose.ui.unit.defaultFontScaleConverters
import androidx.compose.ui.unit.isNonLinearFontScalingActive
import androidx.compose.ui.util.lerp
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

internal object FontScaleConverterFactory {

    private const val ScaleKeyMultiplier = 100f

    private val lookupTablesWriteLock = reentrantLock()

    private val lookupTables = SparseArrayCompat<FontScaleConverter>().apply {
        lookupTablesWriteLock.withLock {
            defaultFontScaleConverters().forEach { (scale, converter) ->
                put(getKey(scale), converter)
            }
        }
    }

    /**
     * Finds a matching FontScaleConverter for the given fontScale factor.
     */
    fun forScale(fontScale: Float): FontScaleConverter? {
        if (!isNonLinearFontScalingActive(fontScale)) {
            return null
        }

        val index = lookupTables.indexOfKey(getKey(fontScale))
        if (index >= 0) {
            return lookupTables.valueAt(index)
        }
        // Didn't find an exact match: interpolate between two existing tables
        val lowerIndex = -(index + 1) - 1
        val higherIndex = lowerIndex + 1
        val converter = if (higherIndex >= lookupTables.size()) {
            // We have gone beyond our bounds and have nothing to interpolate between.
            // Just give them a straight linear table instead.
            // This works because when FontScaleConverter encounters a size beyond its bounds, it
            // calculates a linear fontScale factor using the ratio of the last element pair.
            FontScaleConverterTable(listOf(1f), listOf(fontScale))
        } else {
            val (startScale, startTable) = if (lowerIndex < 0) {
                // if we're in between 1x and the first table, interpolate between them.
                // (See b/336720383)
                1f to FontScaleConverterTable(NonLinearFontSizeAnchors, NonLinearFontSizeAnchors)
            } else {
                getScaleFromKey(lookupTables.keyAt(lowerIndex)) to lookupTables.valueAt(lowerIndex)
            }

            val endScale = getScaleFromKey(lookupTables.keyAt(higherIndex))

            createInterpolatedTableBetween(
                startTable,
                lookupTables.valueAt(higherIndex),
                constrainedMap(0f, 1f, startScale, endScale, fontScale)
            )
        }

        put(fontScale, converter)

        return converter
    }

    private fun createInterpolatedTableBetween(
        start: FontScaleConverter,
        end: FontScaleConverter,
        interpolationPoint: Float
    ): FontScaleConverter = FontScaleConverterTable(
        fromSp = NonLinearFontSizeAnchors,
        toDp = NonLinearFontSizeAnchors.map { sp ->
            lerp(start.convertSpToDp(sp), end.convertSpToDp(sp), interpolationPoint)
        }
    )


    private fun getKey(fontScale: Float): Int {
        return (fontScale * ScaleKeyMultiplier).toInt()
    }

    private fun getScaleFromKey(key: Int): Float {
        return key.toFloat() / ScaleKeyMultiplier
    }

    private fun put(scaleKey: Float, fontScaleConverter: FontScaleConverter) {
        lookupTablesWriteLock.withLock {
            lookupTables.put(getKey(scaleKey), fontScaleConverter)
        }
    }
}
