/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 支付业务类型枚举（DM §4.2 {@code kind_enum} 持久化为 {@link #getCode()}）。
 */
public enum PaymentKind implements EnumInterface {

    /** 住院预交金收取；{@code charge_item_ids} 允许为空（DM §4.5 预交金例外）。 */
    HOSPITAL_DEPOSIT(
        0L,
        "HOSPITAL_DEPOSIT",
        "住院存款（预交金收取）",
        "住院预交金收取场景；收费明细标识集合可为空。",
        0L),

    /** 门诊收费（含挂号收费）；须关联收费明细标识集合。 */
    OUTPATIENT_CLINIC(
        0L,
        "OUTPATIENT_CLINIC",
        "门诊收费（含挂号收费）",
        "门诊收费、挂号收费等场景。",
        1L),

    /** 住院费用结算（出院结算等）；须关联住院收费明细标识集合。 */
    INPATIENT_CLINIC(
        0L,
        "INPATIENT_CLINIC",
        "住院费用（出院结算）",
        "住院费用、出院结算等场景。",
        2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    PaymentKind(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    /**
     * 是否允许 {@code charge_item_ids} 为空（仅住院预交金收取，见 DM §4.5 预交金例外）。
     */
    public boolean allowsEmptyChargeItemIds() {
        return this == HOSPITAL_DEPOSIT;
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
