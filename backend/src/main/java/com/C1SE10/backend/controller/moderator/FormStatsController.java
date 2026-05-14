package com.C1SE10.backend.controller.moderator;

import com.C1SE10.backend.model.FormTemplate;
import com.C1SE10.backend.repository.FormTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/moderator/form-stats")
@CrossOrigin("*")
@RequiredArgsConstructor
public class FormStatsController {

    private final FormTemplateRepository formRepo;

    @GetMapping("/{moderatorId}")
    public ResponseEntity<?> getFormStats(@PathVariable Integer moderatorId) {
        Map<String, Object> data = new HashMap<>();

        // Lấy tất cả form của moderator
        List<FormTemplate> forms = formRepo.findByModerator_UserId(moderatorId);

        // Đếm theo trạng thái
        Map<String, Long> stats = new LinkedHashMap<>();
        for (FormTemplate.Status st : FormTemplate.Status.values()) {
            long count = forms.stream().filter(f -> f.getStatus() == st).count();
            stats.put(st.name().toLowerCase(), count);
        }
        data.put("stats", stats);

        // 5 form gần đây nhất
        List<Map<String, Object>> recentForms = forms.stream()
                .sorted(Comparator.comparing(FormTemplate::getCreatedAt).reversed())
                .limit(5)
                .map(f -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("title", f.getTitle());
                    m.put("status", f.getStatus().name());
                    m.put("createdAt", f.getCreatedAt());
                    return m;
                })
                .collect(Collectors.toList());
        data.put("recentForms", recentForms);

        // Dữ liệu biểu đồ theo tháng (group by tháng)
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/yyyy");
        Map<String, Map<String, Long>> grouped = new TreeMap<>();

        for (FormTemplate f : forms) {
            if (f.getCreatedAt() == null) continue;
            String month = f.getCreatedAt().format(fmt);
            grouped.putIfAbsent(month, new HashMap<>());
            Map<String, Long> monthData = grouped.get(month);
            String st = f.getStatus().name().toLowerCase();
            monthData.put(st, monthData.getOrDefault(st, 0L) + 1);
        }

        // Chuẩn hóa về dạng List cho biểu đồ
        List<Map<String, Object>> monthlyData = grouped.entrySet().stream()
                .map(e -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("month", e.getKey());
                    map.put("draft", e.getValue().getOrDefault("draft", 0L));
                    map.put("pending", e.getValue().getOrDefault("pending", 0L));
                    map.put("approved", e.getValue().getOrDefault("approved", 0L));
                    map.put("rejected", e.getValue().getOrDefault("rejected", 0L));
                    return map;
                })
                .sorted(Comparator.comparing(m -> (String) m.get("month")))
                .collect(Collectors.toList());

        data.put("monthlyData", monthlyData);

        return ResponseEntity.ok(data);
    }
}

