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

package androidx.appactions.builtintypes.types

import androidx.appactions.builtintypes.properties.Name

interface NoInternetConnection : Thing {
    override fun toBuilder(): Builder<*>

    companion object {
        @JvmStatic
        fun Builder(): Builder<*> = NoInternetConnectionBuilderImpl()
    }

    interface Builder<Self : Builder<Self>> : Thing.Builder<Self> {
        override fun build(): NoInternetConnection
    }
}

private class NoInternetConnectionBuilderImpl :
    NoInternetConnection.Builder<NoInternetConnectionBuilderImpl> {

    private var identifier: String? = null
    private var name: Name? = null

    override fun build() = NoInternetConnectionImpl(identifier, name)

    override fun setIdentifier(text: String?): NoInternetConnectionBuilderImpl =
        apply { identifier = text }

    override fun setName(text: String): NoInternetConnectionBuilderImpl =
        apply { name = Name(text) }

    override fun setName(name: Name?): NoInternetConnectionBuilderImpl = apply { this.name = name }

    override fun clearName(): NoInternetConnectionBuilderImpl = apply { name = null }
}

private class NoInternetConnectionImpl(override val identifier: String?, override val name: Name?) :
    NoInternetConnection {
    override fun toBuilder(): NoInternetConnection.Builder<*> =
        NoInternetConnectionBuilderImpl().setIdentifier(identifier).setName(name)

    override fun toString(): String = "NoInternetConnection"
}