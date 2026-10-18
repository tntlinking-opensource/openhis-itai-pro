/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.utils;

import com.zxplat.domain.share.constants.DoctorStationDiagnosisConstants;

/**
 * 诊断中西医维度（{@code WESTERN}/{@code TCM}）与界面展示文案的映射。
 *
 * <p>持久化与快照仍使用 {@code WESTERN}/{@code TCM} 标签；出站补充中文便于医生站直接展示。</p>
 *
 */
public final class DiagnosisKindPresentationUtil {

    private DiagnosisKindPresentationUtil() {
    }

    /**
     * 将中西医维度标签转为中文展示。
     *
     * @param diagnosisKind {@code WESTERN} / {@code TCM} 等，大小写不敏感
     * @return {@code 西医} / {@code 中医}，无法识别时为 {@code null}
     */
    public static String toDisplay(String diagnosisKind) {
        if (diagnosisKind == null || diagnosisKind.isBlank()) {
            return null;
        }
        String v = diagnosisKind.trim();
        if (DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_WESTERN.equalsIgnoreCase(v)) {
            return "西医";
        }
        if (DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_TCM.equalsIgnoreCase(v)) {
            return "中医";
        }
        return null;
    }
}
