/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.registrationtype.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增挂号类型请求：落库为医疗服务创建 + 关联实例定价（销售金额，分）由应用服务二次写入。
 */
@Data
public class CreateRegistrationTypeRequest {
    /** 挂号类型显示名称。 */
    @NotBlank(message = "挂号类型名称不能为空")
    @Size(max = 255, message = "名称长度不能超过255")
    private String name;

    /** 医保侧编码或对照字段。 */
    @Size(max = 255, message = "医保码长度不能超过255")
    private String ybNo;

    /**
     * 挂号类型编码（adm_healthcare_service.type_code），未传时由应用层兜底为"1"。
     */
    @Size(max = 64, message = "挂号类型编码长度不能超过64")
    private String typeCode;

    /** 挂号费销售金额（元）；新建时必填，由应用服务同步写入定价。 */
    private BigDecimal amount;
}
