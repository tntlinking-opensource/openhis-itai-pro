/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 诊断模板绑定类型枚举。
 */
public enum ConditionTemplateBinding implements EnumInterface {
    /** 个人（ConditionTemplateBinding，编码 {@code PERSONAL}）。 */
    PERSONAL(0L, "PERSONAL", "个人", "", 1L),
    /** 科室（ConditionTemplateBinding，编码 {@code DEPARTMENT}）。 */
    DEPARTMENT(0L, "DEPARTMENT", "科室", "", 2L),
    /** 全院（ConditionTemplateBinding，编码 {@code HOSPITAL}）。 */
    HOSPITAL(0L, "HOSPITAL", "全院", "", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    ConditionTemplateBinding(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
