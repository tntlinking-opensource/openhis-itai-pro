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
 * 供应请求位置类型枚举。
 *
 * <p>存储口径：{@code wkf_supply_request.source_type_enum} / {@code wkf_supply_request.purpose_type_enum}。</p>
 */
public enum SupplyRequestType implements EnumInterface {
    /**
     * DEPARTMENT。
     */
    DEPARTMENT(0L, "DEPARTMENT", "科室", "", 2L),
    /**
     * CABINET。
     */
    CABINET(0L, "CABINET", "库房", "", 11L),
    /**
     * PHARMACY。
     */
    PHARMACY(0L, "PHARMACY", "药房", "", 16L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * SupplyRequestType。
     */
    SupplyRequestType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
