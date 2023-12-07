/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.scene.ComposeSceneContext
import androidx.compose.ui.scene.ComposeSceneLayer
import androidx.compose.ui.uikit.ComposeUIViewControllerConfiguration
import androidx.compose.ui.unit.LayoutDirection
import kotlin.coroutines.CoroutineContext
import platform.UIKit.UIView
import platform.UIKit.UIViewController

internal interface ComposeContainer {
    val rootViewController: UIViewController
    val layoutDirection: LayoutDirection
    val focusStack: FocusStack<UIView>
    val configuration: ComposeUIViewControllerConfiguration
    fun createLayer(
        currentComposeSceneContext: ComposeSceneContext,
        focusable: Boolean,
        sceneBridge: ComposeSceneMediator,
        coroutineDispatcher: CoroutineContext,
    ): ComposeSceneLayer

    @Composable
    fun ProvideRootCompositionLocals(content: @Composable () -> Unit)
}
