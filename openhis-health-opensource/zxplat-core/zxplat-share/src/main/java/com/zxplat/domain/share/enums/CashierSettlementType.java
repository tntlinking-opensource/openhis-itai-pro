/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 交账批次业务类型（DM-FIN-003 {@code type_enum}）。
 *
 * <p>仅四码：收款门/住 + 药房门/住。库内旧码由 Flyway 一次性迁完，运行时不兼容。</p>
 */
public enum CashierSettlementType implements EnumInterface {

    /** 门诊收款日结。 */
    OP_PAYMENT(0L, "OP_PAYMENT", "门诊收款日结", "挂载 outpatient payment", 0L),

    /** 住院收款日结。 */
    IP_PAYMENT(0L, "IP_PAYMENT", "住院收款日结", "挂载 inpatient payment", 1L),

    /** 门诊药房日结。 */
    OP_PHARMACY(0L, "OP_PHARMACY", "门诊药房日结", "挂载 outpatient medication_dispense", 2L),

    /** 住院药房日结。 */
    IP_PHARMACY(0L, "IP_PHARMACY", "住院药房日结", "挂载 inpatient medication_dispense", 3L);

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

    /**
     * 是否为收款日结类型（挂载 payment）。
     *
     * @param typeEnum 类型 code
     * @return true=收款
     */
    public static boolean isPaymentType(String typeEnum) {
        if (typeEnum == null) {
            return false;
        }
        return OP_PAYMENT.getCode().equals(typeEnum) || IP_PAYMENT.getCode().equals(typeEnum);
    }

    /**
     * 是否为药房日结类型（挂载 dispense）。
     *
     * @param typeEnum 类型 code
     * @return true=药房
     */
    public static boolean isPharmacyType(String typeEnum) {
        if (typeEnum == null) {
            return false;
        }
        return OP_PHARMACY.getCode().equals(typeEnum) || IP_PHARMACY.getCode().equals(typeEnum);
    }
}
