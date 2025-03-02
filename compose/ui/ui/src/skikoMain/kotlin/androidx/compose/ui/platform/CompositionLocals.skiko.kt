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

package androidx.compose.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.InternalComposeUiApi
import androidx.lifecycle.LifecycleOwner

/**
 * The CompositionLocal containing the current [LifecycleOwner].
 */
@Deprecated(
    "Moved to lifecycle-runtime-compose library in androidx.lifecycle.compose package.",
    ReplaceWith("androidx.lifecycle.compose.LocalLifecycleOwner"),
)
actual val LocalLifecycleOwner get() = androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * The CompositionLocal that provides information about Screen Reader state associated with current
 * scene.
 */
@InternalComposeUiApi
val LocalPlatformScreenReader = staticCompositionLocalOf<PlatformScreenReader> {
    error("CompositionLocal LocalPlatformScreenReader not present")
}
