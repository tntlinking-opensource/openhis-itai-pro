/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.application.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 分配角色请求
 */
@Data
public class AssignRolesRequest {

    @NotEmpty(message = "角色ID列表不能为空")
    private List<Long> roleIds;
}
