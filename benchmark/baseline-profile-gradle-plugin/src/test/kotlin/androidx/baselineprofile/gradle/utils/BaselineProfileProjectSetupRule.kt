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

package androidx.baselineprofile.gradle.utils

import androidx.testutils.gradle.ProjectSetupRule
import com.android.build.api.AndroidPluginVersion
import com.google.testing.platform.proto.api.core.LabelProto
import com.google.testing.platform.proto.api.core.PathProto
import com.google.testing.platform.proto.api.core.TestArtifactProto
import com.google.testing.platform.proto.api.core.TestResultProto
import com.google.testing.platform.proto.api.core.TestStatusProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import java.io.File
import java.util.Properties
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.testkit.runner.GradleRunner
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.runner.Description
import org.junit.runners.model.Statement

internal const val ANDROID_APPLICATION_PLUGIN = "com.android.application"
internal const val ANDROID_LIBRARY_PLUGIN = "com.android.library"
internal const val ANDROID_TEST_PLUGIN = "com.android.test"

class BaselineProfileProjectSetupRule(
    private val forceAgpVersion: String? = null
) : ExternalResource() {

    /**
     * Root folder for the project setup that contains 3 modules.
     */
    val rootFolder = TemporaryFolder().also { it.create() }

    /**
     * Represents a module with the app target plugin applied.
     */
    val appTarget by lazy {
        AppTargetModule(
            rule = appTargetSetupRule,
            name = appTargetName,
        )
    }

    /**
     * Represents a module with the consumer plugin applied.
     */
    val consumer by lazy {
        ConsumerModule(
            rule = consumerSetupRule,
            name = consumerName,
            producerName = producerName
        )
    }

    /**
     * Represents a module with the producer plugin applied.
     */
    val producer by lazy {
        ProducerModule(
            rule = producerSetupRule,
            name = producerName,
            tempFolder = tempFolder,
            consumer = consumer
        )
    }

    // Temp folder for temp generated files that need to be referenced by a module.
    private val tempFolder by lazy { File(rootFolder.root, "temp").apply { mkdirs() } }

    // Project setup rules
    private val appTargetSetupRule by lazy { ProjectSetupRule(rootFolder.root) }
    private val consumerSetupRule by lazy { ProjectSetupRule(rootFolder.root) }
    private val producerSetupRule by lazy { ProjectSetupRule(rootFolder.root) }

    // Module names (generated automatically)
    private val appTargetName: String by lazy {
        appTargetSetupRule.rootDir.relativeTo(rootFolder.root).name
    }
    private val consumerName: String by lazy {
        consumerSetupRule.rootDir.relativeTo(rootFolder.root).name
    }
    private val producerName: String by lazy {
        producerSetupRule.rootDir.relativeTo(rootFolder.root).name
    }

    override fun apply(base: Statement, description: Description): Statement {
        return RuleChain
            .outerRule(appTargetSetupRule)
            .around(producerSetupRule)
            .around(consumerSetupRule)
            .around { b, _ -> applyInternal(b) }
            .apply(base, description)
    }

    private fun applyInternal(base: Statement) = object : Statement() {
        override fun evaluate() {

            // Creates the main gradle.properties
            rootFolder.newFile("gradle.properties").writer().use {
                val props = Properties()
                props.setProperty(
                    "org.gradle.jvmargs", "-Xmx2g -XX:+UseParallelGC -XX:MaxMetaspaceSize=512m"
                )
                props.setProperty("android.useAndroidX", "true")
                props.store(it, null)
            }

            // Creates the main settings.gradle
            rootFolder.newFile("settings.gradle").writeText(
                """
                include '$appTargetName'
                include '$producerName'
                include '$consumerName'
            """.trimIndent()
            )

            val repositoriesBlock = """
                repositories {
                    ${producerSetupRule.allRepositoryPaths.joinToString("\n") { """ maven { url "$it" } """ }}
                }
            """.trimIndent()

            val agpDependency = if (forceAgpVersion == null) {
                """"${appTargetSetupRule.props.agpDependency}""""
            } else {
                """
                    ("com.android.tools.build:gradle") { version { strictly "$forceAgpVersion" } }
                    """.trimIndent()
            }
            rootFolder.newFile("build.gradle").writeText(
                """
                buildscript {
                    $repositoriesBlock
                    dependencies {

                        // Specifies agp dependency
                        classpath $agpDependency

                        // Specifies plugin dependency
                        classpath "androidx.baselineprofile.consumer:androidx.baselineprofile.consumer.gradle.plugin:+"
                        classpath "androidx.baselineprofile.producer:androidx.baselineprofile.producer.gradle.plugin:+"
                        classpath "androidx.baselineprofile.apptarget:androidx.baselineprofile.apptarget.gradle.plugin:+"
                    }
                }

                allprojects {
                    $repositoriesBlock
                }

            """.trimIndent()
            )

            // Copies test project data
            mapOf(
                "app-target" to appTargetSetupRule,
                "consumer" to consumerSetupRule,
                "producer" to producerSetupRule
            ).forEach { (folder, project) ->
                File("src/test/test-data", folder)
                    .apply { deleteOnExit() }
                    .copyRecursively(project.rootDir)
            }

            base.evaluate()
        }
    }

    private fun AndroidPluginVersion.versionString(): String {
        val preview = if (!previewType.isNullOrBlank()) {
            "-$previewType${"%02d".format(preview)}"
        } else {
            ""
        }
        return "$major.$minor.$micro$preview"
    }
}

data class VariantProfile(
    val flavor: String?,
    val buildType: String = "release",
    val profileFileLines: Map<String, List<String>> = mapOf(),
    val startupFileLines: Map<String, List<String>> = mapOf()
) {
    val nonMinifiedVariant = "${flavor ?: ""}NonMinified${buildType.capitalized()}"
}

interface Module {

    val name: String
    val rule: ProjectSetupRule
    val rootDir: File
        get() = rule.rootDir
    val gradleRunner: GradleRunner
        get() = GradleRunner.create().withProjectDir(rule.rootDir)

    fun setBuildGradle(buildGradleContent: String) =
        rule.writeDefaultBuildGradle(
            prefix = buildGradleContent,
            suffix = """
                $GRADLE_CODE_PRINT_TASK
            """.trimIndent()
        )
}

class AppTargetModule(
    override val rule: ProjectSetupRule,
    override val name: String,
) : Module {

    fun setup() {
        setBuildGradle(
            """
                plugins {
                    id("com.android.application")
                    id("androidx.baselineprofile.apptarget")
                }
                android {
                    namespace 'com.example.namespace'
                }
            """.trimIndent()
        )
    }
}

class ProducerModule(
    override val rule: ProjectSetupRule,
    override val name: String,
    private val tempFolder: File,
    private val consumer: Module
) : Module {

    fun setupWithFreeAndPaidFlavors(
        freeReleaseProfileLines: List<String>,
        paidReleaseProfileLines: List<String>,
        freeReleaseStartupProfileLines: List<String> = listOf(),
        paidReleaseStartupProfileLines: List<String> = listOf(),
    ) {
        setup(
            variantProfiles = listOf(
                VariantProfile(
                    flavor = "free",
                    buildType = "release",
                    profileFileLines = mapOf("myTest" to freeReleaseProfileLines),
                    startupFileLines = mapOf("myStartupTest" to freeReleaseStartupProfileLines)
                ),
                VariantProfile(
                    flavor = "paid",
                    buildType = "release",
                    profileFileLines = mapOf("myTest" to paidReleaseProfileLines),
                    startupFileLines = mapOf("myStartupTest" to paidReleaseStartupProfileLines)
                ),
            )
        )
    }

    fun setupWithoutFlavors(
        releaseProfileLines: List<String>,
        releaseStartupProfileLines: List<String> = listOf(),
    ) {
        setup(
            variantProfiles = listOf(
                VariantProfile(
                    flavor = null,
                    buildType = "release",
                    profileFileLines = mapOf("myTest" to releaseProfileLines),
                    startupFileLines = mapOf("myStartupTest" to releaseStartupProfileLines)
                )
            )
        )
    }

    fun setup(
        variantProfiles: List<VariantProfile> = listOf(
            VariantProfile(
                flavor = null,
                buildType = "release",
                profileFileLines = mapOf(
                    "myTest" to listOf(
                        Fixtures.CLASS_1_METHOD_1,
                        Fixtures.CLASS_2_METHOD_2,
                        Fixtures.CLASS_2,
                        Fixtures.CLASS_1
                    )
                ),
                startupFileLines = mapOf(
                    "myStartupTest" to listOf(
                        Fixtures.CLASS_3_METHOD_1,
                        Fixtures.CLASS_4_METHOD_1,
                        Fixtures.CLASS_3,
                        Fixtures.CLASS_4
                    )
                ),
            )
        ),
        baselineProfileBlock: String = "",
        additionalGradleCodeBlock: String = "",
        targetProject: Module = consumer,
        managedDevices: List<String> = listOf()
    ) {
        val managedDevicesBlock = """
            testOptions.managedDevices.devices {
            ${
            managedDevices.joinToString("\n") {
                """
                $it(ManagedVirtualDevice) {
                    device = "Pixel 6"
                    apiLevel = 31
                    systemImageSource = "aosp"
                }

            """.trimIndent()
            }
        }
            }
        """.trimIndent()

        val flavorsBlock = """
            productFlavors {
                flavorDimensions = ["version"]
                ${
            variantProfiles
                .filter { !it.flavor.isNullOrBlank() }
                .joinToString("\n") { " ${it.flavor} { dimension \"version\" } " }
        }
            }
        """.trimIndent()

        val buildTypesBlock = """
            buildTypes {
                ${
            variantProfiles
                .filter { it.buildType.isNotBlank() && it.buildType != "release" }
                .joinToString("\n") { " ${it.buildType} { initWith(debug) } " }
        }
            }
        """.trimIndent()

        val disableConnectedAndroidTestsBlock = variantProfiles.joinToString("\n") {

            // Creates a folder to use as results dir
            val variantOutputDir = File(tempFolder, it.nonMinifiedVariant)
            val testResultsOutputDir =
                File(variantOutputDir, "testResultsOutDir").apply { mkdirs() }
            val profilesOutputDir =
                File(variantOutputDir, "profilesOutputDir").apply { mkdirs() }

            // Writes the fake test result proto in it, with the given lines
            writeFakeTestResultsProto(
                testResultsOutputDir = testResultsOutputDir,
                profilesOutputDir = profilesOutputDir,
                profileFileLines = it.profileFileLines,
                startupFileLines = it.startupFileLines
            )

            // Gradle script to injects a fake and disable the actual task execution for
            // android test
            """
            afterEvaluate {
                project.tasks.named("connected${it.nonMinifiedVariant.capitalized()}AndroidTest") {
                    it.resultsDir.set(new File("${testResultsOutputDir.absolutePath}"))
                    onlyIf { false }
                }
            }

                """.trimIndent()
        }

        setBuildGradle(
            """
                import com.android.build.api.dsl.ManagedVirtualDevice

                plugins {
                    id("com.android.test")
                    id("androidx.baselineprofile.producer")
                }

                android {
                    $flavorsBlock

                    $buildTypesBlock

                    $managedDevicesBlock

                    namespace 'com.example.namespace.test'
                    targetProjectPath = ":${targetProject.name}"
                }

                dependencies {
                }

                baselineProfile {
                    $baselineProfileBlock
                }

                $disableConnectedAndroidTestsBlock

                $additionalGradleCodeBlock

            """.trimIndent()
        )
    }

    private fun writeFakeTestResultsProto(
        testResultsOutputDir: File,
        profilesOutputDir: File,
        profileFileLines: Map<String, List<String>>,
        startupFileLines: Map<String, List<String>>
    ) {

        val testResultProtoBuilder = TestResultProto.TestResult.newBuilder()

        // This function writes a profile file for each key of the map, containing for lines
        // the strings in the list in the value.
        val writeProfiles: (Map<String, List<String>>, String) -> (Unit) = { map, fileNamePart ->
            map.forEach {

                val fakeProfileFile = File(
                    profilesOutputDir,
                    "fake-$fileNamePart-${it.key}.txt"
                ).apply { writeText(it.value.joinToString(System.lineSeparator())) }

                testResultProtoBuilder.addOutputArtifact(
                    TestArtifactProto.Artifact.newBuilder()
                        .setLabel(
                            LabelProto.Label.newBuilder()
                                .setLabel("additionaltestoutput.benchmark.trace")
                                .build()
                        )
                        .setSourcePath(
                            PathProto.Path.newBuilder()
                                .setPath(fakeProfileFile.absolutePath)
                                .build()
                        )
                        .build()
                )
            }
        }

        writeProfiles(profileFileLines, "baseline-prof")
        writeProfiles(startupFileLines, "startup-prof")

        val testSuiteResultProto = TestSuiteResultProto.TestSuiteResult.newBuilder()
            .setTestStatus(TestStatusProto.TestStatus.PASSED)
            .addTestResult(testResultProtoBuilder.build())
            .build()

        File(testResultsOutputDir, "test-result.pb")
            .apply { outputStream().use { testSuiteResultProto.writeTo(it) } }
    }
}

class ConsumerModule(
    override val rule: ProjectSetupRule,
    override val name: String,
    private val producerName: String
) : Module {

    fun setup(
        androidPlugin: String,
        flavors: Boolean = false,
        dependencyOnProducerProject: Boolean = true,
        buildTypeAnotherRelease: Boolean = false,
        addAppTargetPlugin: Boolean = androidPlugin == ANDROID_APPLICATION_PLUGIN,
        baselineProfileBlock: String = "",
        additionalGradleCodeBlock: String = "",
    ) {
        val flavorsBlock = """
            productFlavors {
                flavorDimensions = ["version"]
                free { dimension "version" }
                paid { dimension "version" }
            }

        """.trimIndent()

        val buildTypeAnotherReleaseBlock = """
            buildTypes {
                anotherRelease { initWith(release) }
            }

        """.trimIndent()

        val dependencyOnProducerProjectBlock = """
            dependencies {
                baselineProfile(project(":$producerName"))
            }

        """.trimIndent()

        setBuildGradle(
            """
                plugins {
                    id("$androidPlugin")
                    id("androidx.baselineprofile.consumer")
                    ${if (addAppTargetPlugin) "id(\"androidx.baselineprofile.apptarget\")" else ""}
                }
                android {
                    namespace 'com.example.namespace'
                    ${if (flavors) flavorsBlock else ""}
                    ${if (buildTypeAnotherRelease) buildTypeAnotherReleaseBlock else ""}
                }

               ${if (dependencyOnProducerProject) dependencyOnProducerProjectBlock else ""}

                baselineProfile {
                    $baselineProfileBlock
                }

                $additionalGradleCodeBlock

            """.trimIndent()
        )
    }
}
