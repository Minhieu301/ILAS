package com.C1SE10.backend.dto.response.moderator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LawDiffDTO {
    private Integer lawId;
    private String lawTitle;
    private String lawCode;
    private Integer oldVersion;
    private Integer newVersion;
    private LocalDateTime oldCreatedAt;
    private LocalDateTime newCreatedAt;
    private String changeNote;
    private int totalAdded;
    private int totalDeleted;
    private int totalEqual;
    private List<ArticleDiffDTO> articleDiffs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArticleDiffDTO {
        private String articleNumber;
        private String articleTitle;
        private String diffStatus;
        private List<DiffLineDTO> lines;
    }
}