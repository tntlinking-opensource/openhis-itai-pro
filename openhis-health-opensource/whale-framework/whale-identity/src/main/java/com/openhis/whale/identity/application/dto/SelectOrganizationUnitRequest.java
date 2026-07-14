/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.application.dto;

import lombok.Data;

/**
 * 选择科室请求
 */
@Data
public class SelectOrganizationUnitRequest {

    /**
     * 组织单元ID（科室ID）
     */
    private Long organizationUnitId;
}
