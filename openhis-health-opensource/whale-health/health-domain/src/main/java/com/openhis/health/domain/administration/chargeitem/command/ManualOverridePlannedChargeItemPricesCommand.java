/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 批量手工改价：仅允许 {@code PLANNED} 收费明细调整单价并落库审计字段。
 */
@Getter
@Setter
public class ManualOverridePlannedChargeItemPricesCommand
    extends BatchCommand<ManualOverridePlannedChargeItemPricesCommand.Item> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 就诊主键（门禁：明细须归属该就诊）。 */
    private Long encounterId;

    /**
     * 单条改价项。
     */
    @Getter
    @Setter
    public static class Item {
        /** 收费明细主键。 */
        private Long chargeItemId;
        /** 新单价（结算刻度）。 */
        private BigDecimal unitPrice;
        /** 改价原因码。 */
        private String overrideReasonCode;
        /** 改价原因说明。 */
        private String overrideReasonText;
    }
}
