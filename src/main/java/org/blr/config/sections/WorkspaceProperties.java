package org.blr.config.sections;

import org.springframework.boot.context.properties.bind.DefaultValue;

public record WorkspaceProperties(
    @DefaultValue("/workspace") String root
) {
}
