package com.C1SE10.backend.service.admin;

import com.C1SE10.backend.dto.response.admin.ReportResponse;
import com.C1SE10.backend.model.*;
import com.C1SE10.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportService {

    private final UserAccountRepository userAccountRepository;
    private final SimplifiedArticleRepository simplifiedArticleRepository;
    private final FormTemplateRepository formTemplateRepository;
    private final FeedbackRepository feedbackRepository;
    private final PageViewLogRepository pageViewLogRepository;
    private final LawRepository lawRepository;

        private static final Map<String, String> PAGE_LABELS = Map.ofEntries(
            Map.entry("/", "Trang chủ"),
            Map.entry("/about", "Giới thiệu"),
            Map.entry("/search", "Tra cứu văn bản"),
            Map.entry("/search/detail", "Chi tiết kết quả tra cứu"),
            Map.entry("/chat", "Chat pháp lý"),
            Map.entry("/chat/history", "Lịch sử chat"),
            Map.entry("/profile", "Hồ sơ người dùng"),
            Map.entry("/user/dashboard", "Dashboard người dùng"),
            Map.entry("/user/search", "Tra cứu (User)"),
            Map.entry("/user/search/detail", "Chi tiết tra cứu (User)"),
            Map.entry("/user/form", "Danh sách biểu mẫu"),
            Map.entry("/user/form/detail/:id", "Chi tiết biểu mẫu"),
            Map.entry("/user/feedback", "Phản hồi người dùng")
        );

    public ReportResponse getReport(String range, String reportType) {
        DateRange currentRange = resolveRange(range);
        DateRange previousRange = previousRange(currentRange);

        ReportResponse.Summary summary = buildSummary(currentRange, previousRange);
        List<ReportResponse.DailyMetric> weeklyData = buildDailyMetrics(currentRange);
        List<ReportResponse.CategoryShare> categories = buildCategoryDistribution(currentRange);
        List<ReportResponse.TopContentItem> topContents = buildTopContents(currentRange);

        return ReportResponse.builder()
                .period(new ReportResponse.PeriodInfo(
                        normalizeRange(range),
                        currentRange.start(),
                        currentRange.end()
                ))
                .stats(summary)
                .weeklyData(weeklyData)
                .categoryDistribution(categories)
                .topContents(topContents)
                .build();
    }

    private ReportResponse.Summary buildSummary(DateRange current, DateRange previous) {
        long totalUsers = userAccountRepository.count();
        long totalContent = simplifiedArticleRepository.count();
        long totalForms = formTemplateRepository.count();
        long totalFeedback = feedbackRepository.count();

        long newUsers = userAccountRepository.countByCreatedAtBetween(current.start(), current.end());
        long newContent = simplifiedArticleRepository.countByCreatedAtBetween(current.start(), current.end());
        long newForms = formTemplateRepository.countByCreatedAtBetween(current.start(), current.end());
        long newFeedback = feedbackRepository.countByCreatedAtBetween(current.start(), current.end());

        long prevUsers = userAccountRepository.countByCreatedAtBetween(previous.start(), previous.end());
        long prevContent = simplifiedArticleRepository.countByCreatedAtBetween(previous.start(), previous.end());
        long prevForms = formTemplateRepository.countByCreatedAtBetween(previous.start(), previous.end());
        long prevFeedback = feedbackRepository.countByCreatedAtBetween(previous.start(), previous.end());

        return ReportResponse.Summary.builder()
                .totalUsers(totalUsers)
                .totalContent(totalContent)
                .totalForms(totalForms)
                .totalFeedback(totalFeedback)
                .newUsers(newUsers)
                .newContent(newContent)
                .newForms(newForms)
                .newFeedback(newFeedback)
                .usersChange(calcChangePercent(newUsers, prevUsers))
                .contentChange(calcChangePercent(newContent, prevContent))
                .formsChange(calcChangePercent(newForms, prevForms))
                .feedbackChange(calcChangePercent(newFeedback, prevFeedback))
                .build();
    }

    private List<ReportResponse.DailyMetric> buildDailyMetrics(DateRange range) {
        List<UserAccount> users = userAccountRepository.findByCreatedAtBetween(range.start(), range.end());
        List<SimplifiedArticle> contents = simplifiedArticleRepository.findByCreatedAtBetween(range.start(), range.end());
        List<FormTemplate> forms = formTemplateRepository.findByCreatedAtBetween(range.start(), range.end());

        Map<LocalDate, long[]> bucket = initBuckets(range);

        users.stream()
                .map(UserAccount::getCreatedAt)
                .filter(date -> date != null)
                .forEach(date -> incrementBucket(bucket, date.toLocalDate(), 0));

        contents.stream()
                .map(SimplifiedArticle::getCreatedAt)
                .filter(date -> date != null)
                .forEach(date -> incrementBucket(bucket, date.toLocalDate(), 1));

        forms.stream()
                .map(FormTemplate::getCreatedAt)
                .filter(date -> date != null)
                .forEach(date -> incrementBucket(bucket, date.toLocalDate(), 2));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");
        return bucket.entrySet().stream()
                .map(entry -> ReportResponse.DailyMetric.builder()
                        .label(entry.getKey().format(formatter))
                        .users(entry.getValue()[0])
                        .content(entry.getValue()[1])
                        .forms(entry.getValue()[2])
                        .build())
                .collect(Collectors.toList());
    }

    private Map<LocalDate, long[]> initBuckets(DateRange range) {
        Map<LocalDate, long[]> bucket = new LinkedHashMap<>();
        LocalDate cursor = range.start().toLocalDate();
        LocalDate endDate = range.end().toLocalDate();
        while (!cursor.isAfter(endDate)) {
            bucket.put(cursor, new long[]{0L, 0L, 0L});
            cursor = cursor.plusDays(1);
        }
        return bucket;
    }

    private void incrementBucket(Map<LocalDate, long[]> bucket, LocalDate date, int index) {
        long[] counts = bucket.get(date);
        if (counts != null && index >= 0 && index < counts.length) {
            counts[index] = counts[index] + 1;
        }
    }

    private List<ReportResponse.CategoryShare> buildCategoryDistribution(DateRange range) {
        List<SimplifiedArticle> simplifiedArticles =
                simplifiedArticleRepository.findByCreatedAtBetween(range.start(), range.end());

        Map<String, Long> categoryCounts = simplifiedArticles.stream()
                .collect(Collectors.groupingBy(
                        sa -> normalizeLabel(sa.getCategory()),
                        Collectors.counting()
                ));

        // Fallback: dùng lawType nếu chưa có dữ liệu simplified
        if (categoryCounts.isEmpty()) {
            categoryCounts = lawRepository.findAll().stream()
                    .collect(Collectors.groupingBy(
                            law -> normalizeLabel(law.getLawType()),
                            Collectors.counting()
                    ));
        }

        return categoryCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .map(entry -> ReportResponse.CategoryShare.builder()
                        .name(entry.getKey())
                        .value(entry.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    private List<ReportResponse.TopContentItem> buildTopContents(DateRange range) {
        List<PageViewLog> views = pageViewLogRepository.findByCreatedAtBetween(range.start(), range.end());

        Map<String, Long> pathCounts = new HashMap<>();
        for (PageViewLog view : views) {
            String normalizedPath = normalizeTrackedPath(view.getPath());
            if (normalizedPath == null) {
            continue;
            }
            pathCounts.merge(normalizedPath, 1L, Long::sum);
        }

        List<ReportResponse.TopContentItem> topByView = pathCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .map(entry -> ReportResponse.TopContentItem.builder()
                .title(resolvePageTitle(entry.getKey()))
                        .views(entry.getValue())
                        .downloads(0L) // hệ thống chưa log download; giữ 0 để phản ánh thực tế
                        .build())
                .collect(Collectors.toList());

        if (!topByView.isEmpty()) {
            return topByView;
        }

        // Fallback: hiển thị 5 nội dung mới nhất (đã duyệt) nếu chưa có dữ liệu page view
        return simplifiedArticleRepository.findAllApproved().stream()
                .sorted((a, b) -> {
                    LocalDateTime aTime = Optional.ofNullable(a.getCreatedAt()).orElse(LocalDateTime.MIN);
                    LocalDateTime bTime = Optional.ofNullable(b.getCreatedAt()).orElse(LocalDateTime.MIN);
                    return bTime.compareTo(aTime);
                })
                .limit(5)
                .map(sa -> ReportResponse.TopContentItem.builder()
                        .title(resolveArticleTitle(sa))
                        .views(0L)
                        .downloads(0L)
                        .build())
                .collect(Collectors.toList());
    }

    private String resolveArticleTitle(SimplifiedArticle sa) {
        if (sa == null || sa.getArticle() == null) {
            return "Nội dung";
        }
        return Optional.ofNullable(sa.getArticle().getArticleTitle())
                .orElse("Nội dung");
    }

    private String normalizeTrackedPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }

        String normalized = rawPath.trim();

        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }

        int hashIndex = normalized.indexOf('#');
        if (hashIndex >= 0) {
            normalized = normalized.substring(0, hashIndex);
        }

        normalized = normalized.replaceAll("/+", "/");
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }

        if (normalized.startsWith("/admin") || normalized.startsWith("/moderator")) {
            return null;
        }

        return toRouteTemplate(normalized);
    }

    private String toRouteTemplate(String path) {
        String result = path;
        if (result.matches("^/user/form/detail/\\d+$")) {
            return "/user/form/detail/:id";
        }
        if (result.matches("^/form/detail/\\d+$")) {
            return "/form/detail/:id";
        }
        return result;
    }

    private String resolvePageTitle(String normalizedPath) {
        String label = PAGE_LABELS.get(normalizedPath);
        if (label != null) {
            return label;
        }

        String cleaned = normalizedPath;
        if (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isBlank()) {
            return "Trang chủ";
        }

        List<String> segments = new ArrayList<>();
        for (String segment : cleaned.split("/")) {
            if (!segment.isBlank()) {
                segments.add(segment);
            }
        }

        if (segments.isEmpty()) {
            return "Trang";
        }

        return String.join(" / ", segments);
    }

    private double calcChangePercent(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return ((double) (current - previous) / previous) * 100.0;
    }

    private DateRange resolveRange(String range) {
        String normalized = normalizeRange(range);
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start;

        switch (normalized) {
            case "month":
                start = end.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                break;
            case "quarter":
                int currentMonth = end.getMonthValue();
                int quarterStartMonth = ((currentMonth - 1) / 3) * 3 + 1;
                start = LocalDateTime.of(
                        LocalDate.of(end.getYear(), quarterStartMonth, 1),
                        java.time.LocalTime.MIDNIGHT
                );
                break;
            case "year":
                start = LocalDateTime.of(end.getYear(), 1, 1, 0, 0);
                break;
            case "week":
            default:
                LocalDate today = LocalDate.now();
                start = today.minusDays(6).atStartOfDay();
                break;
        }

        long days = ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()) + 1;
        return new DateRange(start, end, days);
    }

    private DateRange previousRange(DateRange current) {
        LocalDateTime prevEnd = current.start().minusNanos(1);
        LocalDateTime prevStart = current.start().minusDays(current.days());
        return new DateRange(prevStart, prevEnd, current.days());
    }

    private String normalizeRange(String range) {
        if (range == null || range.isBlank()) {
            return "week";
        }
        return range.trim().toLowerCase();
    }

    private String normalizeLabel(String label) {
        if (label == null || label.isBlank()) {
            return "Khác";
        }
        return label.trim();
    }

    private record DateRange(LocalDateTime start, LocalDateTime end, long days) {}
}

