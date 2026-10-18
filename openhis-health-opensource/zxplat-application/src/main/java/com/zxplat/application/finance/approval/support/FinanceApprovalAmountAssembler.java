/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.approval.support;

import com.zxplat.application.finance.approval.dto.FinanceApprovalDocumentRowDto;
import com.zxplat.domain.custom.priceadjustment.queries.model.PriceAdjustmentImpactSummaryQueryModel;
import com.zxplat.domain.share.enums.SupplyType;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 财务审批列表金额列装配（AS-FIN-APR-001-SDD §3.3 {@code fillAmounts}；BR-APR-009）。
 *
 * <p>按单据类型填对应金额字段；类型为空/全部时多列并存、空值可空。金额取行汇总，禁止前端重算。
 * 调价须分列填进价盈亏与售价盈亏，禁止加总为一列。</p>
 */
@Component
public class FinanceApprovalAmountAssembler {

    /**
     * 按供应明细行汇总金额写入列表行（进货/退货/损益/盘点口径）。
     *
     * @param row   列表行；不可 null
     * @param lines 同号供应明细；可空
     */
    public void fillAmounts(FinanceApprovalDocumentRowDto row, List<SupplyRequestModel> lines) {
        if (row == null || StringUtils.isEmpty(row.getTypeEnum())) {
            return;
        }
        if (lines == null || lines.isEmpty()) {
            return;
        }
        String typeCode = row.getTypeEnum().trim();
        // 进货：行 totalPrice 汇总 → 进货金额
        if (SupplyType.CABINET_INBOUND_RECEIPT.getCode().equals(typeCode)) {
            row.setInboundAmount(this.sumTotalPrice(lines));
            return;
        }
        // 退供应商：行 totalPrice 汇总 → 退货金额
        if (SupplyType.CABINET_RETURN_TO_SUPPLIER.getCode().equals(typeCode)) {
            row.setReturnAmount(this.sumTotalPrice(lines));
            return;
        }
        // 损益三码：profitLossPrice 汇总 → 盈亏金额
        if (SupplyType.CABINET_GAIN_LOSS.getCode().equals(typeCode)
            || SupplyType.PHARMACY_GAIN_LOSS.getCode().equals(typeCode)
            || SupplyType.DEPT_GAIN_LOSS.getCode().equals(typeCode)) {
            row.setProfitLossAmount(this.sumProfitLossPrice(lines));
            return;
        }
        // 盘点三码：实盘金额 + 盈亏金额
        if (SupplyType.CABINET_STOCKTAKE.getCode().equals(typeCode)
            || SupplyType.PHARMACY_STOCKTAKE.getCode().equals(typeCode)
            || SupplyType.DEPT_STOCKTAKE.getCode().equals(typeCode)) {
            row.setStocktakeActualAmount(this.sumTotalPrice(lines));
            row.setProfitLossAmount(this.sumProfitLossPrice(lines));
        }
    }

    /**
     * 按调价盈亏汇总写入列表行（进价盈亏、售价盈亏分列；对齐 AS-FIN-PRC-001 单号汇总口径）。
     *
     * @param row    列表行；不可 null
     * @param impact 盈亏汇总；可 null（两列均不填）
     */
    public void fillAmounts(
        FinanceApprovalDocumentRowDto row,
        PriceAdjustmentImpactSummaryQueryModel impact) {
        if (row == null) {
            return;
        }
        if (impact == null) {
            return;
        }
        BigDecimal purchase = impact.getTotalPurchaseImpact();
        // 进价侧有值则按计算刻度写入进价盈亏列（禁止与售价加总）
        if (purchase != null) {
            row.setPriceAdjustmentPurchaseImpact(MonetaryAmountSupport.scaleCalc(purchase));
        }
        BigDecimal selling = impact.getTotalSellingImpact();
        // 售价侧有值则按计算刻度写入售价盈亏列（禁止与进价加总）
        if (selling != null) {
            row.setPriceAdjustmentSellingImpact(MonetaryAmountSupport.scaleCalc(selling));
        }
    }

    /**
     * 汇总行 {@code totalPrice}（进货/退货/实盘金额口径）。
     *
     * @param lines 明细行
     * @return 6 位计算刻度合计；全空则 null
     */
    private BigDecimal sumTotalPrice(List<SupplyRequestModel> lines) {
        boolean any = false;
        BigDecimal sum = MonetaryAmountSupport.scaleCalc(BigDecimal.ZERO);
        for (SupplyRequestModel line : lines) {
            if (line == null || line.getTotalPrice() == null) {
                continue;
            }
            any = true;
            sum = MonetaryAmountSupport.scaleCalc(sum.add(line.getTotalPrice()));
        }
        if (!any) {
            return null;
        }
        return sum;
    }

    /**
     * 汇总行 {@code profitLossPrice}（盘点/损益盈亏口径）。
     *
     * @param lines 明细行
     * @return 6 位计算刻度合计；全空则 null
     */
    private BigDecimal sumProfitLossPrice(List<SupplyRequestModel> lines) {
        boolean any = false;
        BigDecimal sum = MonetaryAmountSupport.scaleCalc(BigDecimal.ZERO);
        for (SupplyRequestModel line : lines) {
            if (line == null || line.getProfitLossPrice() == null) {
                continue;
            }
            any = true;
            sum = MonetaryAmountSupport.scaleCalc(sum.add(line.getProfitLossPrice()));
        }
        if (!any) {
            return null;
        }
        return sum;
    }
}
