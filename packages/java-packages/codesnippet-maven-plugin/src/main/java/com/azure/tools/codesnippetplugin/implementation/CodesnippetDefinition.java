// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.tools.codesnippetplugin.implementation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents the definition of a codesnippet.
 */
final class CodesnippetDefinition {
    private final Path definitionFile;
    private final String alias;
    private final int beginLine;
    private final List<String> codesnippet;

    // End line isn't known at the time of creation.
    private int endLine;

    /**
     * Creates a new codesnippet definition.
     *
     * @param definitionFile File containing the codesnippet definition.
     * @param alias Alias of the codesnippet.
     * @param beginLine Line in the file where the codesnippet begins.
     */
    CodesnippetDefinition(Path definitionFile, String alias, int beginLine) {
        this.definitionFile = definitionFile;
        this.alias = alias;
        this.beginLine = beginLine;
        this.codesnippet = new ArrayList<>();
        this.endLine = -1;
    }

    void addCodesnippetLine(String line) {
        codesnippet.add(line);
    }

    void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    Path getDefinitionFile() {
        return definitionFile;
    }

    String getAlias() {
        return alias;
    }

    int getBeginLine() {
        return beginLine;
    }

    int getEndLine() {
        return endLine;
    }

    List<String> getCodesnippet() {
        return codesnippet;
    }

    @Override
    public int hashCode() {
        return Objects.hash(definitionFile, alias, beginLine, endLine, codesnippet);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CodesnippetDefinition)) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        CodesnippetDefinition other = (CodesnippetDefinition) obj;
        return Objects.equals(definitionFile, other.definitionFile)
            && Objects.equals(alias, other.alias)
            && beginLine == other.beginLine
            && endLine == other.endLine
            && Objects.equals(codesnippet, other.codesnippet);
    }
}
