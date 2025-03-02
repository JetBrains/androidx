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

package sample.optin

open class AnnotatedKotlinMembers {
    @ExperimentalKotlinAnnotation var field: Int = -1

    @set:ExperimentalKotlinAnnotation var fieldWithSetMarker: Int = -1

    @ExperimentalKotlinAnnotation
    fun method(): Int {
        return -1
    }

    @ExperimentalJavaAnnotation
    fun methodWithJavaMarker(): Int {
        return -1
    }

    internal companion object {
        @JvmStatic
        @ExperimentalKotlinAnnotation
        fun methodStatic(): Int {
            return -1
        }

        @JvmStatic @ExperimentalKotlinAnnotation val fieldStatic: Int = -1
    }
}
