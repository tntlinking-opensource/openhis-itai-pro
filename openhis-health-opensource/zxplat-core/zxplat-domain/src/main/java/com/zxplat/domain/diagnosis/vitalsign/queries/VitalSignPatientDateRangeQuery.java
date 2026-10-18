/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.diagnosis.vitalsign.queries;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 按患者与时间区间查询生命体征条件（SDD §5.2 listByPatientAndDateRange 入参）。
 */
@Getter
@Setter
public class VitalSignPatientDateRangeQuery {

    /** 患者标识（必填）。 */
    private Long patientId;

    /** 区间起始（必填）。 */
    private LocalDateTime from;

    /** 区间结束（必填）。 */
    private LocalDateTime to;
}
