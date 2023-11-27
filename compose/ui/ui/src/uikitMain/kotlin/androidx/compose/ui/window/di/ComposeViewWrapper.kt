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

package androidx.compose.ui.window.di

import androidx.compose.runtime.State
import androidx.compose.ui.window.SkikoUIView
import platform.UIKit.UIView

internal interface ComposeViewWrapper {
    val view: UIView
    val isReadyToShowContent: State<Boolean>
    fun needRedraw()
    fun dispose()
    var isForcedToPresentWithTransactionEveryFrame:Boolean
}

internal class ComposeViewWrapperImpl(
    val skikoUIView: SkikoUIView,
) : ComposeViewWrapper {
    override val view: UIView = skikoUIView
    override val isReadyToShowContent: State<Boolean> = skikoUIView.isReadyToShowContent
    override fun needRedraw() = skikoUIView.needRedraw()
    override fun dispose() = skikoUIView.dispose()
    override var isForcedToPresentWithTransactionEveryFrame: Boolean
        get() = skikoUIView.isForcedToPresentWithTransactionEveryFrame
        set(value) {
            skikoUIView.isForcedToPresentWithTransactionEveryFrame = value
        }
}
