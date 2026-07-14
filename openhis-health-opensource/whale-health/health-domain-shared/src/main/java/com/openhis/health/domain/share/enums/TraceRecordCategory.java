/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 追溯码记录类别（DM-CUS-010 §4.4 {@code record_category_enum}）。
 */
public enum TraceRecordCategory implements EnumInterface {

    /**
     * 进出院类：改变主表剩余与在院态。
     */
    HOSPITAL_IO(0L, "HOSPITAL_IO", "进出院", "动主表剩余与在院态", 1L),

    /**
     * 院内流转：调拨/请领等成对记子表，不改变主表剩余。
     */
    INTERNAL_TRANSFER(0L, "INTERNAL_TRANSFER", "院内流转", "院内调拨成对 OUT+IN", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    TraceRecordCategory(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
