/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 借阅申请工作流状态。
 */
public enum BorrowRequestStatus implements EnumInterface {
/** 待审（BorrowRequestStatus，持久化编码 {@code pending}）。 */
    PENDING(0L, "pending", "待审", "", 0L),
/** 已批准（BorrowRequestStatus，持久化编码 {@code approved}）。 */
    APPROVED(0L, "approved", "已批准", "", 1L),
/** 已拒绝（BorrowRequestStatus，持久化编码 {@code rejected}）。 */
    REJECTED(0L, "rejected", "已拒绝", "", 2L),
/** 已借（BorrowRequestStatus，持久化编码 {@code borrowed}）。 */
    BORROWED(0L, "borrowed", "已借", "", 3L),
/** 已还（BorrowRequestStatus，持久化编码 {@code returned}）。 */
    RETURNED(0L, "returned", "已还", "", 4L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举常量构造，绑定持久化 {@code code}、展示名与排序。
     *
     * @param codeSystemId 编码体系主键（预留）
     * @param code 持久化 code
     * @param display 界面展示名
     * @param definition 定义说明
     * @param sortOrder 排序号
     */
    BorrowRequestStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
