package com.C1SE10.backend.dto.response.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TopQuestionResponse {
    private String question;
    private Long count;
}
