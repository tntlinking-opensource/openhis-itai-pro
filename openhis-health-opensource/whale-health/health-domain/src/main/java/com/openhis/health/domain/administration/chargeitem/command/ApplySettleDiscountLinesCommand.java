/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量写入收费折扣结算快照命令（DM-ADM-011 / BR-OPC-032）。
 *
 * <p>仅更新金额与折扣快照字段，不改状态；调用方须保证行仍为可结算态。</p>
 */
@Getter
@Setter
public class ApplySettleDiscountLinesCommand {

    /**
     * 折扣行清单。
     */
    private List<Item> items = new ArrayList<>();

    /**
     * 解析非空行列表。
     *
     * @return 行列表（永不 null）
     */
    public List<Item> resolveItems() {
        if (this.items == null) {
            return new ArrayList<>();
        }
        return this.items;
    }

    /**
     * 单行折扣快照。
     */
    @Getter
    @Setter
    public static class Item {

        /**
         * 收费明细主键。
         */
        private Long chargeItemId;

        /**
         * 折后应收（结算 2 位）。
         */
        private BigDecimal discountedReceivable;

        /**
         * 优惠额（原价 − 折后，结算 2 位）。
         */
        private BigDecimal discountGap;

        /**
         * 有效折扣率。
         */
        private BigDecimal settleDiscountRate;

        /**
         * 来源 code（CONFIG / CASHIER）。
         */
        private String settleDiscountSourceEnum;
    }
}
