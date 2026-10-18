/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 药品发放类型枚举。
 */
public enum MedicationDispenseType implements EnumInterface {
    /** 门诊发放（MedicationDispenseType，编码 {@code OUTPATIENT}）。 */
    OUTPATIENT(0L, "OUTPATIENT", "门诊发放", "", 1L),
    /** 住院发放（MedicationDispenseType，编码 {@code INPATIENT}）。 */
    INPATIENT(0L, "INPATIENT", "住院发放", "", 2L),
    /** 临时发放（MedicationDispenseType，编码 {@code TEMPORARY}）。 */
    TEMPORARY(0L, "TEMPORARY", "临时发放", "", 3L),
    /** 退药发放（MedicationDispenseType，编码 {@code REFUND}）。 */
    REFUND(0L, "REFUND", "退药发放", "", 4L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /** 枚举构造：绑定编码系统、持久化 code、展示名与排序。 */
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
