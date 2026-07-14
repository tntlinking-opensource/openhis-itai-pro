/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 供应商状态枚举。
 */
public enum SupplierStatus implements EnumInterface {
    /** 草稿（SupplierStatus，编码 {@code DRAFT}）。 */
    DRAFT(0L, "DRAFT", "草稿", "", 1L),
    /** 已启用（SupplierStatus，编码 {@code ACTIVE}）。 */
    ACTIVE(0L, "ACTIVE", "已启用", "", 2L),
    /** 已暂停（SupplierStatus，编码 {@code SUSPENDED}）。 */
    SUSPENDED(0L, "SUSPENDED", "已暂停", "", 3L),
    /** 已删除（SupplierStatus，编码 {@code DELETED}）。 */
    DELETED(0L, "DELETED", "已删除", "", 4L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    SupplierStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
