/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.patient.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 患者简要信息展示 DTO（列表或检索摘要）。
 *
 */
@Data
public class PatientSummaryDto {
    /** 患者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;
    /** 姓名。 */
    private String patientName;
    /** 性别（历史字段：可能为字典整数码，语义以前端约定为准）。 */
    private Integer gender;
    /** 证件号摘要展示。 */
    private String idCard;
}
