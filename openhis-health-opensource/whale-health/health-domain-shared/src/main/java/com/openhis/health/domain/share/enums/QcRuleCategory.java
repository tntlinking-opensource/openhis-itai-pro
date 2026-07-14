/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 质控规则分类。
 */
public enum QcRuleCategory implements EnumInterface {
/** 完整性（QcRuleCategory，持久化编码 {@code completeness}）。 */
    COMPLETENESS(0L, "completeness", "完整性", "", 0L),
/** 及时性（QcRuleCategory，持久化编码 {@code timeliness}）。 */
    TIMELINESS(0L, "timeliness", "及时性", "", 1L),
/** 一致性（QcRuleCategory，持久化编码 {@code consistency}）。 */
    CONSISTENCY(0L, "consistency", "一致性", "", 2L),
/** 编码（QcRuleCategory，持久化编码 {@code coding}）。 */
    CODING(0L, "coding", "编码", "", 3L),
/** 文书（QcRuleCategory，持久化编码 {@code document}）。 */
    DOCUMENT(0L, "document", "文书", "", 4L);

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
    QcRuleCategory(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
