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

package com.openhis.health.domain.share.enums;

/**
 * 急诊分诊级别枚举。
 */
public enum TriageLevel implements EnumInterface {
    /**
     * LEVEL_I。
     */
    LEVEL_I(0L, "LEVEL_I", "一级：危（濒危）", "", 1L),
    /**
     * LEVEL_II。
     */
    LEVEL_II(0L, "LEVEL_II", "二级：重（危重）", "", 2L),
    /**
     * LEVEL_III。
     */
    LEVEL_III(0L, "LEVEL_III", "三级：急（急症）", "", 3L),
    /**
     * LEVEL_IV。
     */
    LEVEL_IV(0L, "LEVEL_IV", "四级：非急（普通）", "", 4L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * TriageLevel。
     */
    TriageLevel(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    public Long getCodeSystemId() {
        return codeSystemId;
    }

    public String getCode() {
        return code;
    }

    public String getDisplay() {
        return display;
    }

    public String getDefinition() {
        return definition;
    }

    public Long getSortOrder() {
        return sortOrder;
    }

}
