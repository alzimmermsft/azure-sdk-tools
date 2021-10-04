// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.tools.codesnippetplugin.implementation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Results of injecting codesnippets.
 */
final class InjectionResult {
    private final StringBuilder injectionUpdates;
    private final boolean updateSourceFile;
    private final List<VerifyResult> missingCodesnippets;
    private final List<VerifyResult> badCodesnippets;

    /**
     * Creates the results of injecting codesnippets.
     *
     * @param injectionUpdates The updated source file after codesnippet injections.
     * @param updateSourceFile Flag indicating if codesnippets were injected into the source file. If they weren't then
     * {@link #updateSourceFile(Path)} becomes a no-op.
     * @param missingCodesnippets Codesnippets that the source file references but don't exist.
     * @param badCodesnippets Codesnippets that were injected but didn't match the codesnippet definition.
     */
    InjectionResult(StringBuilder injectionUpdates, boolean updateSourceFile,
        List<VerifyResult> missingCodesnippets, List<VerifyResult> badCodesnippets) {
        this.injectionUpdates = injectionUpdates;
        this.updateSourceFile = updateSourceFile;
        this.missingCodesnippets = missingCodesnippets;
        this.badCodesnippets = badCodesnippets;
    }

    /**
     * Updates the source file with the injected codesnippets.
     *
     * @param sourceFile The source file.
     * @throws IOException If an I/O exception occurs while updating the source file.
     */
    void updateSourceFile(Path sourceFile) throws IOException {
        if (updateSourceFile) {
            try (BufferedWriter writer = Files.newBufferedWriter(sourceFile, StandardCharsets.UTF_8)) {
                writer.write(injectionUpdates.toString());
            }
        }
    }

    /**
     * Gets the string value of the source file after being updated with codesnippet injections.
     *
     * @return The string value of the source file after being updated with codesnippet injections.
     */
    StringBuilder getInjectionUpdates() {
        return injectionUpdates;
    }

    /**
     * Gets the flag indicating if codesnippets were injected and the source file needs to be re-written.
     *
     * @return Flag indicating if codesnippets were injected and the source file needs to be re-written.
     */
    boolean isUpdateSourceFile() {
        return updateSourceFile;
    }

    /**
     * Gets the missing codesnippets verification results.
     *
     * @return The missing codesnippets verification results.
     */
    List<VerifyResult> getMissingCodesnippets() {
        return missingCodesnippets;
    }

    /**
     * Gets the bad codesnippets verification results.
     *
     * @return The bad codesnippets verification results.
     */
    List<VerifyResult> getBadCodesnippets() {
        return badCodesnippets;
    }
}
