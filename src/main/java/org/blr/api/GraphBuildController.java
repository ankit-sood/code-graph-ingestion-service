package org.blr.api;

import org.blr.application.GraphBuildApplicationService;
import org.blr.api.dto.GraphBuildAcceptedResponse;
import org.blr.api.dto.GraphBuildRequest;
import org.blr.api.dto.GraphBuildStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/repositories/{repositoryId}/graph-builds")
public class GraphBuildController {

    private final GraphBuildApplicationService graphBuildApplicationService;

    public GraphBuildController(GraphBuildApplicationService graphBuildApplicationService) {
        this.graphBuildApplicationService = graphBuildApplicationService;
    }

    @PostMapping
    public ResponseEntity<GraphBuildAcceptedResponse> createBuild(
        @PathVariable String repositoryId,
        @RequestBody(required = false) GraphBuildRequest request
    ) {
        GraphBuildAcceptedResponse response = graphBuildApplicationService.createBuild(
            repositoryId,
            request == null ? null : request.commitSha()
        );
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/{buildId}")
    public ResponseEntity<GraphBuildStatusResponse> getBuildStatus(
        @PathVariable String repositoryId,
        @PathVariable String buildId
    ) {
        GraphBuildStatusResponse response = graphBuildApplicationService.getBuildStatus(repositoryId, buildId);
        return ResponseEntity.ok(response);
    }
}
