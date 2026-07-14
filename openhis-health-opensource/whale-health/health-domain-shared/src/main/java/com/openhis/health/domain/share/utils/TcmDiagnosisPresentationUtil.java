/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.utils;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;





/**
 * 中医病证组展示文案工具（与前端 {@code formatTcmDiagnosisDisplayName} 口径一致）。
 */
public final class TcmDiagnosisPresentationUtil {

    private TcmDiagnosisPresentationUtil() {
    }

    /**
     * 合并病名与证候为列表/树展示名：{@code 疾病（证候）}。
     *
     * @param diseaseName  病名
     * @param syndromeName 证候名
     * @return 非空展示名；两侧皆空时返回 {@code null}
     */
    public static String formatGroupDisplayName(String diseaseName, String syndromeName) {
        String disease = trimToNull(diseaseName);
        String syndrome = trimToNull(syndromeName);
        // 病名与证候皆有：合并为「病名（证候）」展示
        if (disease != null && syndrome != null) {
            return disease + "（" + syndrome + "）";
        }
        // 仅病名或仅证候：返回非空侧
        if (disease != null) {
            return disease;
        }
        return syndrome;
    }
}
