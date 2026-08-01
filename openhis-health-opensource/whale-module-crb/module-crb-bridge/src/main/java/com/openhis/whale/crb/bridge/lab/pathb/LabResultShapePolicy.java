/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.lab.pathb;

import com.openhis.whale.crb.domain.enums.LabResultShape;

/**
 * 检验结果形态判定（对照表优先，其次数值可解析性）。
 */
public final class LabResultShapePolicy {

    private LabResultShapePolicy() {
    }

    /**
     * 判定明细应走定性还是定量字段组。
     *
     * @param configuredShape 对照表 result_shape（可空）
     * @param testResult 原文结果
     * @return QUAL 或 QUANT
     */
    public static LabResultShape resolve(String configuredShape, String testResult) {
        if (LabResultShape.QUAL.getCode().equals(configuredShape)) {
            return LabResultShape.QUAL;
        }
        if (LabResultShape.QUANT.getCode().equals(configuredShape)) {
            return LabResultShape.QUANT;
        }
        if (parseNumber(testResult) != null) {
            return LabResultShape.QUANT;
        }
        return LabResultShape.QUAL;
    }

    /**
     * 尝试将结果解析为数值；失败返回 null。
     *
     * @param raw 原文
     * @return Double 或 null
     */
    public static Double parseNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            return Double.valueOf(trimmed);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
