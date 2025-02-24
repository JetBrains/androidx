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

import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import kotlin.math.absoluteValue
import kotlin.math.sign

/**
 * A lookup table for non-linear font scaling. Converts font sizes given in "sp" dimensions to a
 * "dp" dimension according to a non-linear curve by interpolating values in a lookup table.
 */
internal class FontScaleConverterTable(
    val fromSp: List<Float>,
    val toDp: List<Float>
) : FontScaleConverter {

    init {
        require(!(fromSp.size != toDp.size || fromSp.isEmpty())) {
            "Array lengths must match and be nonzero"
        }
    }


    override fun convertDpToSp(dp: Float): Float {
        return lookupAndInterpolate(dp, toDp, fromSp)
    }

    override fun convertSpToDp(sp: Float): Float {
        return lookupAndInterpolate(sp, fromSp, toDp)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as FontScaleConverterTable

        if (fromSp != other.fromSp) return false
        if (toDp != other.toDp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fromSp.hashCode()
        result = 31 * result + toDp.hashCode()
        return result
    }

    companion object {
        private fun lookupAndInterpolate(
            sourceValue: Float,
            sourceValues: List<Float>,
            targetValues: List<Float>
        ): Float {
            val sourceValuePositive = sourceValue.absoluteValue
            // TODO(b/247861374): find a match at a higher index?
            val sign = sign(sourceValue)
            // We search for exact matches only, even if it's just a little off. The interpolation
            // will
            // handle any non-exact matches.
            val index = sourceValues.binarySearch {  it.compareTo(sourceValuePositive) }
            return if (index >= 0) {
                // exact match, return the matching dp
                sign * targetValues[index]
            } else {
                // must be a value in between index and index + 1: interpolate.
                val lowerIndex = -(index + 1) - 1
                val startSp: Float
                val endSp: Float
                val startDp: Float
                val endDp: Float
                if (lowerIndex >= sourceValues.size - 1) {
                    // It's past our lookup table. Determine the last elements' scaling factor and
                    // use.
                    startSp = sourceValues[sourceValues.size - 1]
                    startDp = targetValues[sourceValues.size - 1]
                    if (startSp == 0f) return 0f
                    val scalingFactor = startDp / startSp
                    return sourceValue * scalingFactor
                } else if (lowerIndex == -1) {
                    // It's smaller than the smallest value in our table. Interpolate from 0.
                    startSp = 0f
                    startDp = 0f
                    endSp = sourceValues[0]
                    endDp = targetValues[0]
                } else {
                    startSp = sourceValues[lowerIndex]
                    endSp = sourceValues[lowerIndex + 1]
                    startDp = targetValues[lowerIndex]
                    endDp = targetValues[lowerIndex + 1]
                }
                sign * constrainedMap(
                    startDp,
                    endDp,
                    startSp,
                    endSp,
                    sourceValuePositive
                )
            }
        }
    }
}

/**
 * Inverse of [lerp]. More precisely, returns the interpolation scalar (s) that satisfies the
 * equation: `value = `[lerp]`(a, b, s)`
 *
 * If `a == b`, then this function will return 0.
 */
private fun lerpInv(a: Float, b: Float, value: Float): Float {
    return if (a != b) (value - a) / (b - a) else 0.0f
}

/**
 * Calculates a value in [rangeMin, rangeMax] that maps value in [valueMin, valueMax] to
 * returnVal in [rangeMin, rangeMax].
 *
 * Always returns a constrained value in the range [rangeMin, rangeMax], even if value is
 * outside [valueMin, valueMax].
 *
 * Eg: constrainedMap(0f, 100f, 0f, 1f, 0.5f) = 50f constrainedMap(20f, 200f, 10f, 20f, 20f) =
 * 200f constrainedMap(20f, 200f, 10f, 20f, 50f) = 200f constrainedMap(10f, 50f, 10f, 20f, 5f) =
 * 10f
 *
 * @param rangeMin minimum of the range that should be returned.
 * @param rangeMax maximum of the range that should be returned.
 * @param valueMin minimum of range to map `value` to.
 * @param valueMax maximum of range to map `value` to.
 * @param value to map to the range [`valueMin`, `valueMax`]. Note, can be outside this range,
 *   resulting in a clamped value.
 * @return the mapped value, constrained to [`rangeMin`, `rangeMax`].
 */
internal fun constrainedMap(
    rangeMin: Float,
    rangeMax: Float,
    valueMin: Float,
    valueMax: Float,
    value: Float
): Float {
    return lerp(
        rangeMin,
        rangeMax,
        lerpInv(valueMin, valueMax, value).fastCoerceIn(0f, 1f)
    )
}
