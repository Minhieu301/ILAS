package com.C1SE10.backend.service.moderator;

import com.C1SE10.backend.dto.response.moderator.DiffLineDTO;
import com.C1SE10.backend.dto.response.moderator.LawDiffDTO;
import com.C1SE10.backend.dto.response.moderator.LawVersionSummaryDTO;
import com.C1SE10.backend.model.Article;
import com.C1SE10.backend.model.Law;
import com.C1SE10.backend.model.LawVersion;
import com.C1SE10.backend.repository.ArticleRepository;
import com.C1SE10.backend.repository.LawRepository;
import com.C1SE10.backend.repository.LawVersionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LawDiffService {

    private final LawRepository lawRepository;
    private final LawVersionRepository lawVersionRepository;
    private final ArticleRepository articleRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public LawVersion saveSnapshot(Integer lawId, Integer changedBy, String changeNote) {
        try {
            Law law = lawRepository.findById(lawId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy văn bản luật với id: " + lawId));

            List<Article> articles = articleRepository.findByLawId(lawId);
            List<Map<String, Object>> snapshot = new ArrayList<>();

            for (Article article : articles) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("articleId", article.getArticleId());
                item.put("articleNumber", article.getArticleNumber());
                item.put("articleTitle", article.getArticleTitle());
                item.put("content", article.getContent());
                item.put("status", article.getStatus());
                snapshot.add(item);
            }

            String snapshotJson = objectMapper.writeValueAsString(snapshot);

            List<LawVersion> versions = lawVersionRepository.findAllByLawIdOrderByVersionNumberDesc(lawId);
            int nextVersion = versions.isEmpty() ? 1 : versions.get(0).getVersionNumber() + 1;

            LawVersion lawVersion = LawVersion.builder()
                    .lawId(lawId)
                    .versionNumber(nextVersion)
                    .title(law.getTitle())
                    .snapshotJson(snapshotJson)
                    .changedBy(changedBy)
                    .changeNote(changeNote)
                    .build();

            return lawVersionRepository.save(lawVersion);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Không thể lưu snapshot phiên bản văn bản luật: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public LawDiffDTO getDiff(Integer lawId) {
        List<LawVersion> versions = lawVersionRepository.findAllByLawIdOrderByVersionNumberDesc(lawId);
        if (versions.size() < 2) {
            throw new RuntimeException("Cần ít nhất 2 phiên bản để so sánh");
        }
        return buildDiff(lawId, versions.get(1), versions.get(0));
    }

    @Transactional(readOnly = true)
    public LawDiffDTO getDiffBetween(Integer lawId, Integer v1, Integer v2) {
        LawVersion version1 = lawVersionRepository.findByLawIdAndVersionNumber(lawId, v1)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiên bản " + v1 + " của văn bản luật id: " + lawId));
        LawVersion version2 = lawVersionRepository.findByLawIdAndVersionNumber(lawId, v2)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiên bản " + v2 + " của văn bản luật id: " + lawId));
        return buildDiff(lawId, version1, version2);
    }

    @Transactional(readOnly = true)
    public List<LawVersionSummaryDTO> getHistory(Integer lawId) {
        List<LawVersion> versions = lawVersionRepository.findAllByLawIdOrderByVersionNumberDesc(lawId);
        List<LawVersionSummaryDTO> result = new ArrayList<>();
        for (LawVersion version : versions) {
            result.add(LawVersionSummaryDTO.builder()
                    .versionId(version.getVersionId())
                    .versionNumber(version.getVersionNumber())
                    .changeNote(version.getChangeNote())
                    .changedBy(version.getChangedBy())
                    .createdAt(version.getCreatedAt())
                    .build());
        }
        return result;
    }

    private LawDiffDTO buildDiff(Integer lawId, LawVersion oldV, LawVersion newV) {
        Law law = lawRepository.findById(lawId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy văn bản luật với id: " + lawId));

        List<Map<String, Object>> oldSnapshot = parseSnapshot(oldV.getSnapshotJson());
        List<Map<String, Object>> newSnapshot = parseSnapshot(newV.getSnapshotJson());

        Map<String, Map<String, Object>> oldMap = indexByArticleNumber(oldSnapshot);
        Map<String, Map<String, Object>> newMap = indexByArticleNumber(newSnapshot);

        Set<String> allNumbers = new LinkedHashSet<>();
        allNumbers.addAll(oldMap.keySet());
        allNumbers.addAll(newMap.keySet());

        List<LawDiffDTO.ArticleDiffDTO> articleDiffs = new ArrayList<>();
        int totalAdded = 0;
        int totalDeleted = 0;
        int totalEqual = 0;

        for (String articleNumber : allNumbers) {
            Map<String, Object> oldArticle = oldMap.get(articleNumber);
            Map<String, Object> newArticle = newMap.get(articleNumber);

            if (oldArticle == null && newArticle != null) {
                totalAdded++;
                articleDiffs.add(LawDiffDTO.ArticleDiffDTO.builder()
                        .articleNumber(articleNumber)
                        .articleTitle(str(newArticle, "articleTitle"))
                        .diffStatus("ADDED")
                        .lines(wrapLines(str(newArticle, "content"), "INSERT"))
                        .build());
                continue;
            }

            if (newArticle == null && oldArticle != null) {
                totalDeleted++;
                articleDiffs.add(LawDiffDTO.ArticleDiffDTO.builder()
                        .articleNumber(articleNumber)
                        .articleTitle(str(oldArticle, "articleTitle"))
                        .diffStatus("DELETED")
                        .lines(wrapLines(str(oldArticle, "content"), "DELETE"))
                        .build());
                continue;
            }

            String oldContent = str(oldArticle, "content");
            String newContent = str(newArticle, "content");

            if (Objects.equals(oldContent, newContent)) {
                totalEqual++;
                continue;
            }

            articleDiffs.add(LawDiffDTO.ArticleDiffDTO.builder()
                    .articleNumber(articleNumber)
                    .articleTitle(str(newArticle, "articleTitle"))
                    .diffStatus("MODIFIED")
                    .lines(computeLineDiff(oldContent, newContent))
                    .build());
        }

        return LawDiffDTO.builder()
                .lawId(lawId)
                .lawTitle(law.getTitle())
                .lawCode(law.getCode())
                .oldVersion(oldV.getVersionNumber())
                .newVersion(newV.getVersionNumber())
                .oldCreatedAt(oldV.getCreatedAt())
                .newCreatedAt(newV.getCreatedAt())
                .changeNote(Optional.ofNullable(newV.getChangeNote()).orElse(""))
                .totalAdded(totalAdded)
                .totalDeleted(totalDeleted)
                .totalEqual(totalEqual)
                .articleDiffs(articleDiffs)
                .build();
    }

    private List<DiffLineDTO> computeLineDiff(String oldText, String newText) {
        String[] oldLines = (oldText == null ? "" : oldText).split("\\n", -1);
        String[] newLines = (newText == null ? "" : newText).split("\\n", -1);

        int m = oldLines.length;
        int n = newLines.length;
        int[][] lcs = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (oldLines[i - 1].equals(newLines[j - 1])) {
                    lcs[i][j] = lcs[i - 1][j - 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i - 1][j], lcs[i][j - 1]);
                }
            }
        }

        Deque<DiffLineDTO> stack = new ArrayDeque<>();
        int i = m;
        int j = n;

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldLines[i - 1].equals(newLines[j - 1])) {
                stack.push(DiffLineDTO.builder()
                        .type("EQUAL")
                        .content(oldLines[i - 1])
                        .lineNumber(i)
                        .build());
                i--;
                j--;
            } else if (j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j])) {
                stack.push(DiffLineDTO.builder()
                        .type("INSERT")
                        .content(newLines[j - 1])
                        .lineNumber(j)
                        .build());
                j--;
            } else if (i > 0) {
                stack.push(DiffLineDTO.builder()
                        .type("DELETE")
                        .content(oldLines[i - 1])
                        .lineNumber(i)
                        .build());
                i--;
            }
        }

        List<DiffLineDTO> result = new ArrayList<>();
        while (!stack.isEmpty()) {
            result.add(stack.pop());
        }
        return result;
    }

    private List<DiffLineDTO> wrapLines(String content, String type) {
        String[] lines = (content == null ? "" : content).split("\\n", -1);
        List<DiffLineDTO> result = new ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            result.add(DiffLineDTO.builder()
                    .type(type)
                    .content(lines[index])
                    .lineNumber(index + 1)
                    .build());
        }
        return result;
    }

    private List<Map<String, Object>> parseSnapshot(String json) {
        try {
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Map<String, Map<String, Object>> indexByArticleNumber(List<Map<String, Object>> items) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String articleNumber = str(item, "articleNumber");
            if (!articleNumber.isBlank()) {
                result.put(articleNumber, item);
            }
        }
        return result;
    }

    private String str(Map<String, Object> map, String key) {
        if (map == null) {
            return "";
        }
        Object value = map.get(key);
        return Objects.toString(value, "");
    }
}