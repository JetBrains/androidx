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

import kotlin.jvm.JvmInline


@JvmInline
internal value class EmojiRange(val range: IntRange)

internal interface EmojiValue {
    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}

internal class EmojiBasicValue(val value: CodePoint) : EmojiValue {
    override fun equals(other: Any?): Boolean {
        if (other !is EmojiBasicValue) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()
}

internal class EmojiValuePair(val high: CodePoint, val low: CodePoint) : EmojiValue {
    override fun equals(other: Any?): Boolean {
        if (other !is EmojiValuePair) return false
        return high == other.high && low == other.low
    }

    override fun hashCode(): Int {
        return low xor (high ushr 32)
    }
}

internal class EmojiValueSequence(val sequence: List<CodePoint>) : EmojiValue {
    override fun equals(other: Any?): Boolean {
        if (other !is EmojiValueSequence) return false
        return sequence == other.sequence
    }

    override fun hashCode(): Int {
        return sequence.hashCode()
    }
}

/** Returns whether a character is an emoji or the start of an emoji sequence.  */
internal fun String.isEmojiOrEmojiSequenceStartAt(index: Int): Boolean {
    val c0 = codePointAt(index)
    if (EMOJI_RANGES.any { c0 in it.range }) return true
    if (EmojiBasicValue(c0) in EMOJI_BASIC_VALUES) return true
    val nextIndex = offsetByCodePoints(index, offset = 1)
    if (nextIndex > lastIndex) return false
    val c1 = codePointAt(nextIndex)
    if (EmojiValuePair(high = c0, low = c1) in EMOJI_VALUE_PAIRS) return true

    val possibleSequences = EMOJI_VALUE_SEQUENCES_BY_FIRST_CODEPOINT[c0]?.toSet() ?: return false
    val maxCodePoints = codePointsAt(index).take(MAX_SEQUENCE_LOOKAHEAD).toList()
    for (lookAhead in 3..maxCodePoints.size) {
        val codePoints = maxCodePoints.subList(0, lookAhead)
        if (EmojiValueSequence(codePoints) in possibleSequences) return true
    }

    return false
}

private val EMOJI_VALUE_SEQUENCES_BY_FIRST_CODEPOINT =
    EMOJI_VALUE_SEQUENCES.groupBy { it.sequence.first() }

private val MAX_SEQUENCE_LOOKAHEAD = EMOJI_VALUE_SEQUENCES.maxOf { it.sequence.size }