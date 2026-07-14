/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationrequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 患者历史医嘱查询模型。
 */
@Getter
@Setter
public class MedicationRequestHistoryQueryModel {
    /** 主键。 */
    private Long id;
    /** 就诊主键。 */
    private Long encounterId;
    /** 药品主键。 */
    private Long medicationId;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** created时间。 */
    private LocalDateTime createdAt;
}
