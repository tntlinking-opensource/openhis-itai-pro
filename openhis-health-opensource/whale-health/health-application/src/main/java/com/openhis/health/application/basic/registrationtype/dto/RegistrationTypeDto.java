/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.registrationtype.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 挂号类型维护界面展示：底层为医疗服务主数据，附带销售价金额（元，来自收费定义行解析）。
 */
@Data
public class RegistrationTypeDto {
    /** 医疗服务主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /** 挂号类型名称。 */
    private String name;
    /** 医保目录编码或对照码。 */
    private String ybNo;
    /** 业务流水号/内部编码。 */
    private String busNo;
    /** 启用标志：1 启用，0 停用。 */
    private Integer activeFlag;
    /** 挂号费销售金额（元，两位小数），无定价行时为 {@code null}。 */
    private BigDecimal amount;
}
