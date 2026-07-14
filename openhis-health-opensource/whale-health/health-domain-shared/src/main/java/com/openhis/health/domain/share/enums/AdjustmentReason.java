/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;
/**
 * 调整原因枚举。
 *
 * <p>存储口径：字符串编码。</p>
 */
public enum AdjustmentReason implements EnumInterface {
    /** 市场价格调整（AdjustmentReason，编码 {@code MARKET_CHANGE}）。 */
    MARKET_CHANGE(0L, "MARKET_CHANGE", "市场价格调整", "", 1L),
    /** 促销活动（AdjustmentReason，编码 {@code PROMOTION}）。 */
    PROMOTION(0L, "PROMOTION", "促销活动", "", 2L),
    /** 成本变动（AdjustmentReason，编码 {@code COST_CHANGE}）。 */
    COST_CHANGE(0L, "COST_CHANGE", "成本变动", "", 3L),
    /** 政策调整（AdjustmentReason，编码 {@code POLICY_CHANGE}）。 */
    POLICY_CHANGE(0L, "POLICY_CHANGE", "政策调整", "", 4L),
    /** 数据错误修正（AdjustmentReason，编码 {@code MISTAKE}）。 */
    MISTAKE(0L, "MISTAKE", "数据错误修正", "", 5L),
    /** 其他原因（AdjustmentReason，编码 {@code OTHER}）。 */
    OTHER(0L, "OTHER", "其他原因", "", 6L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /** 枚举构造：绑定编码系统、持久化 code、展示名与排序。 */
    AdjustmentReason(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
