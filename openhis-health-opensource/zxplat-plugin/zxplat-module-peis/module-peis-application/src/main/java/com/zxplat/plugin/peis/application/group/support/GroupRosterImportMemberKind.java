/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.support;

/**
 * 团检名册模板导入人员类型。
 */
public enum GroupRosterImportMemberKind {

    STUDENT,
    /** 校医院教职工：导入时同步 Campus 档案。 */
    STAFF,
    /** 企事业单位：仅 PEIS 名册，不写 Campus。 */
    ENTERPRISE;

    public static GroupRosterImportMemberKind parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase();
        for (GroupRosterImportMemberKind kind : values()) {
            if (kind.name().equals(normalized)) {
                return kind;
            }
        }
        String trimmed = raw.trim();
        if ("学生".equals(trimmed)) {
            return STUDENT;
        }
        if ("教职工".equals(trimmed)) {
            return STAFF;
        }
        if ("企事业".equals(trimmed) || "企事业单位".equals(trimmed)) {
            return ENTERPRISE;
        }
        if ("STAFF".equalsIgnoreCase(trimmed)) {
            return STAFF;
        }
        return null;
    }

    public boolean usesEmployeeNo() {
        return this == STAFF || this == ENTERPRISE;
    }
}
