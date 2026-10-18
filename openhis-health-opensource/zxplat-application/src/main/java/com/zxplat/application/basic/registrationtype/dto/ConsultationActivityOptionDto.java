/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.registrationtype.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 诊查费候选项（挂号类型弹窗多选，IF-BAS-REG-02）。
 */
@Data
public class ConsultationActivityOptionDto {
    /** ActivityDefinition 主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /** 项目名称。 */
    private String name;
    /** 业务编码。 */
    private String busNo;
    /** 当前 SALE 售价（元）；无价为 null。 */
    private BigDecimal salePrice;
}
