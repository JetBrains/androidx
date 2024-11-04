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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.SessionMutex
import androidx.compose.ui.draganddrop.UIKitDragAndDropManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.toComposeEvent
import androidx.compose.ui.input.pointer.HistoricalChange
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.layout.OffsetToFocusedRect
import androidx.compose.ui.layout.OverlayLayout
import androidx.compose.ui.layout.adjustedToFocusedRectOffset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.AccessibilityMediator
import androidx.compose.ui.platform.AccessibilitySyncOptions
import androidx.compose.ui.platform.CUPERTINO_TOUCH_SLOP
import androidx.compose.ui.platform.DefaultInputModeManager
import androidx.compose.ui.platform.EmptyViewConfiguration
import androidx.compose.ui.platform.LocalLayoutMargins
import androidx.compose.ui.platform.LocalSafeArea
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSessionScope
import androidx.compose.ui.platform.PlatformWindowContext
import androidx.compose.ui.platform.UIKitTextInputService
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.uikit.ComposeUIViewControllerConfiguration
import androidx.compose.ui.uikit.ExclusiveLayoutConstraints
import androidx.compose.ui.uikit.LocalKeyboardOverlapHeight
import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.uikit.density
import androidx.compose.ui.uikit.embedSubview
import androidx.compose.ui.uikit.layoutConstraintsToCenterInParent
import androidx.compose.ui.uikit.layoutConstraintsToMatch
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.asCGRect
import androidx.compose.ui.unit.asDpOffset
import androidx.compose.ui.unit.asDpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toDpRect
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toPlatformInsets
import androidx.compose.ui.viewinterop.LocalInteropContainer
import androidx.compose.ui.viewinterop.TrackInteropPlacementContainer
import androidx.compose.ui.viewinterop.UIKitInteropContainer
import androidx.compose.ui.viewinterop.UIKitInteropTransaction
import androidx.compose.ui.window.ApplicationForegroundStateListener
import androidx.compose.ui.window.ComposeSceneKeyboardOffsetManager
import androidx.compose.ui.window.FocusStack
import androidx.compose.ui.window.GestureEvent
import androidx.compose.ui.window.KeyboardVisibilityListener
import androidx.compose.ui.window.MetalRedrawer
import androidx.compose.ui.window.TouchesEventKind
import androidx.compose.ui.window.UserInputView
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlinx.cinterop.CValue
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreGraphics.CGAffineTransformIdentity
import platform.CoreGraphics.CGAffineTransformInvert
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGSize
import platform.QuartzCore.CACurrentMediaTime
import platform.QuartzCore.CATransaction
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIEvent
import platform.UIKit.UIPress
import platform.UIKit.UITouch
import platform.UIKit.UITouchPhase
import platform.UIKit.UIView
import platform.UIKit.UIViewControllerTransitionCoordinatorProtocol
import platform.UIKit.UIWindow

/**
 * iOS specific-implementation of [PlatformContext.SemanticsOwnerListener] used to track changes in [SemanticsOwner].
 *
 * @property rootView The UI container associated with the semantics owner.
 * @property coroutineContext The coroutine context to use for handling semantics changes.
 * @property getAccessibilitySyncOptions A lambda function to retrieve the latest accessibility synchronization options.
 * @property performEscape A lambda to delegate accessibility escape operation. Returns true if the escape was handled, false otherwise.
 */
@OptIn(ExperimentalComposeApi::class)
private class SemanticsOwnerListenerImpl(
    private val rootView: UIView,
    private val coroutineContext: CoroutineContext,
    private val getAccessibilitySyncOptions: () -> AccessibilitySyncOptions,
    private val convertToAppWindowCGRect: (Rect, UIWindow) -> CValue<CGRect>,
    private val performEscape: () -> Boolean
) : PlatformContext.SemanticsOwnerListener {
    private var mediator: AccessibilityMediator? = null

    override fun onSemanticsOwnerAppended(semanticsOwner: SemanticsOwner) {
        if (mediator == null) {
            mediator = AccessibilityMediator(
                rootView,
                semanticsOwner,
                coroutineContext,
                getAccessibilitySyncOptions,
                convertToAppWindowCGRect,
                performEscape
            )
        }
    }

    override fun onSemanticsOwnerRemoved(semanticsOwner: SemanticsOwner) {
        if (mediator?.owner == semanticsOwner) {
            mediator?.dispose()
            mediator = null
        }
    }

    override fun onSemanticsChange(semanticsOwner: SemanticsOwner) {
        if (mediator?.owner == semanticsOwner) {
            mediator?.onSemanticsChange()
        }
    }

    override fun onLayoutChange(semanticsOwner: SemanticsOwner, semanticsNodeId: Int) {
        if (mediator?.owner == semanticsOwner) {
            mediator?.onLayoutChange(nodeId = semanticsNodeId)
        }
    }

    fun dispose() {
        mediator?.dispose()
        mediator = null
    }
}

internal sealed interface ComposeSceneMediatorLayout {
    data object Fill : ComposeSceneMediatorLayout
    class Center(val size: CValue<CGSize>) : ComposeSceneMediatorLayout
}

internal class ComposeSceneMediator(
    private val parentView: UIView,
    private val configuration: ComposeUIViewControllerConfiguration,
    private val focusStack: FocusStack?,
    private val windowContext: PlatformWindowContext,
    private val coroutineContext: CoroutineContext,
    private val redrawer: MetalRedrawer,
    onGestureEvent: (GestureEvent) -> Unit,
    composeSceneFactory: (
        invalidate: () -> Unit,
        platformContext: PlatformContext,
        coroutineContext: CoroutineContext
    ) -> ComposeScene
) {
    private var onPreviewKeyEvent: (KeyEvent) -> Boolean = { false }

    private var onKeyEvent: (KeyEvent) -> Boolean = { false }

    private var keyboardOverlapHeight by mutableStateOf(0.dp)
    private var animateKeyboardOffsetChanges by mutableStateOf(false)

    private val viewConfiguration: ViewConfiguration =
        object : ViewConfiguration by EmptyViewConfiguration {
            override val touchSlop: Float
                get() = with(density) {
                    // this value is originating from iOS 16 drag behavior reverse engineering
                    CUPERTINO_TOUCH_SLOP.dp.toPx()
                }
        }

    private val scene: ComposeScene by lazy {
        composeSceneFactory(
            ::setNeedsRedraw,
            PlatformContextImpl(),
            coroutineContext,
        )
    }

    var density: Density
        get() = scene.density
        set(value) {
            scene.density = value
        }

    var layoutDirection: LayoutDirection
        get() = scene.layoutDirection
        set(value) {
            scene.layoutDirection = value
        }

    var compositionLocalContext: CompositionLocalContext?
        get() = scene.compositionLocalContext
        set(value) {
            scene.compositionLocalContext = value
        }

    private val userInputViewConstraints = ExclusiveLayoutConstraints()

    private val applicationForegroundStateListener =
        ApplicationForegroundStateListener { _ ->
            // Sometimes the application can trigger animation and go background before the animation is
            // finished. The scheduled GPU work is performed, but no presentation can be done, causing
            // mismatch between visual state and application state. This can be fixed by forcing
            // a redraw when app returns to foreground, which will ensure that the visual state is in
            // sync with the application state even if such sequence of events took a place.
            redrawer.setNeedsRedraw()
        }

    /**
     * View wrapping the hierarchy managed by this Mediator.
     */
    private val view = ComposeSceneMediatorView(
        onLayoutSubviews = ::updateLayout
    )

    /**
     * View that handles the user input events and hosts interop views.
     */
    private val userInputView = UserInputView(
        ::hitTestInteropView,
        ::onTouchesEvent,
        onGestureEvent,
        ::isPointInsideInteractionBounds,
        ::onKeyboardPresses
    )

    /**
     * Container for managing UIKitView and UIKitViewController
     */
    private val interopContainer = UIKitInteropContainer(
        root = userInputView,
        requestRedraw = ::setNeedsRedraw
    )

    var interactionBounds = IntRect.Zero

    private val dragAndDropManager = UIKitDragAndDropManager(
        view = userInputView,
        getComposeRootDragAndDropNode = { scene.rootDragAndDropNode },
    )

    /**
     * A callback to define whether the precondition for the user input view hit test is met.
     *
     * @param point Point in the interaction view coordinate space.
     */
    private fun isPointInsideInteractionBounds(point: CValue<CGPoint>) =
        interactionBounds.contains(point.asDpOffset().toOffset(view.density).round())

    @OptIn(ExperimentalComposeApi::class)
    private val semanticsOwnerListener by lazy {
        SemanticsOwnerListenerImpl(
            rootView = view,
            coroutineContext = coroutineContext,
            getAccessibilitySyncOptions = {
                configuration.accessibilitySyncOptions
            },
            convertToAppWindowCGRect = { rect, window ->
                windowContext.convertWindowRect(rect, window)
                    .toDpRect(Density(window.screen.scale.toFloat()))
                    .asCGRect()
            },
            performEscape = {
                val down = onKeyboardEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown))
                val up = onKeyboardEvent(KeyEvent(Key.Escape, KeyEventType.KeyUp))

                down || up
            }
        )
    }
    @OptIn(ExperimentalComposeApi::class)
    private val keyboardManager by lazy {
        ComposeSceneKeyboardOffsetManager(
            view = viewForKeyboardOffsetTransform,
            keyboardOverlapHeightChanged = { height ->
                if (configuration.platformLayers) {
                    if (keyboardOverlapHeight != height) {
                        animateKeyboardOffsetChanges = false
                    }
                }
                keyboardOverlapHeight = height
            }
        )
    }

    private val textInputService: UIKitTextInputService by lazy {
        UIKitTextInputService(
            updateView = {
                setNeedsRedraw()
                CATransaction.flush() // clear all animations
            },
            rootView = view,
            viewConfiguration = viewConfiguration,
            focusStack = focusStack,
            onInputStarted = {
                animateKeyboardOffsetChanges = true
            },
            onKeyboardPresses = ::onKeyboardPresses
        ).also {
            KeyboardVisibilityListener.initialize()
        }
    }

    val hasInvalidations: Boolean
        get() = scene.hasInvalidations() || keyboardManager.isAnimating

    private fun hitTestInteropView(point: CValue<CGPoint>, event: UIEvent?): UIView? =
        point.useContents {
            val position = asDpOffset().toOffset(density)
            val interopView = scene.hitTestInteropView(position)

            // Find a group of a holder associated with a given interop view or view controller
            interopView?.let {
                interopContainer.groupForInteropView(it)
            }
        }

    /**
     * Converts [UITouch] objects from [touches] to [ComposeScenePointer] and dispatches them to the appropriate handlers.
     * @param view the [UIView] that received the touches
     * @param touches a [Set] of [UITouch] objects. Erasure happens due to K/N not supporting Obj-C lightweight generics.
     * @param event the [UIEvent] associated with the touches
     * @param eventKind the [TouchesEventKind] of the touches
     */
    private fun onTouchesEvent(
        view: UIView,
        touches: Set<*>,
        event: UIEvent?,
        eventKind: TouchesEventKind
    ) {
        val pointers = touches.map {
            val touch = it as UITouch
            val id = touch.hashCode().toLong()
            val position = touch.offsetInView(view, density.density)
            ComposeScenePointer(
                id = PointerId(id),
                position = position,
                pressed = when (eventKind) {
                    // When CMPGestureRecognizer fails, it means that all touches are now redirected
                    // to the interop view. They are still technically pressed, but Compose must
                    // treat them as lifted because it's the last event that Compose receives
                    // during this touch sequence.
                    TouchesEventKind.REDIRECTED -> false
                    else -> touch.isPressed
                },
                type = PointerType.Touch,
                pressure = touch.force.toFloat(),
                historical = event?.historicalChangesForTouch(
                    touch,
                    view,
                    density.density
                ) ?: emptyList()
            )
        }

        // If the touches were cancelled due to gesture failure, the timestamp is not available,
        // because no actual event with touch updates happened. We just use the current time in
        // this case.
        val timestamp = event?.timestamp ?: CACurrentMediaTime()

        scene.sendPointerEvent(
            eventType = eventKind.toPointerEventType(),
            pointers = pointers,
            timeMillis = (timestamp * 1e3).toLong(),
            nativeEvent = event
        )
    }

    init {
        view.translatesAutoresizingMaskIntoConstraints = false
        parentView.addSubview(view)
        setLayout(ComposeSceneMediatorLayout.Fill)

        view.embedSubview(userInputView)
    }

    private var lastFocusedRect: Rect? = null
    private fun getFocusedRect(): Rect? {
        return scene.focusManager.getFocusRect()?.also {
            lastFocusedRect = it
        } ?: lastFocusedRect
    }

    fun setContent(content: @Composable () -> Unit) {
        view.runOnceOnAppeared {
            focusStack?.pushAndFocus(userInputView)

            scene.setContent {
                ProvideComposeSceneMediatorCompositionLocals {
                    FocusAboveKeyboardIfNeeded {
                        interopContainer.TrackInteropPlacementContainer(content = content)
                    }
                }
            }
        }
    }

    private fun updateViewOffset() {
        val yOffset = density.adjustedToFocusedRectOffset(
            insets = PlatformInsets(bottom = keyboardOverlapHeight),
            focusedRect = getFocusedRect(),
            size = scene.size
        ).y / density.density

        viewForKeyboardOffsetTransform.layer.setAffineTransform(
            CGAffineTransformMakeTranslation(0.0, yOffset.toDouble())
        )
    }

    fun performOrientationChangeAnimation(
        targetSize: CValue<CGSize>,
        coordinator: UIViewControllerTransitionCoordinatorProtocol
    ) {
        val startSnapshotView = view.snapshotViewAfterScreenUpdates(false) ?: return
        startSnapshotView.translatesAutoresizingMaskIntoConstraints = false
        parentView.addSubview(startSnapshotView)
        targetSize.useContents {
            NSLayoutConstraint.activateConstraints(
                listOf(
                    startSnapshotView.widthAnchor.constraintEqualToConstant(height),
                    startSnapshotView.heightAnchor.constraintEqualToConstant(width),
                    startSnapshotView.centerXAnchor.constraintEqualToAnchor(parentView.centerXAnchor),
                    startSnapshotView.centerYAnchor.constraintEqualToAnchor(parentView.centerYAnchor)
                )
            )
        }
        redrawer.isForcedToPresentWithTransactionEveryFrame = true
        setLayout(ComposeSceneMediatorLayout.Center(targetSize))
        userInputView.transform = coordinator.targetTransform

        coordinator.animateAlongsideTransition(
            animation = {
                startSnapshotView.alpha = 0.0
                startSnapshotView.transform = CGAffineTransformInvert(coordinator.targetTransform)
                userInputView.transform = CGAffineTransformIdentity.readValue()
            },
            completion = {
                startSnapshotView.removeFromSuperview()
                setLayout(ComposeSceneMediatorLayout.Fill)
                redrawer.isForcedToPresentWithTransactionEveryFrame = false
            }
        )

        userInputView.setNeedsLayout()
        view.layoutIfNeeded()
    }

    fun render(canvas: Canvas, nanoTime: Long) {
        scene.render(canvas, nanoTime)
    }

    fun retrieveInteropTransaction(): UIKitInteropTransaction =
        interopContainer.retrieveTransaction()

    private fun setLayout(value: ComposeSceneMediatorLayout) {
        when (value) {
            ComposeSceneMediatorLayout.Fill -> {
                userInputViewConstraints.set(
                    view.layoutConstraintsToMatch(parentView)
                )
            }

            is ComposeSceneMediatorLayout.Center -> {
                userInputViewConstraints.set(
                    view.layoutConstraintsToCenterInParent(parentView, value.size)
                )
            }
        }
    }

    private var safeArea by mutableStateOf(PlatformInsets.Zero)

    private var layoutMargins by mutableStateOf(PlatformInsets.Zero)

    @Composable
    private fun ProvideComposeSceneMediatorCompositionLocals(content: @Composable () -> Unit) =
        CompositionLocalProvider(
            LocalInteropContainer provides interopContainer,
            LocalKeyboardOverlapHeight provides keyboardOverlapHeight,
            LocalSafeArea provides safeArea,
            LocalLayoutMargins provides layoutMargins,
            content = content
        )

    @OptIn(ExperimentalComposeApi::class)
    @Composable
    private fun FocusAboveKeyboardIfNeeded(content: @Composable () -> Unit) {
        if (configuration.onFocusBehavior == OnFocusBehavior.FocusableAboveKeyboard) {
            if (configuration.platformLayers) {
                OffsetToFocusedRect(
                    insets = PlatformInsets(bottom = keyboardOverlapHeight),
                    getFocusedRect = ::getFocusedRect,
                    size = scene.size,
                    animationDuration = if (animateKeyboardOffsetChanges) {
                        FOCUS_CHANGE_ANIMATION_DURATION
                    } else {
                        0.seconds
                    },
                    animationCompletion = {
                        animateKeyboardOffsetChanges = false
                    },
                    content = content
                )
            } else {
                LaunchedEffect(keyboardOverlapHeight) {
                    scene.invalidatePositionInWindow()
                }
                LaunchedEffect(animateKeyboardOffsetChanges) {
                    if (animateKeyboardOffsetChanges) {
                        UIView.animateWithDuration(
                            duration = FOCUS_CHANGE_ANIMATION_DURATION.toDouble(
                                DurationUnit.SECONDS
                            ),
                            animations = ::updateViewOffset,
                            completion = {
                                scene.invalidatePositionInWindow()
                                animateKeyboardOffsetChanges = false
                            }
                        )
                    }
                }
                OverlayLayout(
                    modifier = Modifier.onGloballyPositioned {
                        updateViewOffset()
                    },
                    content = content
                )
            }
        } else {
            content()
        }
    }

    fun dispose() {
        onPreviewKeyEvent = { false }
        onKeyEvent = { false }

        view.dispose()
        textInputService.stopInput()
        applicationForegroundStateListener.dispose()
        focusStack?.popUntilNext(userInputView)
        keyboardManager.dispose()
        userInputView.dispose()

        view.removeFromSuperview()

        scene.close()
        interopContainer.dispose()
        semanticsOwnerListener.dispose()
    }

    private fun setNeedsRedraw() {
        redrawer.setNeedsRedraw()
    }

    /**
     * Updates the [ComposeScene] with the properties derived from the [view].
     */
    private fun updateLayout() {
        density = view.density

        layoutMargins = view.layoutMargins.toPlatformInsets()
        safeArea = view.safeAreaInsets.toPlatformInsets()

        val boundsInPx = view.bounds.useContents {
            with(density) {
                asDpRect().toRect()
            }
        }
        scene.size = IntSize(
            width = boundsInPx.width.roundToInt(),
            height = boundsInPx.height.roundToInt()
        )
    }

    fun sceneDidAppear() {
        keyboardManager.start()
    }

    fun sceneWillDisappear() {
        keyboardManager.stop()
    }

    fun setKeyEventListener(
        onPreviewKeyEvent: ((KeyEvent) -> Boolean)?,
        onKeyEvent: ((KeyEvent) -> Boolean)?
    ) {
        this.onPreviewKeyEvent = onPreviewKeyEvent ?: { false }
        this.onKeyEvent = onKeyEvent ?: { false }
    }

    /**
     * Converts [UIPress] objects to [KeyEvent] and dispatches them to the appropriate handlers.
     * @param presses a [Set] of [UIPress] objects. Erasure happens due to K/N not supporting Obj-C lightweight generics.
     */
    private fun onKeyboardPresses(presses: Set<*>) {
        presses.forEach {
            val press = it as UIPress
            onKeyboardEvent(press.toComposeEvent())
        }
    }

    private fun onKeyboardEvent(keyEvent: KeyEvent): Boolean =
        textInputService.onPreviewKeyEvent(keyEvent) // TODO: fix redundant call
            || onPreviewKeyEvent(keyEvent)
            || scene.sendKeyEvent(keyEvent)
            || onKeyEvent(keyEvent)

    @OptIn(ExperimentalComposeApi::class)
    private var viewForKeyboardOffsetTransform = if (configuration.platformLayers) {
        view
    } else {
        parentView
    }

    private inner class PlatformContextImpl : PlatformContext by PlatformContext.Empty {
        override val windowInfo: WindowInfo get() = windowContext.windowInfo

        override fun convertLocalToWindowPosition(localPosition: Offset): Offset =
            windowContext.convertLocalToWindowPosition(
                viewForKeyboardOffsetTransform,
                localPosition
            )

        override fun convertWindowToLocalPosition(positionInWindow: Offset): Offset =
            windowContext.convertWindowToLocalPosition(
                viewForKeyboardOffsetTransform,
                positionInWindow
            )

        override fun convertLocalToScreenPosition(localPosition: Offset): Offset =
            windowContext.convertLocalToScreenPosition(
                viewForKeyboardOffsetTransform,
                localPosition
            )

        override fun convertScreenToLocalPosition(positionOnScreen: Offset): Offset =
            windowContext.convertScreenToLocalPosition(
                viewForKeyboardOffsetTransform,
                positionOnScreen
            )

        override val viewConfiguration get() = this@ComposeSceneMediator.viewConfiguration
        override val inputModeManager = DefaultInputModeManager(InputMode.Touch)
        override val textInputService get() = this@ComposeSceneMediator.textInputService
        override val textToolbar get() = this@ComposeSceneMediator.textInputService
        override val semanticsOwnerListener get() = this@ComposeSceneMediator.semanticsOwnerListener
        override val dragAndDropManager get() = this@ComposeSceneMediator.dragAndDropManager

        private val textInputSessionMutex = SessionMutex<IOSTextInputSession>()

        override suspend fun textInputSession(session: suspend PlatformTextInputSessionScope.() -> Nothing): Nothing =
            textInputSessionMutex.withSessionCancellingPrevious(
                sessionInitializer = {
                    IOSTextInputSession(it)
                },
                session = session
            )
    }

    private inner class IOSTextInputSession(
        coroutineScope: CoroutineScope
    ) : PlatformTextInputSessionScope, CoroutineScope by coroutineScope {
        private val innerSessionMutex = SessionMutex<Nothing?>()

        override suspend fun startInputMethod(request: PlatformTextInputMethodRequest): Nothing =
            innerSessionMutex.withSessionCancellingPrevious(
                sessionInitializer = { null }
            ) {
                coroutineScope {
                    launch {
                        request.textLayoutResult.collect {
                            textInputService.updateTextLayoutResult(it)
                        }
                    }
                    suspendCancellableCoroutine<Nothing> { continuation ->
                        textInputService.startInput(
                            value = request.state,
                            imeOptions = request.imeOptions,
                            editProcessor = request.editProcessor,
                            onEditCommand = request.onEditCommand,
                            onImeActionPerformed = request.onImeAction ?: {}
                        )

                        continuation.invokeOnCancellation {
                            textInputService.stopInput()
                        }
                    }
                }
            }

        override fun updateSelectionState(newState: TextFieldValue) {
            textInputService.updateState(oldValue = null, newValue = newState)
        }
    }
}

private val FOCUS_CHANGE_ANIMATION_DURATION = 0.15.seconds

private fun TouchesEventKind.toPointerEventType(): PointerEventType =
    when (this) {
        TouchesEventKind.BEGAN -> PointerEventType.Press
        TouchesEventKind.MOVED -> PointerEventType.Move

        TouchesEventKind.ENDED, TouchesEventKind.CANCELLED, TouchesEventKind.REDIRECTED ->
            PointerEventType.Release
    }

private fun UIEvent.historicalChangesForTouch(
    touch: UITouch,
    view: UIView,
    density: Float
): List<HistoricalChange> {
    val touches = coalescedTouchesForTouch(touch) ?: return emptyList()

    return if (touches.size > 1) {
        // the last touch is not included because it is the actual touch reported by the event
        touches.dropLast(1).map {
            val historicalTouch = it as UITouch
            val position = historicalTouch.offsetInView(view, density)
            HistoricalChange(
                uptimeMillis = (historicalTouch.timestamp * 1e3).toLong(),
                position = position,
                originalEventPosition = position
            )
        }
    } else {
        emptyList()
    }
}

private val UITouch.isPressed
    get() = when (phase) {
        UITouchPhase.UITouchPhaseEnded, UITouchPhase.UITouchPhaseCancelled -> false
        else -> true
    }

private fun UITouch.offsetInView(view: UIView, density: Float): Offset =
    locationInView(view).useContents {
        Offset(x.toFloat() * density, y.toFloat() * density)
    }
