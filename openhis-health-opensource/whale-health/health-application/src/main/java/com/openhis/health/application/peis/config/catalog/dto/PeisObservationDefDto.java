/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.config.catalog.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * PEIS 体检小项 DTO（CFG-03）。
 */
@Data
public class PeisObservationDefDto {

    /** 小项主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long definitionId;

    /** 小项编码。 */
    private String code;

    /** 小项名称。 */
    private String display;

    /** 值类型。 */
    private String valueTypeEnum;

    /** 分类编码。 */
    private String categoryCode;

    /** 单位编码。 */
    private String unitCode;

    /** 状态：active / inactive。 */
    private String statusEnum;

    /** 执行科室机构主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;

    /** 执行科室名称（列表展示 enrich）。 */
    private String orgName;

    /** 被多少个体检项目引用（未软删绑定，按 AD 去重）。 */
    private Integer linkedActivityCount;
}
