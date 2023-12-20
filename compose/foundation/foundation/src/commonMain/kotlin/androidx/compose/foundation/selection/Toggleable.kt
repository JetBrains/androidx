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

package androidx.compose.foundation.selection

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.platform.inspectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState

/**
 * Configure component to make it toggleable via input and accessibility events
 *
 * This version has no [MutableInteractionSource] or [Indication] parameters, the default indication
 * from [LocalIndication] will be used. To specify [MutableInteractionSource] or [Indication], use
 * the other overload.
 *
 * If you are only creating this toggleable modifier inside composition, consider using the other
 * overload and explicitly passing `LocalIndication.current` for improved performance. For more
 * information see the documentation on the other overload.
 *
 * @sample androidx.compose.foundation.samples.ToggleableSample
 *
 * @see [Modifier.triStateToggleable] if you require support for an indeterminate state.
 *
 * @param value whether Toggleable is on or off
 * @param enabled whether or not this [toggleable] will handle input events and appear
 * enabled for semantics purposes
 * @param role the type of user interface element. Accessibility services might use this
 * to describe the element or do customizations
 * @param onValueChange callback to be invoked when toggleable is clicked,
 * therefore the change of the state in requested.
 */
fun Modifier.toggleable(
    value: Boolean,
    enabled: Boolean = true,
    role: Role? = null,
    onValueChange: (Boolean) -> Unit
) = composed(
    inspectorInfo = debugInspectorInfo {
        name = "toggleable"
        properties["value"] = value
        properties["enabled"] = enabled
        properties["role"] = role
        properties["onValueChange"] = onValueChange
    }
) {
    val localIndication = LocalIndication.current
    val interactionSource = if (localIndication is IndicationNodeFactory) {
        // We can fast path here as it will be created inside clickable lazily
        null
    } else {
        // We need an interaction source to pass between the indication modifier and clickable, so
        // by creating here we avoid another composed down the line
        remember { MutableInteractionSource() }
    }
    Modifier.toggleable(
        value = value,
        interactionSource = interactionSource,
        indication = localIndication,
        enabled = enabled,
        role = role,
        onValueChange = onValueChange
    )
}

/**
 * Configure component to make it toggleable via input and accessibility events.
 *
 * By default, if [interactionSource] is `null`, and [indication] is an [IndicationNodeFactory], an
 * internal [MutableInteractionSource] will be lazily created along with the [indication] only when
 * needed. This reduces the performance cost of toggleable during composition, as creating the
 * [indication] can be delayed until there is an incoming
 * [androidx.compose.foundation.interaction.Interaction]. If you are only passing a remembered
 * [MutableInteractionSource] and you are never using it outside of toggleable, it is recommended to
 * instead provide `null` to enable lazy creation.
 * If you are providing a [MutableInteractionSource], but you are only observing the
 * [MutableInteractionSource] and never emitting interactions, you can explicitly enable lazy
 * creation using [lazilyCreateIndication].
 * If you are emitting interactions or you need the [indication] to be created immediately, you can
 * pass `false` to [lazilyCreateIndication]. Note that [lazilyCreateIndication] only applies for
 * [IndicationNodeFactory] [indication]s. [Indication] instances using the deprecated
 * [Indication.rememberUpdatedInstance] API can not be lazily created.
 *
 * If [indication] is _not_ an [IndicationNodeFactory], and instead implements the deprecated
 * [Indication.rememberUpdatedInstance] method, you should explicitly pass a remembered
 * [MutableInteractionSource] as a parameter for [interactionSource] instead of `null`, as this
 * cannot be lazily created inside toggleable.
 *
 * @sample androidx.compose.foundation.samples.ToggleableSample
 *
 * @see [Modifier.triStateToggleable] if you require support for an indeterminate state.
 *
 * @param value whether Toggleable is on or off
 * @param interactionSource [MutableInteractionSource] that will be used to dispatch
 * [PressInteraction.Press] when this toggleable is pressed. If `null`, an internal
 * [MutableInteractionSource] will be created if needed.
 * @param indication indication to be shown when modified element is pressed. Be default,
 * indication from [LocalIndication] will be used. Pass `null` to show no indication, or
 * current value from [LocalIndication] to show theme default
 * @param enabled whether or not this [toggleable] will handle input events and appear
 * enabled for semantics purposes
 * @param role the type of user interface element. Accessibility services might use this
 * to describe the element or do customizations
 * @param lazilyCreateIndication if `true` (recommended for most cases), and [indication] is an
 * [IndicationNodeFactory], [indication] will only be created when this toggleable emits an
 * [androidx.compose.foundation.interaction.Interaction]. If [interactionSource] is `null`, or
 * you are only reading from the [interactionSource] and never emitting an interaction, you should
 * typically provide true. If you are emitting an interaction, or you need the indication to be
 * eagerly created, provide false. Note that this parameter has no effect if [indication] is not
 * an [IndicationNodeFactory].
 * @param onValueChange callback to be invoked when toggleable is clicked,
 * therefore the change of the state in requested.
 */
fun Modifier.toggleable(
    value: Boolean,
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    enabled: Boolean = true,
    role: Role? = null,
    lazilyCreateIndication: Boolean = (interactionSource == null) &&
        (indication is IndicationNodeFactory),
    onValueChange: (Boolean) -> Unit
) = inspectable(
    inspectorInfo = debugInspectorInfo {
        name = "toggleable"
        properties["value"] = value
        properties["interactionSource"] = interactionSource
        properties["indication"] = indication
        properties["enabled"] = enabled
        properties["role"] = role
        properties["lazilyCreateIndication"] = lazilyCreateIndication
        properties["onValueChange"] = onValueChange
    }
) {
    Modifier.triStateToggleable(
        state = ToggleableState(value),
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        role = role,
        lazilyCreateIndication = lazilyCreateIndication,
        onClick = { onValueChange(!value) }
    )
}

@Deprecated("Maintained for binary compatibility", level = DeprecationLevel.HIDDEN)
fun Modifier.toggleable(
    value: Boolean,
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    enabled: Boolean = true,
    role: Role? = null,
    onValueChange: (Boolean) -> Unit
): Modifier = toggleable(
    value = value,
    interactionSource = interactionSource,
    indication = indication,
    enabled = enabled,
    role = role,
    lazilyCreateIndication = false,
    onValueChange = onValueChange
)

/**
 * Configure component to make it toggleable via input and accessibility events with three
 * states: On, Off and Indeterminate.
 *
 * TriStateToggleable should be used when there are dependent Toggleables associated to this
 * component and those can have different values.
 *
 * This version has no [MutableInteractionSource] or [Indication] parameters, the default indication
 * from [LocalIndication] will be used. To specify [MutableInteractionSource] or [Indication], use
 * the other overload.
 *
 * If you are only creating this triStateToggleable modifier inside composition, consider using the
 * other overload and explicitly passing `LocalIndication.current` for improved performance. For
 * more information see the documentation on the other overload.
 *
 * @sample androidx.compose.foundation.samples.TriStateToggleableSample
 *
 * @see [Modifier.toggleable] if you want to support only two states: on and off
 *
 * @param state current value for the component
 * @param enabled whether or not this [triStateToggleable] will handle input events and
 * appear enabled for semantics purposes
 * @param role the type of user interface element. Accessibility services might use this
 * to describe the element or do customizations
 * @param onClick will be called when user clicks the toggleable.
 */
fun Modifier.triStateToggleable(
    state: ToggleableState,
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit
) = composed(
    inspectorInfo = debugInspectorInfo {
        name = "triStateToggleable"
        properties["state"] = state
        properties["enabled"] = enabled
        properties["role"] = role
        properties["onClick"] = onClick
    }
) {
    val localIndication = LocalIndication.current
    val interactionSource = if (localIndication is IndicationNodeFactory) {
        // We can fast path here as it will be created inside clickable lazily
        null
    } else {
        // We need an interaction source to pass between the indication modifier and clickable, so
        // by creating here we avoid another composed down the line
        remember { MutableInteractionSource() }
    }
    Modifier.triStateToggleable(
        state = state,
        interactionSource = interactionSource,
        indication = localIndication,
        enabled = enabled,
        role = role,
        onClick = onClick
    )
}

/**
 * Configure component to make it toggleable via input and accessibility events with three
 * states: On, Off and Indeterminate.
 *
 * TriStateToggleable should be used when there are dependent Toggleables associated to this
 * component and those can have different values.
 *
 * By default, if [interactionSource] is `null`, and [indication] is an [IndicationNodeFactory], an
 * internal [MutableInteractionSource] will be lazily created along with the [indication] only when
 * needed. This reduces the performance cost of triStateToggleable during composition, as creating
 * the [indication] can be delayed until there is an incoming
 * [androidx.compose.foundation.interaction.Interaction]. If you are only passing a remembered
 * [MutableInteractionSource] and you are never using it outside of triStateToggleable, it is
 * recommended to instead provide `null` to enable lazy creation.
 * If you are providing a [MutableInteractionSource], but you are only observing the
 * [MutableInteractionSource] and never emitting interactions, you can explicitly enable lazy
 * creation using [lazilyCreateIndication].
 * If you are emitting interactions or you need the [indication] to be created immediately, you can
 * pass `false` to [lazilyCreateIndication]. Note that [lazilyCreateIndication] only applies for
 * [IndicationNodeFactory] [indication]s. [Indication] instances using the deprecated
 * [Indication.rememberUpdatedInstance] API can not be lazily created.
 *
 * If [indication] is _not_ an [IndicationNodeFactory], and instead implements the deprecated
 * [Indication.rememberUpdatedInstance] method, you should explicitly pass a remembered
 * [MutableInteractionSource] as a parameter for [interactionSource] instead of `null`, as this
 * cannot be lazily created inside triStateToggleable.
 *
 * @sample androidx.compose.foundation.samples.TriStateToggleableSample
 *
 * @see [Modifier.toggleable] if you want to support only two states: on and off
 *
 * @param state current value for the component
 * @param interactionSource [MutableInteractionSource] that will be used to dispatch
 * [PressInteraction.Press] when this triStateToggleable is pressed. If `null`, an internal
 * [MutableInteractionSource] will be created if needed.
 * @param indication indication to be shown when modified element is pressed. Be default,
 * indication from [LocalIndication] will be used. Pass `null` to show no indication, or
 * current value from [LocalIndication] to show theme default
 * @param enabled whether or not this [triStateToggleable] will handle input events and
 * appear enabled for semantics purposes
 * @param role the type of user interface element. Accessibility services might use this
 * to describe the element or do customizations
 * @param lazilyCreateIndication if `true` (recommended for most cases), and [indication] is an
 * [IndicationNodeFactory], [indication] will only be created when this triStateToggleable emits an
 * [androidx.compose.foundation.interaction.Interaction]. If [interactionSource] is `null`, or
 * you are only reading from the [interactionSource] and never emitting an interaction, you should
 * typically provide true. If you are emitting an interaction, or you need the indication to be
 * eagerly created, provide false. Note that this parameter has no effect if [indication] is not
 * an [IndicationNodeFactory].
 * @param onClick will be called when user clicks the toggleable.
 */
fun Modifier.triStateToggleable(
    state: ToggleableState,
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    enabled: Boolean = true,
    role: Role? = null,
    lazilyCreateIndication: Boolean = (interactionSource == null) &&
        (indication is IndicationNodeFactory),
    onClick: () -> Unit
) = inspectable(
    inspectorInfo = debugInspectorInfo {
        name = "triStateToggleable"
        properties["state"] = state
        properties["interactionSource"] = interactionSource
        properties["indication"] = indication
        properties["enabled"] = enabled
        properties["role"] = role
        properties["lazilyCreateIndication"] = lazilyCreateIndication
        properties["onClick"] = onClick
    }
) {
    clickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        role = role,
        lazilyCreateIndication = lazilyCreateIndication,
        onClick = onClick
    ).semantics {
        this.toggleableState = state
    }
}

@Deprecated("Maintained for binary compatibility", level = DeprecationLevel.HIDDEN)
fun Modifier.triStateToggleable(
    state: ToggleableState,
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = triStateToggleable(
    state = state,
    interactionSource = interactionSource,
    indication = indication,
    enabled = enabled,
    role = role,
    lazilyCreateIndication = false,
    onClick = onClick
)
