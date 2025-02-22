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

package androidx.appactions.interaction.service

import android.util.SizeF
import android.widget.RemoteViews
import android.widget.RemoteViewsService.RemoteViewsFactory
import androidx.annotation.RestrictTo

/**
 * Holder for RemoteViews UI response.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
data class RemoteViewsInternal(
    val remoteViews: RemoteViews,
    val size: SizeF,
    val changedViewIds: HashSet<Int> = HashSet<Int>(),
    val remoteViewsFactories: HashMap<Int, RemoteViewsFactory> = HashMap<Int, RemoteViewsFactory>()
) {
    init {
        this.changedViewIds.addAll(changedViewIds)
        this.remoteViewsFactories.putAll(remoteViewsFactories)
    }
}
