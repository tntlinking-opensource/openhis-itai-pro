/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 组套模板适用场景枚举。
 */
public enum OrderTemplateScene implements EnumInterface {
    /** 门诊（OrderTemplateScene，编码 {@code OUTPATIENT}）。 */
    OUTPATIENT(0L, "OUTPATIENT", "门诊", "", 1L),
    /** 住院（OrderTemplateScene，编码 {@code INPATIENT}）。 */
    INPATIENT(0L, "INPATIENT", "住院", "", 2L),
    /** 通用（OrderTemplateScene，编码 {@code COMMON}）。 */
    COMMON(0L, "COMMON", "通用", "", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    OrderTemplateScene(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
