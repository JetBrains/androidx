/*
 * Copyright 2018 The Android Open Source Project
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
package androidx.lifecycle

import androidx.core.bundle.Bundle
import androidx.lifecycle.ViewModelProvider.Companion.VIEW_MODEL_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.savedstate.SavedState
import androidx.savedstate.SavedStateRegistryOwner
import kotlin.reflect.KClass


public actual abstract class AbstractSavedStateViewModelFactory :
    ViewModelProvider.Factory {

    actual constructor()

    actual constructor(
        owner: SavedStateRegistryOwner,
        defaultArgs: SavedState?
    )

    @Deprecated(level = DeprecationLevel.HIDDEN, message = "Use constructor with SavedState")
    actual constructor(
        owner: SavedStateRegistryOwner,
        defaultArgs: Bundle?
    )

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if no [VIEW_MODEL_KEY] provided by [ViewModelProvider]
     */
    public override fun <T : ViewModel> create(
        modelClass: KClass<T>,
        extras: CreationExtras
    ): T {
        val key = extras[VIEW_MODEL_KEY]
            ?: throw IllegalStateException(
                "VIEW_MODEL_KEY must always be provided by ViewModelProvider"
            )
        return create(key, modelClass, extras.createSavedStateHandle())
    }

    protected actual open fun <T : ViewModel> create(
        key: String,
        modelClass: KClass<T>,
        handle: SavedStateHandle
    ): T = throw NotImplementedError()
}
