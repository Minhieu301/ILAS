package com.C1SE10.backend.controller.user;

import com.C1SE10.backend.dto.common.ApiResponse;
import com.C1SE10.backend.dto.request.user.UserAccountRequest;
import com.C1SE10.backend.dto.response.user.UserAccountResponse;
import com.C1SE10.backend.service.log.AuditLogService;
import com.C1SE10.backend.service.user.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = {"http://localhost", "http://localhost:*", "http://127.0.0.1", "http://127.0.0.1:*"})
public class UserAccountController {

    private final UserAccountService userAccountService;
    private final AuditLogService auditLogService;

    /**
     * Lấy danh sách tất cả người dùng
     * GET /api/users
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserAccountResponse>>> getAllUsers() {
        try {
            List<UserAccountResponse> users = userAccountService.getAllUsers();
            return ResponseEntity.ok(ApiResponse.success("Lấy danh sách người dùng thành công!", users));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Lỗi khi lấy danh sách người dùng: " + e.getMessage()));
        }
    }

    /**
     * Lấy thông tin người dùng theo ID
     * GET /api/users/{userId}
     */
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserAccountResponse>> getUserById(@PathVariable Integer userId) {
        try {
            Optional<UserAccountResponse> userOpt = userAccountService.getUserById(userId);
            return userOpt.map(user ->
                    ResponseEntity.ok(ApiResponse.success("Lấy thông tin người dùng thành công!", user))
            ).orElseGet(() ->
                    ResponseEntity.ok(ApiResponse.error("Không tìm thấy người dùng với ID: " + userId))
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Lỗi khi lấy thông tin người dùng: " + e.getMessage()));
        }
    }

    /**
     * Tạo tài khoản mới (Admin)
     * POST /api/users
     */
    @PostMapping
    public ResponseEntity<ApiResponse<UserAccountResponse>> createAccount(@RequestBody UserAccountRequest request) {
        try {
            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Tên đăng nhập không được để trống"));
            }
            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Email không được để trống"));
            }
            if (userAccountService.isUsernameExists(request.getUsername())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Tên đăng nhập đã tồn tại"));
            }
            if (userAccountService.isEmailExists(request.getEmail())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Email đã tồn tại"));
            }

            UserAccountResponse created = userAccountService.createAccount(request);
            auditLogService.logSafe("Tạo tài khoản", "Tạo tài khoản username=" + created.getUsername());
            return ResponseEntity.ok(ApiResponse.success("Tạo tài khoản thành công!", created));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Lỗi khi tạo tài khoản: " + e.getMessage()));
        }
    }

    /**
     * Cập nhật thông tin tài khoản
     * PUT /api/users/{userId}
     */
    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserAccountResponse>> updateAccount(
            @PathVariable Integer userId,
            @RequestBody UserAccountRequest request) {
        try {
            if (request.getEmail() != null) {
                Optional<UserAccountResponse> currentUser = userAccountService.getUserById(userId);

                if (currentUser.isPresent()) {
                    String currentEmail = currentUser.get().getEmail();
                    if (!request.getEmail().equals(currentEmail)
                            && userAccountService.isEmailExists(request.getEmail())) {
                        return ResponseEntity.badRequest()
                                .body(ApiResponse.error("Email đã được sử dụng bởi tài khoản khác"));
                    }
                }
            }


            UserAccountResponse updated = userAccountService.updateAccount(userId, request);
            auditLogService.logSafe("Cập nhật tài khoản", "Cập nhật tài khoản id=" + userId);
            return ResponseEntity.ok(ApiResponse.success("Cập nhật tài khoản thành công!", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Lỗi khi cập nhật tài khoản: " + e.getMessage()));
        }
    }

    /**
     * Xóa tài khoản
     * DELETE /api/users/{userId}
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<String>> deleteAccount(@PathVariable Integer userId) {
        try {
            boolean deleted = userAccountService.deleteAccount(userId);
            auditLogService.logSafe("Xóa tài khoản", "Xóa tài khoản id=" + userId);
            if (deleted) {
                return ResponseEntity.ok(ApiResponse.success("Xóa tài khoản thành công!", "Người dùng đã bị xóa"));
            } else {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Không tìm thấy người dùng để xóa"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Lỗi khi xóa tài khoản: " + e.getMessage()));
        }
    }

    /**
     * Kiểm tra email có tồn tại không
     * GET /api/users/check-email?email={email}
     */
    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<Boolean>> checkEmail(@RequestParam String email) {
        try {
            boolean exists = userAccountService.isEmailExists(email);
            return ResponseEntity.ok(ApiResponse.success("Kiểm tra email thành công!", exists));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Lỗi khi kiểm tra email: " + e.getMessage()));
        }
    }

    /**
     * Kiểm tra username có tồn tại không
     * GET /api/users/check-username?username={username}
     */
    @GetMapping("/check-username")
    public ResponseEntity<ApiResponse<Boolean>> checkUsername(@RequestParam String username) {
        try {
            boolean exists = userAccountService.isUsernameExists(username);
            return ResponseEntity.ok(ApiResponse.success("Kiểm tra username thành công!", exists));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Lỗi khi kiểm tra username: " + e.getMessage()));
        }
    }
}
