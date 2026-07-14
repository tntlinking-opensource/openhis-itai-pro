/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 支付流水状态枚举。
 */
public enum PaymentStatus implements EnumInterface {
    /** 草稿（预结算已生成，尚未收款）（PaymentStatus，编码 {@code DRAFT}）。 */
    DRAFT(0L, "DRAFT", "草稿（预结算已生成，尚未收款）", "", 0L),
    /** 成功（收款已完成）（PaymentStatus，编码 {@code SUCCESS}）。 */
    SUCCESS(0L, "SUCCESS", "成功（收款已完成）", "", 1L),
    /** 已取消（收费前放弃）（PaymentStatus，编码 {@code CANCELLED}）。 */
    CANCELLED(0L, "CANCELLED", "已取消（收费前放弃）", "", 2L),
    /** 已全额退款（PaymentStatus，编码 {@code REFUNDED}）。 */
    REFUNDED(0L, "REFUNDED", "已全额退款", "", 3L),
    /** 已部分退款（PaymentStatus，编码 {@code PARTIAL_REFUNDED}）。 */
    PARTIAL_REFUNDED(0L, "PARTIAL_REFUNDED", "已部分退款", "", 4L),
    /** 退款受理中（PaymentStatus，编码 {@code REFUND_PROCESSING}）。 */
    REFUND_PROCESSING(0L, "REFUND_PROCESSING", "退款受理中", "", 5L),
    /** 错误（支付处理失败或误录）（PaymentStatus，编码 {@code ERROR}）。 */
    ERROR(0L, "ERROR", "错误（支付处理失败或误录）", "", 9L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    PaymentStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
