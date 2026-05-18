package com.C1SE10.backend.service.moderator;

import com.C1SE10.backend.dto.response.moderator.FeedbackModeratorResponse;
import com.C1SE10.backend.model.Feedback;
import com.C1SE10.backend.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedbackModeratorService {

    private final FeedbackRepository feedbackRepository;

    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public List<FeedbackModeratorResponse> getAll() {
        return feedbackRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt())) 
                .map(f -> {

                    String articleTitle = "Không rõ";
                    if (f.getArticle() != null) {
                        articleTitle = f.getArticle().getArticleTitle();
                    }

                    return FeedbackModeratorResponse.builder()
                            .id(f.getFeedbackId())
                            .lawTitle(articleTitle)
                            .userName(f.getUser() != null ? f.getUser().getFullName() : "Ẩn danh")
                            .content(f.getContent())
                            .date(f.getCreatedAt().format(fmt))
                            .status(f.getStatus().name())
                            .build();
                })
                .toList();
    }


    public FeedbackModeratorResponse markResolved(Integer id) {
        Feedback f = feedbackRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phản hồi"));

        f.setStatus(Feedback.Status.RESOLVED);
        feedbackRepository.save(f);

        String articleTitle = "Không rõ";

        if (f.getArticle() != null) {
            articleTitle = f.getArticle().getArticleTitle();
        }

        return FeedbackModeratorResponse.builder()
                .id(f.getFeedbackId())
                .lawTitle(articleTitle)
                .userName(f.getUser() != null ? f.getUser().getFullName() : "")
                .content(f.getContent())
                .date(f.getCreatedAt().format(fmt))
                .status(f.getStatus().name())
                .build();
    }

    

}

