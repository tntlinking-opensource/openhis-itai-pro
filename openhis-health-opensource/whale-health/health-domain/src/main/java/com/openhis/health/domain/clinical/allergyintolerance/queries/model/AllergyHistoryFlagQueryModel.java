/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.allergyintolerance.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 过敏史标记查询模型。
 */
@Getter
@Setter
public class AllergyHistoryFlagQueryModel {
    /** 患者主键。 */
    private Long patientId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 历史标志。 */
    private Boolean historyFlag;
    /** 记录次数。 */
    private Long recordCount;
}
