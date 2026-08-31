/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 体检日结清单共享常量（AS-PE-FIN-003）。
 */
public interface PhysicalExamDailySettlementConstants {

    /** 查询失败（EX-PE-DS-101）。 */
    String MESSAGE_CODE_QUERY_FAILED = "EX-PE-DS-101";

    /** 查询日期非法（EX-PE-DS-102）。 */
    String MESSAGE_CODE_DATE_INVALID = "EX-PE-DS-102";

    /** 查询时间起止连接符。 */
    String DATE_RANGE_JOIN = "至";

    /** 人次展示后缀。 */
    String VISIT_COUNT_SUFFIX = "人";

    /** 科室缺失时的展示名。 */
    String EMPTY_DEPARTMENT_NAME = "未分科室";

    /** 开单人缺失时的展示名。 */
    String EMPTY_ORDERER_NAME = "未登记开单人";

    /** 患者姓名缺失时的展示名。 */
    String EMPTY_PATIENT_NAME = "未知";
}
