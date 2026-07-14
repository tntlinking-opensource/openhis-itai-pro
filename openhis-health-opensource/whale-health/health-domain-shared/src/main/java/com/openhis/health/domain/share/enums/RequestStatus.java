/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

import lombok.Getter;

/**
 * 药品申请状态枚举。
 */
@Getter
public enum RequestStatus implements EnumInterface {
    /** 待签发（RequestStatus，编码 {@code DRAFT}）。 */
    DRAFT(0L, "DRAFT", "待签发", "", 1L),
    /** 已发送（RequestStatus，编码 {@code ACTIVE}）。 */
    ACTIVE(0L, "ACTIVE", "已发送", "", 2L),
    /** 已校对（RequestStatus，编码 {@code VERIFIED}）。 */
    VERIFIED(0L, "VERIFIED", "已校对", "", 3L),
    /** 暂挂（RequestStatus，编码 {@code HOLD}）。 */
    HOLD(0L, "HOLD", "暂挂", "", 4L),
    /** 已取消（RequestStatus，编码 {@code CANCELLED}）。 */
    CANCELLED(0L, "CANCELLED", "已取消", "", 5L),
    /** 已停嘱（RequestStatus，编码 {@code STOPPED}）。 */
    STOPPED(0L, "STOPPED", "已停嘱", "", 6L),
    /** 已完成（RequestStatus，编码 {@code COMPLETED}）。 */
    COMPLETED(0L, "COMPLETED", "已完成", "", 7L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    RequestStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

}
