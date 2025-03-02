/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.navigation

import androidx.kruth.assertThat
import androidx.savedstate.read
import androidx.savedstate.savedState
import kotlin.test.Test

class NavArgumentTest {
    @Test
    @Suppress("DEPRECATION")
    fun putDefaultValue() {
        val bundle = savedState()
        val argument =
            NavArgument.Builder().setDefaultValue("abc").setType(NavType.StringType).build()
        argument.putDefaultValue("name", bundle)
        assertThat(bundle.read { getString("name") }).isEqualTo("abc")
    }

    @Test
    fun verify() {
        val bundle = savedState {
                putString("stringArg", "abc")
                putInt("intArg", 123)
                putNull("intArrayArg")
            }

        val stringArgument = NavArgument.Builder().setType(NavType.StringType).build()
        val intArgument = NavArgument.Builder().setType(NavType.IntType).build()
        val intArrArgument =
            NavArgument.Builder().setType(NavType.IntArrayType).setIsNullable(true).build()
        val intArrNonNullArgument =
            NavArgument.Builder().setType(NavType.IntArrayType).setIsNullable(false).build()

        assertThat(stringArgument.verify("stringArg", bundle)).isTrue()
        assertThat(intArgument.verify("intArg", bundle)).isTrue()
        assertThat(intArrArgument.verify("intArrayArg", bundle)).isTrue()
        assertThat(intArrNonNullArgument.verify("intArrayArg", bundle)).isFalse()
    }

    @Test
    fun setUnknownDefaultValuePresent() {
        val argument =
            NavArgument.Builder()
                .setType(NavType.IntType)
                .setIsNullable(false)
                .setUnknownDefaultValuePresent(true)
                .build()

        assertThat(argument.isDefaultValuePresent).isTrue()
        assertThat(argument.isDefaultValueUnknown).isTrue()
    }
}
