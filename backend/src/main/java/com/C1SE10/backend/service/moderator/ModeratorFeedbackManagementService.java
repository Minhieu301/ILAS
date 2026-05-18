package com.C1SE10.backend.service.moderator;

import com.C1SE10.backend.dto.response.admin.AdminFeedbackResponseDTO;
import com.C1SE10.backend.model.Feedback;
import com.C1SE10.backend.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ModeratorFeedbackManagementService {

    private final FeedbackRepository feedbackRepo;

    private String mapStatus(Feedback.Status status) {
        return switch (status) {
            case UNPROCESSED -> "new";
            case PENDING    -> "forwarded";
            case RESOLVED   -> "resolved";
        };
    }

    private AdminFeedbackResponseDTO toDTO(Feedback f) {
        return AdminFeedbackResponseDTO.builder()
                .id(f.getFeedbackId())
                .user(f.getUser() != null ? f.getUser().getFullName() : "Khách")
                .email(f.getUser() != null ? f.getUser().getEmail() : null)
                .content(f.getContent())
                .status(mapStatus(f.getStatus()))
                .createdAt(f.getCreatedAt())
                .build();
    }

    private boolean isAdminTargetedFeedback(Feedback f) {
        String content = f.getContent();
        if (content == null || content.isBlank()) {
            return false;
        }

        String normalized = content.toUpperCase(Locale.ROOT);
        return normalized.contains("[GỬI ADMIN]") || normalized.contains("[AI_FEEDBACK]");
    }

    public List<AdminFeedbackResponseDTO> getAll(boolean canViewAdminTargeted) {
        return feedbackRepo.findAllOrderByLatest()
                .stream()
                .filter(f -> canViewAdminTargeted || !isAdminTargetedFeedback(f))
                .map(this::toDTO)
                .toList();
    }

    public List<AdminFeedbackResponseDTO> getByStatus(String status, boolean canViewAdminTargeted) {
        Feedback.Status s = switch (status) {
            case "new" -> Feedback.Status.UNPROCESSED;
            case "forwarded" -> Feedback.Status.PENDING;
            case "resolved" -> Feedback.Status.RESOLVED;
            default -> throw new RuntimeException("Trạng thái không hợp lệ: " + status);
        };

        return feedbackRepo.findByStatus(s).stream()
                .filter(f -> canViewAdminTargeted || !isAdminTargetedFeedback(f))
                .map(this::toDTO).toList();
    }

    public AdminFeedbackResponseDTO forward(Integer id) {
        Feedback f = feedbackRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phản hồi"));

        f.setStatus(Feedback.Status.PENDING);
        return toDTO(feedbackRepo.save(f));
    }

    public AdminFeedbackResponseDTO resolve(Integer id) {
        Feedback f = feedbackRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phản hồi"));

        f.setStatus(Feedback.Status.RESOLVED);
        return toDTO(feedbackRepo.save(f));
    }

    public void delete(Integer id) {
        feedbackRepo.deleteById(id);
    }
}

