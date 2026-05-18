package com.C1SE10.backend.controller.admin;

import com.C1SE10.backend.dto.request.admin.CreateUserRequest;
import com.C1SE10.backend.dto.request.admin.UpdateUserRequest;
import com.C1SE10.backend.dto.response.admin.UserResponseDTO;
import com.C1SE10.backend.service.admin.AdminUserService;

import lombok.RequiredArgsConstructor;
import com.C1SE10.backend.service.log.AuditLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AuditLogService auditLogService;

    @GetMapping
    public List<UserResponseDTO> getUsers() {
        return adminUserService.getAllUsers();
    }

    @PostMapping
    public UserResponseDTO createUser(@RequestBody CreateUserRequest req) {
        UserResponseDTO created = adminUserService.createUser(req);
        auditLogService.logSafe("Tạo người dùng", "Tạo user: " + created.getEmail());
        return created;
    }

    @PutMapping("/{id}")
    public UserResponseDTO updateUser(@PathVariable Integer id, @RequestBody UpdateUserRequest req) {
        UserResponseDTO updated = adminUserService.updateUser(id, req);
        auditLogService.logSafe("Cập nhật người dùng", "Cập nhật user id=" + id);
        return updated;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Integer id) {
        adminUserService.deleteUser(id);
        auditLogService.logSafe("Xóa người dùng", "Xóa user id=" + id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/toggle")
    public ResponseEntity<?> toggleStatus(@PathVariable Integer id) {
        adminUserService.toggleStatus(id);
        auditLogService.logSafe("Đổi trạng thái người dùng", "Toggle trạng thái user id=" + id);
        return ResponseEntity.ok().build();
    }
}
