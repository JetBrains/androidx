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

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.util.AttributeSet
import androidx.annotation.CallSuper
import androidx.annotation.IdRes
import androidx.annotation.RestrictTo
import androidx.collection.SparseArrayCompat
import androidx.collection.keyIterator
import androidx.collection.valueIterator
import androidx.core.content.res.use
import androidx.core.net.toUri
import androidx.navigation.common.R
import androidx.navigation.serialization.generateHashCode
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.savedState
import androidx.savedstate.write
import java.util.regex.Pattern
import kotlin.reflect.KClass
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer

public actual open class NavDestination
actual constructor(public actual val navigatorName: String) {
    /**
     * This optional annotation allows tooling to offer auto-complete for the `android:name`
     * attribute. This should match the class type passed to [parseClassFromName] when parsing the
     * `android:name` attribute.
     */
    @kotlin.annotation.Retention(AnnotationRetention.BINARY)
    @Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
    public annotation class ClassType(val value: KClass<*>)

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public actual class DeepLinkMatch(
        public actual val destination: NavDestination,
        @get:Suppress("NullableCollection") // Needed for nullable savedState
        public actual val matchingArgs: SavedState?,
        private val isExactDeepLink: Boolean,
        private val matchingPathSegments: Int,
        private val hasMatchingAction: Boolean,
        private val mimeTypeMatchLevel: Int
    ) : Comparable<DeepLinkMatch> {
        override fun compareTo(other: DeepLinkMatch): Int {
            // Prefer exact deep links
            if (isExactDeepLink && !other.isExactDeepLink) {
                return 1
            } else if (!isExactDeepLink && other.isExactDeepLink) {
                return -1
            }
            // Then prefer most exact match path segments
            val pathSegmentDifference = matchingPathSegments - other.matchingPathSegments
            if (pathSegmentDifference > 0) {
                return 1
            } else if (pathSegmentDifference < 0) {
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
            if (hasMatchingAction && !other.hasMatchingAction) {
                return 1
            } else if (!hasMatchingAction && other.hasMatchingAction) {
                return -1
            }
            return mimeTypeMatchLevel - other.mimeTypeMatchLevel
        }

        public actual fun hasMatchingArgs(arguments: SavedState?): Boolean {
            if (arguments == null || matchingArgs == null) return false

            matchingArgs.keySet().forEach { key ->
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

    private var idName: String? = null

    public actual var label: CharSequence? = null
    private val deepLinks = mutableListOf<NavDeepLink>()
    private val actions: SparseArrayCompat<NavAction> = SparseArrayCompat()

    private var _arguments: MutableMap<String, NavArgument> = mutableMapOf()

    public actual val arguments: Map<String, NavArgument>
        get() = _arguments.toMap()

    public actual constructor(
        navigator: Navigator<out NavDestination>
    ) : this(NavigatorProvider.getNameForNavigator(navigator.javaClass))

    /**
     * Called when inflating a destination from a resource.
     *
     * @param context local context performing inflation
     * @param attrs attrs to parse during inflation
     */
    @CallSuper
    public open fun onInflate(context: Context, attrs: AttributeSet) {
        context.resources.obtainAttributes(attrs, R.styleable.Navigator).use { array ->
            route = array.getString(R.styleable.Navigator_route)

            if (array.hasValue(R.styleable.Navigator_android_id)) {
                id = array.getResourceId(R.styleable.Navigator_android_id, 0)
                idName = getDisplayName(context, id)
            }
            label = array.getText(R.styleable.Navigator_android_label)
        }
    }

    /**
     * The destination's unique ID. This should be an ID resource generated by the Android resource
     * system.
     *
     * If using safe args, setting this manually will override the ID that was set based on route
     * from KClass.
     */
    @get:IdRes
    public var id: Int = 0
        set(@IdRes id) {
            field = id
            idName = null
        }

    public actual var route: String? = null
        set(route) {
            if (route == null) {
                id = 0
            } else {
                require(route.isNotBlank()) { "Cannot have an empty route" }

                // make sure the route contains all required arguments
                val tempRoute = createRoute(route)
                val tempDeepLink = NavDeepLink.Builder().setUriPattern(tempRoute).build()
                val missingRequiredArguments =
                    _arguments.missingRequiredArguments { key ->
                        key !in tempDeepLink.argumentsNames
                    }
                require(missingRequiredArguments.isEmpty()) {
                    "Cannot set route \"$route\" for destination $this. " +
                        "Following required arguments are missing: $missingRequiredArguments"
                }

                routeDeepLink = lazy { NavDeepLink.Builder().setUriPattern(tempRoute).build() }
                id = tempRoute.hashCode()
            }
            field = route
        }

    /**
     * This destination's unique route as a NavDeepLink.
     *
     * This deeplink must be kept private and segregated from the explicitly added public deeplinks
     * to ensure that external users cannot deeplink into this destination with this routeDeepLink.
     *
     * This value is reassigned a new lazy value every time [route] is updated to ensure that any
     * initialized lazy value is overwritten with the latest value.
     */
    private var routeDeepLink: Lazy<NavDeepLink>? = null

    public actual open val displayName: String
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) get() = idName ?: id.toString()

    public actual open fun hasDeepLink(deepLink: Uri): Boolean {
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
        val routeDeepLink = this.routeDeepLink?.value ?: return null

        val uri = createRoute(route).toUri()

        // includes matching args for path, query, and fragment
        val matchingArguments = routeDeepLink.getMatchingArguments(uri, _arguments) ?: return null
        val matchingPathSegments = routeDeepLink.calculateMatchingPathSegments(uri)
        return DeepLinkMatch(
            this,
            matchingArguments,
            routeDeepLink.isExactDeepLink,
            matchingPathSegments,
            false,
            -1
        )
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
                if (uri != null) deepLink.getMatchingArguments(uri, _arguments) else null
            val matchingPathSegments = deepLink.calculateMatchingPathSegments(uri)
            val requestAction = navDeepLinkRequest.action
            val matchingAction = requestAction != null && requestAction == deepLink.action
            val mimeType = navDeepLinkRequest.mimeType
            val mimeTypeMatchLevel =
                if (mimeType != null) deepLink.getMimeTypeMatchRating(mimeType) else -1
            if (
                matchingArguments != null ||
                    ((matchingAction || mimeTypeMatchLevel > -1) &&
                        hasRequiredArguments(deepLink, uri, _arguments))
            ) {
                val newMatch =
                    DeepLinkMatch(
                        this,
                        matchingArguments,
                        deepLink.isExactDeepLink,
                        matchingPathSegments,
                        matchingAction,
                        mimeTypeMatchLevel
                    )
                if (bestMatch == null || newMatch > bestMatch) {
                    bestMatch = newMatch
                }
            }
        }
        return bestMatch
    }

    private fun hasRequiredArguments(
        deepLink: NavDeepLink,
        uri: Uri?,
        arguments: Map<String, NavArgument>
    ): Boolean {
        val matchingArgs = deepLink.getMatchingPathAndQueryArgs(uri, arguments)
        val missingRequiredArguments =
            arguments.missingRequiredArguments { key -> !matchingArgs.read { contains(key) } }
        return missingRequiredArguments.isEmpty()
    }

    /**
     * Build an array containing the hierarchy from the root down to this destination.
     *
     * @param previousDestination the previous destination we are starting at
     * @return An array containing all of the ids from the previous destination (or the root of the
     *   graph if null) to this destination
     */
    @JvmOverloads
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun buildDeepLinkIds(previousDestination: NavDestination? = null): IntArray {
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
        return hierarchy.toList().map { it.id }.toIntArray()
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

    /**
     * @return Whether this NavDestination supports outgoing actions
     * @see NavDestination.putAction
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public open fun supportsActions(): Boolean {
        return true
    }

    /**
     * Returns the [NavAction] for the given action ID. This will recursively check the
     * [parent][getParent] of this destination if the action destination is not found in this
     * destination.
     *
     * @param id action ID to fetch
     * @return the [NavAction] mapped to the given action id, or null if one has not been set
     */
    public fun getAction(@IdRes id: Int): NavAction? {
        val destination = if (actions.isEmpty) null else actions[id]
        // Search the parent for the given action if it is not found in this destination
        return destination ?: parent?.run { getAction(id) }
    }

    /**
     * Creates a [NavAction] for the given [destId] and associates it with the [actionId].
     *
     * @param actionId action ID to bind
     * @param destId destination ID for the given action
     */
    public fun putAction(@IdRes actionId: Int, @IdRes destId: Int) {
        putAction(actionId, NavAction(destId))
    }

    /**
     * Sets the [NavAction] destination for an action ID.
     *
     * @param actionId action ID to bind
     * @param action action to associate with this action ID
     * @throws UnsupportedOperationException this destination is considered a terminal destination
     *   and does not support actions
     */
    public fun putAction(@IdRes actionId: Int, action: NavAction) {
        if (!supportsActions()) {
            throw UnsupportedOperationException(
                "Cannot add action $actionId to $this as it does not support actions, " +
                    "indicating that it is a terminal destination in your navigation graph and " +
                    "will never trigger actions."
            )
        }
        require(actionId != 0) { "Cannot have an action with actionId 0" }
        actions.put(actionId, action)
    }

    /**
     * Unsets the [NavAction] for an action ID.
     *
     * @param actionId action ID to remove
     */
    public fun removeAction(@IdRes actionId: Int) {
        actions.remove(actionId)
    }

    public actual fun addArgument(argumentName: String, argument: NavArgument) {
        _arguments[argumentName] = argument
    }

    public actual fun removeArgument(argumentName: String) {
        _arguments.remove(argumentName)
    }

    @Suppress("NullableCollection") // Needed for nullable savedState
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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

    /**
     * Parses a dynamic label containing arguments into a String.
     *
     * Supports String Resource arguments by parsing `R.string` values of `ReferenceType` arguments
     * found in `android:label` into their String values.
     *
     * Returns `null` if label is null.
     *
     * Returns the original label if the label was a static string.
     *
     * @param context Context used to resolve a resource's name
     * @param bundle SavedState containing the arguments used in the label
     * @return The parsed string or null if the label is null
     * @throws IllegalArgumentException if an argument provided in the label cannot be found in the
     *   bundle, or if the label contains a string template but the bundle is null
     */
    public fun fillInLabel(context: Context, bundle: SavedState?): String? {
        val label = label ?: return null

        val fillInPattern = Pattern.compile("\\{(.+?)\\}")
        val matcher = fillInPattern.matcher(label)
        val builder = StringBuffer()

        val args = bundle?.read { toMap() } ?: emptyMap()
        while (matcher.find()) {
            val argName = matcher.group(1)
            require(argName != null && argName in args) {
                "Could not find \"$argName\" in $bundle to fill label \"$label\""
            }

            matcher.appendReplacement(builder, /* replacement= */ "")

            val argType = _arguments[argName]?.type
            val argValue =
                if (argType == NavType.ReferenceType) {
                    context.getString(/* resId= */ NavType.ReferenceType[bundle!!, argName] as Int)
                } else {
                    argType!![bundle!!, argName].toString()
                }
            builder.append(argValue)
        }
        matcher.appendTail(builder)
        return builder.toString()
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append(javaClass.simpleName)
        sb.append("(")
        if (idName == null) {
            sb.append("0x")
            sb.append(Integer.toHexString(id))
        } else {
            sb.append(idName)
        }
        sb.append(")")
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

        val equalActions =
            actions.size() == other.actions.size() &&
                actions.keyIterator().asSequence().all { actions.get(it) == other.actions.get(it) }

        val equalArguments =
            _arguments.size == other._arguments.size &&
                _arguments.asSequence().all {
                    other._arguments.containsKey(it.key) && other._arguments[it.key] == it.value
                }

        return id == other.id &&
            route == other.route &&
            equalDeepLinks &&
            equalActions &&
            equalArguments
    }

    @Suppress("DEPRECATION")
    override fun hashCode(): Int {
        var result = id
        result = 31 * result + route.hashCode()
        deepLinks.forEach {
            result = 31 * result + it.uriPattern.hashCode()
            result = 31 * result + it.action.hashCode()
            result = 31 * result + it.mimeType.hashCode()
        }
        actions.valueIterator().forEach { value ->
            result = 31 * result + value.destinationId
            result = 31 * result + value.navOptions.hashCode()
            value.defaultArguments?.read { result = 31 * result + contentDeepHashCode() }
        }
        _arguments.keys.forEach {
            result = 31 * result + it.hashCode()
            result = 31 * result + _arguments[it].hashCode()
        }
        return result
    }

    public actual companion object {
        private val classes = mutableMapOf<String, Class<*>>()

        /**
         * Parse the class associated with this destination from a raw name, generally extracted
         * from the `android:name` attribute added to the destination's XML. This should be the
         * class providing the visual representation of the destination that the user sees after
         * navigating to this destination.
         *
         * This method does name -> Class caching and should be strongly preferred over doing your
         * own parsing if your [Navigator] supports the `android:name` attribute to give consistent
         * behavior across all Navigators.
         *
         * @param context Context providing the package name for use with relative class names and
         *   the ClassLoader
         * @param name Absolute or relative class name. Null names will be ignored.
         * @param expectedClassType The expected class type
         * @return The parsed class
         * @throws IllegalArgumentException if the class is not found in the provided Context's
         *   ClassLoader or if the class is not of the expected type
         */
        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        protected fun <C> parseClassFromName(
            context: Context,
            name: String,
            expectedClassType: Class<out C?>
        ): Class<out C?> {
            var innerName = name
            if (innerName[0] == '.') {
                innerName = context.packageName + innerName
            }
            var clazz = classes[innerName]
            if (clazz == null) {
                try {
                    clazz = Class.forName(innerName, true, context.classLoader)
                    classes[name] = clazz
                } catch (e: ClassNotFoundException) {
                    throw IllegalArgumentException(e)
                }
            }
            require(expectedClassType.isAssignableFrom(clazz!!)) {
                "$innerName must be a subclass of $expectedClassType"
            }
            return clazz as Class<out C?>
        }

        /** Used internally for NavDestinationTest */
        @JvmStatic
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun <C> parseClassFromNameInternal(
            context: Context,
            name: String,
            expectedClassType: Class<out C?>
        ): Class<out C?> {
            return parseClassFromName(context, name, expectedClassType)
        }

        /**
         * Retrieve a suitable display name for a given id.
         *
         * @param context Context used to resolve a resource's name
         * @param id The id to get a display name for
         * @return The resource's name if it is a valid id or just the id itself if it is not a
         *   valid resource
         */
        @JvmStatic
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun getDisplayName(context: Context, id: Int): String {
            // aapt-generated IDs have the high byte nonzero,
            // so anything below that cannot be a valid resource id
            return if (id <= 0x00FFFFFF) {
                id.toString()
            } else
                try {
                    context.resources.getResourceName(id)
                } catch (e: Resources.NotFoundException) {
                    id.toString()
                }
        }

        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public actual fun createRoute(route: String?): String =
            if (route != null) "android-app://androidx.navigation/$route" else ""

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
