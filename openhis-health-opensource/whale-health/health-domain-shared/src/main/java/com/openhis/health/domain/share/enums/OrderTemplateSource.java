/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 组套模板来源（维护端组套 vs 医生站开嘱另存）。
 */
public enum OrderTemplateSource implements EnumInterface {
    /**
     * 维护组套：组套维护端提前配置（对应 openhis 维护页组套语义）。
     */
    MAINTAINED(0L, "MAINTAINED", "维护组套", "", 1L),
    /**
     * 医嘱模板：医生站开嘱完成后另存，下次直接引用（对应 openhis OrdersGroupPackage 存取语义）。
     */
    FROM_ADVICE(0L, "FROM_ADVICE", "医嘱模板", "", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    OrderTemplateSource(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
