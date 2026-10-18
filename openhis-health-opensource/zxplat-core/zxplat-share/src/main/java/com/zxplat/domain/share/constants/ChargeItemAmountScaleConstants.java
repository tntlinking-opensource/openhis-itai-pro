/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 收费定价/账单金额在库内统一为「元」的标度（与 {@code numeric(19,6)} 对齐）。
 *
 * <p>全局金钱契约见 {@code prompts/代码生成通用规范/references/monetary-amount-contract.md}；
 * 计算工具见 {@link com.zxplat.domain.share.utils.MonetaryAmountSupport}。</p>
 */
public interface ChargeItemAmountScaleConstants {

    /**
     * 元金额写入定价行、账单、行级 API 时的统一小数位数（计算刻度；与库 {@code numeric(19,6)} 对齐）。
     */
    int CHARGE_MONETARY_SCALE = 6;

    /**
     * 售价对外刻度（2 位 HALF_UP）：支付/发票合计、售价单价、售价行额、折后售价、目录 SALE/批号售价。
     *
     * <p>收费项 {@code unit_price}/{@code total_price}、包装锚派生后的售价单价均须落在本刻度；
     * 除不尽时 HALF_UP 到本刻度写入收费事实，<strong>禁止</strong>保留 {@link #CHARGE_MONETARY_SCALE} 长尾。
     * <strong>禁止</strong>用于药品进价合计：进价见 {@link #CHARGE_MONETARY_PURCHASE_SETTLE_SCALE}。</p>
     */
    int CHARGE_MONETARY_SETTLE_SCALE = 2;

    /**
     * 进价合计权威结算刻度：3 位 HALF_UP（药品进价全码；无中药时第三位为 0）。
     *
     * <p>适用：药房日结、发药报表、费用明细、医生工作量等<strong>药品进价合计</strong>——先 6 位代数加总再落到本刻度。
     * <strong>禁止</strong>对进价合计套用 {@link #CHARGE_MONETARY_SETTLE_SCALE}（售价两位）。
     * <strong>耗材</strong>进价合计仍用售价两位，不套本刻度。</p>
     */
    int CHARGE_MONETARY_PURCHASE_SETTLE_SCALE = 3;

    /**
     * 前端展示刻度（与 {@link #CHARGE_MONETARY_SETTLE_SCALE} 同为 2 位；仅 UI format /
     * <strong>Excel 售价/收费金额列</strong>，非入账权威）。
     *
     * <p>xlsx <strong>售价 / 收费 / 实收</strong>列 {@code scale=}{@link #CHARGE_MONETARY_DISPLAY_SCALE} + {@code HALF_UP}。
     * <strong>进价单价 / 进价总额</strong>列见 {@link #CHARGE_MONETARY_TCM_UNIT_PRICE_DISPLAY_SCALE}。</p>
     */
    int CHARGE_MONETARY_DISPLAY_SCALE = 2;

    /**
     * 中药饮片/中草药（{@code medication_category_code=4}）**进价、进总价**展示/导出刻度：3 位
     * （与 {@link #CHARGE_MONETARY_PURCHASE_SETTLE_SCALE} 同值；展示不得再压成售价 2 位）。
     *
     * <p>适用：药品目录采购参考价、批号进价、库房进价列、报表/单据 Excel 进价单价与进价合计。
     * Excel 写出 {@code scale=} 本常量 + {@code RoundingMode.DOWN}（第四位起截断，禁止压成 2 位四舍五入）。
     * <strong>售价（销售参考价 / 批号售价 / 收费）仍为 {@link #CHARGE_MONETARY_DISPLAY_SCALE}</strong>。
     * 行级落库仍走 {@link #CHARGE_MONETARY_SCALE}（6 位）；汇总权威走
     * {@link #CHARGE_MONETARY_PURCHASE_SETTLE_SCALE}。与前端 {@code MONEY_TCM_UNIT_PRICE_DP} 对齐。</p>
     */
    int CHARGE_MONETARY_TCM_UNIT_PRICE_DISPLAY_SCALE = 3;
}
