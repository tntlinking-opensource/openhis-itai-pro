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
