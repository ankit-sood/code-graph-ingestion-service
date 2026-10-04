/*

*/
package org.blr.domain;

public enum GraphBuildStatus {
    QUEUED,
    BUILDING,
    VALIDATING,
    PACKAGING,
    UPLOADING,
    PUBLISHED,
    FAILED
}
