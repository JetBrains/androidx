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

package androidx.navigation

import androidx.annotation.RestrictTo
import androidx.navigation.serialization.generateHashCode
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.savedState
import androidx.savedstate.write
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer

public actual open class NavDestination actual constructor(
    public actual val navigatorName: String
) {

    public actual constructor(navigator: Navigator<out NavDestination>) : this(navigator.name)

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public actual class DeepLinkMatch(
        public actual val destination: NavDestination,
        public actual val matchingArgs: SavedState?,
        private val isExactDeepLink: Boolean,
    ) : Comparable<DeepLinkMatch> {
        override fun compareTo(other: DeepLinkMatch): Int {
            // Prefer exact deep links
            if (isExactDeepLink && !other.isExactDeepLink) {
                return 1
            } else if (!isExactDeepLink && other.isExactDeepLink) {
                return -1
            }
            if (matchingArgs != null && other.matchingArgs == null) {
                return 1
            } else if (matchingArgs == null && other.matchingArgs != null) {
                return -1
            }
            if (matchingArgs != null) {
                val sizeDifference =
                    matchingArgs.read { size() } - other.matchingArgs!!.read { size() }
                if (sizeDifference > 0) {
                    return 1
                } else if (sizeDifference < 0) {
                    return -1
                }
            }
            return 0
        }

        public actual fun hasMatchingArgs(arguments: SavedState?): Boolean {
            if (arguments == null || matchingArgs == null) return false

            matchingArgs.read { toMap().keys }.forEach { key ->
                // the arguments must at least contain every argument stored in this deep link
                if (!arguments.read { contains(key) }) return false

                val type = destination._arguments[key]?.type
                val matchingArgValue = type?.get(matchingArgs, key)
                val entryArgValue = type?.get(arguments, key)
                if (type?.valueEquals(matchingArgValue, entryArgValue) == false) {
                    return false
                }
            }
            return true
        }
    }

    public actual var parent: NavGraph? = null
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public set

    public actual var label: CharSequence? = null
    private val deepLinks = mutableListOf<NavDeepLink>()

    private var _arguments: MutableMap<String, NavArgument> = mutableMapOf()

    public actual val arguments: Map<String, NavArgument>
        get() = _arguments.toMap()

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public var id: Int = 0

    public actual var route: String? = null
        set(route) {
            if (route == null) {
                id = 0
            } else {
                require(route.isNotBlank()) { "Cannot have an empty route" }
                val internalRoute = createRoute(route)
                id = internalRoute.hashCode()
                addDeepLink(internalRoute)
            }
            deepLinks.remove(deepLinks.firstOrNull { it.uriPattern == createRoute(field) })
            field = route
        }

    public actual open val displayName: String
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) get() = navigatorName

    public actual open fun hasDeepLink(deepLink: NavUri): Boolean {
        return hasDeepLink(NavDeepLinkRequest(deepLink, null, null))
    }

    public actual open fun hasDeepLink(deepLinkRequest: NavDeepLinkRequest): Boolean {
        return matchDeepLink(deepLinkRequest) != null
    }

    public actual fun addDeepLink(uriPattern: String) {
        addDeepLink(NavDeepLink.Builder().setUriPattern(uriPattern).build())
    }

    public actual fun addDeepLink(navDeepLink: NavDeepLink) {
        val missingRequiredArguments =
            _arguments.missingRequiredArguments { key -> key !in navDeepLink.argumentsNames }
        require(missingRequiredArguments.isEmpty()) {
            "Deep link ${navDeepLink.uriPattern} can't be used to open destination $this.\n" +
                "Following required arguments are missing: $missingRequiredArguments"
        }

        deepLinks.add(navDeepLink)
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public actual fun matchRoute(route: String): DeepLinkMatch? {
        val request = NavDeepLinkRequest.Builder.fromUri(NavUriUtils.parse(createRoute(route))).build()
        val matchingDeepLink =
            if (this is NavGraph) {
                matchDeepLinkComprehensive(
                    request,
                    searchChildren = false,
                    searchParent = false,
                    lastVisited = this
                )
            } else {
                matchDeepLink(request)
            }
        return matchingDeepLink
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public actual open fun matchDeepLink(navDeepLinkRequest: NavDeepLinkRequest): DeepLinkMatch? {
        if (deepLinks.isEmpty()) {
            return null
        }
        var bestMatch: DeepLinkMatch? = null
        for (deepLink in deepLinks) {
            val uri = navDeepLinkRequest.uri
            // first filter out invalid matches
            if (!deepLink.matches(navDeepLinkRequest)) continue
            // then look for positive matches
            val matchingArguments =
                // includes matching args for path, query, and fragment
                if (uri != null) {
                    deepLink.getMatchingArguments(uri, _arguments)
                } else null
            if (matchingArguments != null) {
                val newMatch = DeepLinkMatch(
                    destination = this,
                    matchingArgs = matchingArguments,
                    isExactDeepLink = deepLink.isExactDeepLink,
                )
                if (bestMatch == null || newMatch > bestMatch) {
                    bestMatch = newMatch
                }
            }
        }
        return bestMatch
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun buildDeepLinkDestinations(previousDestination: NavDestination? = null): List<NavDestination> {
        val hierarchy = ArrayDeque<NavDestination>()
        var current: NavDestination? = this
        do {
            val parent = current!!.parent
            if (
            // If the current destination is a sibling of the previous, just add it straightaway
                previousDestination?.parent != null &&
                previousDestination.parent!!.findNode(current.id) === current
            ) {
                hierarchy.addFirst(current)
                break
            }
            if (parent == null || parent.startDestinationId != current.id) {
                hierarchy.addFirst(current)
            }
            if (parent == previousDestination) {
                break
            }
            current = parent
        } while (current != null)
        return hierarchy.toList()
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public actual fun hasRoute(route: String, arguments: SavedState?): Boolean {
        // this matches based on routePattern
        if (this.route == route) return true

        // if no match based on routePattern, this means route contains filled in args or query
        // params
        val matchingDeepLink = matchRoute(route)

        // if no matchingDeepLink or mismatching destination, return false directly
        if (this != matchingDeepLink?.destination) return false

        // Any args (partially or completely filled in) must exactly match between
        // the route and entry's route.
        return matchingDeepLink.hasMatchingArgs(arguments)
    }

    public actual fun addArgument(argumentName: String, argument: NavArgument) {
        _arguments[argumentName] = argument
    }

    public actual fun removeArgument(argumentName: String) {
        _arguments.remove(argumentName)
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Suppress("NullableCollection")
    public actual fun addInDefaultArgs(args: SavedState?): SavedState? {
        if (args == null && _arguments.isEmpty()) {
            return null
        }
        val defaultArgs = savedState()
        for ((key, value) in _arguments) {
            value.putDefaultValue(key, defaultArgs)
        }
        if (args != null) {
            defaultArgs.write { putAll(args) }
            // Don't verify unknown default values - these default values are only available
            // during deserialization for safe args.
            for ((key, value) in _arguments) {
                if (!value.isDefaultValueUnknown) {
                    require(value.verify(key, defaultArgs)) {
                        "Wrong argument type for '$key' in argument savedState. ${value.type.name} " +
                            "expected."
                    }
                }
            }
        }
        return defaultArgs
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append(this::class.simpleName)
        if (!route.isNullOrBlank()) {
            sb.append(" route=")
            sb.append(route)
        }
        if (label != null) {
            sb.append(" label=")
            sb.append(label)
        }
        return sb.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is NavDestination) return false

        val equalDeepLinks = deepLinks == other.deepLinks

        val equalArguments =
            _arguments.size == other._arguments.size &&
                _arguments.asSequence().all {
                    other._arguments.containsKey(it.key) && other._arguments[it.key] == it.value
                }

        return id == other.id &&
            route == other.route &&
            equalDeepLinks &&
            equalArguments
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + route.hashCode()
        deepLinks.forEach {
            result = 31 * result + it.uriPattern.hashCode()
            result = 31 * result + it.action.hashCode()
            result = 31 * result + it.mimeType.hashCode()
        }
        _arguments.keys.forEach {
            result = 31 * result + it.hashCode()
            result = 31 * result + _arguments[it].hashCode()
        }
        return result
    }

    public actual companion object {
        @JvmStatic
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun getDisplayName(id: Int): String = "0x${id.toString(16)}"

        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public actual fun createRoute(route: String?): String =
            if (route != null) "multiplatform-app://androidx.navigation/$route" else ""

        @JvmStatic
        public actual val NavDestination.hierarchy: Sequence<NavDestination>
            get() = generateSequence(this) { it.parent }

        @JvmStatic
        public actual inline fun <reified T : Any> NavDestination.hasRoute(): Boolean =
            hasRoute(T::class)

        @OptIn(InternalSerializationApi::class)
        @JvmStatic
        public actual fun <T : Any> NavDestination.hasRoute(route: KClass<T>): Boolean =
            route.serializer().generateHashCode() == id
    }
}
