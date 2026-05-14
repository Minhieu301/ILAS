package com.C1SE10.backend.controller.moderator;

import com.C1SE10.backend.dto.response.admin.AdminFormTemplateResponse;
import com.C1SE10.backend.model.UserAccount;
import com.C1SE10.backend.service.moderator.ModeratorFormManagementService;
import com.C1SE10.backend.service.log.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/moderator/forms")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasAnyAuthority('Admin', 'ADMIN', 'Moderator', 'MODERATOR', 'moderator')")
public class ModeratorFormManagementController {

    private final ModeratorFormManagementService formService;
    private final AuditLogService auditLogService;

    @GetMapping
    public List<AdminFormTemplateResponse> getForms() {
        return formService.getAllForms();
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Integer id) {
        formService.approveForm(id);
        try {
            UserAccount user = null;
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserAccount ua) user = ua;
            auditLogService.log("Duyệt biểu mẫu", "templateId=" + id, user);
        } catch (Exception ignored) {}
        return ResponseEntity.ok("Approved");
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Integer id) {
        formService.rejectForm(id);
        try {
            UserAccount user = null;
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserAccount ua) user = ua;
            auditLogService.log("Từ chối biểu mẫu", "templateId=" + id, user);
        } catch (Exception ignored) {}
        return ResponseEntity.ok("Rejected");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        formService.deleteForm(id);
        try {
            UserAccount user = null;
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserAccount ua) user = ua;
            auditLogService.log("Xóa biểu mẫu", "templateId=" + id, user);
        } catch (Exception ignored) {}
        return ResponseEntity.ok("Deleted");
    }
}

