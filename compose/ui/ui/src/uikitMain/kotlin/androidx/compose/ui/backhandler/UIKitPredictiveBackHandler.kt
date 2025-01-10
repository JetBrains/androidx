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

package androidx.compose.ui.backhandler

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

internal val LocalBackGestureDispatcher =
    staticCompositionLocalOf<BackGestureDispatcher> {
        error("CompositionLocal BackGestureDispatcher not provided")
    }

@Composable
fun UIKitPredictiveBackHandler(
    enabled: Boolean = true,
    onBack: suspend (progress: Flow<UIKitBackEvent>) -> Unit
) {
    val backGestureDispatcher = LocalBackGestureDispatcher.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val listener = remember { BackGestureListener(scope, onBack) }

    LaunchedEffect(enabled, lifecycleOwner, backGestureDispatcher) {
        if (enabled) {
            lifecycleOwner.lifecycle.currentStateFlow.collect { state ->
                when (state) {
                    Lifecycle.State.STARTED, Lifecycle.State.RESUMED -> {
                        backGestureDispatcher.addListener(listener)
                    }
                    else -> {
                        backGestureDispatcher.removeListener(listener)
                    }
                }
            }
        } else {
            backGestureDispatcher.removeListener(listener)
        }
    }

    DisposableEffect(backGestureDispatcher) {
        onDispose { backGestureDispatcher.removeListener(listener) }
    }
}

@Composable
fun UIKitBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    UIKitPredictiveBackHandler(enabled) { progress ->
        try {
            progress.collect {
                //ignore
            }
            onBack()
        } catch (e: CancellationException) {
            //ignore
        }
    }
}
