/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 科室/医生工作量统计读侧语义码（AS-RPT-001 IF-RPT-WKL-01～04）。
 *
 * <p>{@code encounterScope} 与 {@link com.zxplat.domain.share.enums.EncounterClass} 存储码映射由
 * {@link com.zxplat.application.report.practitionerworkload.support.PractitionerWorkloadLoader} 承担。</p>
 */
public interface PractitionerWorkloadConstants {

    /** 就诊范围：门诊（映射 {@code adm_encounter.class_enum = AMB}）。 */
    String ENCOUNTER_SCOPE_OUTPATIENT = "OUTPATIENT";

    /** 就诊范围：住院（映射 {@code adm_encounter.class_enum = IMP}）。 */
    String ENCOUNTER_SCOPE_INPATIENT = "INPATIENT";

    /** 挂号收费上下文 {@code adm_charge_item.context_enum}（对齐 DM ChargeItemContext.REGISTER）。 */
    String CHARGE_ITEM_CONTEXT_REGISTER = "4";

    /** 明细分页默认条数/页（AS-RPT-001 §1.3）。 */
    int DEFAULT_PAGE_SIZE = 20;

    /** 导出循环分页步长（AS-RPT-001-SDD RPT-WKL-SDD-R-005）。 */
    int EXPORT_PAGE_SIZE = 500;

    /** 导出最大行数（AS-RPT-001 §7）。 */
    int EXPORT_MAX_ROWS = 100000;

    /** 页初始化默认激活 Tab：汇总（AS-RPT-001 BR-RPT-WKL-014）。 */
    String DEFAULT_ACTIVE_TAB_SUMMARY = "SUMMARY";

    /** 就诊范围门诊展示文案。 */
    String ENCOUNTER_SCOPE_OUTPATIENT_LABEL = "门诊";

    /** 就诊范围住院展示文案。 */
    String ENCOUNTER_SCOPE_INPATIENT_LABEL = "住院";

    /** 导出明细 sheet 名（AS-RPT-001-SDD §3.5）。 */
    String EXPORT_SHEET_NAME = "工作量明细";

    /** Excel MIME（与库房导出常量一致）。 */
    String EXPORT_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 业务消息码：初始化失败（AS-RPT-001 §5）。 */
    String EX_RPT_WKL_101 = "EX-RPT-WKL-101";

    /** 业务消息码：日期/就诊范围非法（AS-RPT-001 §5）。 */
    String EX_RPT_WKL_102 = "EX-RPT-WKL-102";

    /** 业务消息码：fin_type/成本/目录类型无法解析（AS-RPT-001 §5）。 */
    String EX_RPT_WKL_103 = "EX-RPT-WKL-103";

    /** 业务消息码：汇总/明细查询失败（AS-RPT-001 §5）。 */
    String EX_RPT_WKL_104 = "EX-RPT-WKL-104";

    /** 业务消息码：导出失败或超上限（AS-RPT-001 §5）。 */
    String EX_RPT_WKL_105 = "EX-RPT-WKL-105";

    /** 业务消息码：科室与门诊/住院域不一致（AS-RPT-001 §5）。 */
    String EX_RPT_WKL_106 = "EX-RPT-WKL-106";

    /** 业务消息码：无明细可导出（AS-RPT-001 §5）。 */
    String EX_RPT_WKL_201 = "EX-RPT-WKL-201";

    /** 目录类型展示：诊疗（BR-RPT-WKL-006）。 */
    String CATALOG_TYPE_ACTIVITY_LABEL = "诊疗";

    /** 目录类型展示：药品（BR-RPT-WKL-006）。 */
    String CATALOG_TYPE_MEDICATION_LABEL = "药品";

    /** 目录类型展示：耗材（BR-RPT-WKL-006）。 */
    String CATALOG_TYPE_DEVICE_LABEL = "耗材";

    /** 医生下拉预加载条数（初始化 IF-01）。 */
    int PRACTITIONER_OPTION_PAGE_SIZE = 500;
}
