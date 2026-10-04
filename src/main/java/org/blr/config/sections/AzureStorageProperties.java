package org.blr.config.sections;

import org.springframework.boot.context.properties.bind.DefaultValue;

public record AzureStorageProperties(
    @DefaultValue("") String accountUrl,
    @DefaultValue("codegraph-artifacts") String container,
    @DefaultValue("graph-ingestion") String pathPrefix
) {
}
