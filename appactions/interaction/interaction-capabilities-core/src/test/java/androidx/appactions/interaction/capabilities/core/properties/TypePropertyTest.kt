/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.appactions.interaction.capabilities.core.properties

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TypePropertyTest {
    @Test
    fun dynamicInventory_test() {
        val mutablePossibleValues = mutableListOf<String>("a", "b")
        val testProperty = TypeProperty.Builder<String>()
            .setPossibleValueSupplier { mutablePossibleValues.toList() }
            .build()

        assertThat(testProperty.possibleValues).containsExactly("a", "b")

        mutablePossibleValues.add("c")

        assertThat(testProperty.possibleValues).containsExactly("a", "b", "c")
    }
}
