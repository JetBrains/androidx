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

package androidx.compose.ui.events

import androidx.compose.ui.event.InputModifiers
import androidx.compose.ui.event.NativeKeyboardEvent
import androidx.compose.ui.event.MouseButtons
import androidx.compose.ui.event.NativePointerEvent
import androidx.compose.ui.event.PointerEventRecord
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import org.jetbrains.skiko.SkikoPlatformPointerEvent
import org.jetbrains.skiko.currentNanoTime
import org.w3c.dom.TouchEvent
import org.w3c.dom.asList
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.WheelEvent

private fun KeyboardEvent.toSkikoKey(): Long {
    var key = this.keyCode.toLong()
    val side = this.location
    if (side == KeyboardEvent.DOM_KEY_LOCATION_RIGHT) {
        if (
            key == Key.CtrlLeft.keyCode ||
            key == Key.ShiftLeft.keyCode ||
            key == Key.MetaLeft.keyCode
        )
            key = key.or(0x80000000)
    }
    return key
}

private fun KeyboardEvent.toSkikoModifiers(): InputModifiers {
    var result = 0
    if (altKey) {
        result = result.or(InputModifiers.ALT.value)
    }
    if (shiftKey) {
        result = result.or(InputModifiers.SHIFT.value)
    }
    if (ctrlKey) {
        result = result.or(InputModifiers.CONTROL.value)
    }
    if (metaKey) {
        result = result.or(InputModifiers.META.value)
    }

    return InputModifiers(result)
}

internal fun KeyboardEvent.toSkikoEvent(
    kind: KeyEventType
): NativeKeyboardEvent {
    val skikoKey = toSkikoKey()
    return NativeKeyboardEvent(
        Key(skikoKey),
        toSkikoModifiers(),
        kind,
        timeStamp.toInt().toLong(),
        this
    )
}

private fun getButtonValue(button: Int): Int {
    return (when (button) {
        0 -> MouseButtons.LEFT
        1 -> MouseButtons.MIDDLE
        2 -> MouseButtons.RIGHT
        3 -> MouseButtons.BUTTON_4
        4 -> MouseButtons.BUTTON_5
        else -> MouseButtons.NONE
    }).value
}

private var buttonsFlags = 0
private fun MouseEvent.toSkikoPressedMouseButtons(
    kind: PointerEventType
): MouseButtons {
    // https://developer.mozilla.org/en-US/docs/Web/API/MouseEvent/button
    val button = button.toInt()

    buttonsFlags = if (kind == PointerEventType.Press) {
        buttonsFlags.or(getButtonValue(button))
    } else {
        buttonsFlags.xor(getButtonValue(button))
    }

    return MouseButtons(buttonsFlags)
}

internal fun MouseEvent.toSkikoEvent(
    kind: PointerEventType
): NativePointerEvent {
    return NativePointerEvent(
        x = offsetX,
        y = offsetY,
        pressedButtons = toSkikoPressedMouseButtons(kind),
        kind = kind,
        timestamp = timeStamp.toInt().toLong(),
        platform = this
    )
}

internal fun MouseEvent.toSkikoDragEvent(): NativePointerEvent {
    return NativePointerEvent(
        x = offsetX,
        y = offsetY,
        pressedButtons = MouseButtons(buttonsFlags),
        kind = PointerEventType.Move,
        timestamp = timeStamp.toInt().toLong(),
        platform = this
    )
}

internal fun WheelEvent.toSkikoScrollEvent(): NativePointerEvent {
    return NativePointerEvent(
        x = offsetX,
        y = offsetY,
        deltaX = deltaX,
        deltaY = deltaY,
        pressedButtons = MouseButtons(buttonsFlags),
        kind = PointerEventType.Scroll,
        timestamp = timeStamp.toInt().toLong(),
        platform = this
    )
}

private abstract external class ExtendedTouchEvent : TouchEvent {
    val force: Double
}

internal fun TouchEvent.toSkikoEvent(
    kind: PointerEventType,
    offsetX: Double,
    offsetY: Double
): NativePointerEvent {
    val pointers = changedTouches.asList().map { touch ->
        val x = touch.clientX.toDouble() - offsetX
        val y = touch.clientY.toDouble() - offsetY
        val force = touch.unsafeCast<ExtendedTouchEvent>().force

        PointerEventRecord(
            x = x,
            y = y,
            pressed = when (kind) {
                PointerEventType.Press, PointerEventType.Move -> true
                else -> false
            },
            device = PointerType.Touch,
            id = touch.identifier.toLong(),
            pressure = force
        )
    }

    return NativePointerEvent(
        x = pointers.map { it.x }.average(),
        y = pointers.map { it.y }.average(),
        kind = kind,
        timestamp = (currentNanoTime() / 1E6).toLong(),
        pointers = pointers,
        platform = this.unsafeCast<SkikoPlatformPointerEvent>()
    )
}