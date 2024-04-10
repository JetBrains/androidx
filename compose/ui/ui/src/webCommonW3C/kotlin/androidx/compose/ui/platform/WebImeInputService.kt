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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.DeleteAllCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PlatformTextInputService
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTextAreaElement

internal class WebImeInputService(parentInputService: InputAwareInputService) : PlatformTextInputService, InputAwareInputService by parentInputService{
    private var currentDomInput: HTMLTextAreaElement? = null

    override fun startInput(
        value: TextFieldValue,
        imeOptions: ImeOptions,
        onEditCommand: (List<EditCommand>) -> Unit,
        onImeActionPerformed: (ImeAction) -> Unit
    ) {
        val input = createHtmlInput(imeOptions, onEditCommand)
        document.body?.appendChild(input)
        currentDomInput = input

        showSoftwareKeyboard()
    }

    override fun stopInput() {
        currentDomInput?.remove()
    }

    override fun showSoftwareKeyboard() {
        currentDomInput?.focus()
    }

    override fun hideSoftwareKeyboard() {
        currentDomInput?.blur()
    }

    override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) {
        currentDomInput ?: return
        currentDomInput?.value = newValue.text
        currentDomInput?.setSelectionRange(newValue.selection.start, newValue.selection.end)
    }

    override fun notifyFocusedRect(rect: Rect) {
        super.notifyFocusedRect(rect)
        updateHtmlInputPosition(getOffset(rect))
    }

    private fun updateHtmlInputPosition(offset: Offset) {
        currentDomInput?.style?.left = "${offset.x}px"
        currentDomInput?.style?.top = "${offset.y}px"

        currentDomInput?.focus()
    }

    private fun createHtmlInput(imeOptions: ImeOptions, onEditCommand: (List<EditCommand>) -> Unit): HTMLTextAreaElement {
        val htmlInput = document.createElement("textarea") as HTMLTextAreaElement

        htmlInput.setAttribute("autocorrect", "off")
        htmlInput.setAttribute("autocomplete", "off")
        htmlInput.setAttribute("autocapitalize", "off")
        htmlInput.setAttribute("spellcheck", "false")

        val inputMode = when (imeOptions.keyboardType) {
            KeyboardType.Text -> "text"
            KeyboardType.Ascii -> "text"
            KeyboardType.Number -> "number"
            KeyboardType.Phone -> "tel"
            KeyboardType.Uri -> "url"
            KeyboardType.Email -> "email"
            KeyboardType.Password -> "password"
            KeyboardType.NumberPassword -> "number"
            KeyboardType.Decimal -> "decimal"
            else -> "text"
        }

        val enterKeyHint = when (imeOptions.imeAction) {
            ImeAction.Default -> "enter"
            ImeAction.None -> "enter"
            ImeAction.Done -> "done"
            ImeAction.Go -> "go"
            ImeAction.Next -> "next"
            ImeAction.Previous -> "previous"
            ImeAction.Search -> "search"
            ImeAction.Send -> "send"
            else -> "enter"
        }

        htmlInput.setAttribute("inputmode", inputMode)
        htmlInput.setAttribute("enterkeyhint", enterKeyHint)

        htmlInput.style.apply {
            setProperty("position", "absolute")
            setProperty("user-select", "none")
            setProperty("forced-color-adjust", "none")
            setProperty("white-space", "pre-wrap")
            setProperty("align-content", "center")
            setProperty("top", "0")
            setProperty("left", "0")
            setProperty("padding", "0")
            setProperty("opacity", "0")
            setProperty("color", "transparent")
            setProperty("background", "transparent")
            setProperty("caret-color", "transparent")
            setProperty("outline", "none")
            setProperty("border", "none")
            setProperty("resize", "none")
            setProperty("text-shadow", "none")
        }

        htmlInput.addEventListener("input", {
                val text = htmlInput.value
                val cursorPosition = htmlInput.selectionEnd
                sendImeValueToCompose(onEditCommand, text, cursorPosition)
        })

        return htmlInput
    }

    private fun sendImeValueToCompose(
        onEditCommand: (List<EditCommand>) -> Unit,
        text: String,
        newCursorPosition: Int? = null
    ) {
        val value = if (text == "\n") {
            ""
        } else {
            text
        }

        if (newCursorPosition != null) {
            onEditCommand(
                listOf(
                    DeleteAllCommand(),
                    CommitTextCommand(value, newCursorPosition),
                )
            )
        } else {
            onEditCommand(
                listOf(
                    CommitTextCommand(value, 1)
                )
            )
        }
    }
}