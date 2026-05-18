package com.C1SE10.backend.service.log;

import com.C1SE10.backend.model.AuditLog;
import com.C1SE10.backend.model.UserAccount;
import com.C1SE10.backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Lấy UserAccount từ SecurityContext hiện tại.
     * Trả về null nếu không có hoặc principal không phải UserAccount.
     */
    public static UserAccount currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserAccount ua) return ua;
        return null;
    }

    /**
     * Ghi audit log với user lấy tự động từ SecurityContext.
     * Bọc trong try-catch để không làm lỗi luồng chính.
     */
    public void logSafe(String action, String detail) {
        try {
            log(action, detail, currentUser());
        } catch (Exception ignored) {}
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String detail, UserAccount user) {
        if (action == null || action.isBlank()) {
            return;
        }
        String normalizedAction = action.trim();
        String normalizedDetail = detail == null ? null : detail.trim();
        AuditLog log = AuditLog.builder()
                .action(normalizedAction.length() > 150 ? normalizedAction.substring(0, 150) : normalizedAction)
                .detail(normalizedDetail)
                .user(user)
                .build();
        auditLogRepository.save(log);
    }

    private Map<String, Object> toMap(AuditLog a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("action", a.getAction());
        m.put("detail", a.getDetail());
        m.put("createdAt", a.getCreatedAt() == null ? null : a.getCreatedAt().toString());
        m.put("userId", a.getUser().getUserId());
        m.put("username", a.getUser().getUsername());
        m.put("fullName", a.getUser().getFullName());
        m.put("roleId", a.getUser().getRole().getRoleId());
        m.put("roleName", a.getUser().getRole().getRoleName());
        return m;
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<Map<String, Object>> getRecent() {
        // Fetch a recent window then build lightweight DTOs (maps) including user info.
        List<AuditLog> recent = auditLogRepository.findTop20ByOrderByCreatedAtDesc();

        // Predicate chung: chỉ lấy Admin/Moderator (roleId 2 hoặc 3)
        var isAdminOrMod = (java.util.function.Predicate<AuditLog>) a ->
                a.getUser() != null && a.getUser().getRole() != null &&
                (Integer.valueOf(2).equals(a.getUser().getRole().getRoleId()) ||
                 Integer.valueOf(3).equals(a.getUser().getRole().getRoleId()));

        List<Map<String, Object>> nonLogin = recent.stream()
                .filter(isAdminOrMod)
                .filter(a -> {
                    String act = a.getAction() == null ? "" : a.getAction().toLowerCase();
                    return !act.contains("đăng nhập");
                })
                .map(this::toMap)
                .limit(5)
                .collect(Collectors.toList());

        if (nonLogin.size() >= 5) {
            return nonLogin;
        }

        // Fill with login entries if not enough
        List<Map<String, Object>> loginEntries = recent.stream()
                .filter(isAdminOrMod)
                .filter(a -> {
                    String act = a.getAction() == null ? "" : a.getAction().toLowerCase();
                    return act.contains("đăng nhập");
                })
                .map(this::toMap)
                .collect(Collectors.toList());

        // Combine and trim to 5
        List<Map<String, Object>> combined = new ArrayList<>(nonLogin);
        for (Map<String, Object> m : loginEntries) {
            if (combined.size() >= 5) break;
            combined.add(m);
        }
        return combined;
    }
}


