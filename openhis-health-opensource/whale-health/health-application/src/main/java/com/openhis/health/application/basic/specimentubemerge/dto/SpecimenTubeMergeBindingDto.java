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
 * 标本合管绑定行 DTO（内部 enrich 用，检验项目或耗材）。
 */
@Data
public class SpecimenTubeMergeBindingDto {

    /** 绑定行主键 {@code cus_specimen_tube_merge.id}。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 目标表：{@code wkf_activity_definition} 或 {@code adm_device_definition}。 */
    private String targetTable;

    /** 目标主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;

    /** 目标业务编码。 */
    private String targetBusNo;

    /** 目标展示名。 */
    private String targetName;

    /** 耗材用量；activity 行时为 null。 */
    private BigDecimal quantity;

    /** 计量单位 code。 */
    private String unitCode;

    /** 计量单位展示文案。 */
    private String unitCodeText;

    /** 同设备下展示排序。 */
    private Integer displayOrder;
}
