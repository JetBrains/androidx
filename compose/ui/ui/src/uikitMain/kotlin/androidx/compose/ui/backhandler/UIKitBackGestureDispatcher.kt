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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import kotlin.math.abs
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UIGestureRecognizerStateCancelled
import platform.UIKit.UIGestureRecognizerStateChanged
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UIGestureRecognizerStateFailed
import platform.UIKit.UIRectEdgeLeft
import platform.UIKit.UIRectEdgeRight
import platform.UIKit.UIScreenEdgePanGestureRecognizer
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.darwin.NSObject

private const val BACK_GESTURE_SCREEN_SIZE = 0.3
private const val BACK_GESTURE_VELOCITY = 100

@OptIn(ExperimentalComposeUiApi::class)
internal class UIKitBackGestureDispatcher(
    density: Density,
    getTopLeftOffsetInWindow: () -> IntOffset
) : BackGestureDispatcher() {
    private val iosGestureHandler = UiKitScreenEdgePanGestureHandler(
        density = density,
        getTopLeftOffsetInWindow = getTopLeftOffsetInWindow,
        getListener = { activeListener }
    )

    private val leftEdgePanGestureRecognizer = UIKitBackGestureRecognizer(
        target = iosGestureHandler,
        action = NSSelectorFromString(UiKitScreenEdgePanGestureHandler::handleEdgePan.name + ":")
    ).apply {
        edges = UIRectEdgeLeft
    }

    private val rightEdgePanGestureRecognizer = UIKitBackGestureRecognizer(
        target = iosGestureHandler,
        action = NSSelectorFromString(UiKitScreenEdgePanGestureHandler::handleEdgePan.name + ":")
    ).apply {
        edges = UIRectEdgeRight
    }

    override fun activeListenerChanged() {
        val listener = activeListener
        leftEdgePanGestureRecognizer.enabled = listener != null
        rightEdgePanGestureRecognizer.enabled = listener != null
    }

    fun onDidMoveToWindow(window: UIWindow?, composeRootView: UIView) {
        if (window == null) {
            removeGestureListeners()
        } else {
            var view: UIView = composeRootView
            while (view.superview != window) {
                view = requireNotNull(view.superview) {
                    "Window is not null, but superview is null for ${view.debugDescription}"
                }
            }
            addGestureListeners(view)
        }
    }

    private fun addGestureListeners(view: UIView) {
        view.addGestureRecognizer(leftEdgePanGestureRecognizer)
        view.addGestureRecognizer(rightEdgePanGestureRecognizer)
    }

    private fun removeGestureListeners() {
        leftEdgePanGestureRecognizer.view?.removeGestureRecognizer(leftEdgePanGestureRecognizer)
        rightEdgePanGestureRecognizer.view?.removeGestureRecognizer(rightEdgePanGestureRecognizer)
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type == KeyEventType.KeyUp && event.key == Key.Escape) {
            activeListener?.let {
                it.onStarted()
                it.onCompleted()
            }

            return true
        } else {
            return false
        }
    }
}

@OptIn(BetaInteropApi::class, ExperimentalComposeUiApi::class)
private class UiKitScreenEdgePanGestureHandler(
    private val density: Density,
    private val getTopLeftOffsetInWindow: () -> IntOffset,
    private val getListener: () -> BackGestureListener?
) : NSObject() {
    @ObjCAction
    fun handleEdgePan(recognizer: UIScreenEdgePanGestureRecognizer) {
        val listener = getListener() ?: return
        val view = recognizer.view ?: return
        when (recognizer.state) {
            UIGestureRecognizerStateBegan -> {
                listener.onStarted()
            }

            UIGestureRecognizerStateChanged -> {
                val touchLocation = recognizer.locationOfTouch(0u, view)
                touchLocation.useContents {
                    view.bounds.useContents {
                        val topLeft = getTopLeftOffsetInWindow()
                        val touch = DpOffset(x.dp, y.dp).toOffset(density)

                        val edge = recognizer.edges
                        val absX: Double = if (edge == UIRectEdgeLeft) x else size.width - x
                        val event = BackEventCompat(
                            touchX = touch.x - topLeft.x,
                            touchY = touch.y - topLeft.y,
                            progress = (absX / size.width).toFloat(),
                            swipeEdge = if (edge == UIRectEdgeLeft) {
                                BackEventCompat.EDGE_LEFT
                            } else {
                                BackEventCompat.EDGE_RIGHT
                            }
                        )

                        listener.onProgressed(event)
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
                                velX > BACK_GESTURE_VELOCITY -> listener.onCompleted()
                                //if movement is backward
                                velX < -10 -> listener.onCancelled()
                                //if there is no movement, or the movement is slow,
                                //but the touch is already more than BACK_GESTURE_SCREEN_SIZE
                                abs(x) >= size.width * BACK_GESTURE_SCREEN_SIZE -> listener.onCompleted()
                                else -> listener.onCancelled()
                            }
                        }
                    }
                }
            }

            UIGestureRecognizerStateCancelled -> {
                listener.onCancelled()
            }

            UIGestureRecognizerStateFailed -> {
                listener.onCancelled()
            }
        }
    }
}

/**
 * A special gesture recognizer that can cancel touches in the Compose scene.
 * See [androidx.compose.ui.window.UserInputGestureRecognizer.canBePreventedByGestureRecognizer]
 */
internal class UIKitBackGestureRecognizer(
    target: Any?, action: CPointer<out CPointed>?
) : UIScreenEdgePanGestureRecognizer(target = target, action = action)