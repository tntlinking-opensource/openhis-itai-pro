/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application.dto;

import com.zxplat.framework.core.application.dto.BaseQueryRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleQueryRequest extends BaseQueryRequest {

    private String keyword;
    private Boolean isDefault;
    private Boolean isStatic;
}
