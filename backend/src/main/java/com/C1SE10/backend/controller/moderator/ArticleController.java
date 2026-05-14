package com.C1SE10.backend.controller.moderator;

import com.C1SE10.backend.model.Article;
import com.C1SE10.backend.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;
import java.util.*;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
@CrossOrigin("*")
public class ArticleController {

    private final ArticleRepository articleRepo;

    @GetMapping
    public ResponseEntity<?> getActiveArticles() {
        // Lấy chỉ các điều luật có status = 'active'
        List<Article> activeList = articleRepo.findByStatus("active");

        // Trả về danh sách DTO rút gọn, không trả trực tiếp entity để tránh lỗi Lazy
        List<Map<String, Object>> response = activeList.stream()
            .map(a -> {
                String raw = a.getContent();
                String preview = (raw != null && raw.length() > 200)
                        ? raw.substring(0, 200) + "..."
                        : raw;

                Integer lawId = null;
                String lawTitle = null;
                if (a.getLaw() != null) {
                    lawId = a.getLaw().getLawId();
                    lawTitle = a.getLaw().getTitle();
                }

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("articleId", a.getArticleId());
                row.put("articleNumber", a.getArticleNumber());
                row.put("articleTitle", a.getArticleTitle());
                row.put("content", preview);
                row.put("lawId", lawId);
                row.put("lawTitle", lawTitle);

                return row;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }



    // Lấy điều luật cụ thể theo ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Integer id) {
        return articleRepo.findById(id)
                .map(a -> {
                    Integer lawId = null;
                    String lawTitle = null;
                    if (a.getLaw() != null) {
                        lawId = a.getLaw().getLawId();
                        lawTitle = a.getLaw().getTitle();
                    }

                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("articleId", a.getArticleId());
                    row.put("articleNumber", a.getArticleNumber());
                    row.put("articleTitle", a.getArticleTitle());
                    row.put("content", a.getContent());
                    row.put("lawId", lawId);
                    row.put("lawTitle", lawTitle);
                    return row;
                })
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

