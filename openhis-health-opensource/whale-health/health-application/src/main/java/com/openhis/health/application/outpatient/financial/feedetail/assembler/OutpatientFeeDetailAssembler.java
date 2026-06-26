/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.financial.feedetail.assembler;

import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailAmountSummaryDto;
import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailLineDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailChargeRowQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentContextQueryModel;
import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.OutpatientFeeDetailConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 门诊费用明细金额列与合计装配（AS-OP-FIN-005 §1.3.1；**BR-OP-FDT-010～012**）。
 *
 * <p>只读展示计算；禁止承载筛选或持久化逻辑。细分类取定价行 {@code fin_type_code}，与日结交账口径一致。
 * 医保分摊 Gate 与
 * {@link com.openhis.health.domain.financial.paymentreconciliation.support.PaymentYbSettlementSupport#hasConfirmed}
 * 口径一致：仅当 payment 上下文 {@code ybFundAndAcctTotal} 大于 0 时分摊（**BR-OP-FDT-011**）。</p>
 */
@Component
public class OutpatientFeeDetailAssembler {

    private static final RoundingMode DISPLAY_ROUNDING = RoundingMode.HALF_UP;

    private static final int DISPLAY_SCALE = 2;

    private static final int CALC_SCALE = ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE;

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
        line.setOrderingDoctorId(chargeItem.getEntererId());
        line.setQuantityValue(this.scaleDisplay(chargeItem.getQuantityValue()));
        line.setUnitPrice(this.scaleDisplay(chargeItem.getUnitPrice()));
        String feeCategoryCode = this.resolveFeeCategoryCode(chargeItem.getServiceTable());
        line.setFeeCategoryCode(feeCategoryCode);
        line.setFeeCategoryDisplay(this.resolveFeeCategoryLabel(feeCategoryCode));
        if (product != null) {
            line.setProductName(product.getProductName());
            line.setProductCode(product.getProductBusNo());
            line.setSpecification(this.resolveSpecification(product));
            line.setInsuranceLevelDisplay(product.getChrgitmLvText());
            if (StringUtils.isNotBlank(product.getRateCodeDictText())) {
                line.setInsuranceTypeDisplay(product.getRateCodeDictText());
            } else {
                line.setInsuranceTypeDisplay(product.getRateCode());
            }
        }
        if (StringUtils.isNotBlank(finTypeCode)) {
            line.setSubCategoryCode(finTypeCode.trim());
        }
        if (StringUtils.isNotBlank(finTypeDisplay)) {
            line.setSubCategoryDisplay(finTypeDisplay.trim());
        } else if (StringUtils.isNotBlank(finTypeCode)) {
            // 财务类型字典未命中时回落 code，便于前端仍可筛选
            line.setSubCategoryDisplay(finTypeCode.trim());
        }
        boolean refundLine = this.isRefundLine(context);
        if (refundLine) {
            line.setTradeDirection(OutpatientFeeDetailConstants.TRADE_DIRECTION_REFUND);
        } else {
            line.setTradeDirection(OutpatientFeeDetailConstants.TRADE_DIRECTION_CHARGE);
        }
        BigDecimal baseAmount = this.nullToZero(chargeItem.getBaseAmount());
        BigDecimal receivableAmount = this.resolveReceivableAmount(chargeItem);
        BigDecimal discountedAmount = this.resolveDiscountedAmount(chargeItem);
        BigDecimal lineAmountRaw = this.nullToZero(chargeItem.getTotalPrice());
        BigDecimal discountGap = baseAmount.subtract(receivableAmount);
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (discountGap.compareTo(BigDecimal.ZERO) > 0) {
            discountAmount = discountGap;
        }
        BigDecimal lineAbsAmount = lineAmountRaw.abs();
        BigDecimal insuranceRaw = BigDecimal.ZERO;
        if (context != null) {
            // Gate：ybFundAndAcctTotal 仅含已确认医保凭证（同 PaymentYbSettlementSupport#hasConfirmed）
            insuranceRaw = this.allocateInsuranceAmount(
                lineAbsAmount,
                this.nullToZero(context.getYbFundAndAcctTotal()),
                this.nullToZero(context.getPaymentSnapshotAbsAmountTotal()));
        }
        BigDecimal actualReceivedRaw = lineAmountRaw;
        BigDecimal selfPayRaw = actualReceivedRaw.subtract(insuranceRaw);
        BigDecimal signMultiplier = BigDecimal.ONE;
        if (refundLine) {
            // 退费 payment 链路金额列统一取负展示（BR-OP-FDT-012）
            signMultiplier = new BigDecimal("-1");
        }
        line.setBaseAmount(this.applySignAndDisplay(baseAmount, signMultiplier));
        line.setDiscountAmount(this.applySignAndDisplay(discountAmount, signMultiplier));
        line.setReceivableAmount(this.applySignAndDisplay(receivableAmount, signMultiplier));
        line.setDiscountedAmount(this.applySignAndDisplay(discountedAmount, signMultiplier));
        line.setLineAmount(this.applySignAndDisplay(lineAmountRaw.abs(), signMultiplier));
        line.setActualReceivedAmount(this.applySignAndDisplay(actualReceivedRaw.abs(), signMultiplier));
        line.setInsuranceAmount(this.applySignAndDisplay(insuranceRaw, signMultiplier));
        line.setSelfPayAmount(this.applySignAndDisplay(selfPayRaw.abs(), signMultiplier));
        line.setDiscountRateDisplay(this.computeDiscountRateDisplay(baseAmount, receivableAmount));
        return line;
    }

    /**
     * 对块内或全量明细行金额列代数求和（已含退单负号）。
     *
     * @param lines 明细行列表；空时返回各列 0
     * @return 合计 DTO
     */
    public OutpatientFeeDetailAmountSummaryDto assembleEncounterSubtotal(List<OutpatientFeeDetailLineDto> lines) {
        OutpatientFeeDetailAmountSummaryDto summary = new OutpatientFeeDetailAmountSummaryDto();
        summary.setBaseAmountTotal(BigDecimal.ZERO.setScale(DISPLAY_SCALE, DISPLAY_ROUNDING));
        summary.setDiscountAmountTotal(BigDecimal.ZERO.setScale(DISPLAY_SCALE, DISPLAY_ROUNDING));
        summary.setReceivableAmountTotal(BigDecimal.ZERO.setScale(DISPLAY_SCALE, DISPLAY_ROUNDING));
        summary.setDiscountedAmountTotal(BigDecimal.ZERO.setScale(DISPLAY_SCALE, DISPLAY_ROUNDING));
        summary.setLineAmountTotal(BigDecimal.ZERO.setScale(DISPLAY_SCALE, DISPLAY_ROUNDING));
        summary.setActualReceivedAmountTotal(BigDecimal.ZERO.setScale(DISPLAY_SCALE, DISPLAY_ROUNDING));
        summary.setInsuranceAmountTotal(BigDecimal.ZERO.setScale(DISPLAY_SCALE, DISPLAY_ROUNDING));
        summary.setSelfPayAmountTotal(BigDecimal.ZERO.setScale(DISPLAY_SCALE, DISPLAY_ROUNDING));
        if (lines == null || lines.isEmpty()) {
            return summary;
        }
        for (OutpatientFeeDetailLineDto line : lines) {
            if (line == null) {
                continue;
            }
            summary.setBaseAmountTotal(summary.getBaseAmountTotal().add(this.nullToZero(line.getBaseAmount())));
            summary.setDiscountAmountTotal(summary.getDiscountAmountTotal().add(this.nullToZero(line.getDiscountAmount())));
            summary.setReceivableAmountTotal(summary.getReceivableAmountTotal().add(this.nullToZero(line.getReceivableAmount())));
            summary.setDiscountedAmountTotal(summary.getDiscountedAmountTotal().add(this.nullToZero(line.getDiscountedAmount())));
            summary.setLineAmountTotal(summary.getLineAmountTotal().add(this.nullToZero(line.getLineAmount())));
            summary.setActualReceivedAmountTotal(
                summary.getActualReceivedAmountTotal().add(this.nullToZero(line.getActualReceivedAmount())));
            summary.setInsuranceAmountTotal(summary.getInsuranceAmountTotal().add(this.nullToZero(line.getInsuranceAmount())));
            summary.setSelfPayAmountTotal(summary.getSelfPayAmountTotal().add(this.nullToZero(line.getSelfPayAmount())));
        }
        summary.setBaseAmountTotal(this.scaleDisplay(summary.getBaseAmountTotal()));
        summary.setDiscountAmountTotal(this.scaleDisplay(summary.getDiscountAmountTotal()));
        summary.setReceivableAmountTotal(this.scaleDisplay(summary.getReceivableAmountTotal()));
        summary.setDiscountedAmountTotal(this.scaleDisplay(summary.getDiscountedAmountTotal()));
        summary.setLineAmountTotal(this.scaleDisplay(summary.getLineAmountTotal()));
        summary.setActualReceivedAmountTotal(this.scaleDisplay(summary.getActualReceivedAmountTotal()));
        summary.setInsuranceAmountTotal(this.scaleDisplay(summary.getInsuranceAmountTotal()));
        summary.setSelfPayAmountTotal(this.scaleDisplay(summary.getSelfPayAmountTotal()));
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
        BigDecimal lineAbs = this.nullToZero(lineAbsAmount);
        BigDecimal ybTotal = this.nullToZero(paymentYbTotal);
        BigDecimal denominator = this.nullToZero(paymentSnapshotAbsTotal);
        if (ybTotal.compareTo(BigDecimal.ZERO) <= 0) {
            // 纯自费 payment 不参与医保分摊
            return BigDecimal.ZERO.setScale(CALC_SCALE, DISPLAY_ROUNDING);
        }
        if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
            // 快照分母为 0 时无法占比，医保列置 0
            return BigDecimal.ZERO.setScale(CALC_SCALE, DISPLAY_ROUNDING);
        }
        return lineAbs.multiply(ybTotal).divide(denominator, CALC_SCALE, DISPLAY_ROUNDING);
    }

    /**
     * 折扣率展示（对齐 AS-OP-FIN-002 BR-OPC-011 步进规整，仅展示不影响金额）。
     *
     * @param baseAmount       原价
     * @param receivableAmount 应收
     * @return 如 {@code 85%}；原价为 0 时返回 {@code null}
     */
    public String computeDiscountRateDisplay(BigDecimal baseAmount, BigDecimal receivableAmount) {
        BigDecimal base = this.nullToZero(baseAmount);
        if (base.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal receivable = this.nullToZero(receivableAmount);
        BigDecimal rate = receivable.multiply(new BigDecimal("100"))
            .divide(base, 0, DISPLAY_ROUNDING);
        return rate.toPlainString() + "%";
    }

    /**
     * {@code service_table} → 费用大类 code（**BR-OP-FDT-006**；与 ChargeItem QueryService 口径一致）。
     *
     * @param serviceTable ChargeItem.service_table
     * @return 费用大类 code
     */
    private String resolveFeeCategoryCode(String serviceTable) {
        if (StringUtils.isBlank(serviceTable)) {
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
        if (StringUtils.isNotBlank(product.getTotalVolume())) {
            return product.getTotalVolume();
        }
        if (StringUtils.isNotBlank(product.getDoseFormCodeDictText())) {
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
            return chargeItem.getDiscountAmount();
        }
        return this.nullToZero(chargeItem.getTotalPrice());
    }

    /**
     * 折后金额：system_discount_price → discount_amount → total_price 回退链。
     *
     * @param chargeItem 收费明细事实
     * @return 折后金额（未缩放）
     */
    private BigDecimal resolveDiscountedAmount(ChargeItem chargeItem) {
        if (chargeItem.getSystemDiscountPrice() != null) {
            return chargeItem.getSystemDiscountPrice();
        }
        if (chargeItem.getDiscountAmount() != null) {
            return chargeItem.getDiscountAmount();
        }
        return this.nullToZero(chargeItem.getTotalPrice());
    }

    /**
     * 绝对值金额按退负符号乘后保留 2 位展示。
     *
     * @param absoluteValue   绝对值
     * @param signMultiplier  1 或 -1
     * @return 展示刻度金额
     */
    private BigDecimal applySignAndDisplay(BigDecimal absoluteValue, BigDecimal signMultiplier) {
        BigDecimal scaled = this.scaleCalc(this.nullToZero(absoluteValue));
        return scaled.multiply(signMultiplier).setScale(DISPLAY_SCALE, DISPLAY_ROUNDING);
    }

    /**
     * 计算中间值统一 6 位 HALF_UP（**AS-OP-FIN-005 §1.3**）。
     *
     * @param value 原始金额
     * @return 计算刻度金额
     */
    private BigDecimal scaleCalc(BigDecimal value) {
        return this.nullToZero(value).setScale(CALC_SCALE, DISPLAY_ROUNDING);
    }

    /**
     * 展示列统一 2 位 HALF_UP。
     *
     * @param value 原始金额
     * @return 展示刻度金额
     */
    private BigDecimal scaleDisplay(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(DISPLAY_SCALE, DISPLAY_ROUNDING);
        }
        return value.setScale(DISPLAY_SCALE, DISPLAY_ROUNDING);
    }

    /**
     * {@code null} 安全转零。
     *
     * @param value 金额
     * @return 非 null 金额
     */
    private BigDecimal nullToZero(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value;
    }
}
