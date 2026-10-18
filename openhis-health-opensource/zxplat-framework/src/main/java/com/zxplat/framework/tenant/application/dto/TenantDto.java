/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.tenant.application.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户DTO
 *
 * @author OpenHIS Contributors
 */
@Data
public class TenantDto {

    private Long id;
    private String name;
    private String code;
    private String description;
    private Boolean isActive;
    private String extraProperties;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
