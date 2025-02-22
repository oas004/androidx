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

package androidx.room.compiler.processing.ksp

import androidx.room.compiler.processing.XAnnotated
import androidx.room.compiler.processing.XEnumEntry
import androidx.room.compiler.processing.XEnumTypeElement
import androidx.room.compiler.processing.XHasModifiers
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration

internal class KspEnumEntry(
    env: KspProcessingEnv,
    override val declaration: KSClassDeclaration,
    override val enclosingElement: XEnumTypeElement
) : KspElement(env, declaration),
    XHasModifiers by KspHasModifiers.create(declaration),
    XAnnotated by KspAnnotated.create(env, declaration, KspAnnotated.UseSiteFilter.NO_USE_SITE),
    XEnumEntry {

    override val name: String
        get() = declaration.simpleName.asString()

    private val qualifiedName: String by lazy {
        (declaration.qualifiedName ?: declaration.simpleName).asString()
    }

    override val fallbackLocationText: String
        get() = qualifiedName

    override val closestMemberContainer: XEnumTypeElement
        get() = enclosingElement

    companion object {
        fun create(
            env: KspProcessingEnv,
            declaration: KSClassDeclaration,
        ): KspEnumEntry {
            require(declaration.classKind == ClassKind.ENUM_ENTRY) {
                "Expected declaration to be an enum entry but was ${declaration.classKind}"
            }
            return KspEnumEntry(
                env,
                declaration,
                KspTypeElement.create(
                    env,
                    declaration
                        .requireEnclosingMemberContainer(env)
                        .declaration as KSClassDeclaration
                ) as XEnumTypeElement
            )
        }
    }
}