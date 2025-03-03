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

package androidx.compose.ui.platform

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.DeleteSurroundingTextInCodePointsCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.PlatformTextInputService2
import androidx.compose.ui.text.input.SetComposingTextCommand
import androidx.compose.ui.text.input.TextEditingScope
import androidx.compose.ui.text.input.TextEditorState
import androidx.compose.ui.text.substring
import java.awt.Rectangle
import java.awt.event.InputMethodEvent
import java.awt.event.KeyEvent
import java.awt.font.TextHitInfo
import java.awt.im.InputMethodRequests
import java.text.AttributedCharacterIterator
import java.text.AttributedString
import java.text.CharacterIterator
import kotlin.math.max
import kotlin.math.min
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs

internal class DesktopTextInputService2(
    private val component: PlatformComponent
) : PlatformTextInputService2 {

    private var currentInputMethodRequests: InputMethodRequestsImpl? = null

    override fun startInput(
        state: TextEditorState,
        imeOptions: ImeOptions,
        editText: (block: TextEditingScope.() -> Unit) -> Unit
    ) {
        component.enableInput(
            InputMethodRequestsImpl(component, state, editText).also {
                currentInputMethodRequests = it
            }
        )
    }

    override fun stopInput() {
        component.disableInput()

        this.currentInputMethodRequests = null
    }

    override fun focusedRectChanged(rect: Rect) {
        currentInputMethodRequests?.focusedRect = rect
    }

    fun onKeyEvent(keyEvent: KeyEvent) {
        when (keyEvent.id) {
            KeyEvent.KEY_TYPED ->
                currentInputMethodRequests?.charKeyPressed = true
            KeyEvent.KEY_RELEASED ->
                currentInputMethodRequests?.charKeyPressed = false
        }
    }

    fun inputMethodTextChanged(event: InputMethodEvent) {
        val inputMethodRequests = currentInputMethodRequests ?: return
        if (!event.isConsumed) {
            inputMethodRequests.replaceInputMethodText(event)
            event.consume()
        }
    }

}

private class InputMethodRequestsImpl(
    private val component: PlatformComponent,
    private val state: TextEditorState,
    private val editText: (block: TextEditingScope.() -> Unit) -> Unit
) : InputMethodRequests {

    private val selection: TextRange
        get() = state.selection

    private val composition: TextRange?
        get() = state.composition

    var focusedRect: Rect? = null

    // This is required to support input of accented characters using press-and-hold method (http://support.apple.com/kb/PH11264).
    // JDK currently properly supports this functionality only for TextComponent/JTextComponent descendants.
    // For our editor component we need this workaround.
    // After https://bugs.openjdk.java.net/browse/JDK-8074882 is fixed, this workaround should be replaced with a proper solution.
    var charKeyPressed: Boolean = false
    var needToDeletePreviousChar: Boolean = false

    override fun getLocationOffset(x: Int, y: Int): TextHitInfo? {
        if (composition != null) {
            // TODO: to properly implement this method we need to somehow have access to
            //  Paragraph at this point
            return TextHitInfo.leading(0)
        }
        return null
    }

    override fun cancelLatestCommittedText(
        attributes: Array<AttributedCharacterIterator.Attribute>?
    ): AttributedCharacterIterator? {
        return null
    }

    override fun getInsertPositionOffset(): Int {
        val composedStartIndex = composition?.start ?: 0
        val composedEndIndex = composition?.end ?: 0

        val caretIndex = selection.start
        if (caretIndex < composedStartIndex) {
            return caretIndex
        }
        if (caretIndex < composedEndIndex) {
            return composedStartIndex
        }
        return caretIndex - (composedEndIndex - composedStartIndex)
    }

    override fun getCommittedTextLength() =
        state.length - (composition?.length ?: 0)

    override fun getSelectedText(
        attributes: Array<AttributedCharacterIterator.Attribute>?
    ): AttributedCharacterIterator {
        if (charKeyPressed && (hostOs == OS.MacOS)) {
            needToDeletePreviousChar = true
        }
        val str = state.substring(selection)
        return AttributedString(str).iterator
    }

    override fun getTextLocation(offset: TextHitInfo?): Rectangle? {
        return focusedRect?.let {
            val x = (it.right / component.density.density).toInt() +
                component.locationOnScreen.x
            val y = (it.top / component.density.density).toInt() +
                component.locationOnScreen.y
            Rectangle(x, y, it.width.toInt(), it.height.toInt())
        }
    }

    override fun getCommittedText(
        beginIndex: Int,
        endIndex: Int,
        attributes: Array<AttributedCharacterIterator.Attribute>?
    ): AttributedCharacterIterator {
        val comp = composition
        // When input is performed with Pinyin and backspace pressed,
        // comp is null and beginIndex > endIndex.
        // TODO Check is this an expected behavior?
        val range = TextRange(
            start = beginIndex.coerceAtMost(state.length),
            end = endIndex.coerceAtMost(state.length)
        )
        if (comp == null) {
            val res = state.substring(range)
            return AttributedString(res).iterator
        }
        val committed = state.substring(
            TextRange(
                min(range.min, comp.min).coerceAtMost(state.length),
                max(range.max, comp.max).coerceAtMost(state.length)
            )
        )
        return AttributedString(committed).iterator
    }

    fun replaceInputMethodText(event: InputMethodEvent) {
        val committed = event.text?.toStringUntil(event.committedCharacterCount) ?: ""
        val composing = event.text?.toStringFrom(event.committedCharacterCount) ?: ""

        editText {
            if (needToDeletePreviousChar && selection.min > 0 && composing.isEmpty()) {
                needToDeletePreviousChar = false
                deleteSurroundingTextInCodePoints(1, 0)
            }
            commitText(committed, 1)
            if (composing.isNotEmpty()) {
                setComposingText(composing, 1)
            }
        }
    }

}


private fun AttributedCharacterIterator.toStringUntil(index: Int) = StringBuilder().apply {
    var i = index
    if (i > 0) {
        var c: Char = setIndex(0)
        while (i > 0) {
            append(c)
            c = next()
            i--
        }
    }
}

private fun AttributedCharacterIterator.toStringFrom(index: Int) = StringBuilder().apply {
    var c: Char = setIndex(index)
    while (c != CharacterIterator.DONE) {
        append(c)
        c = next()
    }
}
