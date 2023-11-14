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
package androidx.compose.ui

import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyInputElement
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.text.input.PlatformTextInputService
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.RootNodeOwner
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.Volatile
import kotlinx.coroutines.*
import org.jetbrains.skia.Canvas as SkCanvas
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.node.SnapshotInvalidationTracker

internal val LocalComposeScene = staticCompositionLocalOf<ComposeScene?> { null }

/**
 * The local [ComposeScene] is typically not-null. This extension can be used in these cases.
 */
@Composable
internal fun CompositionLocal<ComposeScene?>.requireCurrent(): ComposeScene {
    return current ?: error("CompositionLocal LocalComposeScene not provided")
}

/**
 * A virtual container that encapsulates Compose UI content. UI content can be constructed via
 * [setContent] method and with any Composable that manipulates [LayoutNode] tree.
 * To draw content on [SkCanvas], you can use [render] method.
 *
 * To specify available size for the content, you should use [constraints].
 *
 * After [ComposeScene] will no longer needed, you should call [close] method, so all resources
 * and subscriptions will be properly closed. Otherwise there can be a memory leak.
 *
 * [ComposeScene] doesn't support concurrent read/write access from different threads. Except:
 * - [hasInvalidations] can be called from any thread
 * - [invalidate] callback can be called from any thread
 */
class ComposeScene internal constructor(
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
    internal val platform: Platform,
    density: Density = Density(1f),
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    private val invalidate: () -> Unit = {}
) : BaseComposeScene() {
    /**
     * Constructs [ComposeScene]
     *
     * @param coroutineContext Context which will be used to launch effects ([LaunchedEffect],
     * [rememberCoroutineScope]) and run recompositions.
     * @param density Initial density of the content which will be used to convert [dp] units.
     * @param layoutDirection Initial layout direction of the content.
     * @param invalidate Callback which will be called when the content need to be recomposed or
     * rerendered. If you draw your content using [render] method, in this callback you should
     * schedule the next [render] in your rendering loop.
     */
    @ExperimentalComposeUiApi
    constructor(
        coroutineContext: CoroutineContext = Dispatchers.Unconfined,
        density: Density = Density(1f),
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        invalidate: () -> Unit = {}
    ) : this(
        coroutineContext,
        Platform.Empty,
        density,
        layoutDirection,
        invalidate
    )

    /**
     * Constructs [ComposeScene]
     *
     * @param textInputService Platform specific text input service
     * @param coroutineContext Context which will be used to launch effects ([LaunchedEffect],
     * [rememberCoroutineScope]) and run recompositions.
     * @param density Initial density of the content which will be used to convert [dp] units.
     * @param layoutDirection Initial layout direction of the content.
     * @param invalidate Callback which will be called when the content need to be recomposed or
     * rerendered. If you draw your content using [render] method, in this callback you should
     * schedule the next [render] in your rendering loop.
     */
    @ExperimentalComposeUiApi
    constructor(
        textInputService: PlatformTextInputService,
        coroutineContext: CoroutineContext = Dispatchers.Unconfined,
        density: Density = Density(1f),
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        invalidate: () -> Unit = {}
    ) : this(
        coroutineContext,
        object : Platform by Platform.Empty {
            override val textInputService: PlatformTextInputService get() = textInputService
        },
        density,
        layoutDirection,
        invalidate,
    )

    /**
     * Constructs [ComposeScene]
     *
     * @param coroutineContext Context which will be used to launch effects ([LaunchedEffect],
     * [rememberCoroutineScope]) and run recompositions.
     * @param density Initial density of the content which will be used to convert [dp] units.
     * @param invalidate Callback which will be called when the content need to be recomposed or
     * rerendered. If you draw your content using [render] method, in this callback you should
     * schedule the next [render] in your rendering loop.
     */
    constructor(
        coroutineContext: CoroutineContext = Dispatchers.Unconfined,
        density: Density = Density(1f),
        invalidate: () -> Unit = {}
    ) : this(
        coroutineContext,
        density,
        LayoutDirection.Ltr,
        invalidate
    )

    /**
     * Constructs [ComposeScene]
     *
     * @param textInputService Platform specific text input service
     * @param coroutineContext Context which will be used to launch effects ([LaunchedEffect],
     * [rememberCoroutineScope]) and run recompositions.
     * @param density Initial density of the content which will be used to convert [dp] units.
     * @param invalidate Callback which will be called when the content need to be recomposed or
     * rerendered. If you draw your content using [render] method, in this callback you should
     * schedule the next [render] in your rendering loop.
     */
    constructor(
        textInputService: PlatformTextInputService,
        coroutineContext: CoroutineContext = Dispatchers.Unconfined,
        density: Density = Density(1f),
        invalidate: () -> Unit = {}
    ) : this(
        textInputService,
        coroutineContext,
        density,
        LayoutDirection.Ltr,
        invalidate,
    )

    private val snapshotInvalidationTracker = SnapshotInvalidationTracker(::invalidateIfNeeded)

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
    private fun invalidateIfNeeded() {
        hasPendingDraws = frameClock.hasAwaiters ||
            snapshotInvalidationTracker.hasInvalidations ||
            inputHandler.hasInvalidations
        if (hasPendingDraws && !isInvalidationDisabled && !isClosed) {
            invalidate()
        }
    }

    @Deprecated(
        message = "The scene isn't tracking list of roots anymore",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("SkiaRootForTest.onRootCreatedCallback")
    )
    val roots: Set<RootForTest>
        get() = throw NotImplementedError()

    /**
     * Semantics owner that owns [SemanticsNode] objects and notifies listeners of changes to the
     * semantics tree.
     */
    @ExperimentalComposeUiApi
    val semanticsOwner: SemanticsOwner
        get() = requireNotNull(mainOwner).semanticsOwner

    /**
     * The mouse cursor position or null if cursor is not inside a scene.
     */
    internal val lastKnownCursorPosition: Offset?
        get() = inputHandler.lastKnownCursorPosition

    private val job = Job()
    private val coroutineScope = CoroutineScope(coroutineContext + job)

    // We use FlushCoroutineDispatcher for effectDispatcher not because we need `flush` for
    // LaunchEffect tasks, but because we need to know if it is idle (hasn't scheduled tasks)
    private val effectDispatcher = FlushCoroutineDispatcher(coroutineScope)
    private val recomposeDispatcher = FlushCoroutineDispatcher(coroutineScope)
    private val frameClock = BroadcastFrameClock(onNewAwaiters = ::invalidateIfNeeded)
    private val recomposer = Recomposer(coroutineContext + job + effectDispatcher)
    private val inputHandler = ComposeSceneInputHandler(
        processPointerInputEvent = { mainOwner?.onPointerInput(it) },
        processKeyEvent = { mainOwner?.onKeyEvent(it) == true }
    )

    internal var mainOwner: RootNodeOwner? = null
    private var composition: Composition? = null

    /**
     * Density of the content which will be used to convert [dp] units.
     */
    var density: Density = density
        set(value) {
            check(!isClosed) { "ComposeScene is closed" }
            field = value
            mainOwner?.density = value
        }

    /**
     * The layout direction of the content, provided to the composition via [LocalLayoutDirection].
     */
    @ExperimentalComposeUiApi
    var layoutDirection: LayoutDirection = layoutDirection
        set(value) {
            check(!isClosed) { "ComposeScene is closed" }
            field = value
            mainOwner?.layoutDirection = value
        }

    private var isClosed = false

    init {
        GlobalSnapshotManager.ensureStarted()
        coroutineScope.launch(
            recomposeDispatcher + frameClock,
            start = CoroutineStart.UNDISPATCHED
        ) {
            recomposer.runRecomposeAndApplyChanges()
        }
    }

    /**
     * Close all resources and subscriptions. Not calling this method when [ComposeScene] is no
     * longer needed will cause a memory leak.
     *
     * All effects launched via [LaunchedEffect] or [rememberCoroutineScope] will be cancelled
     * (but not immediately).
     *
     * After calling this method, you cannot call any other method of this [ComposeScene].
     */
    fun close() {
        check(!isClosed) { "ComposeScene is already closed" }
        composition?.dispose()
        mainOwner?.dispose()
        recomposer.cancel()
        job.cancel()
        isClosed = true
    }

    /**
     * Returns true if there are pending recompositions, renders or dispatched tasks.
     * Can be called from any thread.
     */
    override fun hasInvalidations() = hasPendingDraws ||
        recomposer.hasPendingWork ||
        effectDispatcher.hasTasks() ||
        recomposeDispatcher.hasTasks()


    internal fun attach(
        owner: RootNodeOwner,
        focusable: Boolean,
        onOutsidePointerEvent: ((PointerInputEvent) -> Unit)? = null,
    ) {
//        (mainOwner as CombinedRootNodeOwner?)?.attach(owner)
//        if (isFocused) {
//            owner.focusOwner.takeFocus()
//        } else {
//            owner.focusOwner.releaseFocus()
//        }
//        inputHandler.onPointerUpdate()
//        invalidateIfNeeded()
    }

    internal fun detach(owner: RootNodeOwner) {
//        (mainOwner as CombinedRootNodeOwner?)?.detach(owner)
//        inputHandler.onPointerUpdate()
//        invalidateIfNeeded()
    }

    /**
     * Top-level composition locals, which will be provided for the Composable content, which is set by [setContent].
     *
     * `null` if no composition locals should be provided.
     */
    var compositionLocalContext: CompositionLocalContext? by mutableStateOf(null)

    /**
     * Update the composition with the content described by the [content] composable. After this
     * has been called the changes to produce the initial composition has been calculated and
     * applied to the composition.
     *
     * Will throw an [IllegalStateException] if the composition has been disposed.
     *
     * @param content Content of the [ComposeScene]
     */
    override fun setContent(
        content: @Composable () -> Unit
    ) = setContent(
        parentComposition = null,
        content = content
    )

    // TODO(demin): We should configure routing of key events if there
    //  are any popups/root present:
    //   - ComposeScene.sendKeyEvent
    //   - ComposeScene.onPreviewKeyEvent (or Window.onPreviewKeyEvent)
    //   - Popup.onPreviewKeyEvent
    //   - NestedPopup.onPreviewKeyEvent
    //   - NestedPopup.onKeyEvent
    //   - Popup.onKeyEvent
    //   - ComposeScene.onKeyEvent
    //  Currently we have this routing:
    //   - [active Popup or the main content].onPreviewKeyEvent
    //   - [active Popup or the main content].onKeyEvent
    //   After we change routing, we can remove onPreviewKeyEvent/onKeyEvent from this method
    internal fun setContent(
        parentComposition: CompositionContext? = null,
        onPreviewKeyEvent: (ComposeKeyEvent) -> Boolean = { false },
        onKeyEvent: (ComposeKeyEvent) -> Boolean = { false },
        content: @Composable () -> Unit
    ) {
        check(!isClosed) { "ComposeScene is closed" }
        inputHandler.onChangeContent()
        composition?.dispose()
        mainOwner?.dispose()

        val mainOwner = createMainLayer(
            KeyInputElement(onKeyEvent = onKeyEvent, onPreKeyEvent = onPreviewKeyEvent)
        )

        this.mainOwner = mainOwner

        // setContent might spawn more owners, so this.mainOwner should be set before that.
        composition = mainOwner.setContent(
            parentComposition ?: recomposer,
            { compositionLocalContext }
        ) {
            CompositionLocalProvider(
                LocalComposeScene provides this,
                content = content
            )
        }

        // to perform all pending work synchronously
        recomposeDispatcher.flush()
    }

    private fun createMainLayer(modifier: Modifier): RootNodeOwner {
        val owner = RootNodeOwner(
            snapshotInvalidationTracker = snapshotInvalidationTracker,
            inputHandler = inputHandler,
            platform = platform,
            density = density,
            layoutDirection = layoutDirection,
            coroutineContext = recomposer.effectCoroutineContext,
            constraints = constraints,
        )
        owner.initialize()
        owner.setRootModifier(modifier)
        if (isFocused) {
            owner.focusOwner.takeFocus()
        } else {
            owner.focusOwner.releaseFocus()
        }
        inputHandler.onPointerUpdate()
        invalidateIfNeeded()

        return owner
    }

    internal fun createAttachedLayer(
        coroutineContext: CoroutineContext,
        modifier: Modifier
    ): RootNodeOwner {
        val owner = RootNodeOwner(
            snapshotInvalidationTracker = snapshotInvalidationTracker,
            inputHandler = inputHandler,
            platform = platform,
            density = density,
            layoutDirection = layoutDirection,
            coroutineContext = coroutineContext,
            constraints = constraints,
        )
        owner.initialize()
        owner.setRootModifier(modifier)

        return owner
    }

    /**
     * Constraints used to measure and layout content.
     */
    override var constraints: Constraints = Constraints()
        set(value) {
            field = value
            mainOwner?.constraints = constraints
        }

    /**
     * Returns the current content size
     */
    override val contentSize: IntSize
        get() {
            check(!isClosed) { "ComposeScene is closed" }
            val mainOwner = mainOwner ?: return IntSize.Zero
            measureAndLayout()
            return mainOwner.contentSize
        }

    internal fun hitTestInteropView(position: Offset): Boolean =
        mainOwner?.hitTestInteropView(position) ?: false

    /**
     * Render the current content on [canvas]. Passed [nanoTime] will be used to drive all
     * animations in the content (or any other code, which uses [withFrameNanos]
     */
    fun render(canvas: SkCanvas, nanoTime: Long): Unit = postponeInvalidation {
        recomposeDispatcher.flush()
        frameClock.sendFrame(nanoTime) // Recomposition

        measureAndLayout()
        draw(canvas.asComposeCanvas())
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
        measureAndLayout()
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

    @ExperimentalComposeUiApi
    override fun sendPointerEvent(
        eventType: PointerEventType,
        pointers: List<Pointer>,
        buttons: PointerButtons,
        keyboardModifiers: PointerKeyboardModifiers,
        scrollDelta: Offset,
        timeMillis: Long,
        nativeEvent: Any?,
        button: PointerButton?,
    ) = postponeInvalidation {
        measureAndLayout()
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

    private fun measureAndLayout() {
        snapshotInvalidationTracker.onLayout()
        mainOwner?.measureAndLayout()
        inputHandler.onLayout()
    }

    private fun draw(canvas: Canvas) {
        snapshotInvalidationTracker.onDraw()
        mainOwner?.draw(canvas)
    }

    private var isFocused = true

    /**
     * Call this function to clear focus from the currently focused component, and set the focus to
     * the root focus modifier.
     */
    @ExperimentalComposeUiApi
    fun releaseFocus() {
        mainOwner?.focusOwner?.releaseFocus()
        isFocused = false
    }

    @ExperimentalComposeUiApi
    fun requestFocus() {
        mainOwner?.focusOwner?.takeFocus()
        isFocused = true
    }

    /**
     * Moves focus in the specified [direction][FocusDirection].
     *
     * If you are not satisfied with the default focus order, consider setting a custom order using
     * [Modifier.focusProperties()][focusProperties].
     *
     * @return true if focus was moved successfully. false if the focused item is unchanged.
     */
    @ExperimentalComposeUiApi
    fun moveFocus(focusDirection: FocusDirection): Boolean =
        mainOwner?.focusOwner?.moveFocus(focusDirection) ?: false

    /**
     * Represents pointer such as mouse cursor, or touch/stylus press.
     * There can be multiple pointers on the screen at the same time.
     */
    @ExperimentalComposeUiApi
    class Pointer(
        /**
         * Unique id associated with the pointer. Used to distinguish between multiple pointers that can exist
         * at the same time (i.e. multiple pressed touches).
         */
        val id: PointerId,

        /**
         * The [Offset] of the pointer.
         */
        val position: Offset,

        /**
         * `true` if the pointer event is considered "pressed". For example,
         * a finger touches the screen or any mouse button is pressed.
         *  During the up event, pointer is considered not pressed.
         */
        val pressed: Boolean,

        /**
         * The device type associated with the pointer, such as [mouse][PointerType.Mouse],
         * or [touch][PointerType.Touch].
         */
        val type: PointerType = PointerType.Mouse,

        /**
         * Pressure of the pointer. 0.0 - no pressure, 1.0 - average pressure
         */
        val pressure: Float = 1.0f,

        /**
         * High-frequency pointer moves in between the current event and the last event.
         * can be used for extra accuracy when touchscreen rate exceeds framerate.
         *
         * Can be empty, if a platform doesn't provide any.
         *
         * For example, on iOS this list is populated using the data of.
         * https://developer.apple.com/documentation/uikit/uievent/1613808-coalescedtouchesfortouch?language=objc
         */
        val historical: List<HistoricalChange> = emptyList()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Pointer

            if (position != other.position) return false
            if (pressed != other.pressed) return false
            if (type != other.type) return false
            if (id != other.id) return false
            if (pressure != other.pressure) return false

            return true
        }

        override fun hashCode(): Int {
            var result = position.hashCode()
            result = 31 * result + pressed.hashCode()
            result = 31 * result + type.hashCode()
            result = 31 * result + id.hashCode()
            result = 31 * result + pressure.hashCode()
            return result
        }

        override fun toString(): String {
            return "Pointer(position=$position, pressed=$pressed, type=$type, id=$id, pressure=$pressure)"
        }
    }
}
