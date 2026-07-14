/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

import lombok.Getter;

/**
 * 号源日程业务状态（DM-WKF-009 §4.2）。
 */
@Getter
public enum ScheduleStatus implements EnumInterface {
    /** 草稿（ScheduleStatus，编码 {@code draft}）。 */
    DRAFT(0L, "draft", "草稿", "", 1L),
    /** 已启用（ScheduleStatus，编码 {@code active}）。 */
    ACTIVE(0L, "active", "已启用", "", 2L),
    /** 已停用（ScheduleStatus，编码 {@code inactive}）。 */
    INACTIVE(0L, "inactive", "已停用", "", 3L),
    /** 已废弃（ScheduleStatus，编码 {@code retired}）。 */
    RETIRED(0L, "retired", "已废弃", "", 4L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    ScheduleStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

}
