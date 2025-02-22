/*
 * Copyright (C) 2022 The Android Open Source Project
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
package androidx.health.connect.client.permission

import androidx.annotation.IntDef
import androidx.annotation.RestrictTo

/**
 * Type of access to health data: read or write.
 *
 * @see HealthPermission.create
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public object AccessTypes {
    const val READ = 1
    const val WRITE = 2
}

/**
 * Type of access to health data: read or write.
 *
 * @suppress
 */
@Retention(AnnotationRetention.SOURCE)
@IntDef(
    value =
        [
            AccessTypes.READ,
            AccessTypes.WRITE,
        ]
)
@RestrictTo(RestrictTo.Scope.LIBRARY)
annotation class AccessType
