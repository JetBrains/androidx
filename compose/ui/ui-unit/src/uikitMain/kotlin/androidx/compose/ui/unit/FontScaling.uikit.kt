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

package androidx.compose.ui.unit

import androidx.compose.ui.unit.fontscaling.FontScaleConverter
import androidx.compose.ui.unit.fontscaling.FontScaleConverterTable

internal actual fun isNonLinearFontScalingActive(fontScale: Float): Boolean {
    return fontScale >= UIKitContentSize.ExtraSmall.fontScale && fontScale !in 0.97f..1.03f
}

internal actual val NonLinearFontSizeAnchors : List<Float> =
    listOf(11f, 12f, 13f, 15f, 16f, 17f, 20f, 22f, 28f, 34f, 100f)

// https://developer.apple.com/design/human-interface-guidelines/typography#iOS-iPadOS-Dynamic-Type-sizes
internal actual fun defaultFontScaleConverters() : Map<Float, FontScaleConverter> = mapOf(
    UIKitContentSize.ExtraSmall.fontScale to FontScaleConverterTable(
        fromSp = NonLinearFontSizeAnchors,
        toDp = listOf(11f, 11f, 12f, 12f, 13f, 14f, 17f, 19f, 25f, 31f, 100f)
    ),
    UIKitContentSize.Small.fontScale to FontScaleConverterTable(
        fromSp = NonLinearFontSizeAnchors,
        toDp = listOf(11f, 11f, 12f, 13f, 14f, 15f, 18f, 20f, 26f, 32f, 100f)
    ),
    UIKitContentSize.Medium.fontScale to FontScaleConverterTable(
        fromSp = NonLinearFontSizeAnchors,
        toDp = listOf(11f, 11f, 12f, 14f, 15f, 16f, 19f, 21f, 27f, 33f, 100f)
    ),
    UIKitContentSize.Large.fontScale to LinearFontScaleConverter(1f),
    UIKitContentSize.XL.fontScale to FontScaleConverterTable(
        fromSp = NonLinearFontSizeAnchors,
        toDp = listOf(13f, 14f, 15f, 17f, 18f, 19f, 22f, 24f, 30f, 36f, 100f)
    ),
    UIKitContentSize.XXL.fontScale to FontScaleConverterTable(
        fromSp = NonLinearFontSizeAnchors,
        toDp = listOf(15f, 16f, 17f, 19f, 20f, 21f, 24f, 26f, 32f, 38f, 100f)
    ),
    UIKitContentSize.XXXL.fontScale to FontScaleConverterTable(
        fromSp = NonLinearFontSizeAnchors,
        toDp = listOf(17f, 18f, 19f, 21f, 22f, 23f, 26f, 28f, 34f, 40f, 100f)
    ),
    UIKitContentSize.AccessibleMedium.fontScale to FontScaleConverterTable(
        fromSp = NonLinearFontSizeAnchors,
        toDp = listOf(20f, 22f, 23f, 25f, 26f, 28f, 31f, 34f, 38f, 44f, 100f)
    ),
    UIKitContentSize.AccessibleLarge.fontScale to FontScaleConverterTable(
        fromSp = NonLinearFontSizeAnchors,
        toDp = listOf(24f, 26f, 27f, 30f, 32f, 33f, 37f, 39f, 43f, 48f, 100f)
    ),
    UIKitContentSize.AccessibleXL.fontScale to FontScaleConverterTable(
        fromSp = NonLinearFontSizeAnchors,
        toDp = listOf(29f, 32f, 33f, 36f, 38f, 40f, 43f, 44f, 48f, 52f, 100f)
    ),
    UIKitContentSize.AccessibleXXL.fontScale to FontScaleConverterTable(
        fromSp = NonLinearFontSizeAnchors,
        toDp = listOf(34f, 37f, 38f, 42f, 44f, 47f, 49f, 50f, 53f, 56f, 100f)
    ),
    UIKitContentSize.AccessibleXXXL.fontScale to FontScaleConverterTable(
        fromSp = NonLinearFontSizeAnchors,
        toDp = listOf(40f, 43f, 44f, 49f, 51f, 53f, 55f, 56f, 58f, 60f, 100f)
    )
)