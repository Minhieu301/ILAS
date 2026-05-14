package com.C1SE10.backend.controller.moderator;

import com.C1SE10.backend.dto.common.ApiResponse;
import com.C1SE10.backend.dto.response.moderator.ContentQualityDTO;
import com.C1SE10.backend.service.moderator.ContentQualityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/moderator/content-quality")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasAnyAuthority('Admin', 'ADMIN', 'Moderator', 'MODERATOR')")
public class ContentQualityController {

    private final ContentQualityService contentQualityService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ContentQualityDTO>>> getQualityReport() {
        return ResponseEntity.ok(ApiResponse.success("OK", contentQualityService.getQualityReport()));
    }

    @GetMapping("/missing-simplified")
    public ResponseEntity<ApiResponse<List<ContentQualityDTO>>> getMissingSimplified() {
        return ResponseEntity.ok(ApiResponse.success("OK", contentQualityService.getMissingSimplified()));
    }
}