/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 角色声明DTO
 */
@Data
public class RoleClaimDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private Long roleId;
    private String claimType;
    private String claimValue;
    private Long tenantId;
    private LocalDateTime createdAt;
}
