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

package androidx.compose.ui.node

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class CompositionLocalModifierNodeTest {
    @get:Rule
    val rule = createComposeRule()

    private val staticLocalInt = staticCompositionLocalOf { 0 }
    private val localInt = compositionLocalOf { 0 }
    private val EmptyBoxMeasurePolicy = MeasurePolicy { _, constraints ->
        layout(constraints.maxWidth, constraints.maxHeight) {}
    }

    @Test
    fun defaultValueReturnedIfNotProvided() {
        var readValue = -1
        val node = object : Modifier.Node(), DrawModifierNode,
            CompositionLocalConsumerModifierNode {
            override fun ContentDrawScope.draw() {
                readValue = currentValueOf(localInt)
                drawContent()
            }
        }
        rule.setContent {
            Layout(modifierNodeElementOf { node }, EmptyBoxMeasurePolicy)
        }

        rule.runOnIdle {
            assertThat(readValue).isEqualTo(0)
        }
    }

    @Test
    fun providedValueReturned() {
        var readValue = -1
        val node = object : Modifier.Node(), DrawModifierNode,
            CompositionLocalConsumerModifierNode {
            override fun ContentDrawScope.draw() {
                readValue = currentValueOf(localInt)
                drawContent()
            }
        }
        rule.setContent {
            CompositionLocalProvider(localInt provides 2) {
                Layout(modifierNodeElementOf { node }, EmptyBoxMeasurePolicy)
            }
        }

        rule.runOnIdle {
            assertThat(readValue).isEqualTo(2)
        }
    }

    @Test
    fun providedValueUpdatedReadsNewValue() {
        var readValue = -1
        var providedValue by mutableStateOf(2)
        val node = object : Modifier.Node(), DrawModifierNode,
            CompositionLocalConsumerModifierNode {
            fun getValue(): Int = currentValueOf(localInt)
            override fun ContentDrawScope.draw() {
                readValue = getValue()
                drawContent()
            }
        }
        rule.setContent {
            CompositionLocalProvider(localInt provides providedValue) {
                Layout(modifierNodeElementOf { node }, EmptyBoxMeasurePolicy)
            }
        }

        rule.runOnIdle {
            assertThat(readValue).isEqualTo(2)
            assertThat(node.getValue()).isEqualTo(2)
            providedValue = 3
        }

        rule.runOnIdle {
            assertThat(readValue).isEqualTo(3)
            assertThat(node.getValue()).isEqualTo(3)
        }
    }

    @Test
    fun defaultStaticValueReturnedIfNotProvided() {
        var readValue = -1
        val node = object : Modifier.Node(), DrawModifierNode,
            CompositionLocalConsumerModifierNode {
            override fun ContentDrawScope.draw() {
                readValue = currentValueOf(staticLocalInt)
                drawContent()
            }
        }
        rule.setContent {
            Layout(modifierNodeElementOf { node }, EmptyBoxMeasurePolicy)
        }

        rule.runOnIdle {
            assertThat(readValue).isEqualTo(0)
        }
    }

    @Test
    fun providedStaticValueReturned() {
        var readValue = -1
        val node = object : Modifier.Node(), DrawModifierNode,
            CompositionLocalConsumerModifierNode {
            override fun ContentDrawScope.draw() {
                readValue = currentValueOf(staticLocalInt)
                drawContent()
            }
        }
        rule.setContent {
            CompositionLocalProvider(staticLocalInt provides 2) {
                Layout(modifierNodeElementOf { node }, EmptyBoxMeasurePolicy)
            }
        }

        rule.runOnIdle {
            assertThat(readValue).isEqualTo(2)
        }
    }

    @Test
    fun providedStaticValueUpdatedReadsNewValue() {
        var readValue = -1
        var providedValue by mutableStateOf(2)
        val node = object : Modifier.Node(), DrawModifierNode,
            CompositionLocalConsumerModifierNode {
            fun getValue(): Int = currentValueOf(staticLocalInt)
            override fun ContentDrawScope.draw() {
                readValue = getValue()
                drawContent()
            }
        }
        rule.setContent {
            CompositionLocalProvider(staticLocalInt provides providedValue) {
                Layout(modifierNodeElementOf { node }, EmptyBoxMeasurePolicy)
            }
        }

        rule.runOnIdle {
            assertThat(readValue).isEqualTo(2)
            assertThat(node.getValue()).isEqualTo(2)
            providedValue = 3
        }

        rule.runOnIdle {
            assertThat(readValue).isEqualTo(3)
            assertThat(node.getValue()).isEqualTo(3)
        }
    }

    // Regression test for b/271875799
    @Test
    fun compositionLocalsUpdateWhenContentMoves() {
        var readValue = -1
        var providedValue by mutableStateOf(42)

        var contentKey by mutableStateOf(1)
        val modifier = modifierNodeElementOf {
            object : Modifier.Node(), DrawModifierNode,
                CompositionLocalConsumerModifierNode {
                override fun ContentDrawScope.draw() {
                    readValue = currentValueOf(localInt)
                    drawContent()
                }
            }
        }

        rule.setContent {
            CompositionLocalProvider(localInt provides providedValue) {
                ReusableContent(contentKey) {
                    Layout(modifier, EmptyBoxMeasurePolicy)
                }
            }
        }

        rule.runOnIdle {
            assertThat(readValue).isEqualTo(42)
        }

        contentKey++
        providedValue = 86

        rule.runOnIdle {
            assertThat(readValue).isEqualTo(86)
        }
    }

    // Regression test for b/271875799
    @Test
    fun staticCompositionLocalsUpdateWhenContentMoves() {
        var readValue = -1
        var providedValue by mutableStateOf(32)

        var contentKey by mutableStateOf(1)
        val modifier = modifierNodeElementOf {
            object : Modifier.Node(), DrawModifierNode,
                CompositionLocalConsumerModifierNode {
                override fun ContentDrawScope.draw() {
                    readValue = currentValueOf(staticLocalInt)
                    drawContent()
                }
            }
        }

        rule.setContent {
            CompositionLocalProvider(staticLocalInt provides providedValue) {
                ReusableContent(contentKey) {
                    Layout(modifier, EmptyBoxMeasurePolicy)
                }
            }
        }

        rule.runOnIdle {
            assertThat(readValue).isEqualTo(32)
        }

        contentKey++
        providedValue = 64

        rule.runOnIdle {
            assertThat(readValue).isEqualTo(64)
        }
    }

    private inline fun <reified T : Modifier.Node> modifierNodeElementOf(
        crossinline create: () -> T
    ): ModifierNodeElement<T> = object : ModifierNodeElement<T>() {
        override fun create(): T = create()
        override fun update(node: T) = node
        override fun hashCode(): Int = System.identityHashCode(this)
        override fun equals(other: Any?) = (other === this)
    }
}
