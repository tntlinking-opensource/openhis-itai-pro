/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 就诊类别枚举。
 */
public enum EncounterClass implements EnumInterface {
    /** 住院（EncounterClass，编码 {@code IMP}）。 */
    IMP(0L, "IMP", "住院", "", 1L),
    /** 门诊（EncounterClass，编码 {@code AMB}）。 */
    AMB(0L, "AMB", "门诊", "", 2L),
    /** 留观（EncounterClass，编码 {@code OBSENC}）。 */
    OBSENC(0L, "OBSENC", "留观", "", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    EncounterClass(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
