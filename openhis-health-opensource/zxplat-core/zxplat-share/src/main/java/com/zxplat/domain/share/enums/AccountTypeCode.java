/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 就诊账户及关联业务 type_code 字典（adm_account、发票草稿等持久化存 {@link #getCode()}）。
 *
 * <p>门诊默认可挂账账户介质为 {@link #PERSONAL_CASH_ACCOUNT}；{@link #REG} 仅用于挂号费发票草稿等非账户主表场景。</p>
 */
public enum AccountTypeCode implements EnumInterface {

    /** 医保电子凭证介质账户。 */
    MEDICAL_ELECTRONIC_CERTIFICATE(
        0L,
        "MEDICAL_ELECTRONIC_CERTIFICATE",
        "医保电子凭证",
        "国家医保电子凭证介质。",
        10L),

    /** 居民身份证介质账户。 */
    ID_CARD(
        0L,
        "ID_CARD",
        "居民身份证",
        "居民身份证介质。",
        20L),

    /** 社会保障卡介质账户。 */
    SOCIAL_SECURITY_CARD(
        0L,
        "SOCIAL_SECURITY_CARD",
        "社会保障卡",
        "实体社会保障卡介质。",
        30L),

    /** 个人现金账户（门诊挂号费、医嘱费用草稿等默认挂账锚点）。 */
    PERSONAL_CASH_ACCOUNT(
        0L,
        "PERSONAL_CASH_ACCOUNT",
        "个人现金账户",
        "个人现金支付账户；门诊默认可挂账介质。",
        40L),

    /** 默认医保账户。 */
    MEDICAL_INSURANCE(
        0L,
        "MEDICAL_INSURANCE",
        "医保账户",
        "就诊默认医保分账账户。",
        50L),

    /**
     * 公费账户（Campus ADR-CMP-001 D12）。
     *
     * <p>挂号时若 Campus 启用且存在学生档案则幂等开立；支付明细公费行挂本类型。
     * 收银改草稿落账见 {@code TODO(ADR-CMP-001 D5/D12)}。</p>
     */
    PUBLIC_EXPENSE_ACCOUNT(
        0L,
        "PUBLIC_EXPENSE_ACCOUNT",
        "公费账户",
        "高校公费分账账户；依赖 zxplat-module-campus。",
        60L),

    /**
     * 挂号费发票草稿等业务 type_code（非 adm_account 主路径；与历史 REG 字面量对齐）。
     */
    REG(
        0L,
        "REG",
        "挂号",
        "挂号费发票草稿等侧字典码。",
        0L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    AccountTypeCode(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    @Override
    public Long getCodeSystemId() {
        return codeSystemId;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDisplay() {
        return display;
    }

    @Override
    public String getDefinition() {
        return definition;
    }

    @Override
    public Long getSortOrder() {
        return sortOrder;
    }
}
