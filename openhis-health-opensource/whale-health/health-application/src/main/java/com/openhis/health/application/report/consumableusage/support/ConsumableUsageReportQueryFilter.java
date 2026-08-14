/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.support;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 耗材使用报表查询筛选（门诊/住院共用；AS-RPT-005/006）。
 *
 * <p>由 AppService 从 Request 装配后传入 QuerySupport；本类禁止依赖 feature Request/Response。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
public class ConsumableUsageReportQueryFilter {

    /** 业务日起（yyyy-MM-dd）。 */
    private String dateFrom;

    /** 业务日止（yyyy-MM-dd）。 */
    private String dateTo;

    /** 汇总维；明细可空。 */
    private String summaryDimension;

    /** 来源：PATIENT_DISPENSE / DEPT_ISSUE。 */
    private String sourceCode;

    /** 耗材房库位主键。 */
    private Long locationId;

    /** 费用性质合同业务号。 */
    private String feeNatureContractNo;

    /** 记录类型：ALL / DISPENSE / REFUND；仅患者侧生效。 */
    private String recordTypeCode;

    /** 耗材类型多选（device_category_code）。 */
    private List<String> deviceCategoryCodes;

    /** 执行人（发耗材人）主键；仅患者侧生效。 */
    private Long performerPractitionerId;

    /** 耗材名称/拼音/编码关键字。 */
    private String deviceKeyword;

    /** 患者关键字；仅患者侧生效。 */
    private String patientKeyword;

    /**
     * 科室组织主键：患者侧=开立/就诊科室（Encounter.organization_id）；
     * 领用侧=领用科室（consuming_organization_id）。
     */
    private Long organizationId;
}
