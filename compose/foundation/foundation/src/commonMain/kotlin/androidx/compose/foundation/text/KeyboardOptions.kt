/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose.foundation.text

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.intl.LocaleList

/**
 * The keyboard configuration options for TextFields. It is not guaranteed if software keyboard
 * will comply with the options provided here.
 *
 * @param capitalization informs the keyboard whether to automatically capitalize characters,
 * words or sentences. Only applicable to only text based [KeyboardType]s such as
 * [KeyboardType.Text], [KeyboardType.Ascii]. It will not be applied to [KeyboardType]s such as
 * [KeyboardType.Number].
 * @param autoCorrect informs the keyboard whether to enable auto correct. Only applicable to
 * text based [KeyboardType]s such as [KeyboardType.Email], [KeyboardType.Uri]. It will not be
 * applied to [KeyboardType]s such as [KeyboardType.Number]. Most of keyboard
 * implementations ignore this value for [KeyboardType]s such as [KeyboardType.Text].
 * @param keyboardType The keyboard type to be used in this text field. Note that this input type
 * is honored by keyboard and shows corresponding keyboard but this is not guaranteed. For example,
 * some keyboards may send non-ASCII character even if you set [KeyboardType.Ascii].
 * @param imeAction The IME action. This IME action is honored by keyboard and may show specific
 * icons on the keyboard. For example, search icon may be shown if [ImeAction.Search] is specified.
 * When [ImeOptions.singleLine] is false, the keyboard might show return key rather than the action
 * requested here.
 * @param platformImeOptions defines the platform specific IME options.
 * @param showKeyboardOnFocus when true, software keyboard will show on focus gain. When
 * false, the user must interact (e.g. tap) before the keyboard is shown. A null value (the default
 * parameter value) means the keyboard will be shown on focus.
 * @param hintLocales List of the languages that the user is supposed to switch to no matter what
 * input method subtype is currently used. This special "hint" can be used mainly for, but not
 * limited to, multilingual users who want IMEs to switch language based on editor's context.
 * Pass null to express the intention that a specific hint should not be set.
 */
@Immutable
class KeyboardOptions(
    val capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    val autoCorrect: Boolean = true,
    val keyboardType: KeyboardType = KeyboardType.Text,
    val imeAction: ImeAction = ImeAction.Default,
    val platformImeOptions: PlatformImeOptions? = null,
    @Suppress("AutoBoxing")
    @get:Suppress("AutoBoxing")
    val showKeyboardOnFocus: Boolean? = null,
    @get:Suppress("NullableCollection")
    val hintLocales: LocaleList? = null
) {

    companion object {
        /**
         * Default [KeyboardOptions]. Please see parameter descriptions for default values.
         */
        @Stable
        val Default = KeyboardOptions()

        /**
         * Default [KeyboardOptions] for [BasicSecureTextField].
         */
        @Stable
        internal val SecureTextField = KeyboardOptions(
            autoCorrect = false,
            keyboardType = KeyboardType.Password
        )
    }

    @Deprecated(
        "Please use the new constructor that takes optional platformImeOptions parameter.",
        level = DeprecationLevel.HIDDEN
    )
    constructor(
        capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
        autoCorrect: Boolean = true,
        keyboardType: KeyboardType = KeyboardType.Text,
        imeAction: ImeAction = ImeAction.Default
    ) : this(
        capitalization = capitalization,
        autoCorrect = autoCorrect,
        keyboardType = keyboardType,
        imeAction = imeAction,
        platformImeOptions = null
    )

    @Deprecated("Maintained for binary compat", level = DeprecationLevel.HIDDEN)
    constructor(
        capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
        autoCorrect: Boolean = true,
        keyboardType: KeyboardType = KeyboardType.Text,
        imeAction: ImeAction = ImeAction.Default,
        platformImeOptions: PlatformImeOptions? = null
    ) : this(
        capitalization,
        autoCorrect,
        keyboardType,
        imeAction,
        platformImeOptions,
        showKeyboardOnFocus = Default.showKeyboardOnFocusOrDefault
    )

    @Deprecated("Maintained for binary compat", level = DeprecationLevel.HIDDEN)
    constructor(
        capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
        autoCorrect: Boolean = true,
        keyboardType: KeyboardType = KeyboardType.Text,
        imeAction: ImeAction = ImeAction.Default,
        platformImeOptions: PlatformImeOptions? = null,
        @Suppress("AutoBoxing")
        showKeyboardOnFocus: Boolean? = null
    ) : this(
        capitalization,
        autoCorrect,
        keyboardType,
        imeAction,
        platformImeOptions,
        showKeyboardOnFocus,
        hintLocales = null
    )

    internal val showKeyboardOnFocusOrDefault get() = showKeyboardOnFocus ?: true

    /**
     * Returns a new [ImeOptions] with the values that are in this [KeyboardOptions] and provided
     * params.
     *
     * @param singleLine see [ImeOptions.singleLine]
     */
    internal fun toImeOptions(singleLine: Boolean = ImeOptions.Default.singleLine) = ImeOptions(
        singleLine = singleLine,
        capitalization = capitalization,
        autoCorrect = autoCorrect,
        keyboardType = keyboardType,
        imeAction = imeAction,
        platformImeOptions = platformImeOptions,
        hintLocales = hintLocales
    )

    /**
     * Returns a copy of this object with the values passed to this method.
     *
     * Note that if an unspecified (null) value is passed explicitly to this method, it will replace
     * any actually-specified value.
     */
    fun copy(
        capitalization: KeyboardCapitalization = this.capitalization,
        autoCorrect: Boolean = this.autoCorrect,
        keyboardType: KeyboardType = this.keyboardType,
        imeAction: ImeAction = this.imeAction,
        platformImeOptions: PlatformImeOptions? = this.platformImeOptions,
        @Suppress("AutoBoxing")
        showKeyboardOnFocus: Boolean? = this.showKeyboardOnFocus,
        hintLocales: LocaleList? = this.hintLocales
    ): KeyboardOptions {
        return KeyboardOptions(
            capitalization = capitalization,
            autoCorrect = autoCorrect,
            keyboardType = keyboardType,
            imeAction = imeAction,
            platformImeOptions = platformImeOptions,
            showKeyboardOnFocus = showKeyboardOnFocus,
            hintLocales = hintLocales
        )
    }

    @Deprecated(
        "Maintained for binary compatibility",
        level = DeprecationLevel.HIDDEN
    )
    fun copy(
        capitalization: KeyboardCapitalization = this.capitalization,
        autoCorrect: Boolean = this.autoCorrect,
        keyboardType: KeyboardType = this.keyboardType,
        imeAction: ImeAction = this.imeAction,
        platformImeOptions: PlatformImeOptions? = this.platformImeOptions,
        @Suppress("AutoBoxing")
        showKeyboardOnFocus: Boolean? = this.showKeyboardOnFocus
    ): KeyboardOptions {
        return KeyboardOptions(
            capitalization = capitalization,
            autoCorrect = autoCorrect,
            keyboardType = keyboardType,
            imeAction = imeAction,
            platformImeOptions = platformImeOptions,
            showKeyboardOnFocus = showKeyboardOnFocus,
            hintLocales = this.hintLocales
            // New properties must be added here even though this is deprecated. The deprecated copy
            // constructors should still work on instances created with newer library versions.
        )
    }

    @Deprecated(
        "Maintained for binary compatibility",
        level = DeprecationLevel.HIDDEN
    )
    fun copy(
        capitalization: KeyboardCapitalization = this.capitalization,
        autoCorrect: Boolean = this.autoCorrect,
        keyboardType: KeyboardType = this.keyboardType,
        imeAction: ImeAction = this.imeAction,
        platformImeOptions: PlatformImeOptions? = this.platformImeOptions
    ): KeyboardOptions {
        return KeyboardOptions(
            capitalization = capitalization,
            autoCorrect = autoCorrect,
            keyboardType = keyboardType,
            imeAction = imeAction,
            platformImeOptions = platformImeOptions,
            showKeyboardOnFocus = this.showKeyboardOnFocus,
            hintLocales = this.hintLocales
            // New properties must be added here even though this is deprecated. The deprecated copy
            // constructors should still work on instances created with newer library versions.
        )
    }

    @Deprecated(
        "Please use the new copy function that takes optional platformImeOptions parameter.",
        level = DeprecationLevel.HIDDEN
    )
    fun copy(
        capitalization: KeyboardCapitalization = this.capitalization,
        autoCorrect: Boolean = this.autoCorrect,
        keyboardType: KeyboardType = this.keyboardType,
        imeAction: ImeAction = this.imeAction
    ): KeyboardOptions {
        return KeyboardOptions(
            capitalization = capitalization,
            autoCorrect = autoCorrect,
            keyboardType = keyboardType,
            imeAction = imeAction,
            platformImeOptions = this.platformImeOptions,
            showKeyboardOnFocus = this.showKeyboardOnFocus,
            hintLocales = this.hintLocales
            // New properties must be added here even though this is deprecated. The deprecated copy
            // constructors should still work on instances created with newer library versions.
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyboardOptions) return false

        if (capitalization != other.capitalization) return false
        if (autoCorrect != other.autoCorrect) return false
        if (keyboardType != other.keyboardType) return false
        if (imeAction != other.imeAction) return false
        if (platformImeOptions != other.platformImeOptions) return false
        if (showKeyboardOnFocus != other.showKeyboardOnFocus) return false
        if (hintLocales != other.hintLocales) return false

        return true
    }

    override fun hashCode(): Int {
        var result = capitalization.hashCode()
        result = 31 * result + autoCorrect.hashCode()
        result = 31 * result + keyboardType.hashCode()
        result = 31 * result + imeAction.hashCode()
        result = 31 * result + platformImeOptions.hashCode()
        result = 31 * result + showKeyboardOnFocus.hashCode()
        result = 31 * result + hintLocales.hashCode()
        return result
    }

    override fun toString(): String {
        return "KeyboardOptions(capitalization=$capitalization, autoCorrect=$autoCorrect, " +
            "keyboardType=$keyboardType, imeAction=$imeAction, " +
            "platformImeOptions=$platformImeOptions, " +
            "showKeyboardOnFocus=$showKeyboardOnFocus, " +
            "hintLocales=$hintLocales)"
    }
}
