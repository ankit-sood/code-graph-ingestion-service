package org.blr.storage;

public record GraphArtifactUploadResult(
    String archiveUri,
    String manifestUri,
    String archiveVersion,
    String manifestVersion
) {
}
