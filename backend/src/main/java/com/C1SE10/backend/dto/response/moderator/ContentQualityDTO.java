package com.C1SE10.backend.dto.response.moderator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentQualityDTO {
    private Integer articleId;
    private String articleNumber;
    private String articleTitle;
    private String lawTitle;
    private int totalFeedbacks;
    private int unresolvedCount;
    private double qualityScore;
    private String qualityLevel;
    private boolean hasSimplified;
    private String simplifiedStatus;
    private List<String> recentFeedbacks;
}