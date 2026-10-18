/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 入院费用性质意向（医生提示款台：医保 / 自费）。
 *
 * <p>仅意向，非真实医保入院或登记合同；真实费用性质由住院登记款台选合同确认。</p>
 */
public enum ChargeIntent implements EnumInterface {
    /** 医保（ChargeIntent，编码 {@code INSURANCE}）。 */
    INSURANCE(0L, "INSURANCE", "医保", "医生提示款台按医保办理入院登记；非医保入院事实。", 1L),
    /** 自费（ChargeIntent，编码 {@code SELF_PAY}）。 */
    SELF_PAY(0L, "SELF_PAY", "自费", "医生提示款台按自费办理入院登记；非费用性质事实。", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     *
     * @param codeSystemId 编码系统标识（本枚举未挂码表时为 0）
     * @param code         持久化身份串
     * @param display      展示名
     * @param definition   业务定义
     * @param sortOrder    展示排序
     */
    ChargeIntent(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
