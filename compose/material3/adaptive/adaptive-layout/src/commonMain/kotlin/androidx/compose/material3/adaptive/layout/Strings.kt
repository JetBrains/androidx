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

package androidx.compose.material3.adaptive.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import kotlin.jvm.JvmInline

@Immutable
@JvmInline
internal expect value class Strings(val value: Int) {
    companion object {
        val defaultPaneExpansionDragHandleContentDescription: Strings
        val defaultPaneExpansionDragHandleActionDescription: Strings
        val defaultPaneExpansionProportionAnchorDescription: Strings
        val defaultPaneExpansionStartOffsetAnchorDescription: Strings
        val defaultPaneExpansionEndOffsetAnchorDescription: Strings
    }
}

@Composable @ReadOnlyComposable internal expect fun getString(string: Strings): String

@Composable
@ReadOnlyComposable
internal expect fun getString(string: Strings, vararg formatArgs: Any): String
