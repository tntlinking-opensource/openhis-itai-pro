/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.config.catalog.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * PEIS 体检项目 DTO（CFG-04）。
 */
@Data
public class PeisActivityDto {

    /** 项目主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;

    /** 项目编码（busNo）。 */
    private String code;

    /** 项目名称。 */
    private String display;

    /** 拼音检索码。 */
    private String pyStr;

    /** 项目类型编码。 */
    private String typeEnum;

    /** 状态：active / inactive（映射 PublicationStatus）。 */
    private String statusEnum;

    /** 绑定小项主键列表（元素须按字符串序列化，避免雪花 Long 在前端丢精度）。 */
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> observationDefinitionIds;

    /** 绑定小项数量（与 observationDefinitionIds 长度一致，列表列直接用）。 */
    private Integer observationCount;

    /** 执行科室机构主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;

    /** 执行科室名称（列表展示 enrich）。 */
    private String orgName;

    /** 适用性别（BOTH/MALE/FEMALE）。 */
    private String permittedGenderEnum;

    /** 目录展示顺序。 */
    private Integer sortOrder;

    /** 是否收费（pricingFlag）。 */
    private Boolean pricingFlag;

    /** 原价/零售价（SALE）。 */
    private BigDecimal salePrice;
}
