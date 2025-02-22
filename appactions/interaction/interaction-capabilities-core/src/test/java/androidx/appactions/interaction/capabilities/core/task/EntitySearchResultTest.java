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

package androidx.appactions.interaction.capabilities.core.task;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class EntitySearchResultTest {

    @Test
    public void emptyList() {
        assertThat(EntitySearchResult.empty().getPossibleValues()).isEmpty();
    }

    @Test
    public void test() {
        EntitySearchResult.Builder<String> builder = new EntitySearchResult.Builder<String>();
        builder.addPossibleValue("foo");
        builder.addPossibleValue("bar");

        assertThat(builder.build().getPossibleValues()).containsExactly("foo", "bar");
    }
}
