/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 首页字段自动取值来源标记（field_sources_json 值域）。
 */
public enum FieldFillSource implements EnumInterface {
    /**
     * 人工修订，自动取值不得覆盖。
     */
    MANUAL(0L, "manual", "人工", "", 0L),
/** 自动（FieldFillSource，持久化编码 {@code auto}）。 */
    AUTO(0L, "auto", "自动", "", 1L),
    /**
     * 人工修订后又被自动刷新覆盖过。
     */
    MIXED(0L, "mixed", "混合", "", 2L);

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
    FieldFillSource(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
