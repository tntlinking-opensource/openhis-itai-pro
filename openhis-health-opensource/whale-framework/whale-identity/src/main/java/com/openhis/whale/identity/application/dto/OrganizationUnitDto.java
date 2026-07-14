/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 组织单元DTO
 */
@Data
public class OrganizationUnitDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String code;
    private String displayName;
    private Long parentId;
    private String parentDisplayName;
    private Integer sortOrder;
    private Boolean isEnabled;
    private String remark;
    private Long tenantId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 子组织单元列表
     */
    private List<OrganizationUnitDto> children;
}
