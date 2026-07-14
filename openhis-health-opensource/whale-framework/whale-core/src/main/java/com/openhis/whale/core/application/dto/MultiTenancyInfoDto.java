/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application.dto;

import lombok.Data;

/**
 * 多租户信息DTO
 */
@Data
public class MultiTenancyInfoDto {

    /**
     * 是否启用多租户
     */
    private Boolean isEnabled;
}
