package com.C1SE10.backend.dto.response.moderator;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FormTemplateResponse {
    private Integer templateId;
    private String title;
    private String category;
    private String description;
    private String fileUrl;
    private String status;
    private String moderatorName;
    private String moderatorEmail;
    private LocalDateTime createdAt;
}

