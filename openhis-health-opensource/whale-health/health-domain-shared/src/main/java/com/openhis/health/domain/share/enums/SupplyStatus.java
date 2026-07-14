/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;
/**
 * 供应请求单据状态枚举。
 *
 * <p>存储口径：{@code wkf_supply_request.status_enum}。</p>
 */
public enum SupplyStatus implements EnumInterface {
    /** 待审核（SupplyStatus，编码 {@code PENDING_APPROVAL}）。 */
    PENDING_APPROVAL(0L, "PENDING_APPROVAL", "待审核", "", 1L),
    /** 审核中（SupplyStatus，编码 {@code APPROVAL}）。 */
    APPROVAL(0L, "APPROVAL", "审核中", "", 2L),
    /** 同意（SupplyStatus，编码 {@code AGREE}）。 */
    AGREE(0L, "AGREE", "同意", "", 3L),
    /** 驳回（SupplyStatus，编码 {@code REJECT}）。 */
    REJECT(0L, "REJECT", "驳回", "", 4L),
    /** 已撤回（SupplyStatus，编码 {@code WITHDRAW}）。 */
    WITHDRAW(0L, "WITHDRAW", "已撤回", "", 9L),
    /** 待复审（SupplyStatus，编码 {@code PENDING_REVIEW}）。 */
    PENDING_REVIEW(0L, "PENDING_REVIEW", "待复审", "", 10L),
    /** 已失效（SupplyStatus，编码 {@code EXPIRED_INVALIDATED}）。 */
    EXPIRED_INVALIDATED(0L, "EXPIRED_INVALIDATED", "已失效", "", 11L),
    /** 已发送（SupplyStatus，编码 {@code SEND}）。 */
    SEND(0L, "SEND", "已发送", "", 12L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    SupplyStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
