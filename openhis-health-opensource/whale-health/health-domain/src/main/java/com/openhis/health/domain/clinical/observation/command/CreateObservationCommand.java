/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.observation.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 新建临床观察实例命令（DM-CLN-007 §5.1.1）。
 */
@Getter
@Setter
public class CreateObservationCommand {

    /** 租户标识。 */
    private Long tenantId;

    /** 机构标识。 */
    private Long orgId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 服务申请主键。 */
    private Long serviceRequestId;

    /** 观察定义主键。 */
    private Long definitionId;

    /** 观察项编码（可选冗余）。 */
    private String code;

    /** 数值结果。 */
    private BigDecimal valueQuantity;

    /** 文本结果。 */
    private String valueString;

    /** 编码概念结果。 */
    private String valueCodeableConcept;

    /** 解读编码。 */
    private String interpretationCode;

    /** 执行人主键。 */
    private Long performerId;
}
