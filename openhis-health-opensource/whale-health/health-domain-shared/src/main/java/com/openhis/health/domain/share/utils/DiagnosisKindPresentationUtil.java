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

package com.openhis.health.domain.share.utils;

import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;

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
