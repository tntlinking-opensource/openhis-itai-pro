/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.setting.application.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设置DTO
 *
 * @author Whale Framework
 */
@Data
public class SettingDto {

    private Long id;
    private String name;
    private String value;
    private String providerName;
    private String providerKey;
    private Long tenantId;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
