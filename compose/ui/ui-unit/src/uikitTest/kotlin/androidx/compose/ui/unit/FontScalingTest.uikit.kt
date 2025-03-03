/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.compose.ui.unit

import androidx.compose.ui.unit.fontscaling.FontScaleConverterFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FontScalingTest {

    @Test
    fun sp_to_dp_test() {

        val density : Density = DensityWithConverter(
            density = 1f,
            fontScale = UIKitContentSize.XXXL.fontScale,
            converter = assertNotNull(FontScaleConverterFactory
                .forScale(UIKitContentSize.XXXL.fontScale))
        )

        with(density) {

            // default
            assertEquals(17f, 11.sp.toDp().value)
            assertEquals(18f, 12.sp.toDp().value)
            assertEquals(19f, 13.sp.toDp().value)
            assertEquals(21f, 15.sp.toDp().value)
            assertEquals(22f, 16.sp.toDp().value)
            assertEquals(23f, 17.sp.toDp().value)
            assertEquals(26f, 20.sp.toDp().value)
            assertEquals(28f, 22.sp.toDp().value)
            assertEquals(34f, 28.sp.toDp().value)
            assertEquals(40f, 34.sp.toDp().value)

            // interpolated
            assertEquals(17.5f, 11.5f.sp.toDp().value)
            assertEquals(31f, 25.sp.toDp().value)
        }
    }
}