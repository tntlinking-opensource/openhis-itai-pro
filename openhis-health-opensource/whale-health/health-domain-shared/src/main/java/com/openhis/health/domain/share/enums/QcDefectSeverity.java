/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 质控缺陷严重级别。
 */
public enum QcDefectSeverity implements EnumInterface {
/** 阻断（QcDefectSeverity，持久化编码 {@code block}）。 */
    BLOCK(0L, "block", "阻断", "", 0L),
/** 警告（QcDefectSeverity，持久化编码 {@code warn}）。 */
    WARN(0L, "warn", "警告", "", 1L),
/** 提示（QcDefectSeverity，持久化编码 {@code info}）。 */
    INFO(0L, "info", "提示", "", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举常量构造，绑定持久化 {@code code}、展示名与排序。
     *
     * @param codeSystemId 编码体系主键（预留）
     * @param code 持久化 code
     * @param display 界面展示名
     * @param definition 定义说明
     * @param sortOrder 排序号
     */
    QcDefectSeverity(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
