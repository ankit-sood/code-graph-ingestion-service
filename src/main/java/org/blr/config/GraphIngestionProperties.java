package org.blr.config;

import org.blr.config.sections.BuildProperties;
import org.blr.config.sections.CodeGraphProperties;
import org.blr.config.sections.GitProperties;
import org.blr.config.sections.RetentionProperties;
import org.blr.config.sections.StorageProperties;
import org.blr.config.sections.WorkspaceProperties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "graph-ingestion")
public record GraphIngestionProperties(
    @DefaultValue WorkspaceProperties workspace,
    @DefaultValue GitProperties git,
    @DefaultValue CodeGraphProperties codegraph,
    @DefaultValue StorageProperties storage,
    @DefaultValue RetentionProperties retention,
    @DefaultValue BuildProperties build
) {
}
