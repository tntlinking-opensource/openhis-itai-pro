/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 交账批次审批状态（DM-FIN-003 {@code status_enum}）。
 *
 * <p>仅四态：待审 / 已通过 / 驳回 / 撤回。库内旧码由 Flyway 一次性迁完，运行时不兼容。</p>
 */
public enum CashierSettlementStatus implements EnumInterface {

    /** 已提交待审；源行已挂载。 */
    SUBMITTED(0L, "SUBMITTED", "待审批", "可撤回、驳回、审批通过", 0L),

    /** 财务已通过（终态）。 */
    APPROVED(0L, "APPROVED", "已通过", "本轮无写", 1L),

    /** 待审驳回；已清挂载。 */
    REJECTED(0L, "REJECTED", "已驳回", "只读审计", 2L),

    /** 提交人撤回；已清挂载。 */
    WITHDRAWN(0L, "WITHDRAWN", "已撤回", "只读审计", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    CashierSettlementStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
     * 是否为待审（可撤回/驳回/通过）。
     *
     * @param statusEnum 状态 code
     * @return true=待审
     */
    public static boolean isSubmitted(String statusEnum) {
        return SUBMITTED.getCode().equals(statusEnum);
    }
}
