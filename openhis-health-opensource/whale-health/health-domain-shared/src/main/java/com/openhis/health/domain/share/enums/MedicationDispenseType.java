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
 * 药品发放类型枚举。
 */
public enum MedicationDispenseType implements EnumInterface {
    /**
     * OUTPATIENT。
     */
    OUTPATIENT(0L, "OUTPATIENT", "门诊发放", "", 1L),
    /**
     * INPATIENT。
     */
    INPATIENT(0L, "INPATIENT", "住院发放", "", 2L),
    /**
     * TEMPORARY。
     */
    TEMPORARY(0L, "TEMPORARY", "临时发放", "", 3L),
    /**
     * REFUND。
     */
    REFUND(0L, "REFUND", "退药发放", "", 4L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * MedicationDispenseType。
     */
    MedicationDispenseType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
