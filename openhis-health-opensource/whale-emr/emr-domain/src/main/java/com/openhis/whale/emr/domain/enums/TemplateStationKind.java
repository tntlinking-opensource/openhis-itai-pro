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

package com.openhis.whale.emr.domain.enums;

/**
 * 医生站场景：用于筛选可见模板（映射就诊类别 code）。
 */
public enum TemplateStationKind {

    OUTPATIENT("OUTPATIENT", "AMB"),
    INPATIENT("INPATIENT", "IMP");

    private final String code;
    /** 对应 {@code EncounterClass} 的 code */
    private final String encounterClassCode;

    TemplateStationKind(String code, String encounterClassCode) {
        this.code = code;
        this.encounterClassCode = encounterClassCode;
    }

    public String getCode() {
        return code;
    }

    public String getEncounterClassCode() {
        return encounterClassCode;
    }

    public static TemplateStationKind fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (TemplateStationKind kind : values()) {
            if (kind.code.equals(normalized)) {
                return kind;
            }
        }
        return null;
    }
}
