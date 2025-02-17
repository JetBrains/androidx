/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.compose.ui.platform

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.scene.ComposeSceneFocusManager
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.EditProcessor
import androidx.compose.ui.text.input.FinishComposingTextCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PlatformTextInputService
import androidx.compose.ui.text.input.SetComposingRegionCommand
import androidx.compose.ui.text.input.SetComposingTextCommand
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.uikit.density
import androidx.compose.ui.uikit.embedSubview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.asCGRect
import androidx.compose.ui.unit.toDpRect
import androidx.compose.ui.unit.toDpSize
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.FocusStack
import androidx.compose.ui.window.IntermediateTextInputUIView
import androidx.compose.ui.window.UserInputView
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.jetbrains.skia.BreakIterator
import platform.UIKit.NSStringFromCGRect
import platform.UIKit.UIColor
import platform.UIKit.UIPress
import platform.UIKit.UIView
import platform.UIKit.reloadInputViews

internal class UIKitTextInputService(
    private val updateView: () -> Unit,
    private val rootView: UIView,
    private val viewConfiguration: ViewConfiguration,
    private val focusStack: FocusStack?,
    private val onInputStarted: () -> Unit,
    /**
     * Callback to handle keyboard presses. The parameter is a [Set] of [UIPress] objects.
     * Erasure happens due to K/N not supporting Obj-C lightweight generics.
     */
    private val onKeyboardPresses: (Set<*>) -> Unit,
    private val focusManager: () -> ComposeSceneFocusManager
) : PlatformTextInputService, TextToolbar {

    private var currentInput: CurrentInput? = null
    private var currentImeOptions: ImeOptions? = null
    private var currentImeActionHandler: ((ImeAction) -> Unit)? = null
    private var textUIView: IntermediateTextInputUIView? = null
    private var textLayoutResult: TextLayoutResult? = null

    /**
     * Workaround to prevent calling textWillChange, textDidChange, selectionWillChange, and
     * selectionDidChange when the value of the current input is changed by the system (i.e., by the user
     * input) not by the state change of the Compose side. These 4 functions call methods of
     * UITextInputDelegateProtocol, which notifies the system that the text or the selection of the
     * current input has changed.
     *
     * This is to properly handle multi-stage input methods that depend on text selection, required by
     * languages such as Korean (Chinese and Japanese input methods depend on text marking). The writing
     * system of these languages contains letters that can be broken into multiple parts, and each keyboard
     * key corresponds to those parts. Therefore, the input system holds an internal state to combine these
     * parts correctly. However, the methods of UITextInputDelegateProtocol reset this state, resulting in
     * incorrect input. (e.g., 컴포즈 becomes ㅋㅓㅁㅍㅗㅈㅡ when not handled properly)
     *
     * @see _tempCurrentInputSession holds the same text and selection of the current input. It is used
     * instead of the old value passed to updateState. When the current value change is due to the
     * user input, updateState is not effective because _tempCurrentInputSession holds the same value.
     * However, when the current value change is due to the change of the user selection or to the
     * state change in the Compose side, updateState calls the 4 methods because the new value holds
     * these changes.
     */
    private var _tempCurrentInputSession: EditProcessor? = null

    /**
     * Workaround to prevent IME action from being called multiple times with hardware keyboards.
     * When the hardware return key is held down, iOS sends multiple newline characters to the application,
     * which makes UIKitTextInputService call the current IME action multiple times without an additional
     * debouncing logic.
     *
     * @see _tempHardwareReturnKeyPressed is set to true when the return key is pressed with a
     * hardware keyboard.
     * @see _tempImeActionIsCalledWithHardwareReturnKey is set to true when the
     * current IME action has been called within the current hardware return key press.
     */
    private var _tempHardwareReturnKeyPressed: Boolean = false
    private var _tempImeActionIsCalledWithHardwareReturnKey: Boolean = false

    /**
     * Workaround to fix voice dictation.
     * UIKit call insertText(text) and replaceRange(range,text) immediately,
     * but Compose recomposition happen on next draw frame.
     * So the value of getSelectedTextRange is in the old state when the replaceRange function is called.
     * @see _tempCursorPos helps to fix this behaviour. Permanently update _tempCursorPos in function insertText.
     * And after clear in updateState function.
     */
    private var _tempCursorPos: Int? = null
    private val mainScope = MainScope()

    override fun startInput(
        value: TextFieldValue,
        imeOptions: ImeOptions,
        onEditCommand: (List<EditCommand>) -> Unit,
        onImeActionPerformed: (ImeAction) -> Unit
    ) {
        currentInput = CurrentInput(value, onEditCommand)
        _tempCurrentInputSession = EditProcessor().apply {
            reset(value, null)
        }
        currentImeOptions = imeOptions
        currentImeActionHandler = onImeActionPerformed

        attachIntermediateTextInputView()
        textUIView?.input = createSkikoInput()
        textUIView?.inputTraits = getUITextInputTraits(imeOptions)

        showSoftwareKeyboard()
        onInputStarted()
    }

    fun startInput(
        value: TextFieldValue,
        imeOptions: ImeOptions,
        editProcessor: EditProcessor?,
        onEditCommand: (List<EditCommand>) -> Unit,
        onImeActionPerformed: (ImeAction) -> Unit
    ) {
        currentInput = CurrentInput(value, onEditCommand)
        _tempCurrentInputSession = editProcessor
        currentImeOptions = imeOptions
        currentImeActionHandler = onImeActionPerformed

        attachIntermediateTextInputView()
        textUIView?.input = createSkikoInput()
        textUIView?.inputTraits = getUITextInputTraits(imeOptions)

        showSoftwareKeyboard()
    }

    override fun stopInput() {
        flushEditCommandsIfNeeded(force = true)
        currentInput = null
        _tempCurrentInputSession = null
        currentImeOptions = null
        currentImeActionHandler = null
        hideSoftwareKeyboard()

        textUIView?.inputTraits = EmptyInputTraits
        textUIView?.input = null
        detachIntermediateTextInputView()
    }

    override fun showSoftwareKeyboard() {
        textUIView?.let {
            focusStack?.pushAndFocus(it)
        }
    }

    override fun hideSoftwareKeyboard() {
        textUIView?.let {
            focusStack?.popUntilNext(it)
        }
    }

    override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) {
        val internalOldValue = _tempCurrentInputSession?.toTextFieldValue()
        val textChanged = internalOldValue == null || internalOldValue.text != newValue.text
        val selectionChanged =
            textChanged || internalOldValue == null || internalOldValue.selection != newValue.selection
        if (textChanged) {
            textUIView?.textWillChange()
        }
        if (selectionChanged) {
            textUIView?.selectionWillChange()
        }
        _tempCurrentInputSession?.reset(newValue, null)
        println("Looks like state updated")
        currentInput?.let { input ->
            input.value = newValue
            _tempCursorPos = null
        }
        if (textChanged) {
            textUIView?.textDidChange()
        }
        if (selectionChanged) {
            textUIView?.selectionDidChange()
        }
        if (textChanged || selectionChanged) {
            updateView()
            textUIView?.reloadInputViews()
        }
    }

    fun onPreviewKeyEvent(event: KeyEvent): Boolean {
        return when (event.key) {
            Key.Enter -> handleEnterKey(event)
            Key.Backspace -> handleBackspace(event)
            Key.Escape -> handleEscape(event)
            else -> false
        }
    }

    override fun updateTextLayoutResult(
        textFieldValue: TextFieldValue,
        offsetMapping: OffsetMapping,
        textLayoutResult: TextLayoutResult,
        textFieldToRootTransform: (Matrix) -> Unit,
        innerTextFieldBounds: Rect,
        decorationBoxBounds: Rect
    ) {
        super.updateTextLayoutResult(
            textFieldValue,
            offsetMapping,
            textLayoutResult,
            textFieldToRootTransform,
            innerTextFieldBounds,
            decorationBoxBounds
        )
        updateTextLayoutResult(textLayoutResult)
    }

    fun updateTextLayoutResult(textLayoutResult: TextLayoutResult) {
        this.textLayoutResult = textLayoutResult

        val matrix = Matrix()
        textFieldToRootTransform(matrix)
        val textFieldFrame = matrix.map(innerTextFieldBounds)
        val contentFrame = matrix.map(
            Rect(
                offset = Offset.Zero,
                size = textLayoutResult.size.toSize()
            )
        )
        val frame = textFieldFrame.toDpRect(rootView.density).asCGRect()
        val bounds = Rect(
            offset = textFieldFrame.topLeft - contentFrame.topLeft,
            size = contentFrame.size
        ).toDpRect(rootView.density).asCGRect()

        println(">> Frame: ${NSStringFromCGRect(frame)} | Bounds: ${NSStringFromCGRect(bounds)}")
    }

    override fun notifyFocusedRect(rect: Rect) {
//        println("notifyFocusedRect(), rect = ${rect}, size = ${rect.size}")
        currentInput?.let { input ->
            input.focusedRect = rect
        }
    }

    private fun handleEnterKey(event: KeyEvent): Boolean {
        _tempImeActionIsCalledWithHardwareReturnKey = false
        return when (event.type) {
            KeyEventType.KeyUp -> {
                _tempHardwareReturnKeyPressed = false
                false
            }

            KeyEventType.KeyDown -> {
                _tempHardwareReturnKeyPressed = true
                // This prevents two new line characters from being added for one hardware return key press.
                true
            }

            else -> false
        }
    }

    private fun handleBackspace(event: KeyEvent): Boolean {
        // This prevents two characters from being removed for one hardware backspace key press.
        return event.type == KeyEventType.KeyDown
    }

    private fun handleEscape(event: KeyEvent): Boolean {
        return if (currentInput != null && event.type == KeyEventType.KeyUp) {
            focusManager().releaseFocus()
            true
        } else {
            false
        }
    }

    private val editCommandsBatch = mutableListOf<EditCommand>()
    private var editBatchDepth: Int = 0
        set(value) {
            field = value
            flushEditCommandsIfNeeded()
        }

    private fun sendEditCommand(vararg commands: EditCommand) {
        _tempCurrentInputSession?.apply(commands.toList())

        editCommandsBatch.addAll(commands)
        flushEditCommandsIfNeeded()
    }

    fun flushEditCommandsIfNeeded(force: Boolean = false) {
        if ((force || editBatchDepth == 0) && editCommandsBatch.isNotEmpty()) {
            val commandList = editCommandsBatch.toList()
            editCommandsBatch.clear()

            currentInput?.onEditCommand?.invoke(commandList)
        }
    }

    private fun getCursorPos(): Int? {
        if (_tempCursorPos != null) {
            return _tempCursorPos
        }
        val selection = getState()?.selection
        if (selection != null && selection.start == selection.end) {
            return selection.start
        }
        return null
    }

    private fun imeActionRequired(): Boolean =
        currentImeOptions?.run {
            singleLine || (
                imeAction != ImeAction.None
                    && imeAction != ImeAction.Default
                    && !(imeAction == ImeAction.Search && _tempHardwareReturnKeyPressed)
                )
        } ?: false

    private fun runImeActionIfRequired(): Boolean {
        val imeAction = currentImeOptions?.imeAction ?: return false
        val imeActionHandler = currentImeActionHandler ?: return false
        if (!imeActionRequired()) {
            return false
        }
        if (!_tempImeActionIsCalledWithHardwareReturnKey) {
            if (imeAction == ImeAction.Default) {
                imeActionHandler(ImeAction.Done)
            } else {
                imeActionHandler(imeAction)
            }
        }
        if (_tempHardwareReturnKeyPressed) {
            _tempImeActionIsCalledWithHardwareReturnKey = true
        }
        return true
    }

    private fun getState(): TextFieldValue? = currentInput?.value
    private fun getFocusedRect(): Rect? = currentInput?.focusedRect

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        if (textUIView == null) {
            // If showMenu() is called and textUIView is not created,
            // then it means that showMenu() called in SelectionContainer without any textfields,
            // and IntermediateTextInputView must be created to show an editing menu
            attachIntermediateTextInputView()
            updateView()
        }
        textUIView?.showTextMenu(
            targetRect = rect.toDpRect(rootView.density).asCGRect(),
            textActions = object : TextActions {
                override val copy: (() -> Unit)? = onCopyRequested
                override val cut: (() -> Unit)? = onCutRequested
                override val paste: (() -> Unit)? = onPasteRequested
                override val selectAll: (() -> Unit)? = onSelectAllRequested
            }
        )
    }

    /**
     * TODO on UIKit native behaviour is hide text menu, when touch outside
     */
    override fun hide() {
        textUIView?.hideTextMenu()
        if ((textUIView != null) && (currentInput == null)) { // means that editing context menu shown in selection container
            textUIView?.resignFirstResponder()
            detachIntermediateTextInputView()
        }
    }

    override val status: TextToolbarStatus
        get() = if (textUIView?.isTextMenuShown() == true)
            TextToolbarStatus.Shown
        else
            TextToolbarStatus.Hidden

    private fun attachIntermediateTextInputView() {
        textUIView?.removeFromSuperview()
        textUIView = IntermediateTextInputUIView(
            viewConfiguration = viewConfiguration
        ).also {
            it.setBackgroundColor(UIColor.redColor.colorWithAlphaComponent(0.5))
            it.setTintColor(UIColor.yellowColor)
            it.onKeyboardPresses = onKeyboardPresses
//            rootView.embedSubview(it)
            placeViewAtTheTop(it)
        }
    }

    private fun detachIntermediateTextInputView() {
        textUIView?.let { view ->
            view.resetOnKeyboardPressesCallback()
            mainScope.launch {
                view.removeFromSuperview()
            }
        }
        textUIView = null
    }

    private fun placeViewAtTheTop(view: UIView) {
//        val targetWindow = rootView.window ?: return
//        var topViewController = targetWindow.rootViewController
//
//        while (topViewController?.presentedViewController != null) {
//            topViewController = topViewController.presentedViewController
//        }
//        topViewController?.view?.embedSubview(view)
//        topViewController?.view?.bringSubviewToFront(view)
        val subviews = rootView.subviews as List<UIView>
        val touchView = subviews.first { it is UserInputView }
        touchView.embedSubview(view)
        touchView.bringSubviewToFront(view)
    }

    private fun createSkikoInput() = object : IOSSkikoInput {

        private var floatingCursorTranslation: Offset? = null

        override fun beginFloatingCursor(offset: DpOffset) {
            val cursorPos = getCursorPos() ?: getState()?.selection?.start ?: return
            val cursorRect = textLayoutResult?.getCursorRect(cursorPos) ?: return
            floatingCursorTranslation = cursorRect.center - offset.toOffset(rootView.density)
        }

        override fun updateFloatingCursor(offset: DpOffset) {
            val translation = floatingCursorTranslation ?: return
            val offsetPx = offset.toOffset(rootView.density)
            val pos = textLayoutResult
                ?.getOffsetForPosition(offsetPx + translation) ?: return

            sendEditCommand(SetSelectionCommand(pos, pos))
        }

        override fun endFloatingCursor() {
            floatingCursorTranslation = null
        }

        override fun beginEditBatch() {
            editBatchDepth++
        }

        override fun endEditBatch() {
            editBatchDepth--
        }

        /**
         * A Boolean value that indicates whether the text-entry object has any text.
         * https://developer.apple.com/documentation/uikit/uikeyinput/1614457-hastext
         */
        override fun hasText(): Boolean = getState()?.text?.isNotEmpty() ?: false

        /**
         * Inserts a character into the displayed text.
         * Add the character text to your class’s backing store at the index corresponding to the cursor and redisplay the text.
         * https://developer.apple.com/documentation/uikit/uikeyinput/1614543-inserttext
         * @param text A string object representing the character typed on the system keyboard.
         */
        override fun insertText(text: String) {
            if (text == "\n") {
                if (runImeActionIfRequired()) {
                    return
                }
            }
            getCursorPos()?.let {
                _tempCursorPos = it + text.length
            }
            sendEditCommand(CommitTextCommand(text, 1))
        }

        /**
         * Deletes a character from the displayed text.
         * Remove the character just before the cursor from your class’s backing store and redisplay the text.
         * https://developer.apple.com/documentation/uikit/uikeyinput/1614572-deletebackward
         */
        override fun deleteBackward() {
            // Before this function calls, iOS changes selection in setSelectedTextRange.
            // All needed characters should be allready selected, and we can just remove them.
            sendEditCommand(
                CommitTextCommand("", 0)
            )
        }

        /**
         * The text position for the end of a document.
         * https://developer.apple.com/documentation/uikit/uitextinput/1614555-endofdocument
         */
        override fun endOfDocument(): Long = getState()?.text?.length?.toLong() ?: 0L

        /**
         * The range of selected text in a document.
         * If the text range has a length, it indicates the currently selected text.
         * If it has zero length, it indicates the caret (insertion point).
         * If the text-range object is nil, it indicates that there is no current selection.
         * https://developer.apple.com/documentation/uikit/uitextinput/1614541-selectedtextrange
         */
        override fun getSelectedTextRange(): IntRange? {
            // TODO incorrect implementation
            println("!!! getSelectedTextRange")
            val cursorPos = getCursorPos()
            println("!!! cursorPos = $cursorPos")
            if (cursorPos != null) {
                println("!!! cursorpos != null -> cursorPos until cursorPos = ${cursorPos until cursorPos}, otherWat = ${IntRange(cursorPos, cursorPos)}")
                return IntRange(cursorPos, cursorPos)
            }
            val selection = getState()?.selection
            println("!!! selection = $selection")
            return if (selection != null) {
                println("!!! selection != null -> selection.start until selection.end = ${selection.start until selection.end}")
                selection.start until selection.end
            } else {
                null
            }
        }

        override fun setSelectedTextRange(range: IntRange?) {
            if (range != null) {
                sendEditCommand(
                    SetSelectionCommand(range.start, range.endInclusive + 1)
                )
            } else {
                sendEditCommand(
                    SetSelectionCommand(endOfDocument().toInt(), endOfDocument().toInt())
                )
            }
        }

        override fun selectAll() {
            sendEditCommand(
                SetSelectionCommand(0, endOfDocument().toInt())
            )
        }

        /**
         * Returns the text in the specified range.
         * https://developer.apple.com/documentation/uikit/uitextinput/1614527-text
         * @param range A range of text in a document.
         * @return A substring of a document that falls within the specified range.
         */
        override fun textInRange(range: IntRange): String {
            val text = getState()?.text
            return text?.substring(range.first, min(range.last + 1, text.length)) ?: ""
        }

        /**
         * Replaces the text in a document that is in the specified range.
         * https://developer.apple.com/documentation/uikit/uitextinput/1614558-replace
         * @param range A range of text in a document.
         * @param text A string to replace the text in range.
         */
        override fun replaceRange(range: IntRange, text: String) {
            sendEditCommand(
                SetComposingRegionCommand(range.start, range.endInclusive + 1),
                SetComposingTextCommand(text, 1),
                FinishComposingTextCommand(),
            )
        }

        /**
         * Inserts the provided text and marks it to indicate that it is part of an active input session.
         * Setting marked text either replaces the existing marked text or,
         * if none is present, inserts it in place of the current selection.
         * https://developer.apple.com/documentation/uikit/uitextinput/1614465-setmarkedtext
         * @param markedText The text to be marked.
         * @param selectedRange A range within markedText that indicates the current selection.
         * This range is always relative to markedText.
         */
        override fun setMarkedText(markedText: String?, selectedRange: IntRange) {
            if (markedText != null) {
                sendEditCommand(
                    SetComposingTextCommand(markedText, 1)
                )
            }
        }

        /**
         * The range of currently marked text in a document.
         * If there is no marked text, the value of the property is nil.
         * Marked text is provisionally inserted text that requires user confirmation;
         * it occurs in multistage text input.
         * The current selection, which can be a caret or an extended range, always occurs within the marked text.
         * https://developer.apple.com/documentation/uikit/uitextinput/1614489-markedtextrange
         */
        override fun markedTextRange(): IntRange? {
            // Checked
            val composition = getState()?.composition
            return if (composition != null) {
                composition.start until composition.end
            } else {
                null
            }
        }

        /**
         * Unmarks the currently marked text.
         * After this method is called, the value of markedTextRange is nil.
         * https://developer.apple.com/documentation/uikit/uitextinput/1614512-unmarktext
         */
        override fun unmarkText() {
            sendEditCommand(FinishComposingTextCommand())
        }

        /**
         * Returns the text position at a specified offset from another text position.
         * Returned value must be in range between 0 and length of text (inclusive).
         */
        override fun positionFromPosition(position: Long, offset: Long): Long {
            val text = getState()?.text ?: return 0

            if (position + offset >= text.lastIndex + 1) {
                return (text.lastIndex + 1).toLong()
            }
            if (position + offset <= 0) {
                return 0
            }
            var resultPosition = position.toInt()
            val iterator = BreakIterator.makeCharacterInstance()
            iterator.setText(text)

            repeat(offset.absoluteValue.toInt()) {
                val iteratorResult = if (offset > 0) {
                    iterator.following(resultPosition)
                } else {
                    iterator.preceding(resultPosition)
                }

                if (iteratorResult == BreakIterator.DONE) {
                    return resultPosition.toLong()
                } else {
                    resultPosition = iteratorResult
                }
            }

            return resultPosition.toLong()
        }

        override fun currentFocusedDpRect(): DpRect? = getFocusedRect()?.toDpRect(rootView.density)

        override fun caretDpRectForPosition(position: Long): DpRect? {
            val text = getState()?.text ?: return null
            if (position < 0 || position > text.length) {
                return null
            }
            val rect = textLayoutResult?.getCursorRect(position.toInt()) ?: return null // null in BTF2
            return rect.toDpRect(rootView.density)
        }

        override fun selectionDpRectsForRange(range: IntRange): List<DpRect> {
            println("selectionDpRectsForRange")
            val currentSelection = getState()?.selection
//
//            val adjustedRange: IntRange = when {
//                // 1. Not in the selection range
//                range.start < currentSelection.start && range.endInclusive > currentSelection.end -> return emptyList()
//                // 2. Start is not included, end is included
//                range.start < currentSelection.start && range.endInclusive <= currentSelection.end -> return emptyList()
//                // 3. Start is included, end is not included
//                // 4. Start and end are the same with selection
//                // 5. Start and end are inside the selection
//                // 6. Start == end TODO: Check iOS behavior and move to the first if necessary
//            }
//            val currentTextLayoutResult = textLayoutResult ?: return emptyList()
//            val density = rootView.density // TODO: extract it to the private method
//            // Either LTR or RTL, it should be a higher selection handle
//            val topLeadingDpRect = currentTextLayoutResult.getBoundingBox(range.first).toDpRect(rootView.density)
//            // Like previously, it should be a lower selection handle, despite the layout direction
//            val bottomTrailingDpRect = currentTextLayoutResult.getBoundingBox(range.last).toDpRect(rootView.density)
            println("Range = ${range.start}, ${range.endInclusive}")
            println("Selection = ${currentSelection?.start}, ${currentSelection?.end}")
            val dpRect = caretDpRectForPosition(range.start.toLong()) ?: return emptyList()
            println("DPRECT = $dpRect")
            return listOf(dpRect)
        }

        override fun closestPositionToPoint(point: DpOffset): Long? {
            return textLayoutResult?.getOffsetForPosition(point.toOffset(rootView.density))
                ?.toLong()
        }

        override fun closestPositionToPoint(point: DpOffset, withinRange: IntRange): Long? {
            val pointOffset =
                textLayoutResult?.getOffsetForPosition(point.toOffset(rootView.density))
                    ?: return null
            if (pointOffset !in withinRange) {
                return null
            }
            return pointOffset.toLong()
        }

        override fun characterRangeAtPoint(point: DpOffset): IntRange? {
            val pointOffset =
                textLayoutResult?.getOffsetForPosition(point.toOffset(rootView.density))
                    ?: return null
            return textLayoutResult?.getWordBoundary(pointOffset)?.toIntRange()
        }

        override fun positionWithinRange(range: IntRange, atCharacterOffset: Long): Long? {
            TODO("Not yet implemented")
        }

        override fun positionWithinRange(range: IntRange, farthestIndirection: String): Long? {
            TODO("Not yet implemented")
        }

        override fun characterRangeByExtendingPosition(
            position: Long,
            direction: String
        ): IntRange? {
            TODO("Not yet implemented")
        }

        override fun baseWritingDirectionForPosition(position: Long, inDirection: String): String? {
            TODO("Not yet implemented")
        }

        override fun offset(fromPosition: Long, toPosition: Long): Long {
            TODO("Not yet implemented")
        }
    }

    private fun TextRange.toIntRange(): IntRange = IntRange(this.start, this.end) // TODO: check RTL
}

private data class CurrentInput(
    var value: TextFieldValue,
    val onEditCommand: (List<EditCommand>) -> Unit,
    var focusedRect: Rect? = null
)
