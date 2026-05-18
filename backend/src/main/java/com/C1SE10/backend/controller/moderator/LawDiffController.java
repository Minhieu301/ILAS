package com.C1SE10.backend.controller.moderator;

import com.C1SE10.backend.dto.common.ApiResponse;
import com.C1SE10.backend.dto.response.moderator.LawDiffDTO;
import com.C1SE10.backend.dto.response.moderator.LawVersionSummaryDTO;
import com.C1SE10.backend.service.moderator.LawDiffService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/moderator/diff")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasAnyAuthority('Admin', 'ADMIN', 'Moderator', 'MODERATOR')")
public class LawDiffController {

    private final LawDiffService diffService;

    @GetMapping("/law/{lawId}")
    public ResponseEntity<ApiResponse<LawDiffDTO>> getDiff(@PathVariable Integer lawId) {
        return ResponseEntity.ok(ApiResponse.success("OK", diffService.getDiff(lawId)));
    }

    @GetMapping("/law/{lawId}/compare")
    public ResponseEntity<ApiResponse<LawDiffDTO>> getDiffBetween(
            @PathVariable Integer lawId,
            @RequestParam Integer v1,
            @RequestParam Integer v2
    ) {
        return ResponseEntity.ok(ApiResponse.success("OK", diffService.getDiffBetween(lawId, v1, v2)));
    }

    @GetMapping("/law/{lawId}/history")
    public ResponseEntity<ApiResponse<List<LawVersionSummaryDTO>>> getHistory(@PathVariable Integer lawId) {
        return ResponseEntity.ok(ApiResponse.success("OK", diffService.getHistory(lawId)));
    }

    @PostMapping("/law/{lawId}/snapshot")
    public ResponseEntity<ApiResponse<Void>> saveSnapshot(
            @PathVariable Integer lawId,
            @RequestParam Integer changedBy,
            @RequestParam(required = false, defaultValue = "") String note
    ) {
        diffService.saveSnapshot(lawId, changedBy, note);
        return ResponseEntity.ok(ApiResponse.success("Đã lưu snapshot", null));
    }
}