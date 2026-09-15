/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.settlement.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 医保费用明细报表 SPI 查询入参（AS-RPT-012/013；仅 CONFIRMED 净行）。
 */
@Data
@Accessors(chain = true)
public class InsuranceSettlementFeeDetailQueryExtensionIn {

    /** 租户主键；空则 SPI 回落当前租户。 */
    private Long tenantId;

    /** 就诊类别：AMB 门诊 / IMP 住院。 */
    private String encounterClass;

    /** 结算时间窗起（含）；与 setl_time 对齐，空 setl_time 回落 created_at。 */
    private LocalDateTime setlTimeFromInclusive;

    /** 结算时间窗止（不含，开区间上界）。 */
    private LocalDateTime setlTimeToExclusive;

    /**
     * 为 true 时仅工伤 settle_mode=WORK_INJURY；为 false/null 时排除工伤并按 contractIds 收窄。
     */
    private Boolean workInjuryOnly;

    /**
     * 省/市合同主键白名单；workInjuryOnly=true 时忽略。
     * 非工伤且本列表空：SPI 返回空集（无合同可命中）。
     */
    private List<Long> contractIds;

    /**
     * 平台险种白名单（如 310/320/330 或 390）；空表示不按险种收窄。
     */
    private List<String> insutypes;

    /**
     * 为 true 表示科室/业务号/姓名预解析后无命中就诊，SPI 应直接空页/零合计。
     */
    private Boolean emptyEncounterScope;

    /**
     * 就诊主键白名单；null/空且 {@link #emptyEncounterScope}≠true 表示不按就诊收窄。
     */
    private List<Long> encounterIds;

    /** 结算 ID 模糊包含（trim 后非空才生效）。 */
    private String setlIdKeyword;

    /** 就医登记号模糊包含。 */
    private String mdtrtIdKeyword;

    /** 患者姓名模糊包含（落 {@code psn_name}）。 */
    private String patientNameKeyword;

    /** 页码（1-based）；分页接口必填。 */
    private Integer pageNo;

    /** 页大小；分页接口必填。 */
    private Integer pageSize;
}
