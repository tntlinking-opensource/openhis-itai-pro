/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 医保医疗类别（对齐国家医保 {@code med_type}，与 chs {@code MedType} 人工保持同步）。
 *
 * <p>持久化身份为 {@link #getCode()} 字符串（如 {@code 11}、{@code 21}），写入
 * {@code adm_encounter_diagnosis.med_type}；health 侧展示文案取 {@link #getDisplay()}。</p>
 *
 * <p><b>同步约定</b>（见 Skill §2.2.4）：平台报文权威枚举为
 * {@code com.openhis.chs.domain.share.enums.MedType}；变更任一侧的 code 或中文文案时，
 * <b>须人工同步另一侧</b>，禁止只改一边。新增常量项亦须双侧同时追加且 code/文案一致。</p>
 *
 * <p>业务分支须比较 {@link #getCode()}，禁止用 {@link #getDisplay()} 驱动逻辑。
 * 勿与院内费别 {@link ChargeTypeCategory}、平台 {@code mdtrt_setl_type} 等其它字段的 code 混用。</p>
 */
public enum YbMedType implements EnumInterface {

/** 普通门诊（YbMedType，持久化编码 {@code 11}）。 */
    GENERAL_OUTPATIENT(0L, "11", "普通门诊", "", 1L),
/** 门诊挂号（YbMedType，持久化编码 {@code 12}）。 */
    OUTPATIENT_REGISTRATION(0L, "12", "门诊挂号", "", 2L),
/** 急诊（YbMedType，持久化编码 {@code 13}）。 */
    EMERGENCY(0L, "13", "急诊", "", 3L),
/** 门诊慢特病（YbMedType，持久化编码 {@code 14}）。 */
    CHRONIC_SPECIAL_DISEASE_OUTPATIENT(0L, "14", "门诊慢特病", "", 4L),
/** 特药（YbMedType，持久化编码 {@code 15}）。 */
    SPECIAL_MEDICATION(0L, "15", "特药", "", 5L),
/** 中医特色门诊（YbMedType，持久化编码 {@code 16}）。 */
    TRADITIONAL_CHINESE_MEDICINE_OUTPATIENT(0L, "16", "中医特色门诊", "", 6L),
/** 普通住院（YbMedType，持久化编码 {@code 21}）。 */
    GENERAL_HOSPITALIZATION(0L, "21", "普通住院", "", 7L),
/** 外伤住院（YbMedType，持久化编码 {@code 22}）。 */
    TRAUMA_HOSPITALIZATION(0L, "22", "外伤住院", "", 8L),
/** 转外诊治住院（YbMedType，持久化编码 {@code 23}）。 */
    EXTERNAL_TREATMENT_HOSPITALIZATION(0L, "23", "转外诊治住院", "", 9L),
/** 急诊转住院（YbMedType，持久化编码 {@code 24}）。 */
    EMERGENCY_TO_HOSPITALIZATION(0L, "24", "急诊转住院", "", 10L),
/** 异地住院（YbMedType，持久化编码 {@code 25}）。 */
    CROSS_PROVINCIAL_HOSPITALIZATION(0L, "25", "异地住院", "", 11L),
/** 单病种住院（YbMedType，持久化编码 {@code 26}）。 */
    SINGLE_DISEASE_HOSPITALIZATION(0L, "26", "单病种住院", "", 12L),
/** 自主就医（YbMedType，持久化编码 {@code 27}）。 */
    SELF_MEDICATION(0L, "27", "自主就医", "", 13L),
/** 日间手术（YbMedType，持久化编码 {@code 28}）。 */
    DAY_SURGERY(0L, "28", "日间手术", "", 14L),
/** 起付线治疗（YbMedType，持久化编码 {@code 29}）。 */
    DEDUCTIBLE_TREATMENT(0L, "29", "起付线治疗", "", 15L),
/** 定点药店购药（YbMedType，持久化编码 {@code 41}）。 */
    DESIGNATED_PHARMACY_PURCHASE(0L, "41", "定点药店购药", "", 16L),
/** 生育门诊（YbMedType，持久化编码 {@code 51}）。 */
    MATERNITY_OUTPATIENT(0L, "51", "生育门诊", "", 17L),
/** 生育住院（YbMedType，持久化编码 {@code 52}）。 */
    MATERNITY_HOSPITALIZATION(0L, "52", "生育住院", "", 18L),
/** 照护保险（YbMedType，持久化编码 {@code 61}）。 */
    CARE_INSURANCE(0L, "61", "照护保险", "", 19L),
/** 意外伤害门诊（YbMedType，持久化编码 {@code 81}）。 */
    ACCIDENTAL_INJURY_OUTPATIENT(0L, "81", "意外伤害门诊", "", 20L),
/** 门诊慢病（YbMedType，持久化编码 {@code 140104}）。 */
    CHRONIC_DISEASE_OUTPATIENT(0L, "140104", "门诊慢病", "", 21L),
/** 门诊特病（YbMedType，持久化编码 {@code 140201}）。 */
    SPECIAL_DISEASE_OUTPATIENT(0L, "140201", "门诊特病", "", 22L),
/** 舒缓疗护住院（YbMedType，持久化编码 {@code 2114}）。 */
    PALLIATIVE_CARE_HOSPITALIZATION(0L, "2114", "舒缓疗护住院", "", 23L),
/** 生育新生儿费用（YbMedType，持久化编码 {@code 5212}）。 */
    NEWBORN_MATERNITY_EXPENSES(0L, "5212", "生育新生儿费用", "", 24L),
/** 低自付住院（YbMedType，持久化编码 {@code 3101}）。 */
    LOW_COPAY_HOSPITALIZATION(0L, "3101", "低自付住院", "", 25L),
/** 低自付门诊（YbMedType，持久化编码 {@code 3102}）。 */
    LOW_COPAY_OUTPATIENT(0L, "3102", "低自付门诊", "", 26L),
    /** 门诊特检特治（限吉林市）。 */
    SPECIAL_EXAMINATION_TREATMENT_OUTPATIENT_JILIN(0L, "990404", "门诊特检特治（限吉林市）", "", 27L),
/** 体检（YbMedType，持久化编码 {@code 9107}）。 */
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
