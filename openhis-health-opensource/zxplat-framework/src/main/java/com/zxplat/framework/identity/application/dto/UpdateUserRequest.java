/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

/**
 * 更新用户请求
 */
@Data
public class UpdateUserRequest {

    private String name;  // 名

    private String surname;  // 姓

    @Email(message = "邮箱格式不正确")
    private String email;

    private String phoneNumber;

    private Boolean isActive;

    private String extraProperties;
}
