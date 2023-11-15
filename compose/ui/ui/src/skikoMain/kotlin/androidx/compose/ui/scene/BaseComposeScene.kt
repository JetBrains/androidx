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

package androidx.compose.ui.scene

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputEvent
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.node.SnapshotInvalidationTracker
import androidx.compose.ui.platform.GlobalSnapshotManager
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.Volatile

@OptIn(InternalComposeUiApi::class)
internal abstract class BaseComposeScene(
    coroutineContext: CoroutineContext,
    private val invalidate: () -> Unit,
) : ComposeScene {
    protected val snapshotInvalidationTracker = SnapshotInvalidationTracker(::invalidateIfNeeded)
    protected val inputHandler: ComposeSceneInputHandler =
        ComposeSceneInputHandler(::processPointerInputEvent, ::processKeyEvent)

    private val frameClock = BroadcastFrameClock(onNewAwaiters = ::invalidateIfNeeded)
    private val recomposer: ComposeSceneRecomposer =
        ComposeSceneRecomposer(coroutineContext, frameClock)
    private var composition: Composition? = null

    protected val compositionContext: CompositionContext
        get() = recomposer.compositionContext

    protected var isClosed = false
        private set

    private var isInvalidationDisabled = false
    private inline fun <T> postponeInvalidation(crossinline block: () -> T): T {
        check(!isClosed) { "ComposeScene is closed" }
        isInvalidationDisabled = true
        val result = try {
            // Try to get see the up-to-date state before running block
            // Note that this doesn't guarantee it, if sendApplyNotifications is called concurrently
            // in a different thread than this code.
            snapshotInvalidationTracker.sendAndPerformSnapshotChanges()
            block()
        } finally {
            isInvalidationDisabled = false
        }
        invalidateIfNeeded()
        return result
    }

    @Volatile
    private var hasPendingDraws = true
    protected fun invalidateIfNeeded() {
        hasPendingDraws = frameClock.hasAwaiters ||
            snapshotInvalidationTracker.hasInvalidations ||
            inputHandler.hasInvalidations
        if (hasPendingDraws && !isInvalidationDisabled && !isClosed && composition != null) {
            invalidate()
        }
    }

    override var compositionLocalContext: CompositionLocalContext? by mutableStateOf(null)

    /**
     * The mouse cursor position or null if cursor is not inside a scene.
     */
    internal val lastKnownCursorPosition: Offset?
        get() = inputHandler.lastKnownCursorPosition

    init {
        GlobalSnapshotManager.ensureStarted()
    }

    override fun close() {
        check(!isClosed) { "ComposeScene is already closed" }
        composition?.dispose()
        recomposer.cancel()
        isClosed = true
    }

    override fun hasInvalidations(): Boolean = hasPendingDraws || recomposer.hasPendingWork

    override fun setContent(content: @Composable () -> Unit) {
        check(!isClosed) { "ComposeScene is closed" }
        inputHandler.onChangeContent()

        composition?.dispose()
        composition = createComposition {
            CompositionLocalProvider( // TODO: Combine with other platform specifics
                LocalComposeScene provides this,
                content = content
            )
        }

        // Perform all pending work synchronously
        recomposer.flush()
    }

    override fun render(canvas: Canvas, nanoTime: Long) = postponeInvalidation {
        recomposer.flush()
        frameClock.sendFrame(nanoTime) // Recomposition

        snapshotInvalidationTracker.onLayout()
        measureAndLayout()
        inputHandler.onLayout()

        snapshotInvalidationTracker.onDraw()
        draw(canvas)
    }

    override fun sendPointerEvent(
        eventType: PointerEventType,
        position: Offset,
        scrollDelta: Offset,
        timeMillis: Long,
        type: PointerType,
        buttons: PointerButtons?,
        keyboardModifiers: PointerKeyboardModifiers?,
        nativeEvent: Any?,
        button: PointerButton?
    ) = postponeInvalidation {
        snapshotInvalidationTracker.onLayout()
        measureAndLayout()
        inputHandler.onLayout()

        inputHandler.onPointerEvent(
            eventType = eventType,
            position = position,
            scrollDelta = scrollDelta,
            timeMillis = timeMillis,
            type = type,
            buttons = buttons,
            keyboardModifiers = keyboardModifiers,
            nativeEvent = nativeEvent,
            button = button
        )
    }

    // TODO(demin): return Boolean (when it is consumed)
    // TODO(demin) verify that pressure is the same on Android and iOS
    override fun sendPointerEvent(
        eventType: PointerEventType,
        pointers: List<ComposeScenePointer>,
        buttons: PointerButtons,
        keyboardModifiers: PointerKeyboardModifiers,
        scrollDelta: Offset,
        timeMillis: Long,
        nativeEvent: Any?,
        button: PointerButton?,
    ) = postponeInvalidation {
        snapshotInvalidationTracker.onLayout()
        measureAndLayout()
        inputHandler.onLayout()

        inputHandler.onPointerEvent(
            eventType = eventType,
            pointers = pointers,
            buttons = buttons,
            keyboardModifiers = keyboardModifiers,
            scrollDelta = scrollDelta,
            timeMillis = timeMillis,
            nativeEvent = nativeEvent,
            button = button
        )
    }

    override fun sendKeyEvent(keyEvent: KeyEvent): Boolean = postponeInvalidation {
        inputHandler.onKeyEvent(keyEvent)
    }

    protected abstract fun createComposition(content: @Composable () -> Unit): Composition

    protected abstract fun processPointerInputEvent(event: PointerInputEvent)

    protected abstract fun processKeyEvent(keyEvent: KeyEvent): Boolean

    protected abstract fun measureAndLayout()

    protected abstract fun draw(canvas: Canvas)
}
