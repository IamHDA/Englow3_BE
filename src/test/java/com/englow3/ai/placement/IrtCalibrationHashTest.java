package com.englow3.ai.placement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class IrtCalibrationHashTest {

    @Test
    void hashIsStableAcrossInputOrderAndChangesWithParameters() {
        AdaptivePlacementDtos.CalibrationItem first = item("A", 1.1);
        AdaptivePlacementDtos.CalibrationItem second = item("B", 1.2);
        AdaptivePlacementDtos.CalibrationImportRequest ordered = request(List.of(first, second));
        AdaptivePlacementDtos.CalibrationImportRequest reversed = request(List.of(second, first));
        AdaptivePlacementDtos.CalibrationImportRequest changed = request(List.of(item("A", 1.3), second));

        assertThat(IrtCalibrationService.sourceHash(ordered)).hasSize(64)
                .isEqualTo(IrtCalibrationService.sourceHash(reversed))
                .isNotEqualTo(IrtCalibrationService.sourceHash(changed));
    }

    private AdaptivePlacementDtos.CalibrationImportRequest request(List<AdaptivePlacementDtos.CalibrationItem> items) {
        return new AdaptivePlacementDtos.CalibrationImportRequest(1, 100, items);
    }

    private AdaptivePlacementDtos.CalibrationItem item(String id, double discrimination) {
        return new AdaptivePlacementDtos.CalibrationItem(id, discrimination, 0, 0.2, 100, 0.1);
    }
}
