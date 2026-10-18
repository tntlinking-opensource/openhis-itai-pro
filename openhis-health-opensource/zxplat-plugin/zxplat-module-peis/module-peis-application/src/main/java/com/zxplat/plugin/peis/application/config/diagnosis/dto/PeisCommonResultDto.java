/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.config.diagnosis.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * PEIS 常见结果 DTO（CFG-09）。
 */
@Data
public class PeisCommonResultDto {

    /** 常见结果主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 绑定小项观察定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long observationDefinitionId;

    /** 科室机构主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deptOrgId;

    /** 模板名称。 */
    private String display;

    /** 默认结果值。 */
    private String valueString;

    /** 解读码。 */
    private String interpretationCode;

    /** 排序号。 */
    private Integer sortNum;

    /** 状态：active / inactive。 */
    private String statusEnum;
}
