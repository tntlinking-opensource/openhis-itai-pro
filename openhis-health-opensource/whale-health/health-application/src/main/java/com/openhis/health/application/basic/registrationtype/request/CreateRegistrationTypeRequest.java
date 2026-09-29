/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.registrationtype.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增挂号类型请求（AS-BAS-MST-015 IF-BAS-REG-03）。
 *
 * <p>金额不入写请求：展示金额由绑定诊查费 AD 当前售价汇总（BR-BAS-REG-004/005）。</p>
 */
@Data
public class CreateRegistrationTypeRequest {
    /** 挂号类型显示名称。 */
    @NotBlank(message = "挂号类型名称不能为空")
    @Size(max = 255, message = "名称长度不能超过255")
    private String name;

    /**
     * 诊查费 ActivityDefinition 主键 CSV（逗号分隔）；零元号可空（BR-BAS-REG-002/003）。
     */
    @Size(max = 512, message = "诊查费绑定串长度不能超过512")
    private String activityIds;

    /**
     * 挂号类型编码（adm_healthcare_service.type_code），未传时由应用层兜底为"1"。
     */
    @Size(max = 64, message = "挂号类型编码长度不能超过64")
    private String typeCode;
}
