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

package androidx.compose.ui.uikit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import kotlin.math.floor
import kotlin.math.roundToLong
import platform.Foundation.NSTimeInterval
import platform.UIKit.UIColor
import platform.UIKit.UIView

internal val UIView.density: Density
    get() = Density(this)

internal fun Color.toUIColor(): UIColor? =
    if (this == Color.Unspecified) {
        null
    } else {
        UIColor(
            red = red.toDouble(),
            green = green.toDouble(),
            blue = blue.toDouble(),
            alpha = alpha.toDouble(),
        )
    }

internal fun NSTimeInterval.toNanoSeconds(): Long {
    // The calculation is split in two instead of
    // `(targetTimestamp * 1e9).toLong()`
    // to avoid losing precision for fractional part
    val integral = floor(this)
    val fractional = this - integral
    val secondsToNanos = 1_000_000_000L
    val nanos = integral.roundToLong() * secondsToNanos + (fractional * 1e9).roundToLong()
    return nanos
}
