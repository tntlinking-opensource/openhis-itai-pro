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
 * 挂号类型维护界面展示（AS-BAS-MST-015）。
 *
 * <p>金额为绑定诊查费 AD 当前 SALE 合计（只读 enrich）；无 {@code ybNo} 业务字段。</p>
 */
@Data
public class RegistrationTypeDto {
    /** 医疗服务主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /** 挂号类型名称。 */
    private String name;
    /** 诊查费 ActivityDefinition 主键 CSV。 */
    private String activityIds;
    /** 已绑诊查费名称摘要（逗号拼接，只读）。 */
    private String activityNames;
    /** 业务流水号/内部编码。 */
    private String busNo;
    /** 启用标志：1 启用，0 停用。 */
    private Integer activeFlag;
    /** 绑定 AD 当前售价合计（元）；无绑为 0。 */
    private BigDecimal amount;
}
