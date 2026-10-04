package org.blr.config.sections;

import org.springframework.boot.context.properties.bind.DefaultValue;

public record LocalStorageProperties(
    @DefaultValue("target/blob-storage") String root
) {
}
