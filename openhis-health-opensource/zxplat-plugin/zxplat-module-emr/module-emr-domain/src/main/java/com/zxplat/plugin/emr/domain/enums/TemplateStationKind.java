/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.enums;

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
