/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.enums;

/**
 * 模板使用范围：个人 / 科室 / 全院。
 */
public enum TemplateScopeLevel {

    PERSONAL("PERSONAL", "个人"),
    DEPARTMENT("DEPARTMENT", "科室"),
    HOSPITAL("HOSPITAL", "全院");

    private final String code;
    private final String display;

    TemplateScopeLevel(String code, String display) {
        this.code = code;
        this.display = display;
    }

    public String getCode() {
        return code;
    }

    public String getDisplay() {
        return display;
    }

    public static TemplateScopeLevel fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (TemplateScopeLevel level : values()) {
            if (level.code.equals(normalized)) {
                return level;
            }
        }
        return null;
    }
}
