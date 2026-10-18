/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;
/**
 * 供应请求单据类别枚举。
 *
 * <p>存储口径：{@code wkf_supply_request.category_enum}。</p>
 */
public enum SupplyCategory implements EnumInterface {
    /** 库存供应（SupplyCategory，编码 {@code STOCK_SUPPLY}）。 */
    STOCK_SUPPLY(0L, "STOCK_SUPPLY", "库存供应", "", 1L),
    /** 非库存供应（SupplyCategory，编码 {@code NON_STOCK}）。 */
    NON_STOCK(0L, "NON_STOCK", "非库存供应", "", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    SupplyCategory(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
