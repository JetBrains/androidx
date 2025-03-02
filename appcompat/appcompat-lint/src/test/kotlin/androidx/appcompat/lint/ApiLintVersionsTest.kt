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

@file:Suppress("UnstableApiUsage")

package androidx.appcompat.lint

import androidx.appcompat.AppCompatIssueRegistry
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.CURRENT_API
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ApiLintVersionsTest {

    @Test
    fun versionsCheck() {
        LintClient.clientName = LintClient.CLIENT_UNIT_TESTS

        val registry = AppCompatIssueRegistry()
        // We hardcode version registry.api to the version that is used to run tests.
        assertEquals("registry.api matches version used to run tests", CURRENT_API, registry.api)
        // Intentionally fails in IDE, because we use different API version in Studio and CLI.
        assertEquals("registry.minApi is set to minimum level of 14", 14, registry.minApi)
    }
}
