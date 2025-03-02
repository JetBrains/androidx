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

package androidx.testutils

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.Navigator

/**
 * A simple Navigator that doesn't actually navigate anywhere, but does dispatch correctly
 */
public expect open class TestNavigator(hasTransitions: Boolean = false) :
    Navigator<TestNavigator.Destination> {

    public val backStack: List<NavBackStackEntry>

    public val current: NavBackStackEntry

    public fun popCurrent()

    public fun onTransitionComplete(entry: NavBackStackEntry)

    override fun createDestination(): Destination

    /**
     * A simple Test destination
     */
    public open class Destination(
        navigator: Navigator<out NavDestination>
    ) : NavDestination
}

internal const val TEST_NAVIGATOR_NAME = "test"
