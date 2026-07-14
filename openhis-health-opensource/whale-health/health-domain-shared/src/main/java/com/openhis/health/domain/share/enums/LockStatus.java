/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;
/**
 * 库存锁定状态枚举（DM-WKF-008 §4.3）。
 */
public enum LockStatus implements EnumInterface {
    /** 锁定中：库存项已锁定，不可修改（LockStatus，编码 {@code LOCKED}）。 */
    LOCKED(0L, "LOCKED", "锁定中：库存项已锁定，不可修改", "", 0L),
    /** 已解锁：正常业务完成后解锁（LockStatus，编码 {@code UNLOCKED}）。 */
    UNLOCKED(0L, "UNLOCKED", "已解锁：正常业务完成后解锁", "", 1L),
    /** 强制解锁：异常恢复场景下强制解锁（LockStatus，编码 {@code FORCE_UNLOCKED}）。 */
    FORCE_UNLOCKED(0L, "FORCE_UNLOCKED", "强制解锁：异常恢复场景下强制解锁", "", 2L),
    /** 待确认：等待确认的锁定状态（LockStatus，编码 {@code PENDING_CONFIRM}）。 */
    PENDING_CONFIRM(0L, "PENDING_CONFIRM", "待确认：等待确认的锁定状态", "", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /** 枚举构造：绑定编码系统、持久化 code、展示名与排序。 */
    LockStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
