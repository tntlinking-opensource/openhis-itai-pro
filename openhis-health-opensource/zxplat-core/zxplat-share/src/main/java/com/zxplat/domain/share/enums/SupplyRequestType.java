/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;
/**
 * 供应请求位置类型枚举。
 *
 * <p>存储口径：{@code wkf_supply_request.source_type_enum} / {@code wkf_supply_request.purpose_type_enum}。</p>
 */
public enum SupplyRequestType implements EnumInterface {
    /** 科室（SupplyRequestType，编码 {@code DEPARTMENT}）。 */
    DEPARTMENT(0L, "DEPARTMENT", "科室", "", 2L),
    /** 库房（SupplyRequestType，编码 {@code CABINET}）。 */
    CABINET(0L, "CABINET", "库房", "", 11L),
    /** 药房（SupplyRequestType，编码 {@code PHARMACY}）。 */
    PHARMACY(0L, "PHARMACY", "药房", "", 16L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    SupplyRequestType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
