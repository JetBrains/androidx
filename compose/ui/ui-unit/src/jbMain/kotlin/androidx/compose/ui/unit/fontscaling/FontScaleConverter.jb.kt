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


/**
 * A converter for non-linear font scaling. Converts font sizes given in "sp" dimensions to a "dp"
 * dimension according to a non-linear curve.
 *
 * This is meant to improve readability at larger font scales: larger fonts will scale up more
 * slowly than smaller fonts, so we don't get ridiculously huge fonts that don't fit on the screen.
 *
 * The thinking here is that large fonts are already big enough to read, but we still want to scale
 * them slightly to preserve the visual hierarchy when compared to smaller fonts.
 */
internal interface FontScaleConverter {
    /** Converts a dimension in "sp" to "dp". */
    fun convertSpToDp(sp: Float): Float

    /** Converts a dimension in "dp" back to "sp". */
    fun convertDpToSp(dp: Float): Float
}
