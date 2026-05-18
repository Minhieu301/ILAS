package com.C1SE10.backend.controller.moderator;

import com.C1SE10.backend.dto.response.admin.AdminFeedbackResponseDTO;
import com.C1SE10.backend.service.moderator.ModeratorFeedbackManagementService;
import com.C1SE10.backend.service.log.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/moderator/feedback")
@RequiredArgsConstructor
@CrossOrigin("*")
@PreAuthorize("hasAnyAuthority('Admin', 'ADMIN', 'Moderator', 'MODERATOR', 'moderator')")
public class ModeratorFeedbackManagementController {

    private final ModeratorFeedbackManagementService feedbackService;
    private final AuditLogService auditLogService;

    private boolean canViewAdminTargetedFeedback(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "Admin".equalsIgnoreCase(a.getAuthority()));
    }

    @GetMapping
    public List<AdminFeedbackResponseDTO> getAll(Authentication authentication) {
        return feedbackService.getAll(canViewAdminTargetedFeedback(authentication));
    }

    @GetMapping("/status/{status}")
    public List<AdminFeedbackResponseDTO> getByStatus(@PathVariable String status, Authentication authentication) {
        return feedbackService.getByStatus(status, canViewAdminTargetedFeedback(authentication));
    }

    @PostMapping("/{id}/forward")
    public AdminFeedbackResponseDTO forward(@PathVariable Integer id) {
        AdminFeedbackResponseDTO dto = feedbackService.forward(id);
        auditLogService.logSafe("Chuyển tiếp phản hồi", "feedbackId=" + id);
        return dto;
    }

    @PostMapping("/{id}/resolve")
    public AdminFeedbackResponseDTO resolve(@PathVariable Integer id) {
        AdminFeedbackResponseDTO dto = feedbackService.resolve(id);
        auditLogService.logSafe("Giải quyết phản hồi", "feedbackId=" + id);
        return dto;
    }

    @DeleteMapping("/{id}")
    public void deleteFeedback(@PathVariable Integer id) {
        feedbackService.delete(id);
        auditLogService.logSafe("Xóa phản hồi", "feedbackId=" + id);
    }
}

