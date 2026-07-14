/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 护士工作量统计读侧语义码（AS-RPT-002 IF-RPT-NWL-01～06）。
 *
 * <p>就诊范围码与 {@link PractitionerWorkloadConstants} 同值，报表筛选语义一致。</p>
 */
public interface NurseWorkloadConstants {

    /** 就诊范围：门诊（映射 {@code Encounter.class_enum = AMB}）。 */
    String ENCOUNTER_SCOPE_OUTPATIENT = PractitionerWorkloadConstants.ENCOUNTER_SCOPE_OUTPATIENT;

    /** 就诊范围：住院（映射 {@code Encounter.class_enum = IMP}）。 */
    String ENCOUNTER_SCOPE_INPATIENT = PractitionerWorkloadConstants.ENCOUNTER_SCOPE_INPATIENT;

    /** 明细分页默认条数/页（AS-RPT-002 §1.3；与 AS-RPT-001 同为 20）。 */
    int DEFAULT_PAGE_SIZE = PractitionerWorkloadConstants.DEFAULT_PAGE_SIZE;

    /** 明细分页上限（IF-RPT-NWL-03 pageSize 上限）。 */
    int MAX_PAGE_SIZE = 1000;

    /** 主 Tab：诊疗执行（AS-RPT-002 §1.3）。 */
    String MAIN_TAB_EXECUTION = "EXECUTION";

    /** 主 Tab：耗材使用（AS-RPT-002 §1.3）。 */
    String MAIN_TAB_CONSUMABLE = "CONSUMABLE";

    /** 导出文件名前缀（AS-RPT-002 §7；NWL-SDD-R-006）。 */
    String EXPORT_FILE_NAME_PREFIX = "护士工作量";

    /** 就诊范围展示：门诊（IF-RPT-NWL-01 encounterScopeOptions）。 */
    String ENCOUNTER_SCOPE_OUTPATIENT_LABEL = "门诊";

    /** 就诊范围展示：住院（IF-RPT-NWL-01 encounterScopeOptions）。 */
    String ENCOUNTER_SCOPE_INPATIENT_LABEL = "住院";

    /** 高值耗材汇总/明细展示（BR-RPT-NWL-013；AS §1.3.1）。 */
    String HVCM_HIGH_DISPLAY = "高值耗材";

    /** 低值耗材汇总/明细展示（BR-RPT-NWL-013）。 */
    String HVCM_LOW_DISPLAY = "低值耗材";

    /** hvcm_flag 未 enrich 时的展示兜底。 */
    String HVCM_UNKNOWN_DISPLAY = "未知";
}
