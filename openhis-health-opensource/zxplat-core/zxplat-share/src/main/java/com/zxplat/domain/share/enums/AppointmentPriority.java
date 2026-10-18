/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 预约候诊优先级（读模型 {@code priority_enum} 与历史整型 1 对齐）。
 */
public enum AppointmentPriority implements EnumInterface {

    /** 优先就诊（最高优先级）。 */
    PRIORITY(0L, "1", "优先就诊", "", 1L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    AppointmentPriority(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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

    /**
     * 转为读模型整型优先级（与历史 {@code priority_enum=1} 一致）。
     *
     * @return 优先级整型
     */
    public int toPriorityEnumValue() {
        if (this.sortOrder == null) {
            return 0;
        }
        return this.sortOrder.intValue();
    }
}
