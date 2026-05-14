package com.C1SE10.backend.dto.response.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseDTO {

    private String question;
    private String answer;
    private List<String> sources;
    private List<String> chunks;
    private String conversationId;
}
