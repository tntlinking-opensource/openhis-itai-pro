/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.settlement.queries;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 医保费用明细报表领域筛选（仅本聚合 {@code yb_settlement_record} Criteria 读）。
 */
@Data
@Accessors(chain = true)
public class InsuranceFeeDetailPageCriteria {

    /** 租户主键。 */
    private Long tenantId;

    /** 就诊类别 AMB/IMP。 */
    private String encounterClass;

    /** 结算时间窗起（含）。 */
    private LocalDateTime setlTimeFromInclusive;

    /** 结算时间窗止（不含）。 */
    private LocalDateTime setlTimeToExclusive;

    /** 为 true 时仅 settle_mode=WORK_INJURY。 */
    private Boolean workInjuryOnly;

    /** 工伤 settle_mode 码（通常 WORK_INJURY）。 */
    private String workInjurySettleMode;

    /** 非工伤时合同主键白名单。 */
    private List<Long> contractIds;

    /** 险种白名单；空不收窄。 */
    private List<String> insutypes;

    /** 为 true 表示就诊预解析无命中，查询应空。 */
    private Boolean emptyEncounterScope;

    /** 就诊主键白名单。 */
    private List<Long> encounterIds;

    /** 结算 ID 模糊关键字。 */
    private String setlIdKeyword;

    /** 就医登记号模糊关键字。 */
    private String mdtrtIdKeyword;

    /** 患者姓名模糊关键字（{@code psn_name}）。 */
    private String patientNameKeyword;

    /** 页码（1-based）。 */
    private Integer pageNo;

    /** 页大小。 */
    private Integer pageSize;
}
