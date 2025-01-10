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

package androidx.compose.ui.platform

import androidx.compose.ui.text.intl.Locale

internal class TranslationsCache<Key>(private val getTranslations: (String) -> Map<Key, String>?) {
    internal fun getString(key: Key): String {
        val locale = Locale.current
        val tag = localeTag(language = locale.language, region = locale.region)
        val translation = translationByLocaleTag.getOrPut(tag) {
            findTranslation(locale)
        }
        return translation.get(key = key) ?: error("Missing translation for $key")
    }

    /**
     * Translations we've already loaded, mapped by the locale tag (see [localeTag]).
     */
    private val translationByLocaleTag = mutableMapOf<String, Map<Key, String>>()

    /**
     * Returns the tag for the given locale.
     *
     * Note that this is our internal format; this isn't the same as [Locale.toLanguageTag].
     */
    private fun localeTag(language: String, region: String) = when {
        language == "" -> ""
        region == "" -> language
        else -> "${language}_$region"
    }

    /**
     * Returns a sequence of locale tags to use as keys to look up the translation for the given locale.
     *
     * Note that we don't need to check children (e.g. use `fr_FR` if `fr` is missing) because the
     * translations should never have a missing parent.
     */
    private fun localeTagChain(locale: Locale) = sequence {
        if (locale.region != "") {
            yield(localeTag(language = locale.language, region = locale.region))
        }
        if (locale.language != "") {
            yield(localeTag(language = locale.language, region = ""))
        }
        yield(localeTag("", ""))
    }

    /**
     * Finds a translation map for the given locale.
     */
    private fun findTranslation(locale: Locale): Map<Key, String> {
        // We don't need to merge translations because each one should contain all the strings.
        return localeTagChain(locale).firstNotNullOf { getTranslations(it) }
    }
}