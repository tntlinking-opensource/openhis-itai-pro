/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.observation.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 临床观察实例读模型（DM-CLN-007 §4.6.1）。
 *
 * <p>{@code definitionDisplay}/{@code categoryCode} 由 AppService enrich，本 QS 不跨聚合 join OD。</p>
 */
@Getter
@Setter
public class ObservationModel {

    /** 观察实例主键。 */
    private Long id;

    /** 就诊主键。 */
    private Long encounterId;

    /** 服务申请主键。 */
    private Long serviceRequestId;

    /** 观察定义主键。 */
    private Long definitionId;

    /** 观察项展示名（App enrich）。 */
    private String definitionDisplay;

    /** 观察项分类（App enrich）。 */
    private String categoryCode;

    /** 数值结果。 */
    private BigDecimal valueQuantity;

    /** 文本结果。 */
    private String valueString;

    /** 解读编码。 */
    private String interpretationCode;

    /** 状态 code。 */
    private String statusEnum;

    /** 来源观察实例主键。 */
    private Long sourceObservationId;

    /** 来源模块 code。 */
    private String sourceModuleEnum;
}
