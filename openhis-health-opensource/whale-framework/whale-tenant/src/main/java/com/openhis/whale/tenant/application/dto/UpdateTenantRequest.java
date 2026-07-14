/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.tenant.application.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新租户请求
 *
 * @author Whale Framework
 */
@Data
public class UpdateTenantRequest {

    @Size(max = 128, message = "租户名称长度不能超过128个字符")
    private String name;

    @Size(max = 512, message = "描述长度不能超过512个字符")
    private String description;

    private Boolean isActive;

    private String extraProperties;
}
