// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.tools.codesnippetplugin.implementation;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Maintains the known codesnippets in a run.
 */
final class SnippetDictionary {
    private final Map<String, CodesnippetDefinition> snippetDictionary = new HashMap<>();

    boolean isActive() {
        return !snippetDictionary.isEmpty();
    }

    void beginSnippet(Path definitionFile, String alias, int beginLine) {
        if (!this.snippetDictionary.containsKey(alias)) {
            this.snippetDictionary.put(alias, new CodesnippetDefinition(definitionFile, alias, beginLine));
        }
    }

    void processLine(String line) {
        snippetDictionary.values().forEach(codesnippetDefinition -> codesnippetDefinition.addCodesnippetLine(line));
    }

    CodesnippetDefinition finalizeSnippet(String alias, int endLine) {
        CodesnippetDefinition value = this.snippetDictionary.get(alias);
        this.snippetDictionary.remove(alias);

        value.setEndLine(endLine);
        return value;
    }
}
