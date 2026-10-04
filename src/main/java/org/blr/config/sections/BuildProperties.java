package org.blr.config.sections;

import org.springframework.boot.context.properties.bind.DefaultValue;

public record BuildProperties(
    @DefaultValue("2") int maxConcurrentBuilds
) {
}
