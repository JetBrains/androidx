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

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import kotlin.math.abs
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow.SUSPEND
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UIGestureRecognizerStateCancelled
import platform.UIKit.UIGestureRecognizerStateChanged
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UIGestureRecognizerStateFailed
import platform.UIKit.UIRectEdge
import platform.UIKit.UIRectEdgeLeft
import platform.UIKit.UIRectEdgeRight
import platform.UIKit.UIScreenEdgePanGestureRecognizer
import platform.UIKit.UIView
import platform.darwin.NSObject

internal class BackGestureListener(
    private val scope: CoroutineScope,
    private val onBack: suspend (progress: Flow<UIKitBackEvent>) -> Unit
) {
    private var channel: Channel<UIKitBackEvent>? = null
    private var progressJob: Job? = null

    fun begun() {
        channel = Channel<UIKitBackEvent>(capacity = BUFFERED, onBufferOverflow = SUSPEND).also {
            progressJob = provideProgress(it.consumeAsFlow())
        }
    }

    fun changed(touchX: Float, touchY: Float, progress: Float, edge: UIRectEdge) {
        channel?.trySend(
            UIKitBackEvent(
                touchX = touchX,
                touchY = touchY,
                progress = progress,
                swipeEdge = if (edge == UIRectEdgeLeft) UIKitBackEvent.EDGE_LEFT else UIKitBackEvent.EDGE_RIGHT
            )
        )
    }

    fun ended() {
        channel?.close()
    }

    fun canceled() {
        channel?.cancel(CancellationException("onBack cancelled"))
        channel = null

        progressJob?.cancel()
        progressJob = null
    }

    private fun provideProgress(flow: Flow<UIKitBackEvent>): Job = scope.launch {
        var completed = false
        onBack(flow.onCompletion { completed = true })
        check(completed) { "You must collect the progress flow" }
    }
}

private const val BACK_GESTURE_SCREEN_SIZE = 0.3
private const val BACK_GESTURE_VELOCITY = 100

@OptIn(BetaInteropApi::class)
internal class BackGestureDispatcher(
    private val density: Density,
    private val getTopLeftOffsetInWindow: () -> IntOffset
) : NSObject() {
    private val listeners = mutableListOf<BackGestureListener>()
    private var activeListener: BackGestureListener? = null

    val leftEdgePanGestureRecognizer = UIScreenEdgePanGestureRecognizer(
        target = this,
        action = NSSelectorFromString(::handleEdgePan.name + ":")
    ).apply {
        edges = UIRectEdgeLeft
    }

    val rightEdgePanGestureRecognizer = UIScreenEdgePanGestureRecognizer(
        target = this,
        action = NSSelectorFromString(::handleEdgePan.name + ":")
    ).apply {
        edges = UIRectEdgeRight
    }

    fun addListener(listener: BackGestureListener) {
        if (listeners.contains(listener)) return
        listeners.add(listener)
        updateUIViewListener()
    }

    fun removeListener(listener: BackGestureListener) {
        listeners.remove(listener)
        updateUIViewListener()
    }

    fun setView(rootView: UIView) {
        rootView.addGestureRecognizer(leftEdgePanGestureRecognizer)
        rootView.addGestureRecognizer(rightEdgePanGestureRecognizer)
    }

    private fun updateUIViewListener() {
        val listener = listeners.lastOrNull()
        activeListener = listener
        leftEdgePanGestureRecognizer.enabled = listener != null
        rightEdgePanGestureRecognizer.enabled = listener != null
    }

    @Suppress("unused")
    @ObjCAction
    fun handleEdgePan(recognizer: UIScreenEdgePanGestureRecognizer) {
        val listener = activeListener ?: return
        val view = recognizer.view ?: return
        when (recognizer.state) {
            UIGestureRecognizerStateBegan -> {
                listener.begun()
            }

            UIGestureRecognizerStateChanged -> {
                val touchLocation = recognizer.locationOfTouch(0u, view)
                touchLocation.useContents {
                    view.bounds.useContents {
                        val topLeft = getTopLeftOffsetInWindow()
                        val touch = DpOffset(x.dp, y.dp).toOffset(density)

                        val edge = recognizer.edges
                        val absX: Double = if (edge == UIRectEdgeLeft) x else size.width - x

                        listener.changed(
                            touchX = touch.x - topLeft.x,
                            touchY = touch.y - topLeft.y,
                            progress = (absX / size.width).toFloat(),
                            edge = edge
                        )
                    }
                }
            }

            UIGestureRecognizerStateEnded -> {
                val translation = recognizer.translationInView(view = view)
                val velocity = recognizer.velocityInView(view)
                velocity.useContents velocity@{
                    translation.useContents {
                        view.bounds.useContents {
                            val edge = recognizer.edges
                            val velX = if (edge == UIRectEdgeLeft) this@velocity.x else -this@velocity.x
                            when {
                                //if movement is fast in the right direction
                                velX > BACK_GESTURE_VELOCITY -> listener.ended()
                                //if movement is backward
                                velX < -10 -> listener.canceled()
                                //if there is no movement, or the movement is slow,
                                //but the touch is already more than BACK_GESTURE_SCREEN_SIZE
                                abs(x) >= size.width * BACK_GESTURE_SCREEN_SIZE -> listener.ended()
                                else -> listener.canceled()
                            }
                        }
                    }
                }
            }

            UIGestureRecognizerStateCancelled -> {
                listener.canceled()
            }

            UIGestureRecognizerStateFailed -> {
                listener.canceled()
            }
        }
    }
}
