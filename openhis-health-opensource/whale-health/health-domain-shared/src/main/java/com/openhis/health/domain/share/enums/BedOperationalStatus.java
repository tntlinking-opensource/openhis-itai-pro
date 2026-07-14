/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 床位运营状态（床位一览读模型 {@code operational_status} 持久化码）。
 */
public enum BedOperationalStatus implements EnumInterface {

/** 空床（BedOperationalStatus）。 */
    UNOCCUPIED(0L, "UNOCCUPIED", "空床", "", 1L),

/** 已占用（BedOperationalStatus）。 */
    OCCUPIED(0L, "OCCUPIED", "已占用", "", 2L),

    /** 预留（已预留给特定患者）。 */
    RESERVED(0L, "RESERVED", "预留", "", 3L),

/** 暂停使用（BedOperationalStatus）。 */
    SUSPENDED(0L, "SUSPENDED", "暂停使用", "", 4L),

/** 已关闭（BedOperationalStatus）。 */
    CLOSED(0L, "CLOSED", "已关闭", "", 5L),

/** 已废止（BedOperationalStatus）。 */
    ABOLISHED(0L, "ABOLISHED", "已废止", "", 6L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    BedOperationalStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
