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
 * 婚姻状态枚举。
 */
public enum MaritalStatus implements EnumInterface {
    /**
     * UNMARRIED。
     */
    UNMARRIED(0L, "UNMARRIED", "未婚", "", 10L),
    /**
     * MARRIED。
     */
    MARRIED(0L, "MARRIED", "已婚", "", 20L),
    /**
     * FIRST_MARRIAGE。
     */
    FIRST_MARRIAGE(0L, "FIRST_MARRIAGE", "初婚", "", 21L),
    /**
     * REMARRIAGE。
     */
    REMARRIAGE(0L, "REMARRIAGE", "再婚", "", 22L),
    /**
     * RESTORED_MARRIAGE。
     */
    RESTORED_MARRIAGE(0L, "RESTORED_MARRIAGE", "复婚", "", 23L),
    /**
     * WIDOWED。
     */
    WIDOWED(0L, "WIDOWED", "丧偶", "", 30L),
    /**
     * DIVORCED。
     */
    DIVORCED(0L, "DIVORCED", "离婚", "", 40L),
    /**
     * UNSPECIFIED。
     */
    UNSPECIFIED(0L, "UNSPECIFIED", "未说明", "", 90L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * MaritalStatus。
     */
    MaritalStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
