package com.azure.tools.apiview.processor.model.maven;

public class Dependency implements MavenGAV {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String scope; // default scope is compile-time scope

    public Dependency(String groupId, String artifactId, String version, String scope) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.scope = scope == null || scope.isEmpty() ? "compile" : scope;
    }

    @Override
    public String getGroupId() {
        return groupId;
    }

    @Override
    public String getArtifactId() {
        return artifactId;
    }

    @Override
    public String getVersion() {
        return version;
    }

    public String getScope() {
        return scope;
    }
}
