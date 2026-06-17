/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.enums;

/**
 * 医保医疗类别（对齐国家医保接口字典 {@code med_type}）。
 *
 * <p>持久化身份为 {@link #getCode()} 字符串（如 {@code 11} 普通门诊、{@code 21} 普通住院），
 * 写入 {@code adm_encounter_diagnosis.med_type}；展示文案取 {@link #getDisplay()}。</p>
 *
 * <p>医保对接类枚举例外：{@code getCode()} 直接使用国家医保 {@code med_type} 数字码，
 * 与 {@link GenerateSource} 等通用业务枚举的英文 code 口径不同。</p>
 *
 * <p>{@code sortOrder} 仅用于下拉排序，非持久化身份。</p>
 */
public enum YbMedType implements EnumInterface {

    /** 普通门诊。 */
    GENERAL_OUTPATIENT(0L, "11", "普通门诊", "", 1L),
    /** 门诊挂号。 */
    OUTPATIENT_REGISTRATION(0L, "12", "门诊挂号", "", 2L),
    /** 急诊。 */
    EMERGENCY(0L, "13", "急诊", "", 3L),
    /** 门诊慢特病。 */
    CHRONIC_SPECIAL_DISEASE_OUTPATIENT(0L, "14", "门诊慢特病", "", 4L),
    /** 特药。 */
    SPECIAL_MEDICATION(0L, "15", "特药", "", 5L),
    /** 中医特色门诊。 */
    TRADITIONAL_CHINESE_MEDICINE_OUTPATIENT(0L, "16", "中医特色门诊", "", 6L),
    /** 普通住院。 */
    GENERAL_HOSPITALIZATION(0L, "21", "普通住院", "", 7L),
    /** 外伤住院。 */
    TRAUMA_HOSPITALIZATION(0L, "22", "外伤住院", "", 8L),
    /** 转外诊治住院。 */
    EXTERNAL_TREATMENT_HOSPITALIZATION(0L, "23", "转外诊治住院", "", 9L),
    /** 急诊转住院。 */
    EMERGENCY_TO_HOSPITALIZATION(0L, "24", "急诊转住院", "", 10L),
    /** 异地住院。 */
    CROSS_PROVINCIAL_HOSPITALIZATION(0L, "25", "异地住院", "", 11L),
    /** 单病种住院。 */
    SINGLE_DISEASE_HOSPITALIZATION(0L, "26", "单病种住院", "", 12L),
    /** 自主就医。 */
    SELF_MEDICATION(0L, "27", "自主就医", "", 13L),
    /** 日间手术。 */
    DAY_SURGERY(0L, "28", "日间手术", "", 14L),
    /** 起付线治疗。 */
    DEDUCTIBLE_TREATMENT(0L, "29", "起付线治疗", "", 15L),
    /** 定点药店购药。 */
    DESIGNATED_PHARMACY_PURCHASE(0L, "41", "定点药店购药", "", 16L),
    /** 生育门诊。 */
    MATERNITY_OUTPATIENT(0L, "51", "生育门诊", "", 17L),
    /** 生育住院。 */
    MATERNITY_HOSPITALIZATION(0L, "52", "生育住院", "", 18L),
    /** 照护保险。 */
    CARE_INSURANCE(0L, "61", "照护保险", "", 19L),
    /** 意外伤害门诊。 */
    ACCIDENTAL_INJURY_OUTPATIENT(0L, "81", "意外伤害门诊", "", 20L),
    /** 门诊慢病。 */
    CHRONIC_DISEASE_OUTPATIENT(0L, "140104", "门诊慢病", "", 21L),
    /** 门诊特病。 */
    SPECIAL_DISEASE_OUTPATIENT(0L, "140201", "门诊特病", "", 22L),
    /** 舒缓疗护住院。 */
    PALLIATIVE_CARE_HOSPITALIZATION(0L, "2114", "舒缓疗护住院", "", 23L),
    /** 生育新生儿费用。 */
    NEWBORN_MATERNITY_EXPENSES(0L, "5212", "生育新生儿费用", "", 24L),
    /** 低自付住院。 */
    LOW_COPAY_HOSPITALIZATION(0L, "3101", "低自付住院", "", 25L),
    /** 低自付门诊。 */
    LOW_COPAY_OUTPATIENT(0L, "3102", "低自付门诊", "", 26L),
    /** 门诊特检特治（限吉林市）。 */
    SPECIAL_EXAMINATION_TREATMENT_OUTPATIENT_JILIN(0L, "990404", "门诊特检特治（限吉林市）", "", 27L),
    /** 体检。 */
    PHYSICAL_EXAMINATION(0L, "9107", "体检", "", 28L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    YbMedType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    @Override
    public Long getCodeSystemId() {
        return codeSystemId;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDisplay() {
        return display;
    }

    @Override
    public String getDefinition() {
        return definition;
    }

    @Override
    public Long getSortOrder() {
        return sortOrder;
    }
}
