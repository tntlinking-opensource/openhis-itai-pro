/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
