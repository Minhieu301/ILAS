package com.C1SE10.backend.controller.moderator;

import com.C1SE10.backend.dto.response.moderator.FeedbackStatsResponse;
import com.C1SE10.backend.service.moderator.FeedbackStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/moderator/feedback-stats")
@RequiredArgsConstructor
public class FeedbackStatsController {

    private final FeedbackStatsService feedbackStatsService;

    @GetMapping("/{moderatorId}")
    public ResponseEntity<FeedbackStatsResponse> getFeedbackStats(@PathVariable Integer moderatorId) {
        FeedbackStatsResponse res = feedbackStatsService.getStats(moderatorId);
        return ResponseEntity.ok(res);
    }
}

