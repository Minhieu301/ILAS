package com.C1SE10.backend.service.moderator;

import com.C1SE10.backend.dto.response.moderator.LawStatsResponse;
import com.C1SE10.backend.model.SimplifiedArticle;
import com.C1SE10.backend.model.UserAccount;
import com.C1SE10.backend.repository.SimplifiedArticleRepository;
import com.C1SE10.backend.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LawStatsService {

    private final SimplifiedArticleRepository simplifiedRepo;
    private final UserAccountRepository userRepo;

    public LawStatsResponse getLawStatsData(Integer moderatorId) {
        UserAccount moderator = userRepo.findById(moderatorId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy moderator"));

        List<SimplifiedArticle> list = simplifiedRepo.findByModerator_UserId(moderatorId);

        long pending = list.stream().filter(s -> s.getStatus() == SimplifiedArticle.Status.PENDING).count();
        long rejected = list.stream().filter(s -> s.getStatus() == SimplifiedArticle.Status.REJECTED).count();
        long approved = list.stream().filter(s -> s.getStatus() == SimplifiedArticle.Status.APPROVED).count();

        // Danh sách 5 bài gần nhất
        List<LawStatsResponse.RecentWork> recentWorks = list.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(5)
                .map(sa -> new LawStatsResponse.RecentWork(
                        sa.getId(),
                        sa.getArticle().getArticleTitle(),
                        sa.getStatus().name(),
                        sa.getCreatedAt()
                ))
                .collect(Collectors.toList());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
        // Gom nhóm các bài theo tháng
        Map<String, List<SimplifiedArticle>> grouped = list.stream()
                .collect(Collectors.groupingBy(sa -> sa.getCreatedAt().format(formatter)));

        List<LawStatsResponse.MonthlyActivity> timeline = grouped.entrySet().stream()
                .map(entry -> {
                    String month = entry.getKey();
                    List<SimplifiedArticle> articles = entry.getValue();

                    long pendingCount = articles.stream().filter(a -> a.getStatus() == SimplifiedArticle.Status.PENDING).count();
                    long rejectedCount = articles.stream().filter(a -> a.getStatus() == SimplifiedArticle.Status.REJECTED).count();
                    long approvedCount = articles.stream().filter(a -> a.getStatus() == SimplifiedArticle.Status.APPROVED).count();

                    return new LawStatsResponse.MonthlyActivity(month, pendingCount, rejectedCount, approvedCount);
                })
                .sorted((a, b) -> a.getMonth().compareTo(b.getMonth()))
                .collect(Collectors.toList());

        // Tạo thống kê tổng
        LawStatsResponse.Stats summaryStats =
                new LawStatsResponse.Stats(pending, rejected, approved);

        return LawStatsResponse.builder()
                .moderatorName(moderator.getFullName() != null ? moderator.getFullName() : moderator.getUsername())
                .stats(summaryStats)
                .recentWorks(recentWorks)
                .activityTimeline(timeline)
                .build();

    }
}

