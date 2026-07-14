/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;
/**
 * 耗材发放状态枚举（对齐 DM-WKF-007 §2.3）。
 */
public enum DispenseStatus implements EnumInterface {
/** 草稿（DispenseStatus，编码 {@code DRAFT}）。 */
    DRAFT(0L, "DRAFT", "草稿", "", 0L),
/** 待配药（DispenseStatus，编码 {@code PREPARATION}）。 */
    PREPARATION(0L, "PREPARATION", "待配药", "", 1L),
/** 已配药（DispenseStatus，编码 {@code PREPARED}）。 */
    PREPARED(0L, "PREPARED", "已配药", "", 2L),
/** 待发药（DispenseStatus，编码 {@code IN_PROGRESS}）。 */
    IN_PROGRESS(0L, "IN_PROGRESS", "待发药", "", 3L),
/** 已发药（DispenseStatus，编码 {@code COMPLETED}）。 */
    COMPLETED(0L, "COMPLETED", "已发药", "", 4L),
/** 已汇总（DispenseStatus，编码 {@code SUMMARIZED}）。 */
    SUMMARIZED(0L, "SUMMARIZED", "已汇总", "", 5L),
/** 部分发药（DispenseStatus，编码 {@code PART_COMPLETED}）。 */
    PART_COMPLETED(0L, "PART_COMPLETED", "部分发药", "", 6L),
/** 暂停发药（DispenseStatus，编码 {@code ON_HOLD}）。 */
    ON_HOLD(0L, "ON_HOLD", "暂停发药", "", 7L),
/** 停止发药（DispenseStatus，编码 {@code STOPPED}）。 */
    STOPPED(0L, "STOPPED", "停止发药", "", 8L),
/** 拒绝发药（DispenseStatus，编码 {@code DECLINED}）。 */
    DECLINED(0L, "DECLINED", "拒绝发药", "", 9L),
/** 待退药（DispenseStatus，编码 {@code PENDING_REFUND}）。 */
    PENDING_REFUND(0L, "PENDING_REFUND", "待退药", "", 10L),
/** 部分退药（DispenseStatus，编码 {@code PART_REFUND}）。 */
    PART_REFUND(0L, "PART_REFUND", "部分退药", "", 11L),
/** 已退药（DispenseStatus，编码 {@code REFUNDED}）。 */
    REFUNDED(0L, "REFUNDED", "已退药", "", 12L),
/** 已取消（DispenseStatus，编码 {@code CANCELLED}）。 */
    CANCELLED(0L, "CANCELLED", "已取消", "", 13L),
/** 未知（DispenseStatus，编码 {@code UNKNOWN}）。 */
    UNKNOWN(0L, "UNKNOWN", "未知", "", 99L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    DispenseStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
     * 判断状态码是否与当前枚举值匹配。
     *
     * @param statusCode 状态码（String）
     * @return 匹配返回 {@code true}
     */
    public boolean sameAs(String statusCode) {
        return this.getCode().equals(statusCode);
    }

    /**
     * 是否计入临时医嘱多段发放的「待发/在途」数量合计（BR-IPP-006 / BR-OPP-006）。
     *
     * <p>不含已发、待退/已退、已拒绝等终态或退药队列行；取消执行后 {@link #PENDING_REFUND} 不得与再执行新建的
     * {@link #PREPARATION} 行重复计入。</p>
     *
     * @return {@code true} 表示该行数量应参与临时医嘱拆分行合计校验
     */
    public boolean countsTowardTemporaryTherapyPendingQuantitySum() {
        return this == DRAFT
            || this == PREPARATION
            || this == PREPARED
            || this == IN_PROGRESS
            || this == SUMMARIZED;
    }

}
