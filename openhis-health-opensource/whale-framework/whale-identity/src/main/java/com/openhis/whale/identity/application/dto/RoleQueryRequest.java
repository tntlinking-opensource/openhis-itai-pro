/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.application.dto;

import com.openhis.whale.core.application.dto.BaseQueryRequest;
import lombok.Data;

/**
 * 角色查询请求
 */
@Data
public class RoleQueryRequest  extends BaseQueryRequest {

    private String keyword;
    private Boolean isDefault;
    private Boolean isStatic;
}
