package com.C1SE10.backend.controller.moderator;

import com.C1SE10.backend.dto.common.ApiResponse;
import com.C1SE10.backend.dto.request.admin.AdminLawRequest;
import com.C1SE10.backend.dto.response.user.LawDTO;
import com.C1SE10.backend.service.moderator.ModeratorLawManagementService;
import com.C1SE10.backend.service.log.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/moderator/laws")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasAnyAuthority('Admin', 'ADMIN', 'Moderator', 'MODERATOR', 'moderator', 'Editor', 'EDITOR', 'editor')")
public class ModeratorLawManagementController {

    private final ModeratorLawManagementService lawManagementService;
    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<LawDTO>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword
    ) {
        Page<LawDTO> data = lawManagementService.list(keyword, page, size);
        return ResponseEntity.ok(ApiResponse.success("OK", data));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LawDTO>> create(@RequestBody AdminLawRequest req) {
        LawDTO created = lawManagementService.create(req);
        auditLogService.logSafe("Tạo văn bản pháp luật", "Tạo luật: " + (created != null ? created.getTitle() : "n/a"));
        return ResponseEntity.ok(ApiResponse.success("Created", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<LawDTO>> update(@PathVariable Integer id, @RequestBody AdminLawRequest req) {
        LawDTO updated = lawManagementService.update(id, req);
        auditLogService.logSafe("Cập nhật văn bản pháp luật", "Cập nhật luật id=" + id + " title=" + (updated != null ? updated.getTitle() : "n/a"));
        return ResponseEntity.ok(ApiResponse.success("Updated", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        lawManagementService.delete(id);
        auditLogService.logSafe("Xóa văn bản pháp luật", "Xóa luật id=" + id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}

