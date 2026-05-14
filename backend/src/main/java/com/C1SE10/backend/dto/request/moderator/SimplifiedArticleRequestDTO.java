package com.C1SE10.backend.dto.request.moderator;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimplifiedArticleRequestDTO {
    private Integer articleId;
    private Integer moderatorId;
    private String category;
    private String contentSimplified;
}

