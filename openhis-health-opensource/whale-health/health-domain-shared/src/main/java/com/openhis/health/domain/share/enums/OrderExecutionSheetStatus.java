/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 医嘱执行单状态（读模型 {@code status_enum} 持久化码，与历史整数码字符串一致）。
 */
public enum OrderExecutionSheetStatus implements EnumInterface {

    /** 执行完成（OrderExecutionSheetStatus，编码 {@code 30}）。 */
    COMPLETED(0L, "30", "执行完成", "", 30L),

    /** 执行取消（OrderExecutionSheetStatus，编码 {@code 90}）。 */
    CANCELLED(0L, "90", "执行取消", "", 90L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    OrderExecutionSheetStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
