/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicerequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 门诊医生站耗材签发后 FIFO 发放编排所需的请求只读切片。
 */
@Getter
@Setter
public class DeviceRequestFifoSignContext {

    /** 耗材请求主键。 */
    private Long deviceReqId;

    /** 耗材定义主键。 */
    private Long deviceDefId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 开立数量。 */
    private BigDecimal quantity;

    /** 计量单位编码。 */
    private String unitCode;

    /** 目录分类编码（耗材定义 {@code category_code}），用于签发相位 FIFO 模板解析。 */
    private String catalogCategoryCode;
}
