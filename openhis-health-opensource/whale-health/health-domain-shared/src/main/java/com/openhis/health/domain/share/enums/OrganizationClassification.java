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
 * 机构分类枚举。
 */
public enum OrganizationClassification implements EnumInterface {
    /**
     * CLINIC。
     */
    CLINIC(0L, "CLINIC", "门诊", "", 1L),
    /**
     * INPATIENT。
     */
    INPATIENT(0L, "INPATIENT", "住院", "", 2L),
    /**
     * PHARMACY。
     */
    PHARMACY(0L, "PHARMACY", "药房", "", 3L),
    /**
     * STORAGE。
     */
    STORAGE(0L, "STORAGE", "库房", "", 4L),
    /**
     * FIN。
     */
    FIN(0L, "FIN", "财务", "", 5L),
    /**
     * NS。
     */
    NS(0L, "NS", "护士站", "", 6L),
    /**
     * MANAGER。
     */
    MANAGER(0L, "MANAGER", "管理", "", 7L),
    /**
     * SUPPORT。
     */
    SUPPORT(0L, "SUPPORT", "后勤支持", "", 8L),
    /**
     * OTHER。
     */
    OTHER(0L, "OTHER", "其他", "", 9L),
    /**
     * TECH。
     */
    TECH(0L, "TECH", "医技", "", 10L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * OrganizationClassification。
     */
    OrganizationClassification(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
