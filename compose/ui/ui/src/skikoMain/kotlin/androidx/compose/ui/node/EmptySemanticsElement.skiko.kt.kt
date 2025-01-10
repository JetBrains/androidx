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

package androidx.compose.ui.node

import androidx.compose.ui.internal.identityHashCode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.EmptySemanticsModifier

internal class EmptySemanticsElement(private val node: EmptySemanticsModifier) :
    ModifierNodeElement<EmptySemanticsModifier>() {
    override fun create() = node

    override fun update(node: EmptySemanticsModifier) {}

    override fun InspectorInfo.inspectableProperties() {
        // Nothing to inspect.
    }

    override fun hashCode(): Int = identityHashCode(this)

    override fun equals(other: Any?) = (other === this)
}
