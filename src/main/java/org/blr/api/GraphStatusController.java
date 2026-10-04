package org.blr.api;

import org.blr.application.GraphStatusApplicationService;
import org.blr.api.dto.GraphStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/repositories/{repositoryId}")
public class GraphStatusController {

    private final GraphStatusApplicationService graphStatusApplicationService;

    public GraphStatusController(GraphStatusApplicationService graphStatusApplicationService) {
        this.graphStatusApplicationService = graphStatusApplicationService;
    }

    @GetMapping("/graph-status")
    public ResponseEntity<GraphStatusResponse> getGraphStatus(@PathVariable String repositoryId) {
        GraphStatusResponse response = graphStatusApplicationService.getGraphStatus(repositoryId);

        return ResponseEntity.ok(response);
    }
}
