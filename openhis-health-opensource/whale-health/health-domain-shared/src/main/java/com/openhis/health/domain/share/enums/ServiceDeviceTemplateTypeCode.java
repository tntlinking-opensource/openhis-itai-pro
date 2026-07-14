/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 诊疗耗材绑定类型编码枚举。
 */
public enum ServiceDeviceTemplateTypeCode implements EnumInterface {
    /** 用法绑定（ServiceDeviceTemplateTypeCode，编码 {@code USAGE}）。 */
    USAGE(0L, "USAGE", "用法绑定", "", 1L),
    /** 诊疗绑定（ServiceDeviceTemplateTypeCode，编码 {@code ACTIVITY}）。 */
    ACTIVITY(0L, "ACTIVITY", "诊疗绑定", "", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    ServiceDeviceTemplateTypeCode(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
