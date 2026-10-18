/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建自定义角色请求（内置角色由种子提供，不可通过本接口创建）。
 */
@Data
public class CreateRoleRequest {

    @NotBlank(message = "角色名称不能为空")
    private String displayName;

    private String description;

    private Boolean isDefault = false;
}
