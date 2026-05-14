package com.C1SE10.backend.dto.response.moderator;

import com.C1SE10.backend.model.SimplifiedArticle;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimplifiedArticleResponseDTO {

    private Integer id;
    private Integer articleId;
    private Integer moderatorId;
    private String articleTitle;
    private String moderatorName;
    private String category;
    private String contentSimplified;
    private String status;
    private LocalDateTime createdAt;

    public static SimplifiedArticleResponseDTO fromEntity(SimplifiedArticle sa) {
        return SimplifiedArticleResponseDTO.builder()
                .id(sa.getId())
                .articleId(sa.getArticle().getArticleId())
                .moderatorId(sa.getModerator().getUserId())
                .articleTitle(sa.getArticle().getArticleTitle())
                .moderatorName(sa.getModerator().getFullName())
                .category(sa.getCategory())
                .contentSimplified(sa.getContentSimplified())
                .status(sa.getStatus().name())
                .createdAt(sa.getCreatedAt())
                .build();
    }
}

