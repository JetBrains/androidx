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

package androidx.compose.ui.window

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.SessionMutex
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSessionScope
import androidx.compose.ui.platform.WebTextInputService
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine

internal class WebTextInputSession(
    coroutineScope: CoroutineScope,
    private val webTextInputService: WebTextInputService
) : PlatformTextInputSessionScope, CoroutineScope by coroutineScope {

    private val innerSessionMutex = SessionMutex<Nothing?>()

    override suspend fun startInputMethod(request: PlatformTextInputMethodRequest) =
        innerSessionMutex.withSessionCancellingPrevious(
            // This session has no data, just init/dispose tasks.
            sessionInitializer = { null }
        ) {
            (suspendCancellableCoroutine<Nothing> { continuation ->
                webTextInputService.startInput(
                    value = request.state,
                    imeOptions = request.imeOptions,
                    onEditCommand = request.onEditCommand,
                    onImeActionPerformed = request.onImeAction ?: {}
                )

                continuation.invokeOnCancellation {
                    webTextInputService.stopInput()
                }
            })
        }

    @ExperimentalComposeUiApi
    override fun updateSelectionState(newState: TextFieldValue) {
        webTextInputService.updateState(oldValue = null, newState)
    }

    @ExperimentalComposeUiApi
    override fun notifyFocusedRect(rect: Rect) {
        webTextInputService.notifyFocusedRect(rect)
    }
}