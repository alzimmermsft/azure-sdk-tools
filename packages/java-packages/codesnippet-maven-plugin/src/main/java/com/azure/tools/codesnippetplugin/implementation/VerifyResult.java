// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.tools.codesnippetplugin.implementation;

import java.nio.file.Path;

/**
 * Verification result for a codesnippet.
 */
final class VerifyResult {
    private final Path sourceFile;
    private final int lineNumber;
    private final String codesnippetAlias;

    /**
     * Creates a verification result for a codesnippet injection.
     *
     * @param sourceFile Source file being injected.
     * @param lineNumber Line number for the codesnippet injection.
     * @param codesnippetAlias Alias of the codesnippet being injected.
     */
    VerifyResult(Path sourceFile, int lineNumber, String codesnippetAlias) {
        this.sourceFile = sourceFile;
        this.lineNumber = lineNumber;
        this.codesnippetAlias = codesnippetAlias;
    }

    /**
     * Gets the source file being injected.
     *
     * @return The source file being injected.
     */
    Path getSourceFile() {
        return sourceFile;
    }

    /**
     * Gets the line number where the codesnippet is being injected.
     *
     * @return The line number where the codesnippet is being injected.
     */
    int getLineNumber() {
        return lineNumber;
    }

    /**
     * Gets the alias of the codesnippet being injected.
     *
     * @return The alias of the codesnippet being injected.
     */
    String getCodesnippetAlias() {
        return codesnippetAlias;
    }
}