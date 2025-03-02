/*
 * Copyright (C) 2017 The Android Open Source Project
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

import androidx.savedstate.read
import androidx.savedstate.savedState
import kotlinx.coroutines.flow.StateFlow

@Navigator.Name("navigation")
public actual open class NavGraphNavigator
actual constructor(private val navigatorProvider: NavigatorProvider) : Navigator<NavGraph>() {

    public actual val backStack: StateFlow<List<NavBackStackEntry>>
        get() = state.backStack

    actual override fun createDestination(): NavGraph {
        return NavGraph(this)
    }

    actual override fun navigate(
        entries: List<NavBackStackEntry>,
        navOptions: NavOptions?,
        navigatorExtras: Extras?
    ) {
        for (entry in entries) {
            navigate(entry, navOptions, navigatorExtras)
        }
    }

    private fun navigate(
        entry: NavBackStackEntry,
        navOptions: NavOptions?,
        navigatorExtras: Extras?
    ) {
        val destination = entry.destination as NavGraph
        // contains restored args or args passed explicitly as startDestinationArgs
        var args = entry.arguments
        val startId = destination.startDestinationId
        val startRoute = destination.startDestinationRoute
        check(startId != 0 || startRoute != null) {
            ("no start destination defined via app:startDestination for ${destination.displayName}")
        }
        val startDestination =
            if (startRoute != null) {
                destination.findNode(startRoute, false)
            } else {
                destination.nodes[startId]
            }
        requireNotNull(startDestination) {
            val dest = destination.startDestDisplayName
            throw IllegalArgumentException(
                "navigation destination $dest is not a direct child of this NavGraph"
            )
        }
        if (startRoute != null) {
            // If startRoute contains only placeholders, we fallback to default arg values.
            // This is to maintain existing behavior of using default value for startDestination
            // while also adding support for args declared in startRoute.
            if (startRoute != startDestination.route) {
                val matchingArgs = startDestination.matchRoute(startRoute)?.matchingArgs
                if (matchingArgs != null && !matchingArgs.read { isEmpty() }) {
                    args = savedState {
                        // we need to add args from startRoute,
                        // but it should not override existing args
                        putAll(matchingArgs)
                        args?.let { putAll(it) }
                    }
                }
            }
            // by this point, the bundle should contain all arguments that don't have
            // default values (regardless of whether the actual default value is known or not).
            if (startDestination.arguments.isNotEmpty()) {
                val missingRequiredArgs =
                    startDestination.arguments.missingRequiredArguments { key ->
                        if (args == null) true else !args.read { contains(key) }
                    }
                require(missingRequiredArgs.isEmpty()) {
                    "Cannot navigate to startDestination $startDestination. " +
                        "Missing required arguments [$missingRequiredArgs]"
                }
            }
        }

        val navigator =
            navigatorProvider.getNavigator<Navigator<NavDestination>>(
                startDestination.navigatorName
            )
        val startDestinationEntry =
            state.createBackStackEntry(
                startDestination,
                // could contain default args, restored args, args passed during setGraph,
                // and args from route
                startDestination.addInDefaultArgs(args)
            )
        navigator.navigate(listOf(startDestinationEntry), navOptions, navigatorExtras)
    }
}
