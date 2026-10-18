/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 费用性质（财务合约）分类：与基础数据维护页 {@code category} 下拉及库内 {@code category} 字符串码对齐。
 *
 * <p>持久化/接口字段与 {@link #getCode()}（{@code "1"}～{@code "4"}）对齐；展示经
 * {@link com.zxplat.domain.share.utils.EnumUtil#fromCode(Class, String)} 解析后取 {@link #getDisplay()}。</p>
 */
public enum ChargeTypeCategory implements EnumInterface {

/** 自费（ChargeTypeCategory，持久化编码 {@code 1}）。 */
    SELF_PAY(0L, "1", "自费", "", 1L),
/** 公费（ChargeTypeCategory，持久化编码 {@code 2}）。 */
    PUBLIC_PAY(0L, "2", "公费", "", 2L),
/** 省医保（ChargeTypeCategory，持久化编码 {@code 3}）。 */
    PROVINCIAL_INSURANCE(0L, "3", "省医保", "", 3L),
/** 市医保（ChargeTypeCategory，持久化编码 {@code 4}）。 */
    MUNICIPAL_INSURANCE(0L, "4", "市医保", "", 4L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    ChargeTypeCategory(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
