package com.C1SE10.backend.dto.response.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatHistoryDTO {
    private String conversationId;
    private String question;
    private String answer;
    private String createdAt;
}
