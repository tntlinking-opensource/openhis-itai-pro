/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.queries.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊挂号报表净汇总行（科室或医生 × 号源类型）。
 *
 * <p>净笔数：有效挂号计入；CANCELLED/PLANNED/ENTERED_IN_ERROR 不计（退号冲减）。
 * {@code contributingEncounterIds} 供应用层按号源 ChargeItem 汇总净挂号费，不直接出站。</p>
 */
@Getter
@Setter
public class OutpatientRegistrationReportSummaryRow {

    /** 维度键。 */
    private String dimensionKey;

    /** 汇总视角码。 */
    private String summaryDimension;

    /** 挂号科室；医生维时可为空。 */
    private Long organizationId;

    /** 挂号医生；科室维时为 null。 */
    private Long practitionerId;

    /** 号源类型主键。 */
    private Long serviceTypeId;

    /** 净挂号笔数。 */
    private long netRegistrationCount;

    /**
     * 计入净笔数的就诊主键（与净笔数同口径；供应用层汇总挂号费）。
     */
    private List<Long> contributingEncounterIds = new ArrayList<>();
}
