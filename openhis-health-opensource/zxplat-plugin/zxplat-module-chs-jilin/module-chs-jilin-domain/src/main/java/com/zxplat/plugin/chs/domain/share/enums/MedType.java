/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.share.enums;

/**
 * 国家医保上传字段 {@code med_type}（医疗类别，1901 字典表 4）— <b>平台权威枚举</b>。
 *
 * <p><b>常见交易</b>：1101 读卡、2203/2204/2206/2207 门诊结算、2301/2304 住院结算、5201 就诊查询。</p>
 * <p><b>怎么用</b>：报文 {@code med_type} 填 {@link #getCode()}；门诊默认 {@link #GENERAL_OUTPATIENT}，
 * 住院默认 {@link #GENERAL_HOSPITALIZATION}。</p>
 * <p>health 镜像 {@code com.zxplat.domain.share.enums.YbMedType} 须与本枚举 code/label
 * <b>人工保持同步</b>（见 Skill §2.2.4）。</p>
 */
public enum MedType implements ChsCodeEnum {

    /** 普通门诊（code 11；门诊结算最常用默认值） */
    GENERAL_OUTPATIENT("11", "普通门诊"),
    /** 门诊挂号（code 12） */
    OUTPATIENT_REGISTRATION("12", "门诊挂号"),
    /** 急诊（code 13） */
    EMERGENCY("13", "急诊"),
    /** 门诊慢特病（code 14；慢病分组结算用） */
    CHRONIC_SPECIAL_DISEASE_OUTPATIENT("14", "门诊慢特病"),
    /** 特药门诊（code 15） */
    SPECIAL_MEDICATION("15", "特药"),
    /** 中医特色门诊（code 16） */
    TRADITIONAL_CHINESE_MEDICINE_OUTPATIENT("16", "中医特色门诊"),
    /** 普通住院（code 21；住院结算最常用默认值） */
    GENERAL_HOSPITALIZATION("21", "普通住院"),
    /** 外伤住院（code 22） */
    TRAUMA_HOSPITALIZATION("22", "外伤住院"),
    /** 转外诊治住院（code 23） */
    EXTERNAL_TREATMENT_HOSPITALIZATION("23", "转外诊治住院"),
    /** 急诊转住院（code 24） */
    EMERGENCY_TO_HOSPITALIZATION("24", "急诊转住院"),
    /** 异地住院（code 25） */
    CROSS_PROVINCIAL_HOSPITALIZATION("25", "异地住院"),
    /** 单病种住院（code 26） */
    SINGLE_DISEASE_HOSPITALIZATION("26", "单病种住院"),
    /** 自主就医（code 27） */
    SELF_MEDICATION("27", "自主就医"),
    /** 日间手术（code 28） */
    DAY_SURGERY("28", "日间手术"),
    /** 起付线治疗（code 29） */
    DEDUCTIBLE_TREATMENT("29", "起付线治疗"),
    /** 定点药店购药（code 41） */
    DESIGNATED_PHARMACY_PURCHASE("41", "定点药店购药"),
    /** 生育门诊（code 51） */
    MATERNITY_OUTPATIENT("51", "生育门诊"),
    /** 生育住院（code 52） */
    MATERNITY_HOSPITALIZATION("52", "生育住院"),
    /** 照护保险（code 61） */
    CARE_INSURANCE("61", "照护保险"),
    /** 意外伤害门诊（code 81） */
    ACCIDENTAL_INJURY_OUTPATIENT("81", "意外伤害门诊"),
    /** 门诊慢病（code 140104；地方扩展码） */
    CHRONIC_DISEASE_OUTPATIENT("140104", "门诊慢病"),
    /** 门诊特病（code 140201；地方扩展码） */
    SPECIAL_DISEASE_OUTPATIENT("140201", "门诊特病"),
    /** 舒缓疗护住院（code 2114） */
    PALLIATIVE_CARE_HOSPITALIZATION("2114", "舒缓疗护住院"),
    /** 生育新生儿费用（code 5212） */
    NEWBORN_MATERNITY_EXPENSES("5212", "生育新生儿费用"),
    /** 低自付住院（code 3101；吉林等地方码） */
    LOW_COPAY_HOSPITALIZATION("3101", "低自付住院"),
    /** 低自付门诊（code 3102） */
    LOW_COPAY_OUTPATIENT("3102", "低自付门诊"),
    /** 门诊特检特治，限吉林市（code 990404） */
    SPECIAL_EXAMINATION_TREATMENT_OUTPATIENT_JILIN("990404", "门诊特检特治（限吉林市）"),
    /** 体检（code 9107） */
    PHYSICAL_EXAMINATION("9107", "体检");

    private final String code;
    private final String label;

    MedType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getLabel() {
        return label;
    }

    /**
     * 按平台 med_type 编码解析；无效或空白返回 {@code null}。
     *
     * @param code 平台 med_type 字符串
     * @return 匹配枚举，未匹配时 {@code null}
     */
    public static MedType fromCodeOptional(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        for (MedType item : values()) {
            if (item.code.equals(trimmed)) {
                return item;
            }
        }
        return null;
    }
}
