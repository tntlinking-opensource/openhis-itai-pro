/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 费用性质状态枚举。
 */
public enum ContractStatus implements EnumInterface {
    /** 已启用：生效中（ContractStatus，编码 {@code ACTIVE}）。 */
    ACTIVE(0L, "ACTIVE", "已启用（生效中）", "", 1L),
    /** 已停用：暂停使用（ContractStatus，编码 {@code INACTIVE}）。 */
    INACTIVE(0L, "INACTIVE", "已停用（暂停使用）", "", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /** 枚举构造：绑定编码系统、持久化 code、展示名与排序。 */
    ContractStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
