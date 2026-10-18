/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.feedetail.assembler;

import com.zxplat.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailAmountSummaryDto;
import com.zxplat.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailLineDto;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.OutpatientFeeDetailChargeRowQueryModel;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentContextQueryModel;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.OutpatientFeeDetailConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 门诊费用明细金额列与合计装配（AS-OP-FIN-005 §1.3.1；**BR-OP-FDT-010～012**；金额刻度 **BR-MON-001**）。
 *
 * <p>只读展示计算；禁止承载筛选或持久化逻辑。细分类取定价行 {@code fin_type_code}，与日结交账口径一致。
 * 行级金额 API 出参保持 6 位（{@link MonetaryAmountSupport#scaleCalc}）；块/就诊合计先 6 位代数加总再
 * {@link MonetaryAmountSupport#scaleSettle} 到 2 位，禁止对先行舍入的行金额再相加。</p>
 * <p>医保分摊 Gate 与
 * {@link com.zxplat.domain.financial.paymentreconciliation.support.PaymentYbSettlementSupport#hasConfirmed}
 * 口径一致：仅当 payment 上下文 {@code ybFundAndAcctTotal} 大于 0 时分摊（**BR-OP-FDT-011**）。</p>
 * <p>优惠金额 = {@code |total_price| − |discount_amount|}（优先 {@code system_discount_price} 差额）；
 * 实收/折后 = 折后应收 {@code discount_amount}（**BR-OP-FDT-010** / **BR-OP-FDT-018**）。禁止把优惠差额当成折后价。</p>
 */
@Component
public class OutpatientFeeDetailAssembler {

    /**
     * 装配单行金额列与基础展示字段（不含患者/就诊头信息）。
     *
     * @param row              域层候选行；调用方保证非空且含 chargeItem
     * @param paymentCtx       关联 payment 上下文；可为 {@code null}
     * @param product          产品定义展示；缺 product 时由调用方 Gate 失败
     * @param finTypeCode      定价行财务类型码（fin_type_code）；可为 {@code null}
     * @param finTypeDisplay   财务类型展示名；字典未命中时由调用方回落 code
     * @return 明细行 DTO
     */
    public OutpatientFeeDetailLineDto assembleLineAmounts(
        OutpatientFeeDetailChargeRowQueryModel row,
        OutpatientFeeDetailPaymentContextQueryModel paymentCtx,
        ProductDefinitionDisplayDto product,
        String finTypeCode,
        String finTypeDisplay) {
        if (row == null || row.getChargeItem() == null) {
            return null;
        }
        ChargeItem chargeItem = row.getChargeItem();
        // 子项账单不进门诊费用明细行
        if (chargeItem.getParentId() != null) {
            return null;
        }
        OutpatientFeeDetailPaymentContextQueryModel context = paymentCtx;
        if (context == null) {
            context = row.getPaymentContext();
        }
        OutpatientFeeDetailLineDto line = new OutpatientFeeDetailLineDto();
        line.setChargeItemId(chargeItem.getId());
        if (context != null) {
            line.setPaymentId(context.getPaymentId());
            if (context.getBillDate() != null) {
                line.setBillDate(context.getBillDate().format(GeneralSharedConstants.DATE_TIME_FORMATTER));
            }
            line.setCashierId(context.getCashierId());
        }
        // 开单医生由应用层回填；收费人仅结算写入（context.cashierId←enterer_id，待结为空）
        line.setQuantityValue(this.scaleQuantityDisplay(chargeItem.getQuantityValue()));
        String feeCategoryCode = this.resolveFeeCategoryCode(chargeItem.getServiceTable());
        line.setFeeCategoryCode(feeCategoryCode);
        line.setFeeCategoryDisplay(this.resolveFeeCategoryLabel(feeCategoryCode));
        if (product != null) {
            line.setProductName(product.getProductName());
            line.setProductCode(product.getProductBusNo());
            line.setSpecification(this.resolveSpecification(product));
            // 医保等级取目录甲乙类文案
            line.setInsuranceLevelDisplay(product.getChrgitmLvText());
        }
        if (StringUtils.isNotEmpty(finTypeCode)) {
            line.setSubCategoryCode(finTypeCode.trim());
        }
        if (StringUtils.isNotEmpty(finTypeDisplay)) {
            line.setSubCategoryDisplay(finTypeDisplay.trim());
        } else if (StringUtils.isNotEmpty(finTypeCode)) {
            // 财务类型字典未命中时回落 code，便于前端仍可筛选
            line.setSubCategoryDisplay(finTypeCode.trim());
        }
        boolean refundLine = this.isRefundLine(context);
        if (refundLine) {
            line.setTradeDirection(OutpatientFeeDetailConstants.TRADE_DIRECTION_REFUND);
        } else {
            line.setTradeDirection(OutpatientFeeDetailConstants.TRADE_DIRECTION_CHARGE);
        }
        BigDecimal baseAmount = MonetaryAmountSupport.nullToZero(chargeItem.getBaseAmount());
        BigDecimal receivableAmount = this.resolveReceivableAmount(chargeItem);
        BigDecimal discountedAmount = this.resolveDiscountedAmount(chargeItem);
        BigDecimal lineAmountRaw = MonetaryAmountSupport.nullToZero(chargeItem.getTotalPrice());
        // Asm：优惠差以售价总价对折后应收，base_amount 空时不再算出 0 优惠
        BigDecimal discountAmount = this.resolveDiscountGapAmount(chargeItem, lineAmountRaw, receivableAmount);
        BigDecimal lineAbsAmount = lineAmountRaw.abs();
        BigDecimal insuranceRaw = BigDecimal.ZERO;
        if (context != null) {
            // Gate：ybFundAndAcctTotal 仅含已确认医保凭证（同 PaymentYbSettlementSupport#hasConfirmed）
            insuranceRaw = this.allocateInsuranceAmount(
                lineAbsAmount,
                MonetaryAmountSupport.nullToZero(context.getYbFundAndAcctTotal()),
                MonetaryAmountSupport.nullToZero(context.getPaymentSnapshotAbsAmountTotal()));
        }
        // Asm：实收跟折后应收，不再把原价 total_price 当实收
        BigDecimal actualReceivedRaw = receivableAmount;
        BigDecimal selfPayRaw = actualReceivedRaw.subtract(insuranceRaw);
        BigDecimal signMultiplier = BigDecimal.ONE;
        if (refundLine) {
            // 退费 payment 链路金额列统一取负展示（BR-OP-FDT-012）
            signMultiplier = new BigDecimal("-1");
        }
        // Asm：行级金额保持 6 位出参，退费行带符号（BR-MON-001）
        line.setBaseAmount(MonetaryAmountSupport.applySignAtCalcScale(baseAmount, signMultiplier));
        line.setDiscountAmount(MonetaryAmountSupport.applySignAtCalcScale(discountAmount, signMultiplier));
        line.setReceivableAmount(MonetaryAmountSupport.applySignAtCalcScale(receivableAmount, signMultiplier));
        line.setDiscountedAmount(MonetaryAmountSupport.applySignAtCalcScale(discountedAmount, signMultiplier));
        BigDecimal signedSaleAmount =
            MonetaryAmountSupport.applySignAtCalcScale(lineAmountRaw.abs(), signMultiplier);
        BigDecimal signedSaleUnit =
            MonetaryAmountSupport.applySignAtCalcScale(
                MonetaryAmountSupport.nullToZero(chargeItem.getUnitPrice()), signMultiplier);
        // 售价：历史落库 ChargeItem 单价/行额（BR-PRICE-SRC-001）；退费行带符号
        line.setSaleUnitPrice(signedSaleUnit);
        line.setSaleAmount(signedSaleAmount);
        line.setActualReceivedAmount(MonetaryAmountSupport.applySignAtCalcScale(actualReceivedRaw.abs(), signMultiplier));
        line.setInsuranceAmount(MonetaryAmountSupport.applySignAtCalcScale(insuranceRaw, signMultiplier));
        line.setSelfPayAmount(MonetaryAmountSupport.applySignAtCalcScale(selfPayRaw.abs(), signMultiplier));
        line.setDiscountRateDisplay(this.computeDiscountRateDisplay(lineAbsAmount, receivableAmount.abs()));
        return line;
    }

    /**
     * 对块内或全量明细行金额列代数求和（已含退单负号）；合计列按 **BR-MON-001** 先 6 位加总再 settle 到 2 位。
     *
     * @param lines 明细行列表；空时返回各列 0
     * @return 合计 DTO（结算刻度 2 位）
     */
    public OutpatientFeeDetailAmountSummaryDto assembleEncounterSubtotal(List<OutpatientFeeDetailLineDto> lines) {
        OutpatientFeeDetailAmountSummaryDto summary = new OutpatientFeeDetailAmountSummaryDto();
        summary.setBaseAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        summary.setDiscountAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        summary.setReceivableAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        summary.setDiscountedAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        summary.setLineAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        summary.setPurchaseAmountTotal(MonetaryAmountSupport.scalePurchaseSettle(BigDecimal.ZERO));
        summary.setSaleAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        summary.setActualReceivedAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        summary.setInsuranceAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        summary.setSelfPayAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        if (lines == null || lines.isEmpty()) {
            return summary;
        }
        BigDecimal baseSum = BigDecimal.ZERO;
        BigDecimal discountSum = BigDecimal.ZERO;
        BigDecimal receivableSum = BigDecimal.ZERO;
        BigDecimal discountedSum = BigDecimal.ZERO;
        BigDecimal lineSum = BigDecimal.ZERO;
        BigDecimal purchaseSum = BigDecimal.ZERO;
        BigDecimal saleSum = BigDecimal.ZERO;
        BigDecimal actualReceivedSum = BigDecimal.ZERO;
        BigDecimal insuranceSum = BigDecimal.ZERO;
        BigDecimal selfPaySum = BigDecimal.ZERO;
        // Pre：按行 6 位金额原值累加，禁止先 round(line,2) 再相加（BR-MON-001）
        for (OutpatientFeeDetailLineDto line : lines) {
            if (line == null) {
                continue;
            }
            baseSum = baseSum.add(MonetaryAmountSupport.nullToZero(line.getBaseAmount()));
            discountSum = discountSum.add(MonetaryAmountSupport.nullToZero(line.getDiscountAmount()));
            receivableSum = receivableSum.add(MonetaryAmountSupport.nullToZero(line.getReceivableAmount()));
            discountedSum = discountedSum.add(MonetaryAmountSupport.nullToZero(line.getDiscountedAmount()));
            // lineAmountTotal 与售价合计同口径（行 DTO 仅保留 saleAmount）
            BigDecimal signedSale = MonetaryAmountSupport.nullToZero(line.getSaleAmount());
            lineSum = lineSum.add(signedSale);
            purchaseSum = purchaseSum.add(MonetaryAmountSupport.nullToZero(line.getPurchaseAmount()));
            saleSum = saleSum.add(signedSale);
            actualReceivedSum = actualReceivedSum.add(MonetaryAmountSupport.nullToZero(line.getActualReceivedAmount()));
            insuranceSum = insuranceSum.add(MonetaryAmountSupport.nullToZero(line.getInsuranceAmount()));
            selfPaySum = selfPaySum.add(MonetaryAmountSupport.nullToZero(line.getSelfPayAmount()));
        }
        // Asm：售价/支付列 2 位；进价合计 3 位
        summary.setBaseAmountTotal(MonetaryAmountSupport.scaleSettle(baseSum));
        summary.setDiscountAmountTotal(MonetaryAmountSupport.scaleSettle(discountSum));
        summary.setReceivableAmountTotal(MonetaryAmountSupport.scaleSettle(receivableSum));
        summary.setDiscountedAmountTotal(MonetaryAmountSupport.scaleSettle(discountedSum));
        summary.setLineAmountTotal(MonetaryAmountSupport.scaleSettle(lineSum));
        summary.setPurchaseAmountTotal(MonetaryAmountSupport.scalePurchaseSettle(purchaseSum));
        summary.setSaleAmountTotal(MonetaryAmountSupport.scaleSettle(saleSum));
        summary.setActualReceivedAmountTotal(MonetaryAmountSupport.scaleSettle(actualReceivedSum));
        summary.setInsuranceAmountTotal(MonetaryAmountSupport.scaleSettle(insuranceSum));
        summary.setSelfPayAmountTotal(MonetaryAmountSupport.scaleSettle(selfPaySum));
        return summary;
    }

    /**
     * 由域层候选行直接装配全量金额 summary（**BR-OP-FDT-010～013**），不装配产品/就诊/人员展示字段。
     *
     * <p>供<strong>汇总 Tab / 导出</strong>等已物化候选行路径；明细分页页脚须改走
     * {@link #assembleDetailPageFooterSaleSummary}，禁止为页脚再全窗物化 RowQueryModel。</p>
     *
     * @param rows                         全量命中候选行（含 payment 上下文与快照分母）
     * @param purchaseAmountByChargeItemId chargeItemId → 历史进价行额（缺键按 0）
     * @return 合计 DTO（结算刻度 2 位）
     */
    public OutpatientFeeDetailAmountSummaryDto assembleGlobalSummaryFromChargeRows(
        List<OutpatientFeeDetailChargeRowQueryModel> rows,
        Map<Long, BigDecimal> purchaseAmountByChargeItemId) {
        OutpatientFeeDetailAmountSummaryDto summary = new OutpatientFeeDetailAmountSummaryDto();
        summary.setBaseAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        summary.setDiscountAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        summary.setReceivableAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        summary.setDiscountedAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        summary.setLineAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        summary.setPurchaseAmountTotal(MonetaryAmountSupport.scalePurchaseSettle(BigDecimal.ZERO));
        summary.setSaleAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        summary.setActualReceivedAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        summary.setInsuranceAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        summary.setSelfPayAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        if (rows == null || rows.isEmpty()) {
            return summary;
        }
        BigDecimal baseSum = BigDecimal.ZERO;
        BigDecimal discountSum = BigDecimal.ZERO;
        BigDecimal receivableSum = BigDecimal.ZERO;
        BigDecimal discountedSum = BigDecimal.ZERO;
        BigDecimal lineSum = BigDecimal.ZERO;
        BigDecimal purchaseSum = BigDecimal.ZERO;
        BigDecimal saleSum = BigDecimal.ZERO;
        BigDecimal actualReceivedSum = BigDecimal.ZERO;
        BigDecimal insuranceSum = BigDecimal.ZERO;
        BigDecimal selfPaySum = BigDecimal.ZERO;
        // Pre：按候选行 6 位原值累加（与 assembleLineAmounts 同口径，BR-MON-001）
        for (OutpatientFeeDetailChargeRowQueryModel row : rows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem chargeItem = row.getChargeItem();
            // 子项账单不进费用明细合计（只认父行）
            if (chargeItem.getParentId() != null) {
                continue;
            }
            OutpatientFeeDetailPaymentContextQueryModel context = row.getPaymentContext();
            boolean refundLine = this.isRefundLine(context);
            BigDecimal signMultiplier = BigDecimal.ONE;
            if (refundLine) {
                // 退费 payment 链路金额列统一取负展示（BR-OP-FDT-012）
                signMultiplier = new BigDecimal("-1");
            }
            BigDecimal baseAmount = MonetaryAmountSupport.nullToZero(chargeItem.getBaseAmount());
            BigDecimal receivableAmount = this.resolveReceivableAmount(chargeItem);
            BigDecimal discountedAmount = this.resolveDiscountedAmount(chargeItem);
            BigDecimal lineAmountRaw = MonetaryAmountSupport.nullToZero(chargeItem.getTotalPrice());
            // Asm：与 assembleLineAmounts 同锚，避免页脚优惠合计被 base_amount 空值抹成 0
            BigDecimal discountAmount = this.resolveDiscountGapAmount(chargeItem, lineAmountRaw, receivableAmount);
            BigDecimal lineAbsAmount = lineAmountRaw.abs();
            BigDecimal insuranceRaw = BigDecimal.ZERO;
            if (context != null) {
                // Gate：ybFundAndAcctTotal 仅含已确认医保凭证（同 PaymentYbSettlementSupport#hasConfirmed）
                insuranceRaw = this.allocateInsuranceAmount(
                    lineAbsAmount,
                    MonetaryAmountSupport.nullToZero(context.getYbFundAndAcctTotal()),
                    MonetaryAmountSupport.nullToZero(context.getPaymentSnapshotAbsAmountTotal()));
            }
            // Asm：实收跟折后应收
            BigDecimal actualReceivedRaw = receivableAmount;
            BigDecimal selfPayRaw = actualReceivedRaw.subtract(insuranceRaw);
            BigDecimal purchaseAmount = BigDecimal.ZERO;
            if (chargeItem.getId() != null && purchaseAmountByChargeItemId != null) {
                BigDecimal resolvedPurchase = purchaseAmountByChargeItemId.get(chargeItem.getId());
                if (resolvedPurchase != null) {
                    purchaseAmount = resolvedPurchase;
                }
            }
            // Asm：带符号累加，禁止对先行 settle 的金额再相加
            baseSum = baseSum.add(MonetaryAmountSupport.applySignAtCalcScale(baseAmount, signMultiplier));
            discountSum = discountSum.add(MonetaryAmountSupport.applySignAtCalcScale(discountAmount, signMultiplier));
            receivableSum = receivableSum.add(
                MonetaryAmountSupport.applySignAtCalcScale(receivableAmount, signMultiplier));
            discountedSum = discountedSum.add(
                MonetaryAmountSupport.applySignAtCalcScale(discountedAmount, signMultiplier));
            BigDecimal signedSaleAmount =
                MonetaryAmountSupport.applySignAtCalcScale(lineAbsAmount, signMultiplier);
            lineSum = lineSum.add(signedSaleAmount);
            saleSum = saleSum.add(signedSaleAmount);
            purchaseSum = purchaseSum.add(
                MonetaryAmountSupport.applySignAtCalcScale(purchaseAmount, signMultiplier));
            actualReceivedSum = actualReceivedSum.add(
                MonetaryAmountSupport.applySignAtCalcScale(actualReceivedRaw.abs(), signMultiplier));
            insuranceSum = insuranceSum.add(
                MonetaryAmountSupport.applySignAtCalcScale(insuranceRaw, signMultiplier));
            selfPaySum = selfPaySum.add(
                MonetaryAmountSupport.applySignAtCalcScale(selfPayRaw.abs(), signMultiplier));
        }
        // Asm：售价/支付列 2 位；进价合计 3 位
        summary.setBaseAmountTotal(MonetaryAmountSupport.scaleSettle(baseSum));
        summary.setDiscountAmountTotal(MonetaryAmountSupport.scaleSettle(discountSum));
        summary.setReceivableAmountTotal(MonetaryAmountSupport.scaleSettle(receivableSum));
        summary.setDiscountedAmountTotal(MonetaryAmountSupport.scaleSettle(discountedSum));
        summary.setLineAmountTotal(MonetaryAmountSupport.scaleSettle(lineSum));
        summary.setPurchaseAmountTotal(MonetaryAmountSupport.scalePurchaseSettle(purchaseSum));
        summary.setSaleAmountTotal(MonetaryAmountSupport.scaleSettle(saleSum));
        summary.setActualReceivedAmountTotal(MonetaryAmountSupport.scaleSettle(actualReceivedSum));
        summary.setInsuranceAmountTotal(MonetaryAmountSupport.scaleSettle(insuranceSum));
        summary.setSelfPayAmountTotal(MonetaryAmountSupport.scaleSettle(selfPaySum));
        return summary;
    }

    /**
     * 明细分页页脚：装配售价合计（2 位）+ 进价合计（3 位）。
     *
     * <p>售价来自 SQL SUM / 投影代数和；进价来自命中 scope 批量历史进价（
     * {@code FeeDetailMatchedScopePurchaseTotalSupport}）。
     * {@code purchaseAmountTotal == null} 表示进价列角延后（由独立 IF 回填），禁止写成 0 误导。</p>
     *
     * @param saleAmountTotal     全窗售价代数和（售价结算刻度；可为 null，按 0）
     * @param purchaseAmountTotal 全窗进价代数和（进价结算刻度；null=延后列角）
     * @return 页脚 summary
     */
    public OutpatientFeeDetailAmountSummaryDto assembleDetailPageFooterSaleSummary(
        BigDecimal saleAmountTotal,
        BigDecimal purchaseAmountTotal) {
        OutpatientFeeDetailAmountSummaryDto summary = new OutpatientFeeDetailAmountSummaryDto();
        summary.setBaseAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        summary.setDiscountAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        summary.setReceivableAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        summary.setDiscountedAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        summary.setActualReceivedAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        summary.setInsuranceAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        summary.setSelfPayAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        // Asm：售价 2 位；进价 null 表示列角另 IF 异步刷，有值则 3 位
        BigDecimal settledSale = MonetaryAmountSupport.scaleSettle(saleAmountTotal);
        summary.setSaleAmountTotal(settledSale);
        summary.setLineAmountTotal(settledSale);
        if (purchaseAmountTotal == null) {
            summary.setPurchaseAmountTotal(null);
        } else {
            summary.setPurchaseAmountTotal(MonetaryAmountSupport.scalePurchaseSettle(purchaseAmountTotal));
        }
        return summary;
    }

    /**
     * 按 payment 快照内 {@code |total_price|} 占比分摊医保基金+个账（**BR-OP-FDT-011**）。
     *
     * @param lineAbsAmount            本行绝对行金额
     * @param paymentYbTotal           payment 已确认医保合计
     * @param paymentSnapshotAbsTotal  快照内绝对行金额之和（分母）
     * @return 本行医保分摊（6 位 HALF_UP）；无医保或分母为 0 时返回 0
     */
    public BigDecimal allocateInsuranceAmount(
        BigDecimal lineAbsAmount,
        BigDecimal paymentYbTotal,
        BigDecimal paymentSnapshotAbsTotal) {
        BigDecimal lineAbs = MonetaryAmountSupport.nullToZero(lineAbsAmount);
        BigDecimal ybTotal = MonetaryAmountSupport.nullToZero(paymentYbTotal);
        BigDecimal denominator = MonetaryAmountSupport.nullToZero(paymentSnapshotAbsTotal);
        if (ybTotal.compareTo(BigDecimal.ZERO) <= 0) {
            // 纯自费 payment 不参与医保分摊
            return MonetaryAmountSupport.scaleCalc(BigDecimal.ZERO);
        }
        if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
            // 快照分母为 0 时无法占比，医保列置 0
            return MonetaryAmountSupport.scaleCalc(BigDecimal.ZERO);
        }
        return MonetaryAmountSupport.proportionAtCalcScale(lineAbs, ybTotal, denominator);
    }

    /**
     * 折扣率展示（对齐 AS-OP-FIN-002 BR-OPC-011 步进规整，仅展示不影响金额）。
     *
     * @param baseAmount       原价
     * @param receivableAmount 应收
     * @return 如 {@code 85%}；原价为 0 时返回 {@code null}
     */
    public String computeDiscountRateDisplay(BigDecimal baseAmount, BigDecimal receivableAmount) {
        BigDecimal base = MonetaryAmountSupport.nullToZero(baseAmount);
        if (base.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal receivable = MonetaryAmountSupport.nullToZero(receivableAmount);
        BigDecimal rate = receivable.multiply(new BigDecimal("100"))
            .divide(base, 0, MonetaryAmountSupport.MONETARY_ROUNDING);
        return rate.toPlainString() + "%";
    }

    /**
     * {@code service_table} → 费用大类 code（**BR-OP-FDT-006**；与 ChargeItem QueryService 口径一致）。
     *
     * @param serviceTable ChargeItem.service_table
     * @return 费用大类 code
     */
    private String resolveFeeCategoryCode(String serviceTable) {
        if (StringUtils.isEmpty(serviceTable)) {
            throw new IllegalArgumentException("charge item service_table is blank");
        }
        String normalized = serviceTable.trim();
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(normalized)
            || TableNameConstants.MED_MEDICATION_DISPENSE.equals(normalized)) {
            return OutpatientFeeDetailConstants.FEE_CATEGORY_MEDICATION;
        }
        if (TableNameConstants.WKF_SERVICE_REQUEST.equals(normalized)) {
            return OutpatientFeeDetailConstants.FEE_CATEGORY_ACTIVITY;
        }
        if (TableNameConstants.WKF_DEVICE_REQUEST.equals(normalized)
            || TableNameConstants.WKF_DEVICE_DISPENSE.equals(normalized)) {
            return OutpatientFeeDetailConstants.FEE_CATEGORY_DEVICE;
        }
        if (TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(normalized)) {
            return OutpatientFeeDetailConstants.FEE_CATEGORY_REGISTER;
        }
        // 未识别锚点整单失败（EX-OP-FDT-103）
        throw new IllegalArgumentException("unsupported charge item service_table for outpatient fee detail: " + normalized);
    }

    /**
     * 费用大类 code → 展示文案。
     *
     * @param feeCategoryCode 大类 code
     * @return 中文展示；未知 code 原样返回
     */
    private String resolveFeeCategoryLabel(String feeCategoryCode) {
        if (OutpatientFeeDetailConstants.FEE_CATEGORY_MEDICATION.equals(feeCategoryCode)) {
            return OutpatientFeeDetailConstants.FEE_CATEGORY_MEDICATION_LABEL;
        }
        if (OutpatientFeeDetailConstants.FEE_CATEGORY_ACTIVITY.equals(feeCategoryCode)) {
            return OutpatientFeeDetailConstants.FEE_CATEGORY_ACTIVITY_LABEL;
        }
        if (OutpatientFeeDetailConstants.FEE_CATEGORY_DEVICE.equals(feeCategoryCode)) {
            return OutpatientFeeDetailConstants.FEE_CATEGORY_DEVICE_LABEL;
        }
        if (OutpatientFeeDetailConstants.FEE_CATEGORY_REGISTER.equals(feeCategoryCode)) {
            return OutpatientFeeDetailConstants.FEE_CATEGORY_REGISTER_LABEL;
        }
        return feeCategoryCode;
    }

    /**
     * 产品规格展示：优先 totalVolume，其次剂型译文。
     *
     * @param product 产品定义展示 DTO
     * @return 规格文案或 {@code null}
     */
    private String resolveSpecification(ProductDefinitionDisplayDto product) {
        if (product == null) {
            return null;
        }
        if (StringUtils.isNotEmpty(product.getTotalVolume())) {
            return product.getTotalVolume();
        }
        if (StringUtils.isNotEmpty(product.getDoseFormCodeDictText())) {
            return product.getDoseFormCodeDictText();
        }
        return null;
    }

    /**
     * 是否退费 payment 链路行（**BR-OP-FDT-012**）。
     *
     * @param context payment 读上下文
     * @return {@code true} 表示退费负号展示
     */
    private boolean isRefundLine(OutpatientFeeDetailPaymentContextQueryModel context) {
        if (context == null || context.getRefundPayment() == null) {
            return false;
        }
        return context.getRefundPayment();
    }

    /**
     * 应收金额：优先 discount_amount，缺失回退 total_price（**BR-CHG-QRY-003**）。
     *
     * @param chargeItem 收费明细事实
     * @return 应收金额（未缩放）
     */
    private BigDecimal resolveReceivableAmount(ChargeItem chargeItem) {
        if (chargeItem.getDiscountAmount() != null) {
            // 收银折扣后折扣后应收落在 discount_amount，优先于原价 total_price
            return chargeItem.getDiscountAmount();
        }
        return MonetaryAmountSupport.nullToZero(chargeItem.getTotalPrice());
    }

    /**
     * 折后金额：与应收同锚 {@code discount_amount}，缺失回退 {@code total_price}。
     * 禁止把优惠差额 {@code system_discount_price} 当成折后价（**BR-OP-FDT-010**）。
     *
     * @param chargeItem 收费明细事实
     * @return 折后金额（未缩放）
     */
    private BigDecimal resolveDiscountedAmount(ChargeItem chargeItem) {
        if (chargeItem.getDiscountAmount() != null) {
            // 折后应收已落库时与收费写路径同锚
            return chargeItem.getDiscountAmount();
        }
        return MonetaryAmountSupport.nullToZero(chargeItem.getTotalPrice());
    }

    /**
     * 优惠差额：优先写路径落库的 {@code system_discount_price}；否则 {@code |售价|−|折后应收|}。
     * {@code base_amount} 在收银折扣场景常为空，禁止再当被减数（**BR-OP-FDT-018**）。
     *
     * @param chargeItem       收费明细事实
     * @param originalAmount   售价总价 {@code total_price}
     * @param receivableAmount 折后应收 {@code discount_amount}
     * @return 非负优惠差额（退费符号由调用方 {@code signMultiplier} 处理）
     */
    private BigDecimal resolveDiscountGapAmount(
        ChargeItem chargeItem,
        BigDecimal originalAmount,
        BigDecimal receivableAmount) {
        BigDecimal storedGap = chargeItem.getSystemDiscountPrice();
        if (storedGap != null && storedGap.compareTo(BigDecimal.ZERO) > 0) {
            // 写路径已落优惠差额时直接采用，避免再与空 base_amount 相减
            return storedGap;
        }
        BigDecimal originalAbs = MonetaryAmountSupport.nullToZero(originalAmount).abs();
        BigDecimal receivableAbs = MonetaryAmountSupport.nullToZero(receivableAmount).abs();
        BigDecimal gap = originalAbs.subtract(receivableAbs);
        if (gap.compareTo(BigDecimal.ZERO) > 0) {
            // 售价高于折后应收才记优惠，避免未打折行出现负优惠
            return gap;
        }
        return BigDecimal.ZERO;
    }

    /**
     * 数量列展示（非金额；保留 2 位便于阅读）。
     *
     * @param value 数量
     * @return 展示数量
     */
    private BigDecimal scaleQuantityDisplay(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, MonetaryAmountSupport.MONETARY_ROUNDING);
        }
        return value.setScale(2, MonetaryAmountSupport.MONETARY_ROUNDING);
    }
}
