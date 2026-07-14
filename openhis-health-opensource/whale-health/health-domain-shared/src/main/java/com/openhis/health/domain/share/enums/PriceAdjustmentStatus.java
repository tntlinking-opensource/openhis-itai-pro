/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 价格调整审批状态枚举。
 */
public enum PriceAdjustmentStatus implements EnumInterface {
    /** 草稿（PriceAdjustmentStatus，编码 {@code draft}）。 */
    DRAFT(0L, "draft", "草稿", "", 1L),
    /** 待审批（PriceAdjustmentStatus，编码 {@code pending}）。 */
    PENDING(0L, "pending", "待审批", "", 2L),
    /** 已通过（PriceAdjustmentStatus，编码 {@code approved}）。 */
    APPROVED(0L, "approved", "已通过", "", 3L),
    /** 已驳回（PriceAdjustmentStatus，编码 {@code rejected}）。 */
    REJECTED(0L, "rejected", "已驳回", "", 4L),
    /** 已废弃（PriceAdjustmentStatus，编码 {@code deprecated}）。 */
    DEPRECATED(0L, "deprecated", "已废弃", "", 5L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    PriceAdjustmentStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
