/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 组套模板行类型枚举。
 */
public enum OrderTemplateLineKind implements EnumInterface {
    /** 药品行（OrderTemplateLineKind，编码 {@code MEDICATION}）。 */
    MEDICATION(0L, "MEDICATION", "药品行", "", 1L),
    /** 诊疗行（OrderTemplateLineKind，编码 {@code SERVICE}）。 */
    SERVICE(0L, "SERVICE", "诊疗行", "", 2L),
    /** 耗材行（OrderTemplateLineKind，编码 {@code DEVICE}）。 */
    DEVICE(0L, "DEVICE", "耗材行", "", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    OrderTemplateLineKind(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
