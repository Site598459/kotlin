/*
 * Copyright 2000-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kotlinp.jvm.test

import org.jetbrains.kotlin.kotlinp.Settings
import org.jetbrains.kotlin.kotlinp.jvm.JvmKotlinp
import org.jetbrains.kotlin.kotlinp.jvm.readKotlinClassHeader
import org.jetbrains.kotlin.test.Constructor
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.backend.BlackBoxCodegenSuppressor
import org.jetbrains.kotlin.test.backend.handlers.JvmBinaryArtifactHandler
import org.jetbrains.kotlin.test.backend.ir.BackendCliJvmFacade
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.backend.ir.JvmIrBackendFacade
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.builders.configureClassicFrontendHandlersStep
import org.jetbrains.kotlin.test.builders.configureFirHandlersStep
import org.jetbrains.kotlin.test.builders.configureJvmArtifactsHandlersStep
import org.jetbrains.kotlin.test.configuration.commonClassicFrontendHandlersForCodegenTest
import org.jetbrains.kotlin.test.configuration.commonConfigurationForJvmTest
import org.jetbrains.kotlin.test.configuration.commonFirHandlersForCodegenTest
import org.jetbrains.kotlin.test.directives.ModuleStructureDirectives
import org.jetbrains.kotlin.test.directives.configureFirParser
import org.jetbrains.kotlin.test.frontend.classic.ClassicFrontend2IrConverter
import org.jetbrains.kotlin.test.frontend.classic.ClassicFrontendFacade
import org.jetbrains.kotlin.test.frontend.classic.ClassicFrontendOutputArtifact
import org.jetbrains.kotlin.test.frontend.fir.Fir2IrCliJvmFacade
import org.jetbrains.kotlin.test.frontend.fir.FirCliJvmFacade
import org.jetbrains.kotlin.test.frontend.fir.FirOutputArtifact
import org.jetbrains.kotlin.test.model.*
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerTest
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.defaultsProvider
import org.jetbrains.kotlin.test.services.moduleStructure
import org.jetbrains.kotlin.test.utils.MultiModuleInfoDumper
import org.jetbrains.kotlin.test.utils.withExtension
import org.jetbrains.org.objectweb.asm.ClassReader
import java.io.File
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.KotlinModuleMetadata
import kotlin.metadata.jvm.UnstableMetadataApi
import kotlin.test.fail

abstract class AbstractKotlinpTest<R : ResultingArtifact.FrontendOutput<R>>(
    val targetFrontend: FrontendKind<R>,
) : AbstractKotlinCompilerTest() {
    abstract val frontendFacade: Constructor<FrontendFacade<R>>
    abstract val frontendToBackendConverter: Constructor<Frontend2BackendConverter<R, IrBackendInput>>
    abstract val backendFacade: Constructor<BackendFacade<IrBackendInput, BinaryArtifacts.Jvm>>

    override fun configure(builder: TestConfigurationBuilder) = with(builder) {
        globalDefaults {
            targetBackend = TargetBackend.JVM_IR
        }

        defaultDirectives {
            ModuleStructureDirectives.MODULE with "test-module"
        }

        commonConfigurationForJvmTest(targetFrontend, frontendFacade, frontendToBackendConverter, backendFacade)
        configureFirParser(FirParser.LightTree)

        configureClassicFrontendHandlersStep {
            commonClassicFrontendHandlersForCodegenTest()
        }
        configureFirHandlersStep {
            commonFirHandlersForCodegenTest()
        }

        configureJvmArtifactsHandlersStep {
            useHandlers({ CompareMetadataHandler(it, verbose = true) })
        }
        useAfterAnalysisCheckers(::BlackBoxCodegenSuppressor)
    }
}

abstract class AbstractK1KotlinpTest : AbstractKotlinpTest<ClassicFrontendOutputArtifact>(FrontendKinds.ClassicFrontend) {
    override val frontendFacade: Constructor<FrontendFacade<ClassicFrontendOutputArtifact>>
        get() = ::ClassicFrontendFacade
    override val frontendToBackendConverter: Constructor<Frontend2BackendConverter<ClassicFrontendOutputArtifact, IrBackendInput>>
        get() = ::ClassicFrontend2IrConverter
    override val backendFacade: Constructor<BackendFacade<IrBackendInput, BinaryArtifacts.Jvm>>
        get() = ::JvmIrBackendFacade
}

abstract class AbstractK2KotlinpTest : AbstractKotlinpTest<FirOutputArtifact>(FrontendKinds.FIR) {
    override val frontendFacade: Constructor<FrontendFacade<FirOutputArtifact>>
        get() = ::FirCliJvmFacade
    override val frontendToBackendConverter: Constructor<Frontend2BackendConverter<FirOutputArtifact, IrBackendInput>>
        get() = ::Fir2IrCliJvmFacade
    override val backendFacade: Constructor<BackendFacade<IrBackendInput, BinaryArtifacts.Jvm>>
        get() = ::BackendCliJvmFacade
}

class CompareMetadataHandler(
    testServices: TestServices,
    private val extension: String = ".txt",
    private val verbose: Boolean = false,
) : JvmBinaryArtifactHandler(testServices) {
    private val multiModuleInfoDumper = MultiModuleInfoDumper()

    override fun processModule(module: TestModule, info: BinaryArtifacts.Jvm) {
        val kotlinp = JvmKotlinp(Settings(isVerbose = verbose, sortDeclarations = true))

        // TODO: produce read-write-read dump

        multiModuleInfoDumper.builderForModule(module).append(buildString {
            for (outputFile in info.classFileFactory.asList().sortedBy { File(it.relativePath).nameWithoutExtension }) {
                val path = outputFile.relativePath
                appendLine("// $path")
                appendLine("// ------------------------------------------")
                when {
                    path.endsWith(".class") -> {
                        val metadata = ClassReader(outputFile.asByteArray().inputStream()).readKotlinClassHeader()!!
                        append(kotlinp.printClassFile(KotlinClassMetadata.readStrict(metadata)))
                    }
                    path.endsWith(".kotlin_module") -> {
                        @OptIn(UnstableMetadataApi::class)
                        append(kotlinp.printModuleFile(KotlinModuleMetadata.read(outputFile.asByteArray())))
                    }
                    else -> {
                        fail("Unknown file: $outputFile")
                    }
                }
            }
        })
    }

    override fun processAfterAllModules(someAssertionWasFailed: Boolean) {
        val sourceFile = testServices.moduleStructure.originalTestDataFiles.first()
        val defaultTxtFile = sourceFile.withExtension(extension)
        val firTxtFile = sourceFile.withExtension(".fir$extension")
        val isFir = testServices.defaultsProvider.frontendKind == FrontendKinds.FIR
        val actualFile = firTxtFile.takeIf { isFir && it.exists() } ?: defaultTxtFile
        assertions.assertEqualsToFile(actualFile, multiModuleInfoDumper.generateResultingDump())
    }
}
