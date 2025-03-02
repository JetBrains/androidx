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
@file:JvmName("SynchronizationKt")

package androidx.compose.ui.text.platform

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmName

internal actual class SynchronizedObject : kotlinx.atomicfu.locks.SynchronizedObject()

internal actual inline fun makeSynchronizedObject(ref: Any?) = SynchronizedObject()

@PublishedApi
@Suppress("BanInlineOptIn")
@OptIn(ExperimentalContracts::class)
internal actual inline fun <R> synchronized(lock: SynchronizedObject, block: () -> R): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return kotlinx.atomicfu.locks.synchronized(lock, block)
}
