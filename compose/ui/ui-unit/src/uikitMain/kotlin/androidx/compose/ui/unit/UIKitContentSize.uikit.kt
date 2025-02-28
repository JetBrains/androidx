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

import platform.UIKit.UIContentSizeCategory
import platform.UIKit.UIContentSizeCategoryExtraSmall
import platform.UIKit.UIContentSizeCategorySmall
import platform.UIKit.UIContentSizeCategoryMedium
import platform.UIKit.UIContentSizeCategoryLarge
import platform.UIKit.UIContentSizeCategoryExtraLarge
import platform.UIKit.UIContentSizeCategoryExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryExtraExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityMedium
import platform.UIKit.UIContentSizeCategoryAccessibilityLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityExtraLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityExtraExtraExtraLarge

internal value class UIKitContentSize(val fontScale : Float) {

    companion object {

        val Default get() = Large

        val ExtraSmall = UIKitContentSize(0.8f)
        val Small = UIKitContentSize(0.85f)
        val Medium = UIKitContentSize(0.9f)
        val Large = UIKitContentSize(1f)
        val XL = UIKitContentSize(1.1f)
        val XXL = UIKitContentSize(1.2f)
        val XXXL = UIKitContentSize(1.3f)
        val AccessibleMedium = UIKitContentSize(1.4f)
        val AccessibleLarge = UIKitContentSize(1.5f)
        val AccessibleXL = UIKitContentSize(1.6f)
        val AccessibleXXL = UIKitContentSize(1.7f)
        val AccessibleXXXL = UIKitContentSize(1.8f)

        fun fromNative(size: UIContentSizeCategory): UIKitContentSize {
            return when (size) {
                UIContentSizeCategoryExtraSmall -> ExtraSmall
                UIContentSizeCategorySmall -> Small
                UIContentSizeCategoryMedium -> Medium
                UIContentSizeCategoryLarge -> Large
                UIContentSizeCategoryExtraLarge -> XL
                UIContentSizeCategoryExtraExtraLarge -> XXL
                UIContentSizeCategoryExtraExtraExtraLarge -> XXXL
                UIContentSizeCategoryAccessibilityMedium -> AccessibleMedium
                UIContentSizeCategoryAccessibilityLarge -> AccessibleLarge
                UIContentSizeCategoryAccessibilityExtraLarge -> AccessibleXL
                UIContentSizeCategoryAccessibilityExtraExtraLarge -> AccessibleXXL
                UIContentSizeCategoryAccessibilityExtraExtraExtraLarge -> AccessibleXXXL
                else -> Default
            }
        }
    }
}