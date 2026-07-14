/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.diagnosis.vitalsign.queries;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 体温单曲线查询条件（仅读主表，DM-DIA-002 v2.0 §6.2.2）。
 */
@Getter
@Setter
public class VitalSignTemperatureSeriesQuery {

    /** 就诊标识（必填）。 */
    private Long encounterId;

    /** 单据类型过滤（可空）。 */
    private String typeEnum;

    /** 记录时点起始（可空）。 */
    private LocalDateTime recordedAtFrom;

    /** 记录时点结束（可空）。 */
    private LocalDateTime recordedAtTo;
}
