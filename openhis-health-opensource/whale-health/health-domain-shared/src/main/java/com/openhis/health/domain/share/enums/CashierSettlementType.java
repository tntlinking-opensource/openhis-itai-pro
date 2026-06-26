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
 * 收费员交账业务范围（DM-FIN-003 {@code type_enum}）。
 */
public enum CashierSettlementType implements EnumInterface {

    /** 门诊交账（含挂号与门诊收费 payment）。 */
    OUTPATIENT(0L, "OUTPATIENT", "门诊交账", "纳入 kind=OUTPATIENT_CLINIC 的 payment", 0L),

    /** 住院交账（出院/中途结算与预交金收退 payment）。 */
    INPATIENT(0L, "INPATIENT", "住院交账", "纳入 kind=INPATIENT_CLINIC、HOSPITAL_DEPOSIT 的 payment", 1L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    CashierSettlementType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
