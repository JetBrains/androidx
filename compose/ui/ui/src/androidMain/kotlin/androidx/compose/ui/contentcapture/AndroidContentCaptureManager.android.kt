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

package androidx.compose.ui.contentcapture

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LongSparseArray
import android.view.View
import android.view.translation.TranslationRequestValue
import android.view.translation.ViewTranslationRequest
import android.view.translation.ViewTranslationResponse
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.collection.IntObjectMap
import androidx.collection.MutableIntObjectMap
import androidx.collection.intObjectMapOf
import androidx.collection.mutableIntObjectMapOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.internal.checkPreconditionNotNull
import androidx.compose.ui.platform.AndroidComposeView
import androidx.compose.ui.platform.SemanticsNodeCopy
import androidx.compose.ui.platform.SemanticsNodeWithAdjustedBounds
import androidx.compose.ui.platform.coreshims.ContentCaptureSessionCompat
import androidx.compose.ui.platform.coreshims.ViewCompatShims
import androidx.compose.ui.platform.coreshims.ViewStructureCompat
import androidx.compose.ui.platform.getAllUncoveredSemanticsNodesToIntObjectMap
import androidx.compose.ui.platform.getTextLayoutResult
import androidx.compose.ui.platform.toLegacyClassName
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastJoinToString
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.function.Consumer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

// TODO(b/272068594): Fix the primitive usage after completing the semantics refactor.
// TODO(b/318748747): Add an interface for ContentCaptureManager to the common module, and then this
//  would be the AndroidImplementation. When we create a LocalContentCaptureManager in the future,
//  we would expose the interface but not this implementation.
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("NullAnnotationGroup")
internal class AndroidContentCaptureManager(
    val view: AndroidComposeView,
    var onContentCaptureSession: () -> ContentCaptureSessionCompat?
) : ContentCaptureManager, DefaultLifecycleObserver, View.OnAttachStateChangeListener {

    @VisibleForTesting internal var contentCaptureSession: ContentCaptureSessionCompat? = null

    /** An ordered list of buffered content capture events. */
    private val bufferedEvents = mutableListOf<ContentCaptureEvent>()

    /**
     * Delay before dispatching a recurring accessibility event in milliseconds. This delay
     * guarantees that a recurring event will be send at most once during the
     * [SendRecurringContentCaptureEventsIntervalMillis] time frame.
     */
    private var SendRecurringContentCaptureEventsIntervalMillis = 100L

    /**
     * Indicates whether the translated information is show or hide in the [AndroidComposeView].
     *
     * See
     * [ViewTranslationCallback](https://cs.android.com/android/platform/superproject/+/refs/heads/master:frameworks/base/core/java/android/view/translation/ViewTranslationCallback.java)
     * for more details of the View translation API.
     */
    private enum class TranslateStatus {
        SHOW_ORIGINAL,
        SHOW_TRANSLATED
    }

    private var translateStatus = TranslateStatus.SHOW_ORIGINAL

    private var currentSemanticsNodesInvalidated = true
    private val boundsUpdateChannel = Channel<Unit>(1)
    internal val handler = Handler(Looper.getMainLooper())

    /**
     * Up to date semantics nodes in pruned semantics tree. It always reflects the current semantics
     * tree. They key is the virtual view id(the root node has a key of
     * AccessibilityNodeProviderCompat.HOST_VIEW_ID and other node has a key of its id).
     */
    internal var currentSemanticsNodes: IntObjectMap<SemanticsNodeWithAdjustedBounds> =
        intObjectMapOf()
        get() {
            if (currentSemanticsNodesInvalidated) { // first instance of retrieving all nodes
                currentSemanticsNodesInvalidated = false
                field = view.semanticsOwner.getAllUncoveredSemanticsNodesToIntObjectMap()
                currentSemanticsNodesSnapshotTimestampMillis = System.currentTimeMillis()
            }
            return field
        }

    private var currentSemanticsNodesSnapshotTimestampMillis = 0L

    // previousSemanticsNodes holds the previous pruned semantics tree so that we can compare the
    // current and previous trees in onSemanticsChange(). We use SemanticsNodeCopy here because
    // SemanticsNode's children are dynamically generated and always reflect the current children.
    // We need to keep a copy of its old structure for comparison.
    private var previousSemanticsNodes: MutableIntObjectMap<SemanticsNodeCopy> =
        mutableIntObjectMapOf()
    private var previousSemanticsRoot =
        SemanticsNodeCopy(view.semanticsOwner.unmergedRootSemanticsNode, intObjectMapOf())
    private var checkingForSemanticsChanges = false

    private val contentCaptureChangeChecker = Runnable {
        if (!isEnabled) return@Runnable

        // TODO(mnuzen): there might be a case where `view.measureAndLayout()` is called twice --
        // once by the CC checker and once by the a11y checker.
        view.measureAndLayout()

        // Semantics structural change
        // Always send disappear event first.
        sendContentCaptureDisappearEvents()
        sendContentCaptureAppearEvents(
            view.semanticsOwner.unmergedRootSemanticsNode,
            previousSemanticsRoot
        )

        // Property change
        checkForContentCapturePropertyChanges(currentSemanticsNodes)
        updateSemanticsCopy()

        checkingForSemanticsChanges = false
    }

    override fun onViewAttachedToWindow(v: View) {}

    override fun onViewDetachedFromWindow(v: View) {
        handler.removeCallbacks(contentCaptureChangeChecker)
        contentCaptureSession = null
    }

    /** True if any content capture service enabled in the system. */
    internal val isEnabled: Boolean
        get() = ContentCaptureManager.isEnabled && contentCaptureSession != null

    override fun onStart(owner: LifecycleOwner) {
        contentCaptureSession = onContentCaptureSession()
        updateBuffersOnAppeared(index = -1, view.semanticsOwner.unmergedRootSemanticsNode)
        notifyContentCaptureChanges()
    }

    override fun onStop(owner: LifecycleOwner) {
        updateBuffersOnDisappeared(view.semanticsOwner.unmergedRootSemanticsNode)
        notifyContentCaptureChanges()
        contentCaptureSession = null
    }

    /**
     * This suspend function loops for the entire lifetime of the Compose instance: it consumes
     * recent layout changes and sends events to the accessibility and content capture framework in
     * batches separated by a 100ms delay.
     */
    internal suspend fun boundsUpdatesEventLoop() {
        for (notification in boundsUpdateChannel) {
            if (isEnabled) {
                notifyContentCaptureChanges()
            }
            if (!checkingForSemanticsChanges) {
                checkingForSemanticsChanges = true
                handler.post(contentCaptureChangeChecker)
            }

            delay(SendRecurringContentCaptureEventsIntervalMillis)
        }
    }

    internal fun onSemanticsChange() {
        // When content capture is turned off, we still want to keep
        // currentSemanticsNodesInvalidated up to date so that when content capture is turned on
        // later, we can refresh currentSemanticsNodes if currentSemanticsNodes is stale.
        currentSemanticsNodesInvalidated = true

        if (isEnabled && !checkingForSemanticsChanges) {
            checkingForSemanticsChanges = true

            handler.post(contentCaptureChangeChecker)
        }
    }

    internal fun onLayoutChange() {
        // When content capture is turned off, we still want to keep
        // currentSemanticsNodesInvalidated up to date so that when content capture is turned on
        // later, we can refresh currentSemanticsNodes if currentSemanticsNodes is stale.
        currentSemanticsNodesInvalidated = true

        // The layout change of a LayoutNode will also affect its children, so even if it doesn't
        // have semantics attached, we should process it.
        if (isEnabled) notifySubtreeStateChangeIfNeeded()
    }

    private fun sendContentCaptureDisappearEvents() {
        previousSemanticsNodes.forEachKey { key ->
            if (!currentSemanticsNodes.contains(key)) {
                bufferContentCaptureViewDisappeared(key)
                notifySubtreeStateChangeIfNeeded()
            }
        }
    }

    private fun sendContentCaptureAppearEvents(newNode: SemanticsNode, oldNode: SemanticsNodeCopy) {
        // Iterate the new tree to notify content capture appear
        newNode.fastForEachReplacedVisibleChildren { index, child ->
            if (!oldNode.children.contains(child.id)) {
                updateBuffersOnAppeared(index, child)
                notifySubtreeStateChangeIfNeeded()
            }
        }

        newNode.replacedChildren.fastForEach { child ->
            if (
                currentSemanticsNodes.contains(child.id) &&
                    previousSemanticsNodes.contains(child.id)
            ) {
                val prevNodeCopy =
                    checkPreconditionNotNull(previousSemanticsNodes[child.id]) {
                        "node not present in pruned tree before this change"
                    }
                sendContentCaptureAppearEvents(child, prevNodeCopy)
            }
        }
    }

    // Analogous to `sendSemanticsPropertyChangeEvents`
    private fun checkForContentCapturePropertyChanges(
        newSemanticsNodes: IntObjectMap<SemanticsNodeWithAdjustedBounds>
    ) {
        newSemanticsNodes.forEachKey { id ->
            // We do doing this search because the new configuration is set as a whole, so we
            // can't indicate which property is changed when setting the new configuration.
            val oldNode = previousSemanticsNodes[id]
            val newNode =
                checkPreconditionNotNull(newSemanticsNodes[id]?.semanticsNode) {
                    "no value for specified key"
                }

            // Content capture requires events to be sent when an item is added/removed.
            if (oldNode == null) {
                newNode.unmergedConfig.props.forEachKey { key ->
                    @Suppress("LABEL_NAME_CLASH")
                    if (key != SemanticsProperties.Text) return@forEachKey
                    val newText =
                        newNode.unmergedConfig.getOrNull(SemanticsProperties.Text)?.firstOrNull()
                    sendContentCaptureTextUpdateEvent(newNode.id, newText.toString())
                }
                return@forEachKey
            }

            newNode.unmergedConfig.props.forEachKey { key ->
                when (key) {
                    SemanticsProperties.Text -> {
                        val oldText =
                            oldNode.unmergedConfig
                                .getOrNull(SemanticsProperties.Text)
                                ?.firstOrNull()
                        val newText =
                            newNode.unmergedConfig
                                .getOrNull(SemanticsProperties.Text)
                                ?.firstOrNull()
                        if (oldText != newText) {
                            sendContentCaptureTextUpdateEvent(newNode.id, newText.toString())
                        }
                    }
                }
            }
        }
    }

    private fun sendContentCaptureTextUpdateEvent(id: Int, newText: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }
        val session = contentCaptureSession ?: return
        // TODO: consider having a `newContentCaptureId` function to improve readability.
        val autofillId = session.newAutofillId(id.toLong())
        checkPreconditionNotNull(autofillId) { "Invalid content capture ID" }
        session.notifyViewTextChanged(autofillId, newText)
    }

    private fun updateSemanticsCopy() {
        previousSemanticsNodes.clear()

        currentSemanticsNodes.forEach { key, value ->
            previousSemanticsNodes[key] =
                SemanticsNodeCopy(value.semanticsNode, currentSemanticsNodes)
        }
        previousSemanticsRoot =
            SemanticsNodeCopy(view.semanticsOwner.unmergedRootSemanticsNode, currentSemanticsNodes)
    }

    private fun notifySubtreeStateChangeIfNeeded() {
        boundsUpdateChannel.trySend(Unit)
    }

    private fun SemanticsNode.toViewStructure(index: Int): ViewStructureCompat? {
        val session = contentCaptureSession ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }

        val rootAutofillId = ViewCompatShims.getAutofillId(view) ?: return null
        val parentNode = parent
        val parentAutofillId =
            if (parentNode != null) {
                session.newAutofillId(parentNode.id.toLong()) ?: return null
            } else {
                rootAutofillId.toAutofillId()
            }
        val structure =
            session.newVirtualViewStructure(parentAutofillId, id.toLong()) ?: return null

        val configuration = this.unmergedConfig
        if (configuration.contains(SemanticsProperties.Password)) {
            return null
        }

        structure.extras?.let {
            // Due to the batching strategy, the ContentCaptureEvent.eventTimestamp is inaccurate.
            // This timestamp in the extra bundle is the equivalent substitution.
            it.putLong(
                VIEW_STRUCTURE_BUNDLE_KEY_TIMESTAMP,
                currentSemanticsNodesSnapshotTimestampMillis
            )
            // An additional index to help the System Intelligence to rebuild hierarchy with order.
            it.putInt(VIEW_STRUCTURE_BUNDLE_KEY_ADDITIONAL_INDEX, index)
        }

        configuration.getOrNull(SemanticsProperties.TestTag)?.let {
            // Treat test tag as resourceId
            structure.setId(id, null, null, it)
        }
        configuration.getOrNull(SemanticsProperties.IsTraversalGroup)?.let {
            structure.setClassName("android.widget.ViewGroup")
        }
        configuration.getOrNull(SemanticsProperties.Text)?.let {
            structure.setClassName("android.widget.TextView")
            structure.setText(it.fastJoinToString("\n"))
        }
        configuration.getOrNull(SemanticsProperties.EditableText)?.let {
            structure.setClassName("android.widget.EditText")
            structure.setText(it)
        }
        configuration.getOrNull(SemanticsProperties.ContentDescription)?.let {
            structure.setContentDescription(it.fastJoinToString("\n"))
        }
        configuration.getOrNull(SemanticsProperties.Role)?.toLegacyClassName()?.let {
            structure.setClassName(it)
        }

        getTextLayoutResult(configuration)?.let {
            val input = it.layoutInput
            val px = input.style.fontSize.value * input.density.density * input.density.fontScale
            structure.setTextStyle(px, 0, 0, 0)
        }

        with(boundsInParent) {
            structure.setDimens(left.toInt(), top.toInt(), 0, 0, width.toInt(), height.toInt())
        }
        return structure
    }

    private fun SemanticsNode.fastForEachReplacedVisibleChildren(
        action: (Int, SemanticsNode) -> Unit
    ) =
        this.replacedChildren.fastForEachIndexedWithFilter(action) {
            currentSemanticsNodes.contains(it.id)
        }

    private inline fun <T> List<T>.fastForEachIndexedWithFilter(
        action: (Int, T) -> Unit,
        predicate: (T) -> Boolean
    ) {
        var i = 0
        for (index in indices) {
            val item = get(index)
            if (predicate(item)) {
                action(i, item)
                i++
            }
        }
    }

    private fun bufferContentCaptureViewAppeared(
        virtualId: Int,
        viewStructure: ViewStructureCompat?
    ) {
        if (viewStructure == null) {
            return
        }

        bufferedEvents.add(
            ContentCaptureEvent(
                virtualId,
                currentSemanticsNodesSnapshotTimestampMillis,
                ContentCaptureEventType.VIEW_APPEAR,
                viewStructure
            )
        )
    }

    private fun bufferContentCaptureViewDisappeared(virtualId: Int) {
        bufferedEvents.add(
            ContentCaptureEvent(
                virtualId,
                currentSemanticsNodesSnapshotTimestampMillis,
                ContentCaptureEventType.VIEW_DISAPPEAR,
                null
            )
        )
    }

    private fun notifyContentCaptureChanges() {
        val session = contentCaptureSession ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        if (bufferedEvents.isNotEmpty()) {
            bufferedEvents.fastForEach { event ->
                when (event.type) {
                    ContentCaptureEventType.VIEW_APPEAR -> {
                        event.structureCompat?.let { node ->
                            session.notifyViewAppeared(node.toViewStructure())
                        }
                    }
                    ContentCaptureEventType.VIEW_DISAPPEAR -> {
                        session.newAutofillId(event.id.toLong())?.let { autofillId ->
                            session.notifyViewDisappeared(autofillId)
                        }
                    }
                }
            }
            session.flush()
            bufferedEvents.clear()
        }
    }

    private fun updateBuffersOnAppeared(index: Int, node: SemanticsNode) {
        if (!isEnabled) {
            return
        }

        updateTranslationOnAppeared(node)

        bufferContentCaptureViewAppeared(node.id, node.toViewStructure(index))
        node.fastForEachReplacedVisibleChildren { i, child -> updateBuffersOnAppeared(i, child) }
    }

    private fun updateBuffersOnDisappeared(node: SemanticsNode) {
        if (!isEnabled) {
            return
        }
        bufferContentCaptureViewDisappeared(node.id)
        node.replacedChildren.fastForEach { child -> updateBuffersOnDisappeared(child) }
    }

    private fun updateTranslationOnAppeared(node: SemanticsNode) {
        val config = node.unmergedConfig
        val isShowingTextSubstitution =
            config.getOrNull(SemanticsProperties.IsShowingTextSubstitution)

        if (translateStatus == TranslateStatus.SHOW_ORIGINAL && isShowingTextSubstitution == true) {
            config.getOrNull(SemanticsActions.ShowTextSubstitution)?.action?.invoke(false)
        } else if (
            translateStatus == TranslateStatus.SHOW_TRANSLATED && isShowingTextSubstitution == false
        ) {
            config.getOrNull(SemanticsActions.ShowTextSubstitution)?.action?.invoke(true)
        }
    }

    // TODO(b/272068594): Find a way to use Public API instead of using this in tests.
    internal fun onShowTranslation() {
        translateStatus = TranslateStatus.SHOW_TRANSLATED
        showTranslatedText()
    }

    // TODO(b/272068594): Find a way to use Public API instead of using this in tests.
    internal fun onHideTranslation() {
        translateStatus = TranslateStatus.SHOW_ORIGINAL
        hideTranslatedText()
    }

    // TODO(b/272068594): Find a way to use Public API instead of using this in tests.
    internal fun onClearTranslation() {
        translateStatus = TranslateStatus.SHOW_ORIGINAL
        clearTranslatedText()
    }

    private fun showTranslatedText() {
        currentSemanticsNodes.forEachValue { node ->
            val config = node.semanticsNode.unmergedConfig
            if (config.getOrNull(SemanticsProperties.IsShowingTextSubstitution) == false) {
                config.getOrNull(SemanticsActions.ShowTextSubstitution)?.action?.invoke(true)
            }
        }
    }

    private fun hideTranslatedText() {
        currentSemanticsNodes.forEachValue { node ->
            val config = node.semanticsNode.unmergedConfig
            if (config.getOrNull(SemanticsProperties.IsShowingTextSubstitution) == true) {
                config.getOrNull(SemanticsActions.ShowTextSubstitution)?.action?.invoke(false)
            }
        }
    }

    private fun clearTranslatedText() {
        currentSemanticsNodes.forEachValue { node ->
            val config = node.semanticsNode.unmergedConfig
            if (config.getOrNull(SemanticsProperties.IsShowingTextSubstitution) != null) {
                config.getOrNull(SemanticsActions.ClearTextSubstitution)?.action?.invoke()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private object ViewTranslationHelperMethods {
        @Suppress("UNUSED_PARAMETER")
        @RequiresApi(Build.VERSION_CODES.S)
        fun onCreateVirtualViewTranslationRequests(
            contentCaptureManager: AndroidContentCaptureManager,
            virtualIds: LongArray,
            supportedFormats: IntArray,
            requestsCollector: Consumer<ViewTranslationRequest?>
        ) {

            virtualIds.forEach {
                val node =
                    contentCaptureManager.currentSemanticsNodes[it.toInt()]?.semanticsNode
                        ?: return@forEach
                val requestBuilder =
                    ViewTranslationRequest.Builder(
                        contentCaptureManager.view.autofillId,
                        node.id.toLong()
                    )

                val text =
                    AnnotatedString(
                        node.unmergedConfig
                            .getOrNull(SemanticsProperties.Text)
                            ?.fastJoinToString("\n") ?: return@forEach
                    )

                requestBuilder.setValue(
                    ViewTranslationRequest.ID_TEXT,
                    TranslationRequestValue.forText(text)
                )
                requestsCollector.accept(requestBuilder.build())
            }
        }

        @RequiresApi(Build.VERSION_CODES.S)
        fun onVirtualViewTranslationResponses(
            contentCaptureManager: AndroidContentCaptureManager,
            response: LongSparseArray<ViewTranslationResponse?>
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return
            }

            // TODO(mnuzen): move post into `AndroidComposeView`
            // This callback can be invoked from non UI thread.
            if (Looper.getMainLooper().thread == Thread.currentThread()) {
                doTranslation(contentCaptureManager, response)
            } else {
                contentCaptureManager.view.post { doTranslation(contentCaptureManager, response) }
            }
        }

        private fun doTranslation(
            contentCaptureManager: AndroidContentCaptureManager,
            response: LongSparseArray<ViewTranslationResponse?>
        ) {
            val size = response.size()
            for (i in 0 until size) {
                val key = response.keyAt(i)
                response.get(key)?.getValue(ViewTranslationRequest.ID_TEXT)?.text?.let {
                    contentCaptureManager.currentSemanticsNodes[key.toInt()]?.semanticsNode?.let {
                        semanticsNode ->
                        semanticsNode.unmergedConfig
                            .getOrNull(SemanticsActions.SetTextSubstitution)
                            ?.action
                            ?.invoke(AnnotatedString(it.toString()))
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    internal fun onCreateVirtualViewTranslationRequests(
        virtualIds: LongArray,
        supportedFormats: IntArray,
        requestsCollector: Consumer<ViewTranslationRequest?>
    ) {
        ViewTranslationHelperMethods.onCreateVirtualViewTranslationRequests(
            this,
            virtualIds,
            supportedFormats,
            requestsCollector
        )
    }

    @RequiresApi(Build.VERSION_CODES.S)
    internal fun onVirtualViewTranslationResponses(
        contentCaptureManager: AndroidContentCaptureManager,
        response: LongSparseArray<ViewTranslationResponse?>
    ) {
        ViewTranslationHelperMethods.onVirtualViewTranslationResponses(
            contentCaptureManager,
            response
        )
    }

    companion object {
        const val VIEW_STRUCTURE_BUNDLE_KEY_TIMESTAMP = "android.view.contentcapture.EventTimestamp"
        const val VIEW_STRUCTURE_BUNDLE_KEY_ADDITIONAL_INDEX =
            "android.view.ViewStructure.extra.EXTRA_VIEW_NODE_INDEX"
    }
}

private enum class ContentCaptureEventType {
    VIEW_APPEAR,
    VIEW_DISAPPEAR,
}

private data class ContentCaptureEvent(
    val id: Int,
    val timestamp: Long,
    val type: ContentCaptureEventType,
    val structureCompat: ViewStructureCompat?,
)
