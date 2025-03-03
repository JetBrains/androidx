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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.DeleteSurroundingTextInCodePointsCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.SetComposingTextCommand
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.browser.document
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.CompositionEvent
import org.w3c.dom.events.EventTarget
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit

/**
 * The purpose of this entity is to isolate synchronization between a TextFieldValue
 * and the DOM HTMLTextAreaElement we are actually listening events on in order to show
 * the virtual keyboard.
 */
internal class BackingTextArea(
    private val imeOptions: ImeOptions,
    private val onEditCommand: (List<EditCommand>) -> Unit,
    private val onImeActionPerformed: (ImeAction) -> Unit,
    private val processKeyboardEvent: (KeyboardEvent) -> Unit
) {
    private val textArea: HTMLTextAreaElement = createHtmlInput()

    private fun processEvent(evt: KeyboardEvent): Boolean {
        // source element is currently in composition session (after "compositionstart" but before "compositionend")
        if (evt.isComposing) return false
        // Unidentified keys is what we get when we press key on a virtual keyboard
        // TODO: In theory nothing stops us from passing Unidentified keys but this yet to be investigated:
        // First, this way we will pass (and attempt to process) "dummy" KeyboardEvents that were designed not to have physical representation at all
        // Second, we need more tests on keyboard in general before doing this anyways
        if (evt.key == "Unidentified") return false
        processKeyboardEvent(evt)
        return true
    }

    private fun initEvents(htmlInput: EventTarget) {
        var removeSymbolOnCompositionStart = false

        DomInputService(htmlInput, object : DomInputListener {
            override fun onKeyDown(evt: KeyboardEvent) {
                // Dead keys occur whenever we are in pre-composition mode, initialized by typing OS-specific special keyboard shortcut
                removeSymbolOnCompositionStart = processEvent(evt) && evt.key != "Dead"
            }

            override fun onKeyUp(evt: KeyboardEvent) {
                processEvent(evt)
            }

            override fun onCompositionStart(evt: CompositionEvent) {
                // whenever very first time compose happens, corresponding keydown event happens earlier
                // so we have to manually delete last key entered
                if (removeSymbolOnCompositionStart) {
                    onEditCommand(listOf(DeleteSurroundingTextInCodePointsCommand(1, 0)))
                    removeSymbolOnCompositionStart = false
                }
            }

            override fun onCompositionUpdate(evt: CompositionEvent) {
                onEditCommand(listOf(SetComposingTextCommand(evt.data, 1)))
            }

            override fun onCompositionEnd(evt: CompositionEvent) {
                onEditCommand(listOf(CommitTextCommand(evt.data, 1)))
            }

            override fun onInputInsertLineBreak(evt: InputEvent) {
                if (imeOptions.singleLine) {
                    onImeActionPerformed(imeOptions.imeAction)
                }
            }


            /*
            This event is used for processing events typed via virtual keyboard
            on mobile devices
             */
            override fun onInputInsertText(evt: InputEvent) {
                val data = evt.data ?: return
                onEditCommand(listOf(CommitTextCommand(data, 1)))
            }

            override fun onInputDeleteContentBackward(evt: InputEvent) {
                processKeyboardEvent(
                    KeyboardEvent(
                        "keydown",
                        KeyboardEventInit(
                            key = "Backspace",
                            code = "Backspace"
                        ).withKeyCode(Key.Backspace)
                    )
                )
            }
        })

        htmlInput.addEventListener("contextmenu", { evt ->
            evt.preventDefault()
            evt.stopPropagation()
        })
    }

    private fun createHtmlInput(): HTMLTextAreaElement {
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
            setProperty("z-index", "-1")
            // TODO: do we need pointer-events: none
            //setProperty("pointer-events", "none")
        }

        initEvents(htmlInput)

        return htmlInput
    }

    fun register() {
        document.body?.appendChild(textArea)
    }

    fun focus() {
        textArea.focus()
    }

    fun blur() {
        textArea.blur()
    }

    fun updateHtmlInputPosition(offset: Offset) {
        textArea.style.left = "${offset.x}px"
        textArea.style.top = "${offset.y}px"

        focus()
    }

    fun updateState(textFieldValue: TextFieldValue) {
//        textArea.value = textFieldValue.text
//        textArea.setSelectionRange(textFieldValue.selection.start, textFieldValue.selection.end)
    }

    fun dispose() {
        textArea.remove()
    }
}

// TODO: reuse in tests
private external interface KeyboardEventInitExtended : KeyboardEventInit {
    var keyCode: Int?
}

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
private fun KeyboardEventInit.withKeyCode(key: Key) =
    (this as KeyboardEventInitExtended).apply {
        this.keyCode = key.keyCode.toInt()
    }