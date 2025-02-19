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

import java.io.File
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction

abstract class UpdateEmojiValuesTask : DefaultTask() {

    /**
     * The URL of the repository to check out.
     */
    @get:Input
    abstract val emojiSequencesUrl: Property<String>

    /**
     * The directory where the Kotlin source files are to be written.
     */
    @get:InputDirectory
    abstract val targetDirectory: DirectoryProperty

    /**
     * The name of the package of the Kotlin source files to be written.
     */
    @get:Input
    abstract val targetPackageName: Property<String>

    @TaskAction
    fun updateEmojiValues() {
        val urlContents = URL(emojiSequencesUrl.get()).readText()

        val basicValues = mutableListOf<Int>()
        val basicRanges = mutableListOf<IntRange>()
        val valuePairs = mutableListOf<Pair<Int, Int>>()
        val valueSequences = mutableListOf<List<Int>>()
        for (line in urlContents.lineSequence()) {
            val spec = line.substringBefore(";", missingDelimiterValue = "").trim()
            when {
                spec.isEmpty() -> continue
                ".." in spec -> {
                    val (start, end) = spec.split("..").map { it.toInt(16) }
                    basicRanges.add(start..end)
                }
                else -> {
                    val values = try {
                        spec.split(" ").map { it.toInt(16) }
                    } catch (e: NumberFormatException) {
                        continue
                    }
                    when (values.size) {
                        1 -> basicValues.add(values[0])
                        2 -> {
                            val high = values[0]
                            if ((high !in basicValues) && basicRanges.none { high in it })
                                valuePairs.add(Pair(values[0], values[1]))
                        }
                        else -> valueSequences.add(values)
                    }
                }
            }
        }

        val indent = "    "
        File(targetDirectory.get().asFile, "EmojiValues.kt").bufferedWriter(Charsets.UTF_8).use { output ->
            output.appendLine("package ${targetPackageName.get()}")
            output.appendLine()

            val date =  LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            output.appendLine("// Generated from ${emojiSequencesUrl.get()} on $date")

            output.appendLine("internal val EMOJI_RANGES = arrayOf(")
            for (r in basicRanges) {
                output.append(indent)
                output.appendLine("EmojiRange(0x${r.first.toString(16)}..0x${r.last.toString(16)}),")
            }
            output.appendLine(")")
            output.appendLine()

            output.appendLine("internal val EMOJI_BASIC_VALUES = hashSetOf(")
            for (v in basicValues) {
                output.append(indent)
                output.appendLine("EmojiBasicValue(0x${v.toString(16)}),")
            }
            output.appendLine(")")
            output.appendLine()

            output.appendLine("// Note that this excludes pairs where the first value is already in EMOJI_RANGES or EMOJI_BASIC_VALUES")
            output.appendLine("internal val EMOJI_VALUE_PAIRS = hashSetOf(")
            for (v in valuePairs) {
                output.append(indent)
                output.appendLine("EmojiValuePair(0x${v.first.toString(16)}, 0x${v.second.toString(16)}),")
            }
            output.appendLine(")")
            output.appendLine()

            output.appendLine("internal val EMOJI_VALUE_SEQUENCES = hashSetOf(")
            for (v in valueSequences) {
                val listAsString = v.joinToString(separator = ",") { "0x${it.toString(16) }" }
                output.append(indent)
                output.appendLine("EmojiValueSequence(listOf($listAsString)),")
            }
            output.appendLine(")")
            output.appendLine()
        }
    }

}