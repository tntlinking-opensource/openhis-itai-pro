/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊挂号明细报表明细页脚统计（AS-RPT-007；对齐门诊挂号当日挂/退口径）。
 *
 * <p>{@code registrationCount} 排除 {@code PLANNED}/{@code ENTERED_IN_ERROR}；
 * {@code cancelledCount} 仅 {@code CANCELLED}；{@code activeCount} = 挂号 − 已退。</p>
 */
@Getter
@Setter
public class OutpatientRegistrationDetailStatsQueryModel {

    /** 挂号笔数（排除待挂号与未收费取消）。 */
    private Long registrationCount;

    /** 已退笔数（仅退号 CANCELLED）。 */
    private Long cancelledCount;

    /** 有效笔数（挂号 − 已退，下限 0）。 */
    private Long activeCount;
}
