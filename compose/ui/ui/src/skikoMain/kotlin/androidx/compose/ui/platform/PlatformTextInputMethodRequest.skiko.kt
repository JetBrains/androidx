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

package androidx.compose.ui.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.EditProcessor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.TextEditingScope
import androidx.compose.ui.text.input.TextEditorState
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.flow.Flow

actual interface PlatformTextInputMethodRequest {
    /** The editor state. */
    @ExperimentalComposeUiApi
    val value: () -> TextFieldValue

    /** The editor state. */
    @ExperimentalComposeUiApi
    val state: TextEditorState

    /** Keyboard configuration such as single line, autocorrect etc. */
    @ExperimentalComposeUiApi
    val imeOptions: ImeOptions

    /** Can be called to perform edit commands on the text field state. */
    @ExperimentalComposeUiApi
    val onEditCommand: (List<EditCommand>) -> Unit

    /** The callback called when the editor action arrives. */
    @ExperimentalComposeUiApi
    val onImeAction: ((ImeAction) -> Unit)?

    /** The edit processor. */
    @ExperimentalComposeUiApi
    val editProcessor: EditProcessor?

    @ExperimentalComposeUiApi
    val outputValue: Flow<TextFieldValue>

    /**
     * A flow with the layout of text in the editor's.
     *
     * When the layout changes, new values will be emitted by the flow.
     */
    @ExperimentalComposeUiApi
    val textLayoutResult: Flow<TextLayoutResult>

    /**
     * A flow with the rectangle (relative to root) of the area where the actual editing occurs.
     *
     * As the area changes, new values will be emitted by the flow.
     */
    @ExperimentalComposeUiApi
    val focusedRectInRoot: Flow<Rect>

    @ExperimentalComposeUiApi
    val editText: (block: TextEditingScope.() -> Unit) -> Unit
}
