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

package androidx.compose.ui.text.input

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextRange

interface TextEditorState : CharSequence {

    val selection: TextRange
    val composition: TextRange?

}

interface TextEditingScope {
    /**
     * Deletes text around the cursor.
     *
     * This intends to replicate [DeleteSurroundingTextInCodePointsCommand] for
     * [PlatformTextInputService2].
     */
    fun deleteSurroundingTextInCodePoints(lengthBeforeCursor: Int, lengthAfterCursor: Int)

    /**
     * Commits text and repositions the cursor.
     *
     * This intends to replicate [CommitTextCommand] for [PlatformTextInputService2].
     */
    fun commitText(text: CharSequence, newCursorPosition: Int)

    /**
     * Sets the composing text and repositions the cursor.
     *
     * This intends to replicate [SetComposingTextCommand] for [PlatformTextInputService2].
     */
    fun setComposingText(text: CharSequence, newCursorPosition: Int)
}

interface PlatformTextInputService2 {

    fun startInput(
        state: TextEditorState,
        imeOptions: ImeOptions,
        editText: (block: TextEditingScope.() -> Unit) -> Unit,
    )

    fun stopInput()

    fun focusedRectChanged(rect: Rect)

}