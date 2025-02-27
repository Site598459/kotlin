/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.diagnostics

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.problems.*
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.jetbrains.kotlin.gradle.plugin.VariantImplementationFactories
import org.jetbrains.kotlin.gradle.utils.newInstance
import javax.inject.Inject

internal interface ProblemsReporter {
    fun reportProblemDiagnostic(diagnostic: ToolingDiagnostic)

    interface Factory : VariantImplementationFactories.VariantImplementationFactory {
        fun getInstance(objects: ObjectFactory): ProblemsReporter
    }
}

internal fun ProblemReporter.report(diagnostic: ToolingDiagnostic, fillSpec: (ProblemSpec) -> ProblemSpec) {
    if (diagnostic.throwable != null) {
        throwing { fillSpec(it) }
    } else {
        reporting(diagnostic::configureProblemSpec)
    }
}

internal abstract class DefaultProblemsReporter @Inject constructor(
    private val problems: Problems,
) : BuildService<BuildServiceParameters.None>, ProblemsReporter {
    override fun reportProblemDiagnostic(diagnostic: ToolingDiagnostic) {
        problems.reporter.report(diagnostic) { fillSpec(it, diagnostic) }
    }

    private fun fillSpec(spec: ProblemSpec, diagnostic: ToolingDiagnostic): ProblemSpec {
        return spec
            .id(diagnostic.id, diagnostic.identifier.displayName, problemGroup(diagnostic.group))
            .details(diagnostic.message)
            .severity(diagnostic.severity.problemSeverity)
            .apply { diagnostic.configureProblemSpec(this) }
    }

    private fun problemGroup(group: DiagnosticGroup): ProblemGroup = KGPProblemGroup(group)

    class Factory : ProblemsReporter.Factory {
        override fun getInstance(objects: ObjectFactory) = objects.newInstance<DefaultProblemsReporter>()
    }
}

// Create own implementation of ProblemGroup. In gradle 18.3 there will be a static factory method for creating ProblemGroup
internal class KGPProblemGroup(val group: DiagnosticGroup) : ProblemGroup {
    override fun getName() = group.groupId
    override fun getDisplayName() = group.displayName
    override fun getParent() = group.parent?.let { KGPProblemGroup(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProblemGroup) return false

        if (getName() != other.name) return false
        if (getParent() != other.parent) return false

        return true
    }

    override fun hashCode(): Int {
        var result = getName().hashCode()
        result = 31 * result + (getParent()?.hashCode() ?: 0)
        return result
    }
}

internal fun ToolingDiagnostic.configureProblemSpec(spec: ProblemSpec): ProblemSpec {
    var mSpec = spec
    solutions.forEach {
        mSpec = mSpec.solution(it)
    }

    documentation?.let {
        mSpec = mSpec.documentedAt(it.url)
    }

    throwable?.let {
        mSpec = mSpec.withException(InvalidUserCodeException(identifier.displayName, it))
    }

    return mSpec
}

internal val ToolingDiagnostic.Severity.problemSeverity: Severity
    get() = when (this) {
        ToolingDiagnostic.Severity.WARNING -> Severity.WARNING
        else -> Severity.ERROR
    }
