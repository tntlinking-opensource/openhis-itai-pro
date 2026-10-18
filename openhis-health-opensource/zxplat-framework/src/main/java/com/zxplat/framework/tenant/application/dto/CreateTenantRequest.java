/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.tenant.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建租户请求
 *
 * @author OpenHIS Contributors
 */
@Data
public class CreateTenantRequest {

    @NotBlank(message = "租户名称不能为空")
    @Size(max = 128, message = "租户名称长度不能超过128个字符")
    private String name;

    @NotBlank(message = "租户代码不能为空")
    @Size(max = 64, message = "租户代码长度不能超过64个字符")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "租户代码只能包含字母、数字、下划线和连字符")
    private String code;

    @Size(max = 512, message = "描述长度不能超过512个字符")
    private String description;

    private Boolean isActive;

    private String extraProperties;
}
