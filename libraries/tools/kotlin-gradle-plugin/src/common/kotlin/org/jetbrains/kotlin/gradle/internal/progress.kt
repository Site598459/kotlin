/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.internal

import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory


internal fun ProgressLoggerFactory.newBuildOpLogger(): ProgressLogger =
    newOperation(ProgressStartEvent.BUILD_OP_CATEGORY)

internal fun <T> ProgressLogger.operation(
    description: String,
    initialStatus: String? = null,
    body: ProgressLogger.() -> T,
): T {
    start(description, initialStatus)
    try {
        return body(this)
    } finally {
        completed()
    }
}
