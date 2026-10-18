/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.config.diagnosis.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * PEIS 诊断建议库模板 DTO（CFG-08）。
 */
@Data
public class PeisDiagnosisTemplateDto {

    /** 诊断定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long definitionId;

    /** 诊断编码。 */
    private String code;

    /** 诊断名称。 */
    private String display;

    /** 分类编码。 */
    private String categoryCode;

    /** 状态：active / inactive。 */
    private String statusEnum;
}
