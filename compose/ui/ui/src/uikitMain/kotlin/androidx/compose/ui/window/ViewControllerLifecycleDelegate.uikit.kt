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

package androidx.compose.ui.window

import androidx.compose.ui.platform.IOSLifecycleOwner
import androidx.compose.ui.uikit.utils.CMPViewControllerLifecycleDelegateProtocol
import androidx.lifecycle.Lifecycle.State
import platform.Foundation.NSNotificationCenter
import platform.darwin.NSObject

internal class ViewControllerLifecycleDelegate(
    private val lifecycleOwner: IOSLifecycleOwner,
    notificationCenter: NSNotificationCenter = NSNotificationCenter.defaultCenter
): NSObject(), CMPViewControllerLifecycleDelegateProtocol {

    private var isViewAppeared = false
    private var isAppForeground = ApplicationForegroundStateListener.isApplicationForeground
    private var isAppActive = isAppForeground
    private var isDisposed = false

    private val applicationForegroundStateListener =
        ApplicationForegroundStateListener(notificationCenter) { isForeground ->
            isAppForeground = isForeground
            updateLifecycleState()
        }

    private val applicationActiveStateListener =
        ApplicationActiveStateListener(notificationCenter) { isActive ->
            isAppActive = isActive
            updateLifecycleState()
        }

    init {
        updateLifecycleState()
    }

    override fun viewControllerWillDealloc() {
        applicationForegroundStateListener.dispose()
        applicationActiveStateListener.dispose()
        lifecycleOwner.viewModelStore.clear()
        isDisposed = true
        updateLifecycleState()
    }

    override fun viewControllerWillAppear() {
        isViewAppeared = true
        updateLifecycleState()
    }

    override fun viewControllerDidDisappear() {
        isViewAppeared = false
        updateLifecycleState()
    }

    private fun updateLifecycleState() {
        lifecycleOwner.lifecycle.currentState = when {
            isDisposed -> State.DESTROYED
            isViewAppeared && isAppForeground && isAppActive -> State.RESUMED
            isViewAppeared && isAppForeground && !isAppActive -> State.STARTED
            else -> State.CREATED
        }
    }
}
