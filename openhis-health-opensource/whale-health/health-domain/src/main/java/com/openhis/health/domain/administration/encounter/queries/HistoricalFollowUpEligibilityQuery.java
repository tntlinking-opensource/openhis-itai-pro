/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.queries;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 复诊0元号资格校验查询入参（BR-REG-020）。
 *
 * <p>判定同一患者、同一挂号科室下，是否存在接诊时刻落在 72 小时窗口内的已接诊门诊就诊。</p>
 */
@Getter
@Setter
public class HistoricalFollowUpEligibilityQuery {

    /** 患者主键。 */
    private Long patientId;

    /** 本次拟挂号科室（{@code adm_encounter.organization_id}）主键。 */
    private Long organizationId;

    /**
     * 校验参照时刻（通常为预结算编排 {@code occurredAt}）；
     * 前次就诊须满足 {@code receptionTime <= asOf < receptionTime + 72h}。
     */
    private LocalDateTime asOf;

    /**
     * 排除的号源类型主键（复诊0元号自身，防止连环续挂）。
     */
    private Long excludedServiceTypeId;
}
