package com.C1SE10.backend.dto.request.moderator;

import lombok.Data;

@Data
public class FormTemplateRequest {
    private String title;
    private String category;
    private String description;
    private String fileUrl;
}

