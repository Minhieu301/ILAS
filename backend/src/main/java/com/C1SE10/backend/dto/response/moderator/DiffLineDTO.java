package com.C1SE10.backend.dto.response.moderator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiffLineDTO {
    private String type;
    private String content;
    private int lineNumber;
}