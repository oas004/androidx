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

package androidx.glance

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.layout.ContentScale
import androidx.glance.layout.PaddingModifier
import androidx.glance.layout.padding
import androidx.glance.layout.runTestingComposition
import androidx.glance.semantics.SemanticsModifier
import androidx.glance.semantics.SemanticsProperties
import androidx.glance.unit.ColorProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class ImageTest {
    private lateinit var fakeCoroutineScope: TestScope

    @Before
    fun setUp() {
        fakeCoroutineScope = TestScope()
    }

    @Test
    fun createImage() = fakeCoroutineScope.runTest {
        val root = runTestingComposition {
            Image(
                provider = ImageProvider(5),
                contentDescription = "Hello World",
                modifier = GlanceModifier.padding(5.dp),
                contentScale = ContentScale.FillBounds
            )
        }

        assertThat(root.children).hasSize(1)
        assertThat(root.children[0]).isInstanceOf(EmittableImage::class.java)

        val img = root.children[0] as EmittableImage

        val imgSource = assertIs<AndroidResourceImageProvider>(img.provider)
        assertThat(imgSource.resId).isEqualTo(5)
        val semanticsModifier = assertNotNull(img.modifier.findModifier<SemanticsModifier>())
        assertThat(semanticsModifier.configuration[SemanticsProperties.ContentDescription])
            .containsExactly("Hello World")
        assertThat(img.contentScale).isEqualTo(ContentScale.FillBounds)
        assertThat(img.modifier.findModifier<PaddingModifier>()).isNotNull()
        assertThat(img.colorFilterParams).isNull()
    }

    @Test
    fun createImage_tintColorFilter() {
        val colorProvider = ColorProvider(Color.Gray)
        fakeCoroutineScope.runTest {
            val root = runTestingComposition {
                Image(
                    provider = ImageProvider(5),
                    contentDescription = "Hello World",
                    modifier = GlanceModifier.padding(5.dp),
                    colorFilter = ColorFilter.tint(colorProvider)
                )
            }

            assertThat(root.children).hasSize(1)
            assertThat(root.children[0]).isInstanceOf(EmittableImage::class.java)

            val img = root.children[0] as EmittableImage

            val colorFilterParams = assertIs<TintColorFilterParams>(img.colorFilterParams)
            assertThat(colorFilterParams.colorProvider).isEqualTo(colorProvider)
        }
    }
}