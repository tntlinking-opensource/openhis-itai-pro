/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.permission.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 获取权限授予信息请求
 */
@Data
public class GetGrantsRequest {

    @NotBlank(message = "权限名称不能为空")
    private String permissionName;
}
