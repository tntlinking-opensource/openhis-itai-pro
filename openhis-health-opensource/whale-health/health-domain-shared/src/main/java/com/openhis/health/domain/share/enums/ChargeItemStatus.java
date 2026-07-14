/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

import lombok.Getter;

/**
 * 收费明细生命周期状态枚举。
 */
@Getter
public enum ChargeItemStatus implements EnumInterface {
/** 草稿（ChargeItemStatus）。 */
    DRAFT(0L, "DRAFT", "草稿", "业务已开立但未进入收费窗口。", 1L),
    /** 已计划（待收费）：医嘱签发等形成可收费事实，尚未占用住院预交金展示余额。 */
    PLANNED(0L, "PLANNED", "待收费", "已形成待收费事实；门诊可预结算，住院签发后待执行/划价激活。", 2L),
    /** 待结算：已划价或已执行入账，占用预交金展示余额（预交金净值 − 待结算合计）。 */
    PENDING_SETTLEMENT(0L, "PENDING_SETTLEMENT", "待结算", "费用已计入在院累计，待日结或出院结算。", 3L),
/** 已结算（ChargeItemStatus）。 */
    SETTLED(0L, "SETTLED", "已结算", "收费已完成。", 4L),
/** 退款中（ChargeItemStatus）。 */
    REFUNDING(0L, "REFUNDING", "退款中", "退费处理中。", 5L),
/** 已退款（ChargeItemStatus）。 */
    REFUNDED(0L, "REFUNDED", "已退款", "退费已完成。", 6L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    ChargeItemStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

}
