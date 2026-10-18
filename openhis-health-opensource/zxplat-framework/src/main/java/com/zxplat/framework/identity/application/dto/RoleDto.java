/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 角色DTO
 */
@Data
public class RoleDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String name;
    private String displayName;
    private String code;
    private String description;
    private Boolean isDefault;
    private Boolean isStatic;
    private Long tenantId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
}
