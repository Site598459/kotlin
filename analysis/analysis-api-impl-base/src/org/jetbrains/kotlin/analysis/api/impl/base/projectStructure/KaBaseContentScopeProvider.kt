/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.impl.base.projectStructure

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaContentScopeProvider
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinContentScopeRefiner
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinGlobalSearchScopeMerger
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule

internal class KaBaseContentScopeProvider : KaContentScopeProvider {
    @OptIn(KaPlatformInterface::class)
    override fun getRefinedContentScope(module: KaModule): GlobalSearchScope {
        val baseContentScope = module.baseContentScope

        val refiners = KotlinContentScopeRefiner.getRefiners(module.project).ifEmpty {
            return baseContentScope
        }

        val enlargementScopes = mutableListOf(baseContentScope)
        val restrictionScopes = mutableListOf<GlobalSearchScope>()

        refiners.forEach { refiner ->
            enlargementScopes.addAll(
                refiner.getEnlargementScopes(module).filter { !GlobalSearchScope.isEmptyScope(it) }
            )

            restrictionScopes.addAll(
                refiner.getRestrictionScopes(module).filter { !GlobalSearchScope.isEmptyScope(it) }
            )
        }

        return mergeScopes(module, enlargementScopes, restrictionScopes)
    }

    private fun mergeScopes(
        module: KaModule,
        enlargementScopes: MutableList<GlobalSearchScope>,
        restrictionScopes: MutableList<GlobalSearchScope>,
    ): GlobalSearchScope {
        val scopeMerger = KotlinGlobalSearchScopeMerger.getInstance(module.project)

        val mergedEnlargementScope = scopeMerger.union(enlargementScopes)
        if (restrictionScopes.isEmpty()) {
            return mergedEnlargementScope
        }

        val mergedRestrictionScope = scopeMerger.union(restrictionScopes)
        return mergedEnlargementScope.intersectWith(mergedRestrictionScope)
    }
}
