/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.specimentubemerge.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 合管耗材绑定行 DTO（与前端 consumableLines 契约对齐）。
 */
@Data
public class SpecimenTubeMergeConsumableLineDto {

    /** 绑定行主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 耗材定义主键 {@code adm_device_definition.id}。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;

    /** 耗材名称（enrich）。 */
    private String targetName;

    /** 合管用量。 */
    private BigDecimal quantity;

    /** 计量单位 code。 */
    private String unitCode;

    /** 计量单位展示名（前端字段 unitName）。 */
    private String unitName;

    /** 展示排序。 */
    private Integer displayOrder;
}
