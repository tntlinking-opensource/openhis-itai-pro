/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 追溯码报表本库状态（AS-CUS-TRC-002 · BR-TRC-RPT-005）。
 */
public enum LocationStockStatus implements EnumInterface {

    /**
     * 指定库位子表聚合净拆零量大于 0。
     */
    IN_LOCATION(0L, "IN_LOCATION", "本库在库", "子表轧差净量 > 0", 1L),

    /**
     * 指定库位子表聚合净拆零量小于等于 0。
     */
    OUT_OF_LOCATION(0L, "OUT_OF_LOCATION", "本库已出", "子表轧差净量 ≤ 0", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    LocationStockStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    @Override
    public Long getCodeSystemId() {
        return codeSystemId;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDisplay() {
        return display;
    }

    @Override
    public String getDefinition() {
        return definition;
    }

    @Override
    public Long getSortOrder() {
        return sortOrder;
    }
}
