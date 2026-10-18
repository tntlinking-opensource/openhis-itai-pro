/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.tenant.application.dto;

import com.zxplat.framework.core.application.dto.BaseQueryRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户查询请求
 *
 * @author OpenHIS Contributors
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantQueryRequest extends BaseQueryRequest {

    /**
     * 关键词（名称或代码）
     */
    private String keyword;

    /**
     * 租户代码
     */
    private String code;

    /**
     * 是否激活
     */
    private Boolean isActive;
}
