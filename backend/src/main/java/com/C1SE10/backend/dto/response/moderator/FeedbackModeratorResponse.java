package com.C1SE10.backend.dto.response.moderator;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackModeratorResponse {
    private Integer id;
    private String lawTitle;
    private String userName;
    private String content;
    private String date;
    private String status;
}

