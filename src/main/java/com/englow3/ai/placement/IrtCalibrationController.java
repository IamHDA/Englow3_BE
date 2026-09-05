package com.englow3.ai.placement;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/admin/ai/placement/calibrations")
@PreAuthorize("hasRole('ADMIN')")
class IrtCalibrationController {

    private final IrtCalibrationService service;

    IrtCalibrationController(IrtCalibrationService service) {
        this.service = service;
    }

    @GetMapping
    List<AdaptivePlacementDtos.CalibrationResponse> versions() {
        return service.versions();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AdaptivePlacementDtos.CalibrationResponse importVersion(
            @Valid @RequestBody AdaptivePlacementDtos.CalibrationImportRequest request) {
        return service.importVersion(request);
    }

    @PostMapping("/{version}/activate")
    AdaptivePlacementDtos.CalibrationResponse activate(@PathVariable @Min(1) int version) {
        return service.activate(version);
    }
}
