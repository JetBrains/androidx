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

package androidx.wear.compose.integration.demos

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.samples.PreRotaryEventSample
import androidx.compose.ui.samples.RotaryEventSample
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Text

@Composable
fun ScrollUsingRotatingCrownDemo() {
    Column {
        CenteredText("Use")
        CenteredText("the rotating")
        CenteredText("side button to scroll")
        RotaryEventSample()
    }
}

@Composable
fun InterceptScrollDemo() {
    Column {
        Spacer(modifier = Modifier.height(20.dp))
        CenteredText("Scroll Target: ")
        PreRotaryEventSample()
    }
}

@Composable
private fun ColumnScope.CenteredText(text: String) {
    Text(text, Modifier.align(Alignment.CenterHorizontally))
}
