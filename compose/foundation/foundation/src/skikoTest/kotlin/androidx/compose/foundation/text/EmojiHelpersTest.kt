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

import kotlin.test.Test
import kotlin.test.assertEquals

class EmojiHelpersTest {
    @Test
    fun testEmojiSequenceStartDetection() {
        val parts = listOf(
            "⌚" to true,  // 1 char, range
            "✅" to true,  // 1 char, value
            "😉" to true,  // 2 chars, range
            "🥺" to true,  // 2 chars, value
            "♥️" to true,  // 2 chars, value-pair
            "x" to false,  // non-emoji
            "\uD83C\uDDEE\uD83C\uDDF1" to true,  // 4-chars, 2-char value-pair (flag)
            "\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F" to true,  // 14 chars, sequence
            "✌\uD83C\uDFFD" to true, // 3 chars, value-pair
        )
        val text = parts.joinToString("") { it.first }

        var index = 0
        for ((s, isEmoji) in parts) {
            assertEquals(
                expected = isEmoji,
                actual = text.isEmojiOrEmojiSequenceStartAt(index),
                message = "Codepoint at index $index ($s) isEmoji: $isEmoji, expected: ${!isEmoji}"
            )
            index += s.length
        }
    }
}