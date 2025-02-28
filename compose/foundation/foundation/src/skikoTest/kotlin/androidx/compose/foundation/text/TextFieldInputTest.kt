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

package androidx.compose.foundation.text

import androidx.compose.foundation.assertThat
import androidx.compose.foundation.isEqualTo
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performTextReplacement
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TextFieldInputTest {
    @Test
    fun textField_backspace_withDiacritic() = runTextFieldInputTest {
        onTextField {
            setTextAndPlaceCursorAtEnd("e\u0301f") // e + combining acute accent + f
            performKeyInput {
                pressKey(Key.Backspace)
                assertTextEquals("e\u0301")
                pressKey(Key.Backspace) // Should remove the accent, not the base character
                assertTextEquals("e")
                pressKey(Key.Backspace)
                assertTextEquals("")
                pressKey(Key.Backspace) // Shouldn't crash
                assertTextEquals("")
            }
        }
    }

    private fun TextFieldInputTestScope.backspace_withEmoji(emoji: String) {
        onTextField {
            setTextAndPlaceCursorAtEnd(emoji)
            performKeyInput {
                pressKey(Key.Backspace)
                assertTextEquals("")
            }
        }
    }

    @Test
    fun textField_backspace_withEmoji() = runTextFieldInputTest {
        backspace_withEmoji("")
        backspace_withEmoji("✅")
        backspace_withEmoji("😉")
        backspace_withEmoji("🥺")
        backspace_withEmoji("♥️")
        backspace_withEmoji("🇮🇱")  // flag
        backspace_withEmoji("🏴󠁧󠁢󠁥󠁮󠁧󠁿")  // Scotland flag (14 characters)
        backspace_withEmoji("✌\uD83C\uDFFD")  // victory hand: medium skin tone
        backspace_withEmoji("👩‍❤️‍💋‍👩")  // 👩 ❤️ 💋‍ 👩 (joined with zero-width-joiner)
    }
}

@OptIn(ExperimentalTestApi::class)
private abstract class TextFieldInputTestScope(
    val uiTest: ComposeUiTest,
) : SemanticsNodeInteractionsProvider by uiTest {
    protected val textFieldTag = "TextField"

    abstract val text: String

    @Composable
    abstract fun TextField()

    fun assertTextEquals(expected: String) {
        assertThat(text).isEqualTo(expected)
    }

    fun onTextField(block: SemanticsNodeInteraction.() -> Unit) =
        with(onNodeWithTag(textFieldTag)) {
            block()
        }

    fun SemanticsNodeInteraction.setTextAndPlaceCursorAtEnd(text: String) {
        performTextReplacement(text)
        performTextInputSelection(TextRange(text.length))
    }
}

@OptIn(ExperimentalTestApi::class)
private class TextField1InputTestScope(
    uiTest: ComposeUiTest,
): TextFieldInputTestScope(uiTest) {
    private var textFieldValue by mutableStateOf(TextFieldValue())

    override val text: String
        get() = textFieldValue.text

    @Composable
    override fun TextField() {
        val focusRequester = FocusRequester()
        BasicTextField(
            value = textFieldValue,
            onValueChange = { textFieldValue = it },
            modifier = Modifier
                .focusRequester(focusRequester)
                .testTag(textFieldTag)
        )

        LaunchedEffect(focusRequester) {
            focusRequester.requestFocus()
        }
    }

    override fun toString() = "TextField1"
}

@OptIn(ExperimentalTestApi::class)
private class TextField2InputTestScope(
    uiTest: ComposeUiTest,
): TextFieldInputTestScope(uiTest) {
    private var textFieldState by mutableStateOf(TextFieldState())

    override val text: String
        get() = textFieldState.text.toString()

    @Composable
    override fun TextField() {
        val focusRequester = FocusRequester()
        BasicTextField(
            state = textFieldState,
            modifier = Modifier
                .focusRequester(focusRequester)
                .testTag(textFieldTag)
        )

        LaunchedEffect(focusRequester) {
            focusRequester.requestFocus()
        }
    }

    override fun toString() = "TextField2"
}

@OptIn(ExperimentalTestApi::class)
private fun runTextFieldInputTest(
    scopeBuilders: List<(ComposeUiTest) -> TextFieldInputTestScope> = listOf(
        ::TextField1InputTestScope,
        ::TextField2InputTestScope,
    ),
    block: TextFieldInputTestScope.() -> Unit
) {
    for (scopeBuilder in scopeBuilders) {
        runComposeUiTest {
            with(scopeBuilder(this)) {
                setContent {
                    TextField()
                }
                block()
            }
        }
    }
}
