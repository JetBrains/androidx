/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.ui.benchmark.focus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.testutils.LayeredComposeTestCase
import androidx.compose.testutils.ToggleableTestCase
import androidx.compose.testutils.benchmark.ComposeBenchmarkRule
import androidx.compose.testutils.benchmark.toggleStateBenchmarkRecompose
import androidx.compose.ui.Modifier
import androidx.compose.ui.benchmark.repeatModifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.unit.dp
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@LargeTest
@RunWith(Parameterized::class)
class ParameterizedFocusBenchmark(val count: Int) {

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun parameters() = listOf(1, 10, 100)
    }

    @get:Rule val composeBenchmarkRule = ComposeBenchmarkRule()

    @Test
    fun modifyActiveHierarchy_addRemoveSubtree() {
        composeBenchmarkRule.toggleStateBenchmarkRecompose({
            object : LayeredComposeTestCase(), ToggleableTestCase {
                private val focusRequester = FocusRequester()
                private var shouldAddNodes by mutableStateOf(false)

                @Composable
                override fun MeasuredContent() {
                    if (shouldAddNodes) {
                        Box(focusTargetModifiers()) {
                            repeat(count) {
                                Box(Modifier.focusTarget()) {
                                    repeat(count) { Box(Modifier.focusTarget()) }
                                }
                            }
                        }
                    }
                }

                @Composable
                override fun ContentWrappers(content: @Composable () -> Unit) {
                    Box(focusTargetModifiers()) {
                        Column {
                            content()
                            Box(Modifier.focusRequester(focusRequester).focusTarget())
                            LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        }
                    }
                }

                override fun toggleState() {
                    shouldAddNodes = !shouldAddNodes
                }
            }
        })
    }

    @Test
    fun modifyActiveHierarchy_addRemoveModifiersWithExistingSubtree() {
        composeBenchmarkRule.toggleStateBenchmarkRecompose({
            object : LayeredComposeTestCase(), ToggleableTestCase {
                private val focusRequester = FocusRequester()
                private var shouldAddNodes by mutableStateOf(false)

                @Composable
                override fun MeasuredContent() {
                    Box(Modifier.thenIf(shouldAddNodes) { focusTargetModifiers() }) {
                        repeat(count) {
                            Box(Modifier.thenIf(shouldAddNodes) { Modifier.focusTarget() }) {
                                repeat(count) {
                                    Box(Modifier.thenIf(shouldAddNodes) { Modifier.focusTarget() })
                                }
                            }
                        }
                    }
                }

                @Composable
                override fun ContentWrappers(content: @Composable () -> Unit) {
                    Box(focusTargetModifiers()) {
                        Column {
                            content()
                            Box(Modifier.focusRequester(focusRequester).focusTarget())
                            LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        }
                    }
                }

                override fun toggleState() {
                    shouldAddNodes = !shouldAddNodes
                }
            }
        })
    }

    @Test
    fun reuseInactiveFocusTarget() {
        composeBenchmarkRule.toggleStateBenchmarkRecompose({
            object : LayeredComposeTestCase(), ToggleableTestCase {

                private var reuseKey by mutableStateOf(0)

                @Composable
                override fun MeasuredContent() {
                    ReusableContent(reuseKey) { Box(focusTargetModifiers()) }
                }

                override fun toggleState() {
                    reuseKey++
                }
            }
        })
    }

    @Test
    fun reuseInactiveFocusTarget_insideActiveParent() {
        composeBenchmarkRule.toggleStateBenchmarkRecompose({
            object : LayeredComposeTestCase(), ToggleableTestCase {

                private val focusRequester = FocusRequester()
                private var reuseKey by mutableStateOf(0)

                @Composable
                override fun MeasuredContent() {
                    ReusableContent(reuseKey) { Box(focusTargetModifiers()) }
                }

                @Composable
                override fun ContentWrappers(content: @Composable () -> Unit) {
                    Box(Modifier.focusRequester(focusRequester).focusTarget()) {
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        content()
                    }
                }

                override fun toggleState() {
                    reuseKey++
                }
            }
        })
    }

    @Test
    fun moveInactiveFocusTarget() {
        composeBenchmarkRule.toggleStateBenchmarkRecompose({
            object : LayeredComposeTestCase(), ToggleableTestCase {

                private var moveContent by mutableStateOf(false)
                private val content = movableContentOf { Box(focusTargetModifiers()) }

                @Composable
                override fun MeasuredContent() {
                    if (moveContent) {
                        Box(Modifier.size(5.dp)) { content() }
                    } else {
                        Box(Modifier.size(10.dp)) { content() }
                    }
                }

                override fun toggleState() {
                    moveContent = !moveContent
                }
            }
        })
    }

    @Test
    fun moveInactiveFocusTarget_insideActiveParent() {
        composeBenchmarkRule.toggleStateBenchmarkRecompose({
            object : LayeredComposeTestCase(), ToggleableTestCase {

                private val focusRequester = FocusRequester()
                private var moveContent by mutableStateOf(false)
                private val movableContent = movableContentOf { Box(focusTargetModifiers()) }

                @Composable
                override fun MeasuredContent() {
                    if (moveContent) {
                        Box(Modifier.size(5.dp)) { movableContent() }
                    } else {
                        Box(Modifier.size(10.dp)) { movableContent() }
                    }
                }

                @Composable
                override fun ContentWrappers(content: @Composable () -> Unit) {
                    Box(Modifier.focusRequester(focusRequester).focusTarget()) {
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        content()
                    }
                }

                override fun toggleState() {
                    moveContent = !moveContent
                }
            }
        })
    }

    private fun focusTargetModifiers() = repeatModifier(count, Modifier::focusTarget)

    private inline fun Modifier.thenIf(condition: Boolean, block: () -> Modifier): Modifier {
        return if (condition) then(block()) else this
    }
}
