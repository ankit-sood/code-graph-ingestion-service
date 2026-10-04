package org.blr.config.sections;

import java.time.Duration;

import org.springframework.boot.context.properties.bind.DefaultValue;

public record GitProperties(
    @DefaultValue("git") String command,
    @DefaultValue("60s") Duration operationTimeout
) {
}
