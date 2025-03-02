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

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

@OptIn(ExperimentalComposeUiApi::class)
internal class DesktopBackGestureDispatcher: BackGestureDispatcher() {
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
            return onBack()
        } else {
            return false
        }
    }

    private fun onBack(): Boolean {
        activeListener?.let {
            it.onStarted()
            it.onCompleted()
            return true
        } ?: return false
    }
}