/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.constants;

import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 电子处方开方/A0001 组装常量。
 *
 * <p>医疗类别 code 与 health {@code YbMedType} / chs {@code MedType} 对齐，仅在本模块使用，禁止运行期互转。</p>
 */
public final class ElepPrescriptionConstants {

    /** 挂号写入 {@code yb_encounter_platform_map} 的普通门诊医疗类别。 */
    public static final String MED_TYPE_GENERAL_OUTPATIENT = "11";

    /** 门诊慢特病。 */
    public static final String MED_TYPE_CHRONIC_SPECIAL = "14";

    /** 门诊慢病。 */
    public static final String MED_TYPE_CHRONIC_DISEASE = "140104";

    /** 门诊特病。 */
    public static final String MED_TYPE_SPECIAL_DISEASE = "140201";

    /** 本方须填病种编码的医疗类别（慢病差异只写电子处方报文，不补 2201）。 */
    public static final Set<String> CHRONIC_MED_TYPES = Set.of(
            MED_TYPE_CHRONIC_SPECIAL,
            MED_TYPE_CHRONIC_DISEASE,
            MED_TYPE_SPECIAL_DISEASE);

    /** 处方类别：西药。 */
    public static final String RX_TYPE_WESTERN = "1";

    /** 处方项目分类：西药。 */
    public static final String RX_ITEM_TYPE_WESTERN = "11";

    /** 处方项目分类名称：西药。 */
    public static final String RX_ITEM_TYPE_WESTERN_NAME = "西药";

    /** 业务类型：定点医疗机构就诊。 */
    public static final String BIZ_TYPE_FIXMEDINS = "01";

    /** 就诊凭证类型：身份证号。 */
    public static final String MDTRT_CERT_TYPE_ID_CARD = "02";

    /** 人员证件类型：居民身份证。 */
    public static final String PSN_CERT_TYPE_ID_CARD = "01";

    /** 门诊住院标识：门诊。 */
    public static final String OTP_IPT_FLAG_OUTPATIENT = "1";

    /** 门诊住院标识：住院。 */
    public static final String OTP_IPT_FLAG_INPATIENT = "2";

    /** 平台性别：男。 */
    public static final String GEND_MALE = "1";

    /** 平台性别：女。 */
    public static final String GEND_FEMALE = "2";

    /** 平台性别：未知。 */
    public static final String GEND_UNKNOWN = "9";

    /** HIS 行政性别：男。 */
    public static final String HIS_GENDER_MALE = "MALE";

    /** HIS 行政性别：女。 */
    public static final String HIS_GENDER_FEMALE = "FEMALE";

    /** HIS 就诊类别：门诊。 */
    public static final String HIS_ENCOUNTER_CLASS_AMB = "AMB";

    /** 特殊病种标志：否。 */
    public static final String SP_DISE_FLAG_NO = "0";

    /** 特殊病种标志：是。 */
    public static final String SP_DISE_FLAG_YES = "1";

    /** 主诊断标志。 */
    public static final String MAINDIAG_FLAG_YES = "1";

    /** 医院审批标志默认（目录限制未启用时按实际情况）。 */
    public static final String HOSP_APPR_FLAG_DEFAULT = "0";

    /** 是否标志：否。 */
    public static final String FLAG_NO = "0";

    /** 是否标志：是。 */
    public static final String FLAG_YES = "1";

    /** 默认处方有效天数。 */
    public static final String DEFAULT_VALI_DAYS = "3";

    /** 诊断顺序号（本页独立开方仅一条主诊断）。 */
    public static final String DIAG_SRT_NO_MAIN = "1";

    /** 诊断类型：西医诊断。 */
    public static final String DIAG_TYPE_WESTERN = "1";

    /** 3-03 开方/就诊时间格式。 */
    public static final String PRSC_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 3-03 开方/就诊时间格式化器。 */
    public static final DateTimeFormatter PRSC_TIME_FORMATTER = DateTimeFormatter.ofPattern(PRSC_TIME_PATTERN);

    /** 院内处方号前缀。 */
    public static final String HOSP_RXNO_PREFIX = "E";

    private ElepPrescriptionConstants() {
    }

    /**
     * 本方医疗类别是否属于慢病（须填病种，但仍只用挂号 map(11) 的 {@code mdtrt_id}）。
     *
     * @param medType 本方医疗类别 code
     * @return 慢病为 true
     */
    public static boolean isChronicMedType(String medType) {
        if (medType == null || medType.isBlank()) {
            return false;
        }
        return CHRONIC_MED_TYPES.contains(medType.trim());
    }

    /**
     * 医生不选手选普通门诊/慢病：有病种则按门诊慢特病，否则普通门诊。
     *
     * @param diseCodg 慢病病种编码
     * @param diseName 慢病病种名称
     * @return {@code 14} 或 {@code 11}
     */
    public static String resolveMedTypeFromDisease(String diseCodg, String diseName) {
        if (diseCodg == null || diseCodg.isBlank()) {
            return MED_TYPE_GENERAL_OUTPATIENT;
        }
        if (diseName == null || diseName.isBlank()) {
            return MED_TYPE_GENERAL_OUTPATIENT;
        }
        return MED_TYPE_CHRONIC_SPECIAL;
    }
}
