package org.blr.config.sections;

import java.time.Duration;

import org.springframework.boot.context.properties.bind.DefaultValue;

public record CodeGraphProperties(
    @DefaultValue("node") String nodeCommand,
    @DefaultValue("codegraph-daemon") String startupCommand,
    @DefaultValue("60s") Duration startupTimeout,
    @DefaultValue("30m") Duration buildTimeout,
    @DefaultValue("10s") Duration shutdownGracePeriod
) {
}
