/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 费用性质分类枚举。
 */
public enum ContractCategory implements EnumInterface {
    /** 自费（ContractCategory，编码 {@code SELF_CHARGE}）。 */
    SELF_CHARGE(0L, "SELF_CHARGE", "", "", 1L),
    /** 公费（ContractCategory，编码 {@code PUBLIC_CHARGE}）。 */
    PUBLIC_CHARGE(0L, "PUBLIC_CHARGE", "", "", 2L),
    /** 省医保（ContractCategory，编码 {@code PROVINCIAL_INSURANCE}）。 */
    PROVINCIAL_INSURANCE(0L, "PROVINCIAL_INSURANCE", "", "", 3L),
    /** 市医保（ContractCategory，编码 {@code MUNICIPAL_INSURANCE}）。 */
    MUNICIPAL_INSURANCE(0L, "MUNICIPAL_INSURANCE", "", "", 4L),
    /** 职工医保（ContractCategory，编码 {@code WORKER_INSURANCE}）。 */
    WORKER_INSURANCE(0L, "WORKER_INSURANCE", "", "", 5L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    ContractCategory(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
