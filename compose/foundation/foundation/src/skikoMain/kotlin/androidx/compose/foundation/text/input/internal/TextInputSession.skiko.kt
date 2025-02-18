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

package androidx.compose.foundation.text.input.internal

import androidx.compose.foundation.content.internal.ReceiveContentConfiguration
import androidx.compose.foundation.text.computeSizeForDefaultText
import androidx.compose.foundation.text.focusedRectInRoot
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldCharSequence
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.setSelectionCoerced
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.EditProcessor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.TextEditingScope
import androidx.compose.ui.text.input.TextEditorState
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.absoluteValue
import kotlin.math.sign
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterNotNull

internal actual suspend fun PlatformTextInputSession.platformSpecificTextInputSession(
    state: TransformedTextFieldState,
    layoutState: TextLayoutState,
    imeOptions: ImeOptions,
    receiveContentConfiguration: ReceiveContentConfiguration?,
    onImeAction: ((ImeAction) -> Unit)?,
    updateSelectionState: (() -> Unit)?,
    stylusHandwritingTrigger: MutableSharedFlow<Unit>?,
    viewConfiguration: ViewConfiguration?
): Nothing {
    val editProcessor = EditProcessor()
    fun onEditCommand(commands: List<EditCommand>) {
        editProcessor.reset(
            value = state.untransformedText.toTextFieldValue(),
            textInputSession = null
        )

        val newValue = editProcessor.apply(commands)

        state.replaceAll(newValue.text)
        state.editUntransformedTextAsUser {
            val untransformedSelection = state.mapFromTransformed(newValue.selection)
            setSelectionCoerced(untransformedSelection.start, untransformedSelection.end)

            val composition = newValue.composition
            if (composition == null) {
                commitComposition()
            } else {
                val untransformedComposition = state.mapFromTransformed(composition)
                setComposition(untransformedComposition.start, untransformedComposition.end)
            }
        }
    }

    fun editText(block: TextEditingScope.() -> Unit) {
        state.editUntransformedTextAsUser {
            with(TextEditingScope(this)) {
                block()
            }
        }
    }

    coroutineScope {
        val outputValueFlow = callbackFlow {
            state.collectImeNotifications { oldValue, newValue, restartIme ->
                println("IME Notification: oldValue=$oldValue, newValue=$newValue, restartIme=$restartIme")
                trySend(newValue.toTextFieldValue())
            }
        }

        val focusedRectInRootFlow = snapshotFlow {
            val layoutResult = layoutState.layoutResult ?: return@snapshotFlow null
            val layoutCoords = layoutState.textLayoutNodeCoordinates ?: return@snapshotFlow null
            focusedRectInRoot(
                layoutResult = layoutResult,
                layoutCoordinates = layoutCoords,
                focusOffset = state.visualText.selection.max,
                sizeForDefaultText = {
                    layoutResult.layoutInput.let {
                        computeSizeForDefaultText(it.style, it.density, it.fontFamilyResolver)
                    }
                }
            )
        }.filterNotNull()

        startInputMethod(
            SkikoPlatformTextInputMethodRequest(
                value = { state.untransformedText.toTextFieldValue() },
                state = state.untransformedText.asTextEditorState(),
                imeOptions = imeOptions,
                onEditCommand = ::onEditCommand,
                onImeAction = onImeAction,
                editProcessor = editProcessor,
                outputValue = outputValueFlow,
                textLayoutResult = snapshotFlow(layoutState::layoutResult).filterNotNull(),
                focusedRectInRoot = focusedRectInRootFlow,
                editText = ::editText
            )
        )
    }
}

private fun TextFieldCharSequence.toTextFieldValue() =
    TextFieldValue(toString(), selection, composition)

private fun TextFieldCharSequence.asTextEditorState() = object : TextEditorState {

    override val length: Int
        get() = this@asTextEditorState.length

    override fun get(index: Int): Char = this@asTextEditorState[index]

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        return this@asTextEditorState.subSequence(startIndex, endIndex)
    }

    override val selection: TextRange
        get() = this@asTextEditorState.selection

    override val composition: TextRange?
        get() = this@asTextEditorState.composition

}

/**
 * Helper function that returns true when [high] is a Unicode high-surrogate code unit and [low] is
 * a Unicode low-surrogate code unit.
 */
private fun isSurrogatePair(high: Char, low: Char): Boolean =
    high.isHighSurrogate() && low.isLowSurrogate()


/**
 * Returns the index, in characters, of the code point at distance [offset] from [index].
 *
 * If there aren't enough codepoints in the correct direction, returns 0 (if [offset] is negative)
 * or the length of the char sequence (if [offset] is positive).
 */
private fun CharSequence.indexOfCodePointAtOffset(index: Int, offset: Int): Int {
    val sign = offset.sign
    val distance = offset.absoluteValue

    var currentOffset = index
    for (i in 0 until distance) {
        currentOffset += sign
        if (currentOffset <= 0) {
            return 0
        } else if (currentOffset >= length) {
            return length
        }


        val lead = this[currentOffset - 1]
        val trail = this[currentOffset]

        if (isSurrogatePair(lead, trail)) {
            currentOffset += sign
        }
    }

    return currentOffset
}


private var TextFieldBuffer.cursor: Int
    get() = if (selection.collapsed) selection.end else -1
    set(value) {
        setSelectionCoerced(value, value)
    }

private fun TextEditingScope(buffer: TextFieldBuffer) = object : TextEditingScope {

    override fun deleteSurroundingTextInCodePoints(
        lengthBeforeCursor: Int,
        lengthAfterCursor: Int
    ) {
        val charSequence = buffer.asCharSequence()
        val selection = buffer.selection
        buffer.delete(
            start = selection.end,
            end = charSequence.indexOfCodePointAtOffset(
                index = selection.end,
                offset = lengthAfterCursor
            )
        )
        buffer.delete(
            start = charSequence.indexOfCodePointAtOffset(
                index = selection.start,
                offset = -lengthBeforeCursor
            ),
            end = selection.start
        )
    }

    override fun commitText(text: CharSequence, newCursorPosition: Int) {
        // API description says replace ongoing composition text if there. Then, if there is no
        // composition text, insert text into cursor position or replace selection.
        val replacementRange = buffer.composition ?: buffer.selection
        buffer.replace(replacementRange.start, replacementRange.end, text)

        // After replace function is called, the editing buffer places the cursor at the end of the
        // modified range.
        val newCursor = buffer.cursor

        // See API description for the meaning of newCursorPosition.
        val newCursorInBuffer =
            if (newCursorPosition > 0) {
                newCursor + newCursorPosition - 1
            } else {
                newCursor + newCursorPosition - text.length
            }
        buffer.setSelectionCoerced(newCursorInBuffer, newCursorInBuffer)
    }

    override fun setComposingText(text: CharSequence, newCursorPosition: Int) {
        val replacementRange = buffer.composition ?: buffer.selection
        // API doc says, if there is ongoing composing text, replace it with new text.
        // If there is no composing text, insert composing text into cursor position with
        // removing selected text if any.
        buffer.replace(replacementRange.start, replacementRange.end, text)
        if (text.isNotEmpty()) {
            buffer.setComposition(replacementRange.start, replacementRange.start + text.length)
        }

        // After replace function is called, the editing buffer places the cursor at the end of the
        // modified range.
        val newCursor = buffer.cursor

        // See API description for the meaning of newCursorPosition.
        val newCursorInBuffer =
            if (newCursorPosition > 0) {
                newCursor + newCursorPosition - 1
            } else {
                newCursor + newCursorPosition - text.length
            }

        buffer.cursor = newCursorInBuffer
    }
}


@OptIn(ExperimentalComposeUiApi::class)
private data class SkikoPlatformTextInputMethodRequest(
    override val value: () -> TextFieldValue,
    override val state: TextEditorState,
    override val imeOptions: ImeOptions,
    override val onEditCommand: (List<EditCommand>) -> Unit,
    override val onImeAction: ((ImeAction) -> Unit)?,
    override val editProcessor: EditProcessor?,
    override val outputValue: Flow<TextFieldValue>,
    override val textLayoutResult: Flow<TextLayoutResult>,
    override val focusedRectInRoot: Flow<Rect>,
    override val editText: (block: TextEditingScope.() -> Unit) -> Unit
): PlatformTextInputMethodRequest
