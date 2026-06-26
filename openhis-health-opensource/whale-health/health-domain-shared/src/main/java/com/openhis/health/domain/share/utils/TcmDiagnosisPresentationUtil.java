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