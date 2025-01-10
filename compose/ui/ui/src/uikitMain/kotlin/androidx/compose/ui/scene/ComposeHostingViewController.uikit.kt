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

package androidx.compose.ui.scene

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.backhandler.BackGestureDispatcher
import androidx.compose.ui.backhandler.LocalBackGestureDispatcher
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.hapticfeedback.CupertinoHapticFeedback
import androidx.compose.ui.platform.AccessibilitySyncOptions
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInternalViewModelStoreOwner
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.PlatformWindowContext
import androidx.compose.ui.uikit.ComposeUIViewControllerConfiguration
import androidx.compose.ui.uikit.InterfaceOrientation
import androidx.compose.ui.uikit.LocalInterfaceOrientation
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.uikit.PlistSanityCheck
import androidx.compose.ui.uikit.density
import androidx.compose.ui.uikit.utils.CMPViewController
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.asDpRect
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.viewinterop.UIKitInteropAction
import androidx.compose.ui.viewinterop.UIKitInteropTransaction
import androidx.compose.ui.window.ComposeView
import androidx.compose.ui.window.DisplayLinkListener
import androidx.compose.ui.window.FocusStack
import androidx.compose.ui.window.GestureEvent
import androidx.compose.ui.window.MetalView
import androidx.compose.ui.window.ViewControllerBasedLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.coroutines.CoroutineContext
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExportObjCClass
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.OSVersion
import org.jetbrains.skiko.available
import platform.CoreGraphics.CGSize
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIAccessibilityIsVoiceOverRunning
import platform.UIKit.UIAccessibilityVoiceOverStatusChanged
import platform.UIKit.UIAccessibilityVoiceOverStatusDidChangeNotification
import platform.UIKit.UIApplication
import platform.UIKit.UIEvent
import platform.UIKit.UIStatusBarAnimation
import platform.UIKit.UIStatusBarStyle
import platform.UIKit.UITraitCollection
import platform.UIKit.UIUserInterfaceLayoutDirection
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIView
import platform.UIKit.UIViewControllerTransitionCoordinatorProtocol
import platform.UIKit.UIWindow
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@OptIn(BetaInteropApi::class, ExperimentalComposeApi::class)
@ExportObjCClass
internal class ComposeHostingViewController(
    private val configuration: ComposeUIViewControllerConfiguration,
    private val content: @Composable () -> Unit,
    private val coroutineContext: CoroutineContext = Dispatchers.Main
) : CMPViewController(nibName = null, bundle = null) {
    private val lifecycleOwner = ViewControllerBasedLifecycleOwner()
    private val hapticFeedback = CupertinoHapticFeedback()

    private val rootMetalView = MetalView(
        retrieveInteropTransaction = {
            mediator?.retrieveInteropTransaction() ?: object : UIKitInteropTransaction {
                override val actions = emptyList<UIKitInteropAction>()
                override val isInteropActive = false
            }
        },
        useSeparateRenderThreadWhenPossible = configuration.parallelRendering,
        render = { canvas, nanoTime ->
            mediator?.render(canvas.asComposeCanvas(), nanoTime)
        }
    ).apply {
        canBeOpaque = configuration.opaque
    }
    private val rootView = ComposeView(
        onDidMoveToWindow = ::onDidMoveToWindow,
        onLayoutSubviews = {},
        metalView = rootMetalView,
        transparentForTouches = false,
        useOpaqueConfiguration = configuration.opaque,
    )
    private var mediator: ComposeSceneMediator? = null
    private val windowContext = PlatformWindowContext()
    private val layers = UIKitComposeSceneLayersHolder(windowContext, configuration.parallelRendering)
    private val layoutDirection get() = getLayoutDirection()
    private var hasViewAppeared: Boolean = false

    private val backGestureDispatcher = BackGestureDispatcher(
        density = rootView.density,
        getTopLeftOffsetInWindow = { IntOffset.Zero } //full screen
    )

    fun hasInvalidations(): Boolean {
        return mediator?.hasInvalidations == true || layers.hasInvalidations
    }

    /*
     * Initial value is arbitrarily chosen to avoid propagating invalid value logic
     * It's never the case in real usage scenario to reflect that in type system
     */
    private val interfaceOrientationState: MutableState<InterfaceOrientation> = mutableStateOf(
        InterfaceOrientation.Portrait
    )
    private val systemThemeState: MutableState<SystemTheme> = mutableStateOf(SystemTheme.Unknown)

    var focusStack: FocusStack? = FocusStack()

    /*
     * On iOS >= 13.0 interfaceOrientation will be deduced from [UIWindowScene] of [UIWindow]
     * to which our [ComposeViewController] is attached.
     * It's never UIInterfaceOrientationUnknown, if accessed after owning [UIWindow] was made key and visible:
     * https://developer.apple.com/documentation/uikit/uiwindow/1621601-makekeyandvisible?language=objc
     */
    private val currentInterfaceOrientation: InterfaceOrientation?
        get() {
            // Modern: https://developer.apple.com/documentation/uikit/uiwindowscene/3198088-interfaceorientation?language=objc
            // Deprecated: https://developer.apple.com/documentation/uikit/uiapplication/1623026-statusbarorientation?language=objc
            return InterfaceOrientation.getByRawValue(
                if (available(OS.Ios to OSVersion(13))) {
                    view.window?.windowScene?.interfaceOrientation
                        ?: UIApplication.sharedApplication.statusBarOrientation
                } else {
                    UIApplication.sharedApplication.statusBarOrientation
                }
            )
        }

    @Suppress("DEPRECATION")
    override fun preferredStatusBarStyle(): UIStatusBarStyle =
        configuration.delegate.preferredStatusBarStyle
            ?: super.preferredStatusBarStyle()

    @Suppress("DEPRECATION")
    override fun preferredStatusBarUpdateAnimation(): UIStatusBarAnimation =
        configuration.delegate.preferredStatysBarAnimation
            ?: super.preferredStatusBarUpdateAnimation()

    @Suppress("DEPRECATION")
    override fun prefersStatusBarHidden(): Boolean =
        configuration.delegate.prefersStatusBarHidden
            ?: super.prefersStatusBarHidden()

    override fun loadView() {
        view = rootView
    }

    @Suppress("DEPRECATION")
    override fun viewDidLoad() {
        super.viewDidLoad()

        if (configuration.enforceStrictPlistSanityCheck) {
            PlistSanityCheck.performIfNeeded()
        }

        configuration.delegate.viewDidLoad()
        systemThemeState.value = traitCollection.userInterfaceStyle.asComposeSystemTheme()

        NSNotificationCenter.defaultCenter.addObserver(
            observer = this,
            selector = NSSelectorFromString(::onAccessibilityChanged.name),
            name = UIAccessibilityVoiceOverStatusDidChangeNotification,
            `object` = null
        )
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()

        mediator?.updateInteractionRect()

        windowContext.updateWindowContainerSize()
    }

    override fun traitCollectionDidChange(previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)

        systemThemeState.value = traitCollection.userInterfaceStyle.asComposeSystemTheme()
    }

    private fun onDidMoveToWindow(window: UIWindow?) {
        val windowContainer = window ?: return
        backGestureDispatcher.setView(rootView.getParentAttachedToWindow(window))

        updateInterfaceOrientationState()

        windowContext.setWindowContainer(windowContainer)
    }

    private fun updateInterfaceOrientationState() {
        currentInterfaceOrientation?.let {
            interfaceOrientationState.value = it
        }
    }

    override fun viewWillTransitionToSize(
        size: CValue<CGSize>,
        withTransitionCoordinator: UIViewControllerTransitionCoordinatorProtocol
    ) {
        super.viewWillTransitionToSize(size, withTransitionCoordinator)

        updateInterfaceOrientationState()
        animateSizeTransition(withTransitionCoordinator)
    }

    @Suppress("DEPRECATION")
    override fun viewWillAppear(animated: Boolean) {
        super.viewWillAppear(animated)

        createMediatorIfNeeded()

        lifecycleOwner.handleViewWillAppear()
        configuration.delegate.viewWillAppear(animated)
    }

    @Suppress("DEPRECATION")
    override fun viewDidAppear(animated: Boolean) {
        super.viewDidAppear(animated)
        hasViewAppeared = true
        mediator?.sceneDidAppear()
        layers.viewDidAppear()
        configuration.delegate.viewDidAppear(animated)
    }

    @Suppress("DEPRECATION")
    override fun viewWillDisappear(animated: Boolean) {
        super.viewWillDisappear(animated)
        hasViewAppeared = false
        mediator?.sceneWillDisappear()
        layers.viewWillDisappear()
        configuration.delegate.viewWillDisappear(animated)
    }

    @Suppress("DEPRECATION")
    @OptIn(NativeRuntimeApi::class)
    override fun viewDidDisappear(animated: Boolean) {
        super.viewDidDisappear(animated)

        dispatch_async(dispatch_get_main_queue()) {
            GC.collect()
        }

        lifecycleOwner.handleViewDidDisappear()
        configuration.delegate.viewDidDisappear(animated)
    }

    override fun viewControllerDidLeaveWindowHierarchy() {
        super.viewControllerDidLeaveWindowHierarchy()

        dispose()
    }

    @OptIn(NativeRuntimeApi::class)
    override fun didReceiveMemoryWarning() {
        println("didReceiveMemoryWarning")
        GC.collect()
        super.didReceiveMemoryWarning()
    }

    /**
     * Animates the layout transition of root view as well as all layers.
     * The animation consists of the following steps
     * - Before the actual animation starts, all initial parameters should be stored in the
     * corresponding lambdas. See [ComposeSceneMediator.prepareAndGetSizeTransitionAnimation].
     * - At the time of the animation phase, the drawing canvas expands to fit the animated scene
     * throughout the animation cycle. See [ComposeView.animateSizeTransition].
     * - The animation phase consists of changing scene and window sizes frame by frame.
     * See [ComposeSceneMediator.prepareAndGetSizeTransitionAnimation] and
     * [PlatformWindowContext.prepareAndGetSizeTransitionAnimation].
     *
     * Known issue: Because per-frame updates between UIKit and Compose are not synchronised,
     * native views can be misaligned with Compose content during animation.
     *
     * @param transitionCoordinator The coordinator that mediates the transition animations.
     */
    private fun animateSizeTransition(
        transitionCoordinator: UIViewControllerTransitionCoordinatorProtocol
    ) {
        val displayLinkListener = DisplayLinkListener()
        val sizeTransitionScope = CoroutineScope(coroutineContext + displayLinkListener.frameClock)
        val duration = transitionCoordinator.transitionDuration.toDuration(DurationUnit.SECONDS)
        displayLinkListener.start()

        val animations = mediator?.prepareAndGetSizeTransitionAnimation()
        layers.animateSizeTransition(sizeTransitionScope, duration)
        rootView.animateSizeTransition(sizeTransitionScope) {
            animations?.invoke(duration)
        }

        transitionCoordinator.animateAlongsideTransition(
            animation = {},
            completion = {
                sizeTransitionScope.cancel()
                displayLinkListener.invalidate()
            }
        )
    }

    private fun createComposeSceneContext(platformContext: PlatformContext): ComposeSceneContext {
        return object : ComposeSceneContext {
            override val platformContext: PlatformContext = platformContext

            override fun createLayer(
                density: Density,
                layoutDirection: LayoutDirection,
                focusable: Boolean,
                compositionContext: CompositionContext
            ): ComposeSceneLayer {
                val layer = UIKitComposeSceneLayer(
                    onClosed = ::detachLayer,
                    createComposeSceneContext = ::createComposeSceneContext,
                    hostCompositionLocals = { ProvideContainerCompositionLocals(it) },
                    metalView = layers.metalView,
                    onGestureEvent = layers::onGestureEvent,
                    initDensity = density,
                    initLayoutDirection = layoutDirection,
                    onFocusBehavior = configuration.onFocusBehavior,
                    onAccessibilityChanged = ::onAccessibilityChanged,
                    focusStack = if (focusable) focusStack else null,
                    windowContext = windowContext,
                    compositionContext = compositionContext,
                )

                attachLayer(layer)

                return layer
            }
        }
    }

    private fun createComposeScene(
        invalidate: () -> Unit,
        platformContext: PlatformContext,
        coroutineContext: CoroutineContext,
    ): ComposeScene = PlatformLayersComposeScene(
        density = view.density,
        layoutDirection = layoutDirection,
        coroutineContext = coroutineContext,
        composeSceneContext = createComposeSceneContext(
            platformContext = platformContext
        ),
        invalidate = invalidate,
    )

    private fun createMediatorIfNeeded() {
        if (mediator == null) {
            mediator = createMediator()
            onAccessibilityChanged()
        }
    }

    private fun createMediator() = ComposeSceneMediator(
        parentView = rootView,
        onFocusBehavior = configuration.onFocusBehavior,
        focusStack = focusStack,
        windowContext = windowContext,
        coroutineContext = coroutineContext,
        redrawer = rootMetalView.redrawer,
        onGestureEvent = ::onGestureEvent,
        composeSceneFactory = ::createComposeScene,
    ).also { mediator ->
        mediator.updateInteractionRect()
        mediator.setContent {
            ProvideContainerCompositionLocals(content)
        }

        rootView.bringSubviewToFront(rootMetalView)
    }

    /**
     * Enables or disables accessibility for each layer, as well as the root mediator, taking into
     * account layer order and ability to overlay underlying content.
     */
    @ObjCAction
    private fun onAccessibilityChanged() {
        var isAccessibilityEnabled =
            configuration.accessibilitySyncOptions.isGlobalAccessibilityEnabled
        layers.withLayers {
            it.fastForEachReversed { layer ->
                layer.isAccessibilityEnabled = isAccessibilityEnabled
                isAccessibilityEnabled = isAccessibilityEnabled && !layer.focusable
            }
        }
        mediator?.isAccessibilityEnabled = isAccessibilityEnabled
    }

    /**
     * When there is an ongoing gesture, we need notify redrawer about it. It should unconditionally
     * unpause CADisplayLink which affects frequency of polling UITouch events on high frequency
     * display and force it to match display refresh rate.
     *
     * Otherwise [UIEvent]s will be dispatched with the 60hz frequency.
     */
    private fun onGestureEvent(gestureEvent: GestureEvent) {
        rootMetalView.needsProactiveDisplayLink = when (gestureEvent) {
            GestureEvent.BEGAN -> true
            GestureEvent.ENDED -> false
        }
    }

    private fun dispose() {
        rootMetalView.dispose()
        lifecycleOwner.dispose()
        mediator?.dispose()
        rootView.dispose()
        mediator = null

        layers.dispose(hasViewAppeared)

        NSNotificationCenter.defaultCenter.removeObserver(
            observer = this,
            name = UIAccessibilityVoiceOverStatusChanged,
            `object` = null
        )
    }

    private fun attachLayer(layer: UIKitComposeSceneLayer) {
        val window = checkNotNull(view.window) {
            "Cannot attach layer if the view is not in the window hierarchy"
        }

        layers.attach(window, layer, hasViewAppeared)
        onAccessibilityChanged()
    }

    private fun detachLayer(layer: UIKitComposeSceneLayer) {
        layers.detach(layer, hasViewAppeared)
        onAccessibilityChanged()
    }

    @Composable
    private fun ProvideContainerCompositionLocals(content: @Composable () -> Unit) =
        CompositionLocalProvider(
            LocalHapticFeedback provides hapticFeedback,
            LocalUIViewController provides this,
            LocalInterfaceOrientation provides interfaceOrientationState.value,
            LocalSystemTheme provides systemThemeState.value,
            LocalLifecycleOwner provides lifecycleOwner,
            LocalInternalViewModelStoreOwner provides lifecycleOwner,
            LocalBackGestureDispatcher provides backGestureDispatcher,
            content = content
        )

    private fun ComposeSceneMediator.updateInteractionRect() {
        interactionBounds = with(density) {
            view.bounds.asDpRect().toRect().roundToIntRect()
        }
    }
}

private fun UIUserInterfaceStyle.asComposeSystemTheme(): SystemTheme {
    return when (this) {
        UIUserInterfaceStyle.UIUserInterfaceStyleLight -> SystemTheme.Light
        UIUserInterfaceStyle.UIUserInterfaceStyleDark -> SystemTheme.Dark
        else -> SystemTheme.Unknown
    }
}

private fun getLayoutDirection() =
    when (UIApplication.sharedApplication().userInterfaceLayoutDirection) {
        UIUserInterfaceLayoutDirection.UIUserInterfaceLayoutDirectionRightToLeft -> LayoutDirection.Rtl
        else -> LayoutDirection.Ltr
    }

@OptIn(ExperimentalComposeApi::class)
private val AccessibilitySyncOptions.isGlobalAccessibilityEnabled
    get() =
        when (this) {
            AccessibilitySyncOptions.Never -> false
            AccessibilitySyncOptions.WhenRequiredByAccessibilityServices -> UIAccessibilityIsVoiceOverRunning()
            AccessibilitySyncOptions.Always -> true
        }

internal fun UIView.getParentAttachedToWindow(window: UIWindow): UIView {
    var result: UIView = this
    while (result.superview != window) {
        result = requireNotNull(result.superview) {
            "Window is not null, but superview is null for ${result.debugDescription}"
        }
    }
    return result
}