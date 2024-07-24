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

import androidx.compose.ui.draganddrop.cupertino.loadString
import androidx.compose.ui.platform.CUPERTINO_TOUCH_SLOP
import androidx.compose.ui.uikit.utils.CMPDragInteractionProxy
import androidx.compose.ui.uikit.utils.CMPDropInteractionProxy
import androidx.compose.ui.uikit.utils.CMPGestureRecognizer
import androidx.compose.ui.viewinterop.InteropView
import kotlin.coroutines.CoroutineContext
import kotlinx.cinterop.CValue
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIDragInteraction
import platform.UIKit.UIDragItem
import platform.UIKit.UIDragSessionProtocol
import platform.UIKit.UIDropInteraction
import platform.UIKit.UIDropOperationForbidden
import platform.UIKit.UIDropProposal
import platform.UIKit.UIDropSessionProtocol
import platform.UIKit.UIEvent
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerState
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UIGestureRecognizerStateCancelled
import platform.UIKit.UIGestureRecognizerStateChanged
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UIGestureRecognizerStateFailed
import platform.UIKit.UIGestureRecognizerStatePossible
import platform.UIKit.UIPress
import platform.UIKit.UIPressesEvent
import platform.UIKit.UITouch
import platform.UIKit.UITouchPhase
import platform.UIKit.UIView
import platform.UIKit.addInteraction
import platform.UIKit.setState

/**
 * Subset of [UITouchPhase] reflecting immediate phase when event is received by the [UIView] or
 * [UIGestureRecognizer].
 */
internal enum class CupertinoTouchesPhase {
    BEGAN, MOVED, ENDED, CANCELLED
}

private val UIGestureRecognizerState.isOngoing: Boolean
    get() =
        when (this) {
            UIGestureRecognizerStateBegan, UIGestureRecognizerStateChanged -> true
            else -> false
        }

/**
 * Implementation of [CMPGestureRecognizer] that handles touch events and forwards
 * them. The main difference from the original [UIView] touches based implementation is that it's built on top of
 * [CMPGestureRecognizer], which play differently with UIKit touches processing and are required
 * for the correct handling of the touch events in interop scenarios, because they rely on
 * [UIGestureRecognizer] failure requirements and touches interception, which is an exclusive way
 * to control touches delivery to [UIView]s and their [UIGestureRecognizer]s in a fine-grain manner.
 */
private class ForwardingGestureRecognizer(
    private var onTouchesEvent: (view: UIView, touches: Set<*>, event: UIEvent?, phase: CupertinoTouchesPhase) -> Unit,
    private val onTouchesCountChanged: (by: Int) -> Unit,
): CMPGestureRecognizer() {
    /**
     * The actual view that was hit-tested by the first touch in the sequence.
     * It could be interop view, for example. If there are tracked touches assignment is ignored.
     */
    var hitTestView: UIView? = null
        set(value) {
            /**
             * Only remember the first hit-tested view in the sequence.
             */
            if (initialLocation == null) {
                field = value
            }
        }

    /**
     * Initial centroid location in the sequence to measure the motion slop and to determine whether the gesture
     * should be recognized or failed and pass touches to interop views.
     */
    private var initialLocation: CValue<CGPoint>? = null

    /**
     * Touches that are currently tracked by the gesture recognizer.
     */
    private val trackedTouches: MutableSet<UITouch> = mutableSetOf()

    /**
     * Coroutine context for the gesture recognizer scheduled operations.
     */
    private val coroutineContext: CoroutineContext = Dispatchers.Main

    /**
     * Scheduled job for the gesture recognizer failure.
     */
    private var failureJob: Job? = null

    /**
     * Checks whether the centroid location of [trackedTouches] has exceeded the scrolling slop
     * relative to [initialLocation]
     */
    private val isLocationDeltaAboveSlop: Boolean
        get() {
            val initialLocation = initialLocation ?: return false
            val centroidLocation = trackedTouchesCentroidLocation ?: return false

            val slop = CUPERTINO_TOUCH_SLOP.toDouble()

            val dx = centroidLocation.useContents { x - initialLocation.useContents { x } }
            val dy = centroidLocation.useContents { y - initialLocation.useContents { y } }

            return dx * dx + dy * dy > slop * slop
        }

    /**
     * Calculates the centroid of the tracked touches.
     */
    private val trackedTouchesCentroidLocation: CValue<CGPoint>?
        get() {
            if (trackedTouches.isEmpty()) {
                return null
            }

            var centroidX = 0.0
            var centroidY = 0.0

            for (touch in trackedTouches) {
                val location = touch.locationInView(view)
                location.useContents {
                    centroidX += x
                    centroidY += y
                }
            }

            return CGPointMake(
                x = centroidX / trackedTouches.size.toDouble(),
                y = centroidY / trackedTouches.size.toDouble()
            )
        }

    init {
        // When is recognized, immediately cancel all touches in the subviews.
        cancelsTouchesInView = false

        // Delays touches reception by underlying views until the gesture recognizer is explicitly
        // stated as failed (aka, the touch sequence is targeted to the interop view).
        delaysTouchesBegan = true
    }

    /**
     * There are following scenarios:
     * 1. Those are first touches in the sequence, the interaction view is hit-tested. In this case, we
     * should start the gesture recognizer immediately and start passing touches to the Compose
     * runtime.
     *
     * 2. Those are first touches in the sequence, an interop view is hit-tested. In this case we
     * intercept touches from interop views until the gesture recognizer is explicitly failed.
     * See [UIGestureRecognizer.delaysTouchesBegan]. In the same time we schedule a failure in
     * [scheduleFailure], which will pass intercepted touches to the interop
     * views if the gesture recognizer is not recognized within a certain time frame
     * (UIScrollView reverse-engineered 150ms is used).
     * The similar approach is used by [UIScrollView](https://developer.apple.com/documentation/uikit/uiscrollview)
     *
     * 3. Those are not the first touches in the sequence. A gesture is recognized.
     * We should continue with scenario (1), we don't yet support multitouch sequence in
     * compose and interop view simultaneously (e.g. scrolling native horizontal
     * scroll and compose horizontal scroll with different fingers)
     *
     * 4. Those are not the first touches in the sequence. A gesture is not recognized.
     * See if centroid of the tracked touches has moved enough to recognize the gesture.
     *
     * TODO (not yet tracked):
     *  An improvement to the current implementation would be to remove the delay if hitTest
     *  on ComposeScene didn't go through a node, that has a PointerFilter attached
     *  (e.g. a scrollable)
     *
     */
    override fun touchesBegan(touches: Set<*>, withEvent: UIEvent) {
        val areTouchesInitial = startTrackingTouches(touches)

        onTouchesEvent(touches, withEvent, CupertinoTouchesPhase.BEGAN)

        if (state.isOngoing || hitTestView == view) {
            // Golden path, immediately start/continue the gesture recognizer if possible and pass touches.
            when (state) {
                UIGestureRecognizerStatePossible -> {
                    setState(UIGestureRecognizerStateBegan)
                }

                UIGestureRecognizerStateBegan, UIGestureRecognizerStateChanged -> {
                    setState(UIGestureRecognizerStateChanged)
                }
            }
        } else {
            if (areTouchesInitial) {
                // We are in the scenario (2), we should schedule failure and pass touches to the
                // interop view.
                scheduleFailure()
            } else {
                // We are in the scenario (4), check if the gesture recognizer should be recognized.
                checkPanIntent()
            }
        }
    }

    /**
     * There are following scenarios:
     * 1. The interaction view is hit-tested, or a gesture is recognized.
     * In this case, we should just forward the touches.
     *
     * 2. An interop view is hit-tested. In this case we should check if the pan intent is met.
     */
    override fun touchesMoved(touches: Set<*>, withEvent: UIEvent) {
        onTouchesEvent(touches, withEvent, CupertinoTouchesPhase.MOVED)

        if (state.isOngoing || hitTestView == view) {
            // Golden path, just update the gesture recognizer state and pass touches to
            // the Compose runtime.

            when (state) {
                UIGestureRecognizerStateBegan, UIGestureRecognizerStateChanged -> {
                    setState(UIGestureRecognizerStateChanged)
                }
            }
        } else {
            checkPanIntent()
        }
    }

    /**
     * There are following scenarios:
     * 1. The interaction view is hit-tested, or a gesture is recognized. Just update the gesture
     * recognizer state and pass touches to the Compose runtime.
     *
     * 2. An interop view is hit-tested. In this case if there are no tracked touches left -
     * we need to allow all the touches to be passed to the interop view by failing explicitly.
     */
    override fun touchesEnded(touches: Set<*>, withEvent: UIEvent) {
        stopTrackingTouches(touches)

        onTouchesEvent(touches, withEvent, CupertinoTouchesPhase.ENDED)

        if (state.isOngoing || hitTestView == view) {
            // Golden path, just update the gesture recognizer state and pass touches to
            // the Compose runtime.

            when (state) {
                UIGestureRecognizerStateBegan, UIGestureRecognizerStateChanged -> {
                    if (trackedTouches.isEmpty()) {
                        cancelScheduledFailure()
                        setState(UIGestureRecognizerStateEnded)
                    } else {
                        setState(UIGestureRecognizerStateChanged)
                    }
                }
            }
        } else {
            if (trackedTouches.isEmpty()) {
                // Explicitly fail the gesture, cancelling a scheduled failure
                cancelScheduledFailure()

                setState(UIGestureRecognizerStateFailed)
            }
        }
    }

    /**
     * There are following scenarios:
     * 1. The interaction view is hit-tested, or a gesture is recognized. Just update the gesture
     * recognizer state and pass touches to the Compose runtime.
     *
     * 2. An interop view is hit-tested. In this case if there are no tracked touches left -
     * we need to allow all the touches to be passed to the interop view by failing explicitly.
     */
    override fun touchesCancelled(touches: Set<*>, withEvent: UIEvent) {
        stopTrackingTouches(touches)

        onTouchesEvent(touches, withEvent, CupertinoTouchesPhase.CANCELLED)

        if (hitTestView == view) {
            // Golden path, just update the gesture recognizer state.

            when (state) {
                UIGestureRecognizerStateBegan, UIGestureRecognizerStateChanged -> {
                    if (trackedTouches.isEmpty()) {
                        cancelScheduledFailure()
                        setState(UIGestureRecognizerStateCancelled)
                    } else {
                        setState(UIGestureRecognizerStateChanged)
                    }
                }
            }
        } else {
            if (trackedTouches.isEmpty()) {
                // Those were the last touches in the sequence
                // Explicitly fail the gesture, cancelling a scheduled failure
                cancelScheduledFailure()

                setState(UIGestureRecognizerStateFailed)
            }
        }
    }

    override fun gestureRecognizerShouldRecognizeSimultaneouslyWithGestureRecognizer(
        gestureRecognizer: UIGestureRecognizer,
        otherGestureRecognizer: UIGestureRecognizer
    ): Boolean {
        // Can't check if either view is null
        val view = gestureRecognizer.view ?: return false
        val otherView = otherGestureRecognizer.view ?: return false

        val otherIsAscendant = !otherView.isDescendantOfView(view)

        // Only allow simultaneous recognition if the other gesture recognizer is attached to the same view
        // or to a view up in the hierarchy
        val result = otherView == view || otherIsAscendant

        println("${gestureRecognizer.`class`()} should recognize simultaneously with ${otherGestureRecognizer.`class`()}: $result")

        return result
    }

    /**
     * We don't require the other gesture recognizer to fail.
     */
    override fun gestureRecognizerShouldRequireFailureOfGestureRecognizer(
        gestureRecognizer: UIGestureRecognizer,
        otherGestureRecognizer: UIGestureRecognizer
    ): Boolean {
        println("${gestureRecognizer.`class`()} requires ${otherGestureRecognizer.`class`()} to fail")
        return false
    }

    override fun gestureRecognizerShouldBeRequiredToFailByGestureRecognizer(
        gestureRecognizer: UIGestureRecognizer,
        otherGestureRecognizer: UIGestureRecognizer
    ): Boolean {
        // Other gesture recognizer should fail if it's attached to a different view
        val result = gestureRecognizer.view != otherGestureRecognizer.view
        println("${gestureRecognizer.`class`()} should be required to fail by ${otherGestureRecognizer.`class`()}: $result")
        return result
    }

    /**
     * Fail the gesture recognizer explicitly.
     *
     * It means we need to pass all the tracked touches to the runtime as cancelled and set failed
     * state on the gesture recognizer.
     *
     * Intercepted touches will be passed to the interop views by UIKit due to
     * [UIGestureRecognizer.delaysTouchesBegan]
     */
    fun fail() {
        setState(UIGestureRecognizerStateFailed)

        // We won't receive other touches events until all fingers are lifted, so we can't rely
        // on touchesEnded/touchesCancelled to reset the state.  We need to immediately notify
        // the runtime about the cancelled touches and reset the state manually
        onTouchesEvent(trackedTouches, null, CupertinoTouchesPhase.CANCELLED)
        stopTrackingTouches(trackedTouches)
    }

    /**
     * Intentionally clean up all dependencies of GestureRecognizerHandlerImpl to prevent retain cycles that
     * can be caused by implicit capture of the view by UIKit objects (such as UIEvent).
     */
    fun dispose() {
        cancelScheduledFailure()
        fail()
        onTouchesEvent = { _, _, _, _ -> }
        trackedTouches.clear()
    }

    private fun scheduleFailure() {
        failureJob?.cancel()

        failureJob = CoroutineScope(coroutineContext).launch {
            // Wait for the gesture to be recognized or failed
            kotlinx.coroutines.delay(150)

            fail()
        }
    }

    private fun cancelScheduledFailure() {
        failureJob?.cancel()
        failureJob = null
    }

    /**
     * Starts tracking the given touches. Remember initial location if those are the first touches
     * in the sequence.
     * @return `true` if the touches are initial, `false` otherwise.
     */
    private fun startTrackingTouches(touches: Set<*>): Boolean {
        val areTouchesInitial = trackedTouches.isEmpty()

        for (touch in touches) {
            trackedTouches.add(touch as UITouch)
        }

        onTouchesCountChanged(touches.size)

        if (areTouchesInitial) {
            initialLocation = trackedTouchesCentroidLocation
        }

        return areTouchesInitial
    }

    /**
     * Check if the tracked touches have moved enough to recognize the gesture.
     */
    private fun checkPanIntent() {
        if (isLocationDeltaAboveSlop) {
            cancelScheduledFailure()
            setState(UIGestureRecognizerStateBegan)
        }
    }

    /**
     * Stops tracking the given touches. If there are no tracked touches left, reset the initial
     * location to null.
     */
    private fun stopTrackingTouches(touches: Set<*>) {
        for (touch in touches) {
            trackedTouches.remove(touch as UITouch)
        }

        onTouchesCountChanged(-touches.size)

        if (trackedTouches.isEmpty()) {
            initialLocation = null
        }
    }

    private fun onTouchesEvent(
        touches: Set<*>,
        event: UIEvent?,
        phase: CupertinoTouchesPhase
    ) {
        val view = view ?: return

        onTouchesEvent(view, touches, event, phase)
    }
}

/**
 * [UIView] subclass that handles touches and keyboard presses events and forwards them
 * to the Compose runtime.
 *
 * @param hitTestInteropView A callback to find an [InteropView] at the given point.
 * @param onTouchesEvent A callback to notify the Compose runtime about touch events.
 * @param onTouchesCountChange A callback to notify the Compose runtime about the number of tracked
 * touches.
 * @param inInteractionBounds A callback to check if the given point is within the interaction
 * bounds as defined by the owning implementation.
 * @param onKeyboardPresses A callback to notify the Compose runtime about keyboard presses.
 * The parameter is a [Set] of [UIPress] objects. Erasure happens due to K/N not supporting Obj-C
 * lightweight generics.
 */
internal class InteractionUIView(
    private var hitTestInteropView: (point: CValue<CGPoint>, event: UIEvent?) -> InteropView?,
    onTouchesEvent: (view: UIView, touches: Set<*>, event: UIEvent?, phase: CupertinoTouchesPhase) -> Unit,
    private var onTouchesCountChange: (count: Int) -> Unit,
    private var inInteractionBounds: (CValue<CGPoint>) -> Boolean,
    private var onKeyboardPresses: (Set<*>) -> Unit,
) : UIView(CGRectZero.readValue()) {
    private val dragInteractionProxy = object : CMPDragInteractionProxy() {
        override fun isSessionRestrictedToDraggingApplication(
            session: UIDragSessionProtocol,
            interaction: UIDragInteraction
        ): Boolean {
            return false
        }

        override fun itemsForBeginningSession(
            session: UIDragSessionProtocol,
            interaction: UIDragInteraction
        ): List<*> {
            return listOf<Any>()
        }

        override fun doesSessionAllowMoveOperation(
            session: UIDragSessionProtocol,
            interaction: UIDragInteraction
        ): Boolean {
            return true
        }
    }

    private val dropInteractionProxy = object : CMPDropInteractionProxy() {
        override fun canHandleSession(
            session: UIDropSessionProtocol,
            interaction: UIDropInteraction
        ): Boolean {
            return false
        }

        override fun performDropFromSession(
            session: UIDropSessionProtocol,
            interaction: UIDropInteraction
        ) {
            CoroutineScope(Dispatchers.Main).launch {
                for (item in session.items) {
                    if (item !is UIDragItem) return@launch
                    val text = item.loadString()
                    println("Dropped string: $text")
                }
            }
        }

        override fun proposalForSessionUpdate(
            session: UIDropSessionProtocol,
            interaction: UIDropInteraction
        ): UIDropProposal {
            return UIDropProposal(UIDropOperationForbidden)
        }
    }

    /**
     * Gesture recognizer that forwards touches to the Compose runtime.
     */
    private val forwardingGestureRecognizer = ForwardingGestureRecognizer(
        onTouchesEvent = onTouchesEvent,
        onTouchesCountChanged = { touchesCount += it }
    )

    /**
     * When there at least one tracked touch, we need notify redrawer about it. It should schedule
     * CADisplayLink which affects frequency of polling UITouch events on high frequency display
     * and forces it to match display refresh rate.
     */
    private var touchesCount = 0
        set(value) {
            field = value
            onTouchesCountChange(value)
        }

    init {
        multipleTouchEnabled = true
        userInteractionEnabled = true

        addGestureRecognizer(forwardingGestureRecognizer)

        addInteraction(UIDragInteraction(delegate = dragInteractionProxy))
        addInteraction(UIDropInteraction(delegate = dropInteractionProxy))
    }

    override fun canBecomeFirstResponder() = true

    override fun pressesBegan(presses: Set<*>, withEvent: UIPressesEvent?) {
        onKeyboardPresses(presses)
        super.pressesBegan(presses, withEvent)
    }

    override fun pressesEnded(presses: Set<*>, withEvent: UIPressesEvent?) {
        onKeyboardPresses(presses)
        super.pressesEnded(presses, withEvent)
    }

    override fun hitTest(point: CValue<CGPoint>, withEvent: UIEvent?): UIView? = savingHitTestResult {
        if (!inInteractionBounds(point)) {
            null
        } else {
            // Find if a scene contains a [InteropViewAnchorModifierNode] at the given point.
            val interopView = hitTestInteropView(point, withEvent)

            if (interopView == null) {
                // Native [hitTest] happens after [pointInside] is checked. If hit testing
                // inside ComposeScene didn't yield any interop view, then we should return [this]
                this
            } else {
                // Transform the point to the interop view's coordinate system.
                // And perform native [hitTest] on the interop view.
                // Return this view if the interop view doesn't handle the hit test.
                interopView.hitTest(
                    point = convertPoint(point, toView = interopView),
                    withEvent = withEvent
                ) ?: this
            }
        }
    }

    /**
     * Intentionally clean up all dependencies of InteractionUIView to prevent retain cycles that
     * can be caused by implicit capture of the view by UIKit objects (such as UIEvent).
     */
    fun dispose() {
        forwardingGestureRecognizer.dispose()

        removeGestureRecognizer(forwardingGestureRecognizer)

        hitTestInteropView = { _, _ -> null }

        onTouchesCountChange = {}
        inInteractionBounds = { false }
        onKeyboardPresses = {}
    }

    /**
     * Execute the given [hitTestBlock] and save the result to the gesture recognizer handler, so
     * that it can be used later to determine if the gesture recognizer should be recognized
     * or failed.
     */
    private fun savingHitTestResult(hitTestBlock: () -> UIView?): UIView? {
        val result = hitTestBlock()
        forwardingGestureRecognizer.hitTestView = result
        return result
    }
}
