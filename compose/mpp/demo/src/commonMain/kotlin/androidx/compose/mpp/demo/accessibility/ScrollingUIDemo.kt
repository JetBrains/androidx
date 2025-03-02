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

package androidx.compose.mpp.demo.accessibility

import androidx.compose.material.Text
import androidx.compose.runtime.Composable

// TODO https://youtrack.jetbrains.com/issue/CMP-7679/Revert-material3-to-1.4.-version-after-material3-isnt-needed-in-jb-main
@Composable
fun SampleScrollingTooltipScreen() {
    Text("Disabled until Material3 is reverted back to 1.4 version")
}

/*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleScrollingTooltipScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sample Screen") },
                navigationIcon = {
                    TooltipBox(
                        positionProvider = rememberTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(text = "Navigation icon") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Navigation icon"
                            )
                        }
                    }
                },
                actions = {
                    TooltipBox(
                        positionProvider = rememberTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(text = "Search") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search icon"
                            )
                        }
                    }
                    TooltipBox(
                        positionProvider = rememberTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(text = "Settings") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings icon"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(40) { index ->
                Text(text = "Item ${index + 1}", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

*/