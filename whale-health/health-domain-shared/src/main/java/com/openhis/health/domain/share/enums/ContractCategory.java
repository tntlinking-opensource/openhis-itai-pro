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
 * 费用性质分类枚举。
 */
public enum ContractCategory implements EnumInterface {
    /**
     * SELF_CHARGE。
     */
    SELF_CHARGE(0L, "SELF_CHARGE", "", "", 1L),
    /**
     * PUBLIC_CHARGE。
     */
    PUBLIC_CHARGE(0L, "PUBLIC_CHARGE", "", "", 2L),
    /**
     * PROVINCIAL_INSURANCE。
     */
    PROVINCIAL_INSURANCE(0L, "PROVINCIAL_INSURANCE", "", "", 3L),
    /**
     * MUNICIPAL_INSURANCE。
     */
    MUNICIPAL_INSURANCE(0L, "MUNICIPAL_INSURANCE", "", "", 4L),
    /**
     * WORKER_INSURANCE。
     */
    WORKER_INSURANCE(0L, "WORKER_INSURANCE", "", "", 5L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * ContractCategory。
     */
    ContractCategory(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
