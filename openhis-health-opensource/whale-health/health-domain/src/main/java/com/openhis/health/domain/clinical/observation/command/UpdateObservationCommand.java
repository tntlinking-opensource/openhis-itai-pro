/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.observation.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 更新临床观察实例命令（DM-CLN-007 §5.1.2）。
 */
@Getter
@Setter
public class UpdateObservationCommand {

    /** 观察实例主键。 */
    private Long observationId;

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
