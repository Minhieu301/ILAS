package com.C1SE10.backend.controller.moderator;

import com.C1SE10.backend.dto.common.ApiResponse;
import com.C1SE10.backend.dto.response.user.SimplifiedArticleDTO;
import com.C1SE10.backend.model.SimplifiedArticle;
import com.C1SE10.backend.service.moderator.ModeratorSimplifiedManagementService;
import com.C1SE10.backend.service.log.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/moderator/simplified-management")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasAnyAuthority('Admin', 'ADMIN', 'Moderator', 'MODERATOR', 'moderator')")
public class ModeratorSimplifiedManagementController {

    private final ModeratorSimplifiedManagementService simplifiedService;
    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SimplifiedArticleDTO>>> list(
            @RequestParam(defaultValue = "PENDING") SimplifiedArticle.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<SimplifiedArticleDTO> data = simplifiedService.listByStatus(status, page, size);
        return ResponseEntity.ok(ApiResponse.success("OK", data));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<SimplifiedArticleDTO>> approve(@PathVariable Integer id) {
        SimplifiedArticleDTO dto = simplifiedService.approve(id);
        String aTitle = dto != null ? dto.getArticleTitle() : null;
        String detail = aTitle != null && !aTitle.isBlank() ? aTitle : ("articleId=" + (dto != null ? dto.getArticleId() : "n/a"));
        auditLogService.logSafe("Duyệt bản rút gọn", detail);
        return ResponseEntity.ok(ApiResponse.success("Approved", dto));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<SimplifiedArticleDTO>> reject(@PathVariable Integer id) {
        SimplifiedArticleDTO dto = simplifiedService.reject(id);
        String rTitle = dto != null ? dto.getArticleTitle() : null;
        String rdetail = rTitle != null && !rTitle.isBlank() ? rTitle : ("articleId=" + (dto != null ? dto.getArticleId() : "n/a"));
        auditLogService.logSafe("Từ chối bản rút gọn", rdetail);
        return ResponseEntity.ok(ApiResponse.success("Rejected", dto));
    }
}

