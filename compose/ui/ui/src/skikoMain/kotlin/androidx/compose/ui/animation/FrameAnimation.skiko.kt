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

package androidx.compose.ui.animation

import androidx.compose.runtime.withFrameNanos
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

internal suspend fun withAnimationProgress(duration: Duration, update: (Float) -> Unit) {
    fun easeInOutProgress(progress: Float) = if (progress < 0.5) {
        2 * progress * progress
    } else {
        (-2 * progress * progress) + (4 * progress) - 1
    }

    update(0f)

    var firstFrameTime = 0L
    var progressDuration = Duration.ZERO
    while (progressDuration < duration) {
        withFrameNanos { frameTime ->
            if (firstFrameTime == 0L) {
                firstFrameTime = frameTime
            }
            progressDuration = (frameTime - firstFrameTime).nanoseconds
            val progress = easeInOutProgress(
                min(1.0, progressDuration / duration).toFloat()
            )
            update(progress)
        }
    }
}
