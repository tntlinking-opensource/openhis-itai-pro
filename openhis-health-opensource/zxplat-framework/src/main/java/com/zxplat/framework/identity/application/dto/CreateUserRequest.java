/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建用户请求
 */
@Data
public class CreateUserRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 256, message = "用户名长度必须在3-256之间")
    private String userName;

    private String name;  // 名

    private String surname;  // 姓

    @Email(message = "邮箱格式不正确")
    private String email;  // 可选字段

    private String phoneNumber;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, message = "密码长度至少8位")
    private String password;

    private Boolean isActive = true;

    private String extraProperties;
}
