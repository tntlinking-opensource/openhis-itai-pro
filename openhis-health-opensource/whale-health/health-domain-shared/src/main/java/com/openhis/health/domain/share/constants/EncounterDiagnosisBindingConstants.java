/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

import java.util.Set;

import com.openhis.health.domain.share.enums.YbMedType;

/**
 * 开嘱绑定就诊诊断：医疗类别「同族」划分与文案（DD-诊断绑定改造 §4.2 / §8.2）。
 *
 * <p>普通族与慢病族不可混绑于同一请求行的 {@code encounter_diagnosis_ids}。
 * 码值对齐 {@link YbMedType#getCode()}；未列入任一族的 med_type 视为「其它」——
 * 仅允许与同码自身同族，不得与普通/慢病混绑。</p>
 */
public interface EncounterDiagnosisBindingConstants {

    /**
     * 普通族：普通门诊等（与慢病族互斥）。
     *
     * <p>含挂号/急诊等同属「非慢特病门诊」开嘱场景的常见码；产品若收窄可仅保留 {@code 11}。</p>
     */
    Set<String> ORDINARY_MED_TYPE_CODES = Set.of(
        YbMedType.GENERAL_OUTPATIENT.getCode(),
        YbMedType.OUTPATIENT_REGISTRATION.getCode(),
        YbMedType.EMERGENCY.getCode(),
        YbMedType.TRADITIONAL_CHINESE_MEDICINE_OUTPATIENT.getCode());

    /**
     * 慢病/慢特病族（与普通族互斥）。
     */
    Set<String> CHRONIC_MED_TYPE_CODES = Set.of(
        YbMedType.CHRONIC_SPECIAL_DISEASE_OUTPATIENT.getCode(),
        YbMedType.CHRONIC_DISEASE_OUTPATIENT.getCode(),
        YbMedType.SPECIAL_DISEASE_OUTPATIENT.getCode());

    /** 族标识：普通。 */
    String FAMILY_ORDINARY = "ORDINARY";

    /** 族标识：慢病/慢特病。 */
    String FAMILY_CHRONIC = "CHRONIC";

    /** 族标识前缀：未归入上列集合时按 med_type 自身隔离。 */
    String FAMILY_OTHER_PREFIX = "OTHER:";

    /**
     * 解析 med_type 所属绑定族。
     *
     * @param medTypeCode {@code adm_encounter_diagnosis.med_type}；可空
     * @return 族标识；空码回落普通族（与缺省普门口径一致）
     */
    static String resolveFamily(String medTypeCode) {
        if (medTypeCode == null || medTypeCode.isBlank()) {
            return FAMILY_ORDINARY;
        }
        String trimmed = medTypeCode.trim();
        if (ORDINARY_MED_TYPE_CODES.contains(trimmed)) {
            return FAMILY_ORDINARY;
        }
        if (CHRONIC_MED_TYPE_CODES.contains(trimmed)) {
            return FAMILY_CHRONIC;
        }
        return FAMILY_OTHER_PREFIX + trimmed;
    }

    /**
     * 判断两码是否同属可混绑一族。
     *
     * @param leftMedType  左 med_type
     * @param rightMedType 右 med_type
     * @return 同族 true
     */
    static boolean sameFamily(String leftMedType, String rightMedType) {
        return resolveFamily(leftMedType).equals(resolveFamily(rightMedType));
    }
}
