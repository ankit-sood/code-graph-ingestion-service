package org.blr.config.sections;

import org.springframework.boot.context.properties.bind.DefaultValue;

public record RetentionProperties(
    @DefaultValue("5") int versions
) {
}
