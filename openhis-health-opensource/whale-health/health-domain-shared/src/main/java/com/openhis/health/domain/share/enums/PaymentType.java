/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 付款类别枚举。
 */
public enum PaymentType implements EnumInterface {
    /** 付费（正向收款）（PaymentType，编码 {@code PAY}）。 */
    PAY(0L, "PAY", "付费（正向收款）", "门诊收费、住院收费、预交金收取等正向收款场景。", 0L),
    /** 退费（逆向退款）（PaymentType，编码 {@code UN_PAY}）。 */
    UN_PAY(0L, "UN_PAY", "退费（逆向退款）", "门诊退费、住院取消结算等逆向退款场景。", 1L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    PaymentType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
