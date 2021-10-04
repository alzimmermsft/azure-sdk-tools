// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.tools.codesnippetplugin.implementation;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SnippetReplacer {
    private static final String LINE_SEPARATOR = System.lineSeparator();

    static final Pattern SNIPPET_DEF_BEGIN =
        Pattern.compile("\\s*//\\s*BEGIN:\\s*([a-zA-Z0-9.#\\-_]*)\\s*");
    static final Pattern SNIPPET_DEF_END = Pattern.compile("\\s*//\\s*END:\\s*([a-zA-Z0-9.#\\-_]*)\\s*");
    static final Pattern SNIPPET_SRC_CALL_BEGIN =
        Pattern.compile("(\\s*)\\*\\s*<!--\\s*src_embed\\s+([a-zA-Z0-9.#\\-_]*)\\s*-->");
    static final Pattern SNIPPET_SRC_CALL_END =
        Pattern.compile("(\\s*)\\*\\s*<!--\\s*end\\s+([a-zA-Z0-9.#\\-_]*)\\s*-->");
    static final Pattern SNIPPET_README_CALL_BEGIN = Pattern.compile("```(\\s*)?java\\s+([a-zA-Z0-9.#\\-_]*)\\s*");
    static final Pattern SNIPPET_README_CALL_END = Pattern.compile("```");
    static final Pattern WHITESPACE_EXTRACTION = Pattern.compile("(\\s*)(.*)");
    static final Pattern END_OF_LINE_WHITESPACES = Pattern.compile("[\\s]+$");

    // Ordering matters. If the ampersand (&) isn't done first it will double encode ampersands used in other
    // replacements.
    private static final Map<String, String> REPLACEMENT_SET = new LinkedHashMap<String, String>() {{
        put("&", "&amp;");
        put("\"", "&quot;");
        put(">", "&gt;");
        put("<", "&lt;");
        put("@", "&#64;");
        put("{", "&#123;");
        put("}", "&#125;");
        put("(", "&#40;");
        put(")", "&#41;");
        put("/", "&#47;");
        put("\\", "&#92;");
    }};

    /**
     * Finds codesnippet definitions and injects them into Javadocs and READMEs.
     * <p>
     * This method effectively updates and verifies codesnippet injections. First, it finds all codesnippet definitions
     * found in the files that match {@code codesnippetGlob} contained in the directory {@code
     * codesnippetRootDirectory}. Codesnippet definitions are denoted by  {@code BEGIN <alias>} and {@code END <alias>}
     * single line comments.
     *
     * <p><strong>Codesnippet Example</strong></p>
     *
     * <pre>
     * // BEGIN: com.azure.data.applicationconfig.configurationclient.instantiation
     * ConfigurationClient configurationClient = new ConfigurationClientBuilder&#40;&#41;
     *     .connectionString&#40;connectionString&#41;
     *     .buildClient&#40;&#41;;
     * // END: com.azure.data.applicationconfig.configurationclient.instantiation
     * </pre>
     *
     * <p>
     * Then, all files that match {@code sourcesGlob} contained in the directory {@code sourcesRootDirectory} and the
     * README file {@code readmePath} have codesnippets injected based on the matching aliases. Javadocs and READMEs use
     * different codesnippet injection patterns, Javadocs use {@code <!-- src_embed <alias> -->} and {@code <!-- end
     * <alias> -->} and READMEs use {@code ```java <alias> ```}.
     *
     * <p><strong>Javadoc Example</strong></p>
     *
     * <pre>
     * <!-- src_embed com.azure.data.applicationconfig.configurationclient.instantiation -->
     * <!-- end com.azure.data.applicationconfig.configurationclient.instantiation -->
     * </pre>
     *
     * <p><strong>README Example</strong></p>
     *
     * <pre>
     * ```java com.azure.data.applicationconfig.configurationclient.instantiation
     * ```
     * </pre>
     *
     * <p>
     * Finally, at the end of injecting codesnippets a validation check will be made to ensure that all codesnippet
     * replacement tags were matched by codesnippet definition tags. If there were any missing codesnippet tags the
     * plugin will fail.
     * <p>
     * If {@code includeSources} and {@code includeReadme} are both false this call is effectively a no-op.
     */
    public static void injectCodesnippets(Path codesnippetRootDirectory, String codesnippetGlob,
        Path sourcesRootDirectory, String sourcesGlob, boolean includeSources, Path readmePath, boolean includeReadme,
        int maxLineLength, Log logger) throws IOException, MojoExecutionException {
        // Neither sources nor README is included in the update, there is no work to be done.
        if (!includeSources && !includeReadme) {
            logger.debug("Neither sources or README were included. No codesnippet updating will be done.");
            return;
        }

        // Get the files that match the codesnippet glob and are contained in the codesnippet root directory.
        List<Path> codesnippetFiles = globFiles(codesnippetRootDirectory, codesnippetGlob, false);

        // Scan the codesnippet files for all codesnippet definitions.
        Map<String, CodesnippetDefinition> foundSnippets = getAllSnippets(codesnippetFiles);

        // Maintain the known missing and bad codesnippets.
        // Bad codesnippets are those that don't match the expected rules, such as max line length.
        List<VerifyResult> missingCodesnippets = new ArrayList<>();
        List<VerifyResult> badCodesnippets = new ArrayList<>();

        // Only get the source files if sources are included in the injection.
        if (includeSources) {
            // Get the files that match the sources glob and are contained in the sources root directory.
            for (Path sourceFile : globFiles(sourcesRootDirectory, sourcesGlob, true)) {
                // Process the source file and replace all codesnippet replacement tags with their definition.
                InjectionResult sourceInjectionResult = injectSnippets(sourceFile,
                    SNIPPET_SRC_CALL_BEGIN, SNIPPET_SRC_CALL_END, foundSnippets, "<pre>", "</pre>", 1, "* ", false);
                sourceInjectionResult.updateSourceFile(sourceFile);
                missingCodesnippets.addAll(sourceInjectionResult.getMissingCodesnippets());
                badCodesnippets.addAll(sourceInjectionResult.getBadCodesnippets());
            }
        }

        // Only process the README if the README is included in the injection.
        if (includeReadme) {
            // Process the README and replace all codesnippet replacement tags with their definition.
            InjectionResult readmeInjectionResult = injectSnippets(readmePath,
                SNIPPET_README_CALL_BEGIN, SNIPPET_README_CALL_END, foundSnippets, "", "", 0, "", true);
            readmeInjectionResult.updateSourceFile(readmePath);
            missingCodesnippets.addAll(readmeInjectionResult.getMissingCodesnippets());
            badCodesnippets.addAll(readmeInjectionResult.getBadCodesnippets());
        }

        if (missingCodesnippets.size() > 0 || badCodesnippets.size() > 0) {
            StringBuilder errorMessage = new StringBuilder();

            for (VerifyResult result : missingCodesnippets) {
                errorMessage.append("Unable to locate codesnippet with alias '")
                    .append(result.getCodesnippetAlias())
                    .append("' referenced in '")
                    .append(result.getSourceFile())
                    .append(":")
                    .append(result.getLineNumber())
                    .append("'.")
                    .append(LINE_SEPARATOR);
            }

            for (VerifyResult result : badCodesnippets) {
                errorMessage.append("Codesnippet with alias '")
                    .append(result.getCodesnippetAlias())
                    .append("' referenced in '")
                    .append(result.getSourceFile())
                    .append(":")
                    .append(result.getLineNumber())
                    .append("' didn't follow codesnippet rules.")
                    .append(LINE_SEPARATOR);
            }

            throw new MojoExecutionException("Codesnippet injection encountered errors:" + LINE_SEPARATOR
                + errorMessage);
        }
    }

    static Map<String, CodesnippetDefinition> getAllSnippets(List<Path> snippetSources)
        throws IOException, MojoExecutionException {
        Map<String, CodesnippetDefinition> locatedSnippets = new HashMap<>();

        Set<CodesnippetDefinition> duplicateCodesnippets = new HashSet<>();

        for (Path samplePath : snippetSources) {
            List<String> fileContent = Files.readAllLines(samplePath, StandardCharsets.UTF_8);
            Map<String, CodesnippetDefinition> tempSnippetMap = new HashMap<>();
            SnippetDictionary snippetReader = new SnippetDictionary();

            forEachLine(fileContent, (lineNumber, line) -> {
                Matcher begin = SNIPPET_DEF_BEGIN.matcher(line);
                Matcher end = SNIPPET_DEF_END.matcher(line);

                if (begin.matches()) {
                    snippetReader.beginSnippet(samplePath, begin.group(1), lineNumber);
                } else if (end.matches()) {
                    String id_ending = end.group(1);
                    CodesnippetDefinition snippetContent = snippetReader.finalizeSnippet(id_ending, lineNumber);
                    if (!tempSnippetMap.containsKey(id_ending)) {
                        tempSnippetMap.put(id_ending, snippetContent);
                    } else {
                        // detect duplicate in file
                        duplicateCodesnippets.add(tempSnippetMap.get(id_ending));
                        duplicateCodesnippets.add(snippetContent);
                    }
                } else if (snippetReader.isActive()) {
                    snippetReader.processLine(line);
                }
            });

            // we need to examine them individually, as we want to get a complete list of all the duplicates in a run
            for (String snippetId : tempSnippetMap.keySet()) {
                if (!locatedSnippets.containsKey(snippetId)) {
                    locatedSnippets.put(snippetId, tempSnippetMap.get(snippetId));
                } else {
                    // detect duplicate across multiple files
                    duplicateCodesnippets.add(locatedSnippets.get(snippetId));
                    duplicateCodesnippets.add(tempSnippetMap.get(snippetId));
                }
            }
        }

        if (duplicateCodesnippets.size() > 0) {
            StringBuilder duplicateCodesnippetMessage = new StringBuilder();

            duplicateCodesnippets.stream()
                .sorted(Comparator.comparing(CodesnippetDefinition::getAlias, String::compareTo))
                .forEach(codesnippetDefinition ->
                    addDuplicateCodesnippetDefinitionError(duplicateCodesnippetMessage, codesnippetDefinition));

            throw new MojoExecutionException("Duplicate codesnippet definitions detected:" + LINE_SEPARATOR +
                duplicateCodesnippetMessage);
        }

        return locatedSnippets;
    }

    private static void addDuplicateCodesnippetDefinitionError(StringBuilder errorMessage,
        CodesnippetDefinition codesnippetDefinition) {
        errorMessage.append("Duplicate codesnippet definition ")
            .append(codesnippetDefinition.getAlias())
            .append(" detected in ")
            .append(codesnippetDefinition.getDefinitionFile())
            .append(":")
            .append(codesnippetDefinition.getBeginLine())
            .append(".")
            .append(LINE_SEPARATOR);
    }

    static InjectionResult injectSnippets(Path file, Pattern beginRegex, Pattern endRegex,
        Map<String, CodesnippetDefinition> snippetMap, String preFence, String postFence, int prefixGroupNum,
        String additionalLinePrefix, boolean disableEscape) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

        List<VerifyResult> missingCodesnippets = new ArrayList<>();
        List<VerifyResult> badCodesnippets = new ArrayList<>();

        StringBuilder modifiedLines = new StringBuilder();
        final boolean[] inSnippet = {false};
        final boolean[] updateSourceFile = {false};
        final String[] currentSnippetId = {""};

        forEachLine(lines, (lineNumber, line) -> {
            Matcher begin = beginRegex.matcher(line);
            Matcher end = endRegex.matcher(line);

            if (begin.matches()) {
                addLinesAndAppendNewLine(modifiedLines, line);
                currentSnippetId[0] = begin.group(2);
                inSnippet[0] = true;
            } else if (end.matches()) {
                if (inSnippet[0]) {
                    List<String> newSnippets;
                    if (snippetMap.containsKey(currentSnippetId[0])) {
                        newSnippets = snippetMap.get(currentSnippetId[0]).getCodesnippet();
                    } else {
                        missingCodesnippets.add(new VerifyResult(file, lineNumber, currentSnippetId[0]));
                        updateSourceFile[0] = true;
                        inSnippet[0] = false;
                        return;
                    }

                    List<String> modifiedSnippets = new ArrayList<>();

                    // We use this additional prefix because in src snippet cases we need to prespace
                    // for readme snippet cases we DONT need the prespace at all.
                    String linePrefix = prefixFunction(end, prefixGroupNum, additionalLinePrefix);

                    for (String snippet : respaceLines(newSnippets)) {
                        String moddedSnippet = disableEscape ? snippet : escapeString(snippet);
                        modifiedSnippets.add(moddedSnippet.length() == 0
                            ? END_OF_LINE_WHITESPACES.matcher(linePrefix).replaceAll("") + LINE_SEPARATOR
                            : linePrefix + moddedSnippet + LINE_SEPARATOR);
                    }

                    if (preFence != null && preFence.length() > 0) {
                        addLinesAndAppendNewLine(modifiedLines, linePrefix, preFence);
                    }

                    modifiedSnippets.forEach(modifiedLines::append);

                    if (postFence != null && postFence.length() > 0) {
                        addLinesAndAppendNewLine(modifiedLines, linePrefix, postFence);
                    }

                    addLinesAndAppendNewLine(modifiedLines, line);
                    updateSourceFile[0] = true;
                    inSnippet[0] = false;
                } else {
                    // This is a line that matched the endRegex without being in a codesnippet itself.
                    // Just append the line and continue processing.
                    addLinesAndAppendNewLine(modifiedLines, line);
                }
            } else if (!inSnippet[0]) {
                // Only modify the lines if not in the codesnippet.
                addLinesAndAppendNewLine(modifiedLines, line);
            }
        });

        return new InjectionResult(modifiedLines, updateSourceFile[0], missingCodesnippets, badCodesnippets);
    }

    private static List<String> respaceLines(List<String> snippetText) {
        // get List of all the leading whitespace in the sample
        // toss out lines that are empty (as they shouldn't mess with the minimum)
        String minWhitespace = null;
        List<String> modifiedStrings = new ArrayList<>();

        for (String snippetLine : snippetText) {
            // only look at non-whitespace only strings for the min indent
            if (snippetLine.trim().length() != 0) {
                Matcher leadSpaceMatch = WHITESPACE_EXTRACTION.matcher(snippetLine);

                if (leadSpaceMatch.matches()) {
                    String leadSpace = leadSpaceMatch.group(1);

                    if (minWhitespace == null || leadSpace.length() < minWhitespace.length())
                        minWhitespace = leadSpace;
                }
            }
        }

        if (minWhitespace != null) {
            for (String snippetLine : snippetText) {
                modifiedStrings.add(snippetLine.replaceFirst(minWhitespace, ""));
            }
        }

        return modifiedStrings;
    }

    private static String prefixFunction(Matcher match, int groupNum, String additionalPrefix) {
        // if we pass -1 as the matcher groupNum, we don't want any prefix at all
        if (match == null || groupNum < 1) {
            return "";
        } else {
            return match.group(groupNum) + additionalPrefix;
        }
    }

    private static String escapeString(String target) {
        if (target != null && target.trim().length() > 0) {
            for (String key : REPLACEMENT_SET.keySet()) {
                target = target.replace(key, REPLACEMENT_SET.get(key));
            }
        }

        return target;
    }

    private static List<Path> globFiles(Path rootFolder, String glob, boolean validate)
        throws IOException, MojoExecutionException {
        if (rootFolder == null || !rootFolder.toFile().isDirectory()) {
            if (validate) {
                throw new MojoExecutionException(String.format("Expected '%s' to be a directory but it wasn't.",
                    rootFolder));
            } else {
                return new ArrayList<>();
            }
        }

        List<Path> locatedPaths = new ArrayList<>();
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);

        Files.walkFileTree(rootFolder, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (pathMatcher.matches(file)) {
                    locatedPaths.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        return locatedPaths;
    }

    private static void addLinesAndAppendNewLine(StringBuilder builder, String... lines) {
        for (String line : lines) {
            builder.append(line);
        }

        builder.append(LINE_SEPARATOR);
    }

    private static void forEachLine(List<String> lines, BiConsumer<Integer, String> forEach) {
        for (int i = 0; i < lines.size(); i++) {
            forEach.accept(i, lines.get(i));
        }
    }

    private SnippetReplacer() {
    }
}
