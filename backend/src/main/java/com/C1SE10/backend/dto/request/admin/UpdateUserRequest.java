package com.C1SE10.backend.dto.request.admin;

import lombok.Data;

@Data
public class UpdateUserRequest {
    private String fullName;
    private String email;
    private String role;
}
