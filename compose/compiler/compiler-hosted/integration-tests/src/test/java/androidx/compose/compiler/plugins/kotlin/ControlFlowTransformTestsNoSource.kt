/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose.compiler.plugins.kotlin

import org.jetbrains.kotlin.config.CompilerConfiguration
import org.junit.Test

class ControlFlowTransformTestsNoSource : AbstractControlFlowTransformTests() {
    override fun CompilerConfiguration.updateConfiguration() {
        put(ComposeConfiguration.SOURCE_INFORMATION_ENABLED_KEY, false)
    }

    @Test
    fun testPublicFunctionAlwaysMarkedAsCall(): Unit = controlFlow(
        """
            @Composable
            fun Test() {
              A(a)
              A(b)
            }
        """,
        """
            @Composable
            fun Test(%composer: Composer?, %changed: Int) {
              %composer = %composer.startRestartGroup(<>)
              sourceInformation(%composer, "C(Test)")
              if (%changed !== 0 || !%composer.skipping) {
                if (isTraceInProgress()) {
                  traceEventStart(<>, %changed, -1, <>)
                }
                A(a, %composer, 0)
                A(b, %composer, 0)
                if (isTraceInProgress()) {
                  traceEventEnd()
                }
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer?, %force: Int ->
                Test(%composer, updateChangedFlags(%changed or 0b0001))
              }
            }
        """
    )

    @Test
    fun testPrivateFunctionDoNotGetMarkedAsCall(): Unit = controlFlow(
        """
            @Composable
            private fun Test() {
              A(a)
              A(b)
            }
        """,
        """
            @Composable
            private fun Test(%composer: Composer?, %changed: Int) {
              %composer = %composer.startRestartGroup(<>)
              if (%changed !== 0 || !%composer.skipping) {
                if (isTraceInProgress()) {
                  traceEventStart(<>, %changed, -1, <>)
                }
                A(a, %composer, 0)
                A(b, %composer, 0)
                if (isTraceInProgress()) {
                  traceEventEnd()
                }
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer?, %force: Int ->
                Test(%composer, updateChangedFlags(%changed or 0b0001))
              }
            }

        """
    )

    @Test
    fun testCallingAWrapperComposable(): Unit = controlFlow(
        """
            @Composable
            fun Test() {
              W {
                A()
              }
            }
        """,
        """
            @Composable
            fun Test(%composer: Composer?, %changed: Int) {
              %composer = %composer.startRestartGroup(<>)
              sourceInformation(%composer, "C(Test)")
              if (%changed !== 0 || !%composer.skipping) {
                if (isTraceInProgress()) {
                  traceEventStart(<>, %changed, -1, <>)
                }
                W(ComposableSingletons%TestKt.lambda-1, %composer, 0b0110)
                if (isTraceInProgress()) {
                  traceEventEnd()
                }
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer?, %force: Int ->
                Test(%composer, updateChangedFlags(%changed or 0b0001))
              }
            }
            internal object ComposableSingletons%TestKt {
              val lambda-1: Function2<Composer, Int, Unit> = composableLambdaInstance(<>, false) { %composer: Composer?, %changed: Int ->
                if (%changed and 0b1011 !== 0b0010 || !%composer.skipping) {
                  if (isTraceInProgress()) {
                    traceEventStart(<>, %changed, -1, <>)
                  }
                  A(%composer, 0)
                  if (isTraceInProgress()) {
                    traceEventEnd()
                  }
                } else {
                  %composer.skipToGroupEnd()
                }
              }
            }
        """
    )

    @Test
    fun testCallingAnInlineWrapperComposable(): Unit = controlFlow(
        """
            @Composable
            fun Test() {
              IW {
                A()
              }
            }
        """,
        """
            @Composable
            fun Test(%composer: Composer?, %changed: Int) {
              %composer = %composer.startRestartGroup(<>)
              sourceInformation(%composer, "C(Test)")
              if (%changed !== 0 || !%composer.skipping) {
                if (isTraceInProgress()) {
                  traceEventStart(<>, %changed, -1, <>)
                }
                IW({ %composer: Composer?, %changed: Int ->
                  A(%composer, 0)
                }, %composer, 0)
                if (isTraceInProgress()) {
                  traceEventEnd()
                }
              } else {
                %composer.skipToGroupEnd()
              }
              %composer.endRestartGroup()?.updateScope { %composer: Composer?, %force: Int ->
                Test(%composer, updateChangedFlags(%changed or 0b0001))
              }
            }
        """
    )
}
