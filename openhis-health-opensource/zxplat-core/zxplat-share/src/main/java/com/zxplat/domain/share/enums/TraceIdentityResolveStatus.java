/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 批量扫码身份反查状态（DM-CUS-010 §4.9）。
 */
public enum TraceIdentityResolveStatus implements EnumInterface {

    /**
     * 主表命中且在院可用。
     */
    OK(0L, "OK", "命中", "返回品项与在院态", 1L),

/** 未建档（TraceIdentityResolveStatus）。 */
    NOT_FOUND(0L, "NOT_FOUND", "未建档", "码从未入库", 2L),

    /**
     * 主表命中但不在院或剩余耗尽。
     */
    NOT_IN_HOSPITAL(0L, "NOT_IN_HOSPITAL", "不在院", "剩余不足或已出院", 3L),

    /**
     * 同一请求内重复提交相同追溯码。
     */
    DUPLICATE_IN_REQUEST(0L, "DUPLICATE_IN_REQUEST", "请求内重复", "应用层去重标记", 4L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    TraceIdentityResolveStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
