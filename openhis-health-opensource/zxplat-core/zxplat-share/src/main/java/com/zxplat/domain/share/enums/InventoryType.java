/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;
/**
 * 仓库类型枚举（DM-WKF-008 §9.3 说明）。
 */
public enum InventoryType implements EnumInterface {
    /** 仓库（InventoryType，编码 {@code INVENTORY}）。 */
    INVENTORY(0L, "INVENTORY", "仓库", "", 1L),
    /** 药房（InventoryType，编码 {@code PHARMACY}）。 */
    PHARMACY(0L, "PHARMACY", "药房", "", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    InventoryType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
