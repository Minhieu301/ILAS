package com.C1SE10.backend.service.moderator;

import com.C1SE10.backend.dto.response.moderator.ContentQualityDTO;
import com.C1SE10.backend.model.Article;
import com.C1SE10.backend.model.Feedback;
import com.C1SE10.backend.model.SimplifiedArticle;
import com.C1SE10.backend.repository.FeedbackRepository;
import com.C1SE10.backend.repository.SimplifiedArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ContentQualityService {

    private final FeedbackRepository feedbackRepository;
    private final SimplifiedArticleRepository simplifiedArticleRepository;

    public List<ContentQualityDTO> getQualityReport() {
        Map<Integer, List<Feedback>> feedbackByArticle = feedbackRepository.findAllWithArticle().stream()
                .collect(Collectors.groupingBy(feedback -> {
                    Article article = feedback.getArticle();
                    if (article == null || article.getArticleId() == null) {
                        throw new RuntimeException("Không thể xác định bài viết từ phản hồi.");
                    }
                    return article.getArticleId();
                }));

        return feedbackByArticle.entrySet().stream()
                .map(entry -> buildDTO(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingDouble(ContentQualityDTO::getQualityScore))
                .collect(Collectors.toList());
    }

    public List<ContentQualityDTO> getMissingSimplified() {
        return getQualityReport().stream()
                .filter(dto -> !dto.isHasSimplified() && dto.getTotalFeedbacks() >= 3)
                .collect(Collectors.toList());
    }

    private ContentQualityDTO buildDTO(Integer articleId, List<Feedback> feedbacks) {
        if (feedbacks == null || feedbacks.isEmpty()) {
            throw new RuntimeException("Không có dữ liệu phản hồi cho bài viết.");
        }

        List<Feedback> sortedFeedbacks = feedbacks.stream()
                .sorted(Comparator.comparing(Feedback::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        Feedback first = sortedFeedbacks.get(0);
        Article article = first.getArticle();
        if (article == null) {
            throw new RuntimeException("Phản hồi không liên kết với bài viết hợp lệ.");
        }

        int totalFeedbacks = sortedFeedbacks.size();
        int unresolvedCount = (int) sortedFeedbacks.stream()
                .filter(feedback -> feedback.getStatus() == Feedback.Status.UNPROCESSED)
                .count();

        double rawScore = 100 - (unresolvedCount * 15) - ((totalFeedbacks - unresolvedCount) * 3);
        double qualityScore = BigDecimal.valueOf(Math.max(0, rawScore))
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();

        String qualityLevel = qualityScore >= 70 ? "GOOD" : qualityScore >= 40 ? "WARNING" : "CRITICAL";

        List<SimplifiedArticle> simplifiedArticles = simplifiedArticleRepository.findAllByArticleId(articleId);
        boolean hasSimplified = !simplifiedArticles.isEmpty();
        String simplifiedStatus = simplifiedArticles.stream()
                .sorted(Comparator.comparing(SimplifiedArticle::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst()
                .map(item -> item.getStatus() == null ? null : item.getStatus().name())
                .orElse(null);

        List<String> recentFeedbacks = sortedFeedbacks.stream()
                .limit(3)
                .map(Feedback::getContent)
                .collect(Collectors.toList());

        String lawTitle = null;
        if (article.getLaw() != null) {
            lawTitle = article.getLaw().getTitle();
        } else if (first.getLaw() != null) {
            lawTitle = first.getLaw().getTitle();
        }

        return ContentQualityDTO.builder()
                .articleId(articleId)
                .articleNumber(article.getArticleNumber())
                .articleTitle(article.getArticleTitle())
                .lawTitle(lawTitle)
                .totalFeedbacks(totalFeedbacks)
                .unresolvedCount(unresolvedCount)
                .qualityScore(qualityScore)
                .qualityLevel(qualityLevel)
                .hasSimplified(hasSimplified)
                .simplifiedStatus(simplifiedStatus)
                .recentFeedbacks(recentFeedbacks)
                .build();
    }
}