package org.blr.storage;

public interface GraphArtifactUploadService {

    GraphArtifactUploadResult uploadAndVerify(GraphArtifactUploadRequest request);
}
