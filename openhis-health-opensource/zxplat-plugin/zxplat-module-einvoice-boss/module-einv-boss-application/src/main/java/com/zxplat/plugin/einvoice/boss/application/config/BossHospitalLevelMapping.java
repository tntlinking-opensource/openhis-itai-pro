/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.config;

/**
 * 医院等级码 → 博思 medicalInstitution 展示名（对齐旧 ZXPlat commomSet）。
 */
public final class BossHospitalLevelMapping {

    private BossHospitalLevelMapping() {
    }

    /**
     * @param hospitalLv 医院等级码（如 05）
     * @return 医疗机构类型名称
     */
    public static String toMedicalInstitution(String hospitalLv) {
        if (hospitalLv == null || hospitalLv.isBlank()) {
            return "二级甲等";
        }
        return switch (hospitalLv.trim()) {
            case "01" -> "三级特等";
            case "02" -> "三级甲等";
            case "03" -> "三级乙等";
            case "04" -> "三级丙等";
            case "05" -> "二级甲等";
            case "06" -> "二级乙等";
            case "07" -> "二级丙等";
            case "08" -> "一级甲等";
            case "09" -> "一级乙等";
            case "10" -> "一级丙等";
            default -> "无等级";
        };
    }
}
