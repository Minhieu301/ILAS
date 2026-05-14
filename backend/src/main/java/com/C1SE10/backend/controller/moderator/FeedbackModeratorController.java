package com.C1SE10.backend.controller.moderator;

import com.C1SE10.backend.dto.response.moderator.FeedbackModeratorResponse;
import com.C1SE10.backend.service.moderator.FeedbackModeratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/moderator/feedback-moderator")
@RequiredArgsConstructor
public class FeedbackModeratorController {

    private final FeedbackModeratorService feedbackModeratorService;

    @GetMapping
    public List<FeedbackModeratorResponse> getAllFeedback() {
        return feedbackModeratorService.getAll();
    }

    @PutMapping("/{id}/resolve")
    public FeedbackModeratorResponse resolve(@PathVariable Integer id) {
        return feedbackModeratorService.markResolved(id);
    }
}

