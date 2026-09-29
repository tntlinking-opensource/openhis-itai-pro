/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

import lombok.Getter;

/**
 * 检查工单执行态（存库 code，对齐 DM-PACS-001）。
 */
@Getter
public enum PacsWorkItemStatus implements EnumInterface {

    /** 已接单，待登记确认。 */
    REGISTERED(0L, "registered", "已接单", "INT 创建后待登记", 1L),
    /** 已登记（有检查号），待摄。 */
    SCHEDULED(0L, "scheduled", "待摄", "登记确认后待执行", 2L),
    /** 检查执行中。 */
    IN_PROGRESS(0L, "in-progress", "执行中", "可选中间态", 3L),
    /** 已检，可写报告。 */
    COMPLETED(0L, "completed", "已检", "执行完成", 4L),
    /** 作废。 */
    ENTERED_IN_ERROR(0L, "entered-in-error", "作废", "撤单或登记作废", 5L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    PacsWorkItemStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }
}
