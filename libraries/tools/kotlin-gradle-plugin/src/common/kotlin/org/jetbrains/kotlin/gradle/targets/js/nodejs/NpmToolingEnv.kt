/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.nodejs

import org.jetbrains.kotlin.gradle.tasks.internal.CleanableStore
import java.io.File

open class NpmToolingEnv(
    val cleanableStore: CleanableStore,
    val version: String,
    val dir: File,
)