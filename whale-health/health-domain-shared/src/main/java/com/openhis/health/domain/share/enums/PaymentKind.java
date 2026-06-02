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
 * 支付业务类型枚举（DM §4.2 {@code kind_enum} 持久化为 {@link #getCode()}）。
 */
public enum PaymentKind implements EnumInterface {
    HOSPITAL_DEPOSIT(
        0L,
        "HOSPITAL_DEPOSIT",
        "住院存款（预交金收取）",
        "住院预交金收取场景；收费明细标识集合可为空。",
        0L),
    OUTPATIENT_CLINIC(
        0L,
        "OUTPATIENT_CLINIC",
        "门诊收费（含挂号收费）",
        "门诊收费、挂号收费等场景。",
        1L),
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
