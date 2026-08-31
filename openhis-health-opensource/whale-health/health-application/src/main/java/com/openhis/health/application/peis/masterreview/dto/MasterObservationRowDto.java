/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.masterreview.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 总检汇总观察行 DTO。
 */
@Data
public class MasterObservationRowDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 观察实例主键。 */
    private Long id;

    /** 服务申请主键。 */
    private Long serviceRequestId;

    /** 观察定义主键。 */
    private Long definitionId;

    /** 分类编码（含 dept_conclusion）。 */
    private String categoryCode;

    /** 小项展示名。 */
    private String definitionDisplay;

    /** 数值结果。 */
    private BigDecimal valueQuantity;

    /** 文本结果。 */
    private String valueString;

    /** 解读码。 */
    private String interpretationCode;

    /** 状态。 */
    private String statusEnum;
}
