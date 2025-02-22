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

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.unit.fontscaling.FontScaleConverterFactory
import platform.UIKit.UIContentSizeCategoryUnspecified
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import platform.UIKit.UIWindow

@InternalComposeUiApi
fun Density(view : UIView): Density {

    val screen : UIScreen = if (view is UIWindow) {
        view.screen
    } else {
        view.window?.screen ?: UIScreen.mainScreen
    }

    val contentSizeCategory = view.window?.traitCollection
        ?.preferredContentSizeCategory ?: UIContentSizeCategoryUnspecified

    val fontScale = UIKitContentSize.fromNative(contentSizeCategory).fontScale

    return DensityWithConverter(
        density = screen.scale.toFloat(),
        fontScale = fontScale,
        converter = FontScaleConverterFactory.forScale(fontScale)
            ?: LinearFontScaleConverter(fontScale)
    )
}

