/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.compose.material3

import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.SearchBarDefaults.InputFieldHeight
import androidx.compose.material3.internal.PredictiveBack
import androidx.compose.material3.tokens.ElevationTokens
import androidx.compose.material3.tokens.FilledTextFieldTokens
import androidx.compose.material3.tokens.MotionTokens
import androidx.compose.material3.tokens.SearchBarTokens
import androidx.compose.material3.tokens.SearchViewTokens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirst
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.delay

/**
 * <a href="https://m3.material.io/components/search/overview" class="external" target="_blank">Material Design search</a>.
 *
 * A search bar represents a floating search field that allows users to enter a keyword or phrase
 * and get relevant information. It can be used as a way to navigate through an app via search
 * queries.
 *
 * An active search bar expands into a search "view" and can be used to display dynamic suggestions.
 *
 * ![Search bar image](https://developer.android.com/images/reference/androidx/compose/material3/search-bar.png)
 *
 * A [SearchBar] expands to occupy the entirety of its allowed size when active. For full-screen
 * behavior as specified by Material guidelines, parent layouts of the [SearchBar] must not pass
 * any [Constraints] that limit its size, and the host activity should set
 * `WindowCompat.setDecorFitsSystemWindows(window, false)`.
 *
 * If this expansion behavior is undesirable, for example on large tablet screens, [DockedSearchBar]
 * can be used instead.
 *
 * An example looks like:
 * @sample androidx.compose.material3.samples.SearchBarSample
 *
 * @param query the query text to be shown in the search bar's input field
 * @param onQueryChange the callback to be invoked when the input service updates the query. An
 * updated text comes as a parameter of the callback.
 * @param onSearch the callback to be invoked when the input service triggers the [ImeAction.Search]
 * action. The current [query] comes as a parameter of the callback.
 * @param active whether this search bar is active
 * @param onActiveChange the callback to be invoked when this search bar's active state is changed
 * @param modifier the [Modifier] to be applied to this search bar
 * @param enabled controls the enabled state of this search bar. When `false`, this component will
 * not respond to user input, and it will appear visually disabled and disabled to accessibility
 * services.
 * @param placeholder the placeholder to be displayed when the search bar's [query] is empty.
 * @param leadingIcon the leading icon to be displayed at the beginning of the search bar container
 * @param trailingIcon the trailing icon to be displayed at the end of the search bar container
 * @param shape the shape of this search bar when it is not [active]. When [active], the shape will
 * always be [SearchBarDefaults.fullScreenShape].
 * @param colors [SearchBarColors] that will be used to resolve the colors used for this search bar
 * in different states. See [SearchBarDefaults.colors].
 * @param tonalElevation when [SearchBarColors.containerColor] is [ColorScheme.surface], a
 * translucent primary color overlay is applied on top of the container. A higher tonal elevation
 * value will result in a darker color in light theme and lighter color in dark theme. See also:
 * [Surface].
 * @param shadowElevation the elevation for the shadow below the search bar
 * @param windowInsets the window insets that the search bar will respect
 * @param interactionSource an optional hoisted [MutableInteractionSource] for observing and
 * emitting [Interaction]s for this search bar. You can use this to change the search bar's
 * appearance or preview the search bar in different states. Note that if `null` is provided,
 * interactions will still happen internally.
 * @param content the content of this search bar that will be displayed below the input field
 */
@ExperimentalMaterial3Api
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = SearchBarDefaults.inputFieldShape,
    colors: SearchBarColors = SearchBarDefaults.colors(),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    shadowElevation: Dp = SearchBarDefaults.ShadowElevation,
    windowInsets: WindowInsets = SearchBarDefaults.windowInsets,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val animationProgress = remember { Animatable(initialValue = if (active) 1f else 0f) }
    val finalBackProgress = remember { mutableFloatStateOf(Float.NaN) }
    val firstBackEvent = remember { mutableStateOf<BackEventCompat?>(null) }
    val currentBackEvent = remember { mutableStateOf<BackEventCompat?>(null) }

    LaunchedEffect(active) {
        val animationInProgress = animationProgress.value > 0 && animationProgress.value < 1
        val animationSpec =
            if (animationInProgress) AnimationPredictiveBackExitFloatSpec
            else if (active) AnimationEnterFloatSpec
            else AnimationExitFloatSpec
        val targetValue = if (active) 1f else 0f
        if (animationProgress.value != targetValue) {
            animationProgress.animateTo(targetValue, animationSpec)
        }
        if (!active) {
            finalBackProgress.floatValue = Float.NaN
            firstBackEvent.value = null
            currentBackEvent.value = null
        }
    }

    val mutatorMutex = remember { MutatorMutex() }
    PredictiveBackHandler(enabled = active) { progress ->
        mutatorMutex.mutate {
            try {
                finalBackProgress.floatValue = Float.NaN
                progress.collect { backEvent ->
                    if (firstBackEvent.value == null) {
                        firstBackEvent.value = backEvent
                    }
                    currentBackEvent.value = backEvent
                    val interpolatedProgress = PredictiveBack.transform(backEvent.progress)
                    animationProgress.snapTo(targetValue = 1 - interpolatedProgress)
                }
                finalBackProgress.floatValue = animationProgress.value
                onActiveChange(false)
            } catch (e: CancellationException) {
                animationProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = AnimationPredictiveBackExitFloatSpec
                )
                finalBackProgress.floatValue = Float.NaN
                firstBackEvent.value = null
                currentBackEvent.value = null
            }
        }
    }

    SearchBarImpl(
        animationProgress = animationProgress,
        finalBackProgress = finalBackProgress,
        firstBackEvent = firstBackEvent,
        currentBackEvent = currentBackEvent,
        modifier = modifier,
        inputField = {
            SearchBarInputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = onSearch,
                active = active,
                onActiveChange = onActiveChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                placeholder = placeholder,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                colors = colors.inputFieldColors,
                interactionSource = interactionSource,
            )
        },
        shape = shape,
        colors = colors,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        windowInsets = windowInsets,
        content = content,
    )
}

/**
 * <a href="https://m3.material.io/components/search/overview" class="external" target="_blank">Material Design search</a>.
 *
 * A search bar represents a floating search field that allows users to enter a keyword or phrase
 * and get relevant information. It can be used as a way to navigate through an app via search
 * queries.
 *
 * An active search bar expands into a search "view" and can be used to display dynamic suggestions.
 *
 * ![Search bar image](https://developer.android.com/images/reference/androidx/compose/material3/docked-search-bar.png)
 *
 * A [DockedSearchBar] displays search results in a bounded table below the input field. It is meant
 * to be an alternative to [SearchBar] when expanding to full-screen size is undesirable on large
 * screens such as tablets.
 *
 * An example looks like:
 * @sample androidx.compose.material3.samples.DockedSearchBarSample
 *
 * @param query the query text to be shown in the search bar's input field
 * @param onQueryChange the callback to be invoked when the input service updates the query. An
 * updated text comes as a parameter of the callback.
 * @param onSearch the callback to be invoked when the input service triggers the [ImeAction.Search]
 * action. The current [query] comes as a parameter of the callback.
 * @param active whether this search bar is active
 * @param onActiveChange the callback to be invoked when this search bar's active state is changed
 * @param modifier the [Modifier] to be applied to this search bar
 * @param enabled controls the enabled state of this search bar. When `false`, this component will
 * not respond to user input, and it will appear visually disabled and disabled to accessibility
 * services.
 * @param placeholder the placeholder to be displayed when the search bar's [query] is empty.
 * @param leadingIcon the leading icon to be displayed at the beginning of the search bar container
 * @param trailingIcon the trailing icon to be displayed at the end of the search bar container
 * @param shape the shape of this search bar
 * @param colors [SearchBarColors] that will be used to resolve the colors used for this search bar
 * in different states. See [SearchBarDefaults.colors].
 * @param tonalElevation when [SearchBarColors.containerColor] is [ColorScheme.surface], a
 * translucent primary color overlay is applied on top of the container. A higher tonal elevation
 * value will result in a darker color in light theme and lighter color in dark theme. See also:
 * [Surface].
 * @param shadowElevation the elevation for the shadow below the search bar
 * @param interactionSource an optional hoisted [MutableInteractionSource] for observing and
 * emitting [Interaction]s for this search bar. You can use this to change the search bar's
 * appearance or preview the search bar in different states. Note that if `null` is provided,
 * interactions will still happen internally.
 * @param content the content of this search bar that will be displayed below the input field
 */
@ExperimentalMaterial3Api
@Composable
fun DockedSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = SearchBarDefaults.dockedShape,
    colors: SearchBarColors = SearchBarDefaults.colors(),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    shadowElevation: Dp = SearchBarDefaults.ShadowElevation,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }

    Surface(
        shape = shape,
        color = colors.containerColor,
        contentColor = contentColorFor(colors.containerColor),
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        modifier = modifier
            .zIndex(1f)
            .width(SearchBarMinWidth)
    ) {
        Column {
            SearchBarInputField(
                modifier = Modifier.fillMaxWidth(),
                query = query,
                onQueryChange = onQueryChange,
                onSearch = onSearch,
                active = active,
                onActiveChange = onActiveChange,
                enabled = enabled,
                placeholder = placeholder,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                colors = colors.inputFieldColors,
                interactionSource = interactionSource,
            )

            AnimatedVisibility(
                visible = active,
                enter = DockedEnterTransition,
                exit = DockedExitTransition,
            ) {
                val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                val maxHeight = remember(screenHeight) {
                    screenHeight * DockedActiveTableMaxHeightScreenRatio
                }
                val minHeight = remember(maxHeight) {
                    DockedActiveTableMinHeight.coerceAtMost(maxHeight)
                }

                Column(Modifier.heightIn(min = minHeight, max = maxHeight)) {
                    HorizontalDivider(color = colors.dividerColor)
                    content()
                }
            }
        }
    }

    BackHandler(enabled = active) {
        onActiveChange(false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchBarInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    colors: TextFieldColors = SearchBarDefaults.inputFieldColors(),
    interactionSource: MutableInteractionSource? = null,
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val searchSemantics = getString(Strings.SearchBarSearch)
    val suggestionsAvailableSemantics = getString(Strings.SuggestionsAvailable)
    val textColor = LocalTextStyle.current.color.takeOrElse {
        val focused = interactionSource.collectIsFocusedAsState().value
        colors.textColor(enabled, isError = false, focused = focused)
    }

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .sizeIn(
                minWidth = SearchBarMinWidth,
                maxWidth = SearchBarMaxWidth,
                minHeight = InputFieldHeight,
            )
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onActiveChange(true) }
            .semantics {
                contentDescription = searchSemantics
                if (active) {
                    stateDescription = suggestionsAvailableSemantics
                }
                onClick {
                    focusRequester.requestFocus()
                    true
                }
            },
        enabled = enabled,
        singleLine = true,
        textStyle = LocalTextStyle.current.merge(TextStyle(color = textColor)),
        cursorBrush = SolidColor(colors.cursorColor(isError = false)),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
        interactionSource = interactionSource,
        decorationBox = @Composable { innerTextField ->
            TextFieldDefaults.DecorationBox(
                value = query,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                placeholder = placeholder,
                leadingIcon = leadingIcon?.let { leading -> {
                    Box(Modifier.offset(x = SearchBarIconOffsetX)) { leading() }
                } },
                trailingIcon = trailingIcon?.let { trailing -> {
                    Box(Modifier.offset(x = -SearchBarIconOffsetX)) { trailing() }
                } },
                shape = SearchBarDefaults.inputFieldShape,
                colors = colors,
                contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(),
                container = {},
            )
        }
    )

    val isFocused = interactionSource.collectIsFocusedAsState().value
    val shouldClearFocus = !active && isFocused
    LaunchedEffect(active) {
        if (shouldClearFocus) {
            // Not strictly needed according to the motion spec, but since the animation already has
            // a delay, this works around b/261632544.
            delay(AnimationDelayMillis.toLong())
            focusManager.clearFocus()
        }
    }
}

/**
 * Defaults used in [SearchBar] and [DockedSearchBar].
 */
@ExperimentalMaterial3Api
object SearchBarDefaults {
    /** Default tonal elevation for a search bar. */
    val TonalElevation: Dp = ElevationTokens.Level0

    /** Default shadow elevation for a search bar. */
    val ShadowElevation: Dp = ElevationTokens.Level0

    @Deprecated(
        message = "Renamed to TonalElevation. Not to be confused with ShadowElevation.",
        replaceWith = ReplaceWith("TonalElevation"),
        level = DeprecationLevel.WARNING,
    )
    val Elevation: Dp = TonalElevation

    /** Default height for a search bar's input field, or a search bar in the inactive state. */
    val InputFieldHeight: Dp = SearchBarTokens.ContainerHeight

    /** Default shape for a search bar's input field, or a search bar in the inactive state. */
    val inputFieldShape: Shape @Composable get() = SearchBarTokens.ContainerShape.value

    /** Default shape for a [SearchBar] in the active state. */
    val fullScreenShape: Shape
        @Composable get() = SearchViewTokens.FullScreenContainerShape.value

    /** Default shape for a [DockedSearchBar]. */
    val dockedShape: Shape @Composable get() = SearchViewTokens.DockedContainerShape.value

    /** Default window insets for a [SearchBar]. */
    val windowInsets: WindowInsets @Composable get() = WindowInsets.statusBars

    /**
     * Creates a [SearchBarColors] that represents the different colors used in parts of the
     * search bar in different states.
     *
     * @param containerColor the container color of the search bar
     * @param dividerColor the color of the divider between the input field and the search results
     * @param inputFieldColors the colors of the input field
     */
    @Composable
    fun colors(
        containerColor: Color = SearchBarTokens.ContainerColor.value,
        dividerColor: Color = SearchViewTokens.DividerColor.value,
        inputFieldColors: TextFieldColors = inputFieldColors(),
    ): SearchBarColors = SearchBarColors(
        containerColor = containerColor,
        dividerColor = dividerColor,
        inputFieldColors = inputFieldColors,
    )

    /**
     * Creates a [TextFieldColors] that represents the different colors used in the search bar
     * input field in different states.
     *
     * Only a subset of the full list of [TextFieldColors] parameters are used in the input field.
     * All other parameters have no effect.
     *
     * @param focusedTextColor the color used for the input text of this input field when focused
     * @param unfocusedTextColor the color used for the input text of this input field when not
     * focused
     * @param disabledTextColor the color used for the input text of this input field when disabled
     * @param cursorColor the cursor color for this input field
     * @param selectionColors the colors used when the input text of this input field is selected
     * @param focusedLeadingIconColor the leading icon color for this input field when focused
     * @param unfocusedLeadingIconColor the leading icon color for this input field when not focused
     * @param disabledLeadingIconColor the leading icon color for this input field when disabled
     * @param focusedTrailingIconColor the trailing icon color for this input field when focused
     * @param unfocusedTrailingIconColor the trailing icon color for this input field when not
     * focused
     * @param disabledTrailingIconColor the trailing icon color for this input field when disabled
     * @param focusedPlaceholderColor the placeholder color for this input field when focused
     * @param unfocusedPlaceholderColor the placeholder color for this input field when not focused
     * @param disabledPlaceholderColor the placeholder color for this input field when disabled
     */
    @Composable
    fun inputFieldColors(
        focusedTextColor: Color = SearchBarTokens.InputTextColor.value,
        unfocusedTextColor: Color = SearchBarTokens.InputTextColor.value,
        disabledTextColor: Color = FilledTextFieldTokens.DisabledInputColor.value
            .copy(alpha = FilledTextFieldTokens.DisabledInputOpacity),
        cursorColor: Color = FilledTextFieldTokens.CaretColor.value,
        selectionColors: TextSelectionColors = LocalTextSelectionColors.current,
        focusedLeadingIconColor: Color = SearchBarTokens.LeadingIconColor.value,
        unfocusedLeadingIconColor: Color = SearchBarTokens.LeadingIconColor.value,
        disabledLeadingIconColor: Color = FilledTextFieldTokens.DisabledLeadingIconColor
            .value.copy(alpha = FilledTextFieldTokens.DisabledLeadingIconOpacity),
        focusedTrailingIconColor: Color = SearchBarTokens.TrailingIconColor.value,
        unfocusedTrailingIconColor: Color = SearchBarTokens.TrailingIconColor.value,
        disabledTrailingIconColor: Color = FilledTextFieldTokens.DisabledTrailingIconColor
            .value.copy(alpha = FilledTextFieldTokens.DisabledTrailingIconOpacity),
        focusedPlaceholderColor: Color = SearchBarTokens.SupportingTextColor.value,
        unfocusedPlaceholderColor: Color = SearchBarTokens.SupportingTextColor.value,
        disabledPlaceholderColor: Color = FilledTextFieldTokens.DisabledInputColor.value
            .copy(alpha = FilledTextFieldTokens.DisabledInputOpacity),
    ): TextFieldColors =
        TextFieldDefaults.colors(
            focusedTextColor = focusedTextColor,
            unfocusedTextColor = unfocusedTextColor,
            disabledTextColor = disabledTextColor,
            cursorColor = cursorColor,
            selectionColors = selectionColors,
            focusedLeadingIconColor = focusedLeadingIconColor,
            unfocusedLeadingIconColor = unfocusedLeadingIconColor,
            disabledLeadingIconColor = disabledLeadingIconColor,
            focusedTrailingIconColor = focusedTrailingIconColor,
            unfocusedTrailingIconColor = unfocusedTrailingIconColor,
            disabledTrailingIconColor = disabledTrailingIconColor,
            focusedPlaceholderColor = focusedPlaceholderColor,
            unfocusedPlaceholderColor = unfocusedPlaceholderColor,
            disabledPlaceholderColor = disabledPlaceholderColor,
        )

    @Deprecated("Maintained for binary compatibility", level = DeprecationLevel.HIDDEN)
    @Composable
    fun inputFieldColors(
        textColor: Color = SearchBarTokens.InputTextColor.value,
        disabledTextColor: Color = FilledTextFieldTokens.DisabledInputColor.value
            .copy(alpha = FilledTextFieldTokens.DisabledInputOpacity),
        cursorColor: Color = FilledTextFieldTokens.CaretColor.value,
        selectionColors: TextSelectionColors = LocalTextSelectionColors.current,
        focusedLeadingIconColor: Color = SearchBarTokens.LeadingIconColor.value,
        unfocusedLeadingIconColor: Color = SearchBarTokens.LeadingIconColor.value,
        disabledLeadingIconColor: Color = FilledTextFieldTokens.DisabledLeadingIconColor
            .value.copy(alpha = FilledTextFieldTokens.DisabledLeadingIconOpacity),
        focusedTrailingIconColor: Color = SearchBarTokens.TrailingIconColor.value,
        unfocusedTrailingIconColor: Color = SearchBarTokens.TrailingIconColor.value,
        disabledTrailingIconColor: Color = FilledTextFieldTokens.DisabledTrailingIconColor
            .value.copy(alpha = FilledTextFieldTokens.DisabledTrailingIconOpacity),
        placeholderColor: Color = SearchBarTokens.SupportingTextColor.value,
        disabledPlaceholderColor: Color = FilledTextFieldTokens.DisabledInputColor.value
            .copy(alpha = FilledTextFieldTokens.DisabledInputOpacity),
    ) = inputFieldColors(
        focusedTextColor = textColor,
        unfocusedTextColor = textColor,
        disabledTextColor = disabledTextColor,
        cursorColor = cursorColor,
        selectionColors = selectionColors,
        focusedLeadingIconColor = focusedLeadingIconColor,
        unfocusedLeadingIconColor = unfocusedLeadingIconColor,
        disabledLeadingIconColor = disabledLeadingIconColor,
        focusedTrailingIconColor = focusedTrailingIconColor,
        unfocusedTrailingIconColor = unfocusedTrailingIconColor,
        disabledTrailingIconColor = disabledTrailingIconColor,
        focusedPlaceholderColor = placeholderColor,
        unfocusedPlaceholderColor = placeholderColor,
        disabledPlaceholderColor = disabledPlaceholderColor,
    )
}

/**
 * Represents the colors used by a search bar in different states.
 *
 * See [SearchBarDefaults.colors] for the default implementation that follows Material
 * specifications.
 */
@ExperimentalMaterial3Api
@Immutable
class SearchBarColors(
    val containerColor: Color,
    val dividerColor: Color,
    val inputFieldColors: TextFieldColors,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SearchBarColors

        if (containerColor != other.containerColor) return false
        if (dividerColor != other.dividerColor) return false
        if (inputFieldColors != other.inputFieldColors) return false

        return true
    }

    override fun hashCode(): Int {
        var result = containerColor.hashCode()
        result = 31 * result + dividerColor.hashCode()
        result = 31 * result + inputFieldColors.hashCode()
        return result
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchBarImpl(
    animationProgress: Animatable<Float, AnimationVector1D>,
    finalBackProgress: MutableFloatState,
    firstBackEvent: MutableState<BackEventCompat?>,
    currentBackEvent: MutableState<BackEventCompat?>,
    modifier: Modifier = Modifier,
    inputField: @Composable () -> Unit,
    shape: Shape = SearchBarDefaults.inputFieldShape,
    colors: SearchBarColors = SearchBarDefaults.colors(),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    shadowElevation: Dp = SearchBarDefaults.ShadowElevation,
    windowInsets: WindowInsets = SearchBarDefaults.windowInsets,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current

    val defaultInputFieldShape = SearchBarDefaults.inputFieldShape
    val defaultFullScreenShape = SearchBarDefaults.fullScreenShape
    val useFullScreenShape by remember {
        derivedStateOf(structuralEqualityPolicy()) { animationProgress.value == 1f }
    }
    val animatedShape = remember(useFullScreenShape, shape) {
        when {
            shape == defaultInputFieldShape ->
                // The shape can only be animated if it's the default spec value
                GenericShape { size, _ ->
                    val radius = with(density) {
                        (SearchBarCornerRadius * (1 - animationProgress.value)).toPx()
                    }
                    addRoundRect(RoundRect(size.toRect(), CornerRadius(radius)))
                }
            useFullScreenShape -> defaultFullScreenShape
            else -> shape
        }
    }
    val surface = @Composable {
        Surface(
            modifier = Modifier,
            shape = animatedShape,
            color = colors.containerColor,
            contentColor = contentColorFor(colors.containerColor),
            tonalElevation = tonalElevation,
            shadowElevation = shadowElevation,
            content = {},
        )
    }

    val showContent by remember {
        derivedStateOf(structuralEqualityPolicy()) { animationProgress.value > 0 }
    }
    val wrappedContent: (@Composable () -> Unit)? = if (showContent) {
        {
            Column(Modifier.graphicsLayer { alpha = animationProgress.value }) {
                HorizontalDivider(color = colors.dividerColor)
                content()
            }
        }
    } else null

    SearchBarLayout(
        animationProgress = animationProgress,
        finalBackProgress = finalBackProgress,
        firstBackEvent = firstBackEvent,
        currentBackEvent = currentBackEvent,
        modifier = modifier,
        windowInsets = windowInsets,
        inputField = inputField,
        surface = surface,
        content = wrappedContent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBarLayout(
    animationProgress: Animatable<Float, AnimationVector1D>,
    finalBackProgress: MutableFloatState,
    firstBackEvent: MutableState<BackEventCompat?>,
    currentBackEvent: MutableState<BackEventCompat?>,
    modifier: Modifier,
    windowInsets: WindowInsets,
    inputField: @Composable () -> Unit,
    surface: @Composable () -> Unit,
    content: (@Composable () -> Unit)?,
) {
    // `Modifier.windowInsetsPadding` does not support animation,
    // so the insets are converted to paddings in the Layout's MeasureScope
    // and the animation calculations are done manually.
    val unconsumedInsets = remember { MutableWindowInsets() }
    Layout(
        modifier = modifier
            .zIndex(1f)
            .onConsumedWindowInsetsChanged { consumedInsets ->
                unconsumedInsets.insets = windowInsets.exclude(consumedInsets)
            }
            .consumeWindowInsets(unconsumedInsets),
        content = {
            Box(Modifier.layoutId(LayoutIdSurface), propagateMinConstraints = true) {
                surface()
            }
            Box(Modifier.layoutId(LayoutIdInputField), propagateMinConstraints = true) {
                inputField()
            }
            content?.let { content ->
                Box(Modifier.layoutId(LayoutIdSearchContent), propagateMinConstraints = true) {
                    content()
                }
            }
        },
    ) { measurables, constraints ->
        @Suppress("NAME_SHADOWING")
        val animationProgress = animationProgress.value

        val inputFieldMeasurable = measurables.fastFirst { it.layoutId == LayoutIdInputField }
        val surfaceMeasurable = measurables.fastFirst { it.layoutId == LayoutIdSurface }
        val contentMeasurable = measurables.fastFirstOrNull { it.layoutId == LayoutIdSearchContent }

        val topPadding = unconsumedInsets.getTop(this) + SearchBarVerticalPadding.roundToPx()
        val bottomPadding = SearchBarVerticalPadding.roundToPx()

        val defaultStartWidth = constraints
            .constrainWidth(inputFieldMeasurable.maxIntrinsicWidth(constraints.maxHeight))
        val defaultStartHeight = constraints
            .constrainHeight(inputFieldMeasurable.minIntrinsicHeight(constraints.maxWidth))

        val predictiveBackStartWidth =
            (constraints.maxWidth * SearchBarPredictiveBackMinScale).roundToInt()
        val predictiveBackStartHeight =
            (constraints.maxHeight * SearchBarPredictiveBackMinScale).roundToInt()
        val predictiveBackMultiplier = calculatePredictiveBackMultiplier(
            currentBackEvent.value,
            animationProgress,
            finalBackProgress.floatValue
        )

        val startWidth =
            lerp(defaultStartWidth, predictiveBackStartWidth, predictiveBackMultiplier)
        val startHeight = lerp(
            topPadding + defaultStartHeight,
            predictiveBackStartHeight,
            predictiveBackMultiplier
        )

        val endWidth = constraints.maxWidth
        val endHeight = constraints.maxHeight

        val width = lerp(startWidth, endWidth, animationProgress)
        val height = lerp(startHeight, endHeight, animationProgress)

        // Note: animatedTopPadding decreases w.r.t. animationProgress
        val animatedTopPadding = lerp(topPadding, 0, animationProgress)
        val animatedBottomPadding = lerp(0, bottomPadding, animationProgress)

        // As the animation proceeds, the surface loses its padding
        // and expands to cover the entire container.
        val surfacePlaceable = surfaceMeasurable
            .measure(Constraints.fixed(width, height - animatedTopPadding))
        val inputFieldPlaceable = inputFieldMeasurable
            .measure(Constraints.fixed(width, defaultStartHeight))
        val contentPlaceable = contentMeasurable?.measure(
            Constraints(
                minWidth = width,
                maxWidth = width,
                minHeight = 0,
                maxHeight = if (constraints.hasBoundedHeight) {
                    (constraints.maxHeight - (topPadding + defaultStartHeight + bottomPadding))
                        .coerceAtLeast(0)
                } else {
                    constraints.maxHeight
                }
            )
        )

        layout(width, height) {
            val minOffsetMargin = SearchBarPredictiveBackMinMargin.roundToPx()
            val predictiveBackOffsetX = calculatePredictiveBackOffsetX(
                constraints = constraints,
                minMargin = minOffsetMargin,
                currentBackEvent = currentBackEvent.value,
                layoutDirection = layoutDirection,
                progress = animationProgress,
                predictiveBackMultiplier = predictiveBackMultiplier,
            )
            val predictiveBackOffsetY = calculatePredictiveBackOffsetY(
                constraints = constraints,
                minMargin = minOffsetMargin,
                currentBackEvent = currentBackEvent.value,
                firstBackEvent = firstBackEvent.value,
                height = height,
                maxOffsetY = SearchBarPredictiveBackMaxOffsetY.roundToPx(),
                predictiveBackMultiplier = predictiveBackMultiplier,
            )

            surfacePlaceable.placeRelative(
                predictiveBackOffsetX,
                predictiveBackOffsetY + animatedTopPadding,
            )
            inputFieldPlaceable.placeRelative(
                predictiveBackOffsetX,
                predictiveBackOffsetY + topPadding,
            )
            contentPlaceable?.placeRelative(
                predictiveBackOffsetX,
                predictiveBackOffsetY + topPadding + inputFieldPlaceable.height +
                    animatedBottomPadding,
            )
        }
    }
}

private fun calculatePredictiveBackMultiplier(
    currentBackEvent: BackEventCompat?,
    progress: Float,
    finalBackProgress: Float
) = when {
    currentBackEvent == null -> 0f // Not in predictive back at all.
    finalBackProgress.isNaN() -> 1f // User is currently swiping predictive back.
    finalBackProgress <= 0 -> 0f // Safety check for divide by zero.
    else -> progress / finalBackProgress // User has released predictive back swipe.
}

private fun calculatePredictiveBackOffsetX(
    constraints: Constraints,
    minMargin: Int,
    currentBackEvent: BackEventCompat?,
    layoutDirection: LayoutDirection,
    progress: Float,
    predictiveBackMultiplier: Float
): Int {
    if (currentBackEvent == null || predictiveBackMultiplier == 0f) {
        return 0
    }
    val directionMultiplier = if (currentBackEvent.swipeEdge == BackEventCompat.EDGE_LEFT) 1 else -1
    val rtlMultiplier = if (layoutDirection == LayoutDirection.Ltr) 1 else -1
    val maxOffsetX =
        (constraints.maxWidth * SearchBarPredictiveBackMaxOffsetXRatio) - minMargin
    val interpolatedOffsetX = maxOffsetX * (1 - progress)
    return (interpolatedOffsetX * predictiveBackMultiplier * directionMultiplier * rtlMultiplier)
        .roundToInt()
}

private fun calculatePredictiveBackOffsetY(
    constraints: Constraints,
    minMargin: Int,
    currentBackEvent: BackEventCompat?,
    firstBackEvent: BackEventCompat?,
    height: Int,
    maxOffsetY: Int,
    predictiveBackMultiplier: Float
): Int {
    if (firstBackEvent == null || currentBackEvent == null || predictiveBackMultiplier == 0f) {
        return 0
    }
    val availableVerticalSpace = max(0, (constraints.maxHeight - height) / 2 - minMargin)
    val adjustedMaxOffsetY = min(availableVerticalSpace, maxOffsetY)
    val yDelta = currentBackEvent.touchY - firstBackEvent.touchY
    val yProgress = abs(yDelta) / constraints.maxHeight
    val directionMultiplier = sign(yDelta)
    val interpolatedOffsetY = lerp(0, adjustedMaxOffsetY, yProgress)
    return (interpolatedOffsetY * predictiveBackMultiplier * directionMultiplier).roundToInt()
}

private const val LayoutIdInputField = "InputField"
private const val LayoutIdSurface = "Surface"
private const val LayoutIdSearchContent = "Content"

// Measurement specs
@OptIn(ExperimentalMaterial3Api::class)
private val SearchBarCornerRadius: Dp = InputFieldHeight / 2
internal val DockedActiveTableMinHeight: Dp = 240.dp
private const val DockedActiveTableMaxHeightScreenRatio: Float = 2f / 3f
internal val SearchBarMinWidth: Dp = 360.dp
private val SearchBarMaxWidth: Dp = 720.dp
internal val SearchBarVerticalPadding: Dp = 8.dp
// Search bar has 16dp padding between icons and start/end, while by default text field has 12dp.
private val SearchBarIconOffsetX: Dp = 4.dp
private const val SearchBarPredictiveBackMinScale: Float = 9f / 10f
private val SearchBarPredictiveBackMinMargin: Dp = 8.dp
private const val SearchBarPredictiveBackMaxOffsetXRatio: Float = 1f / 20f
private val SearchBarPredictiveBackMaxOffsetY: Dp = 24.dp

// Animation specs
private const val AnimationEnterDurationMillis: Int = MotionTokens.DurationLong4.toInt()
private const val AnimationExitDurationMillis: Int = MotionTokens.DurationMedium3.toInt()
private const val AnimationDelayMillis: Int = MotionTokens.DurationShort2.toInt()
private val AnimationEnterEasing = MotionTokens.EasingEmphasizedDecelerateCubicBezier
private val AnimationExitEasing = CubicBezierEasing(0.0f, 1.0f, 0.0f, 1.0f)
private val AnimationEnterFloatSpec: FiniteAnimationSpec<Float> = tween(
    durationMillis = AnimationEnterDurationMillis,
    delayMillis = AnimationDelayMillis,
    easing = AnimationEnterEasing,
)
private val AnimationExitFloatSpec: FiniteAnimationSpec<Float> = tween(
    durationMillis = AnimationExitDurationMillis,
    delayMillis = AnimationDelayMillis,
    easing = AnimationExitEasing,
)
private val AnimationPredictiveBackExitFloatSpec: FiniteAnimationSpec<Float> = tween(
    durationMillis = AnimationExitDurationMillis,
    easing = AnimationExitEasing,
)
private val AnimationEnterSizeSpec: FiniteAnimationSpec<IntSize> = tween(
    durationMillis = AnimationEnterDurationMillis,
    delayMillis = AnimationDelayMillis,
    easing = AnimationEnterEasing,
)
private val AnimationExitSizeSpec: FiniteAnimationSpec<IntSize> = tween(
    durationMillis = AnimationExitDurationMillis,
    delayMillis = AnimationDelayMillis,
    easing = AnimationExitEasing,
)
private val DockedEnterTransition: EnterTransition =
    fadeIn(AnimationEnterFloatSpec) + expandVertically(AnimationEnterSizeSpec)
private val DockedExitTransition: ExitTransition =
    fadeOut(AnimationExitFloatSpec) + shrinkVertically(AnimationExitSizeSpec)
