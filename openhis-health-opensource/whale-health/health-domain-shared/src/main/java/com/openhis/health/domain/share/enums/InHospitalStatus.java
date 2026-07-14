/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 追溯码主档在院状态（DM-CUS-010 §4.5 {@code in_hospital_status_enum}）。
 */
public enum InHospitalStatus implements EnumInterface {

    /**
     * 该码在院内仍有剩余。
     */
    IN_HOSPITAL(0L, "IN_HOSPITAL", "在院", "remaining_split_quantity > 0", 1L),

    /**
     * 该码已耗尽或已离开院内。
     */
    NOT_IN_HOSPITAL(0L, "NOT_IN_HOSPITAL", "不在院", "剩余耗尽或已出院", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    InHospitalStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
