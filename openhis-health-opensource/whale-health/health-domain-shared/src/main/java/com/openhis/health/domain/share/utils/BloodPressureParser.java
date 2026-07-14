/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.utils;

import lombok.Getter;
import lombok.Setter;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 护理记录血压字符串解析（AS-IP-NUR-014 §1.3；SDD §2.3）。
 *
 * <p>UI {@code bloodPressure}（如 {@code 128/76}）优先拆为 SBP+DBP；无法拆分时回落整段 BP 明细。</p>
 */
public final class BloodPressureParser {

    private BloodPressureParser() {
    }

    /**
     * 解析 UI 血压字符串为明细落库形态。
     *
     * @param bloodPressure UI 血压（可空）
     * @return 解析结果；入参为空时各字段均为 null
     */
    public static BloodPressureParseResult parse(String bloodPressure) {
        BloodPressureParseResult result = new BloodPressureParseResult();
        String normalized = trimToNull(bloodPressure);
        if (normalized == null) {
            return result;
        }
        int slashIndex = normalized.indexOf('/');
        if (slashIndex > 0 && slashIndex < normalized.length() - 1) {
            String systolic = trimToNull(normalized.substring(0, slashIndex));
            String diastolic = trimToNull(normalized.substring(slashIndex + 1));
            if (systolic != null && diastolic != null) {
                result.setSystolic(systolic);
                result.setDiastolic(diastolic);
                return result;
            }
        }
        result.setCombinedBp(normalized);
        return result;
    }

    /**
     * 将 SBP/DBP 明细合并为 UI 血压展示串。
     *
     * @param systolic   收缩压
     * @param diastolic  舒张压
     * @param combinedBp 整段 BP 明细
     * @return 展示串；均无值时返回 null
     */
    public static String formatForDisplay(String systolic, String diastolic, String combinedBp) {
        String sbp = trimToNull(systolic);
        String dbp = trimToNull(diastolic);
        if (sbp != null && dbp != null) {
            return sbp + "/" + dbp;
        }
        return trimToNull(combinedBp);
    }

    /**
     * 血压解析结果（写侧明细 code 选择依据）。
     */
    @Getter
    @Setter
    public static class BloodPressureParseResult {

        /** 收缩压（SBP 明细）。 */
        private String systolic;

        /** 舒张压（DBP 明细）。 */
        private String diastolic;

        /** 整段血压（BP 明细，无法拆分时）。 */
        private String combinedBp;

        /**
         * 是否至少一项非空。
         *
         * @return 是否有血压值
         */
        public boolean hasAnyValue() {
            return trimToNull(systolic) != null
                || trimToNull(diastolic) != null
                || trimToNull(combinedBp) != null;
        }
    }
}
