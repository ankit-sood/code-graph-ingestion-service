package org.blr.config.sections;

import java.time.Duration;

import org.springframework.boot.context.properties.bind.DefaultValue;

public record StorageProperties(
    @DefaultValue("local") String provider,
    @DefaultValue("3") int maxAttempts,
    @DefaultValue("500ms") Duration initialBackoff,
    @DefaultValue("3s") Duration maxBackoff,
    @DefaultValue LocalStorageProperties local,
    @DefaultValue AzureStorageProperties azure
) {
}
