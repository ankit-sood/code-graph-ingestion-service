package org.blr.artifact;

import java.nio.file.Path;

public interface GraphArtifactService {

    GraphProjectLayout discoverAndValidate(Path codeGraphOutputRoot);

    GraphArtifactResult packageAndGenerateManifest(GraphArtifactRequest request, GraphProjectLayout layout);
}
