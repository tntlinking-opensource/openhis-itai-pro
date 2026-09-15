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
    /** 历史标志（存在有效过敏原）。 */
    private Boolean historyFlag;
    /** 已确认无已知过敏且无有效过敏原。 */
    private Boolean nkdaFlag;
    /** 有效过敏原条数。 */
    private Long recordCount;
    /** 摘要文案：未登记 / 无 / 过敏原顿号拼接。 */
    private String summaryText;
}
