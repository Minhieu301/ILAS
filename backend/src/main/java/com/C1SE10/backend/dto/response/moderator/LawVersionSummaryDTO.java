package com.C1SE10.backend.dto.response.moderator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LawVersionSummaryDTO {
    private Integer versionId;
    private Integer versionNumber;
    private String changeNote;
    private Integer changedBy;
    private LocalDateTime createdAt;
}