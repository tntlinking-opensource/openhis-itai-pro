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
 * ABO 血型枚举。
 */
public enum BloodTypeAbo implements EnumInterface {
    A(0L, "A", "A 型", "", 1L),
    B(0L, "B", "B 型", "", 2L),
    O(0L, "O", "O 型", "", 3L),
    /**
     * AB。
     */
    AB(0L, "AB", "AB 型", "", 4L),
    /**
     * UNKNOWN。
     */
    UNKNOWN(0L, "UNKNOWN", "不详", "", 5L),
    /**
     * UNTESTED。
     */
    UNTESTED(0L, "UNTESTED", "未查", "", 9L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * BloodTypeAbo。
     */
    BloodTypeAbo(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
