/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.assembler;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.plugin.chs.domain.share.constants.ChsInventoryConstants;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.medication.medicationdispense.entity.MedicationDispense;
import com.zxplat.domain.share.constants.ChargeItemAmountScaleConstants;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 进销存上报数量/单价换算（无 IO、无 Spring）。
 *
 * <p><b>发药</b>：数量与单位一对，不做单位换算。<br>
 * <b>库房</b>：行上数量按拆零量存、单位为操作单位 → 上报前换成操作单位数量；
 * 单价与数量同单位，换算以<strong>包装单位为锚</strong>
 * （{@code P_to = P_from × R_to / R_from}），禁止先落到拆零单价再乘回。</p>
 *
 * <p>数量刻度见 {@link ChsInventoryConstants#UPLOAD_QUANTITY_SCALE}；
 * 单价刻度见 {@link ChargeItemAmountScaleConstants#CHARGE_MONETARY_SCALE} /
 * {@link MonetaryAmountSupport}。</p>
 *
 * <p>3502 {@code cnt} 符号不在本类赋：ContextLoader 先换操作单位量，再由
 * {@link com.zxplat.plugin.chs.domain.share.enums.InvChgType#toSignedCnt} 按 {@code inv_chg_type} 赋正负。</p>
 */
public final class InventoryUploadQuantityAssembler {

    private InventoryUploadQuantityAssembler() {
    }

    /**
     * 发药/退药销售数量：取发放行业务数量绝对值，不换算单位。
     *
     * @param dispense 发放行
     * @return {@code sel_retn_cnt}；无数量时 null
     */
    public static BigDecimal resolveDispenseSelRetnCnt(MedicationDispense dispense) {
        if (dispense == null) {
            return null;
        }
        // 优先发药业务数量
        if (dispense.getDispenseQuantity() != null) {
            return scaleUploadQuantity(dispense.getDispenseQuantity().abs());
        }
        // 回落库存数量字段
        if (dispense.getQuantity() != null) {
            return scaleUploadQuantity(dispense.getQuantity().abs());
        }
        return null;
    }

    /**
     * 库房拆零数量 → 操作单位数量：{@code Q_op = Q_split / R_op}。
     *
     * @param medication        药品定义（取各槽相对拆零倍数）
     * @param operationUnitCode 操作单位（单据 unitCode）
     * @param splitQuantity     拆零数量
     * @return 操作单位数量；无法换算时回落原拆零量
     */
    public static BigDecimal splitQuantityToOperationUnit(
        MedicationDefinition medication,
        String operationUnitCode,
        BigDecimal splitQuantity
    ) {
        if (splitQuantity == null) {
            return null;
        }
        BigDecimal absSplit = splitQuantity.abs();
        BigDecimal opRatio = resolveRatioToSplit(medication, operationUnitCode);
        // 无倍数定义：无法换算，原样上报拆零量
        if (opRatio == null) {
            return scaleUploadQuantity(absSplit);
        }
        // 操作单位即拆零单位：原样
        if (opRatio.compareTo(BigDecimal.ONE) == 0
            && isSplitUnit(medication, operationUnitCode)) {
            return scaleUploadQuantity(absSplit);
        }
        // 倍数非法：避免除零，回落拆零量
        if (opRatio.compareTo(BigDecimal.ZERO) <= 0) {
            return scaleUploadQuantity(absSplit);
        }
        return absSplit.divide(
            opRatio,
            ChsInventoryConstants.UPLOAD_QUANTITY_SCALE,
            MonetaryAmountSupport.MONETARY_ROUNDING);
    }

    /**
     * 单价按包装锚线性换算到目标单位：{@code P_to = P_from × R_to / R_from}（单步，不经拆零单价）。
     *
     * @param medication    药品定义
     * @param fromUnitCode  源单价所属单位；空则视为包装单位
     * @param fromUnitPrice 源单价
     * @param toUnitCode    目标单位（操作单位）
     * @return 目标单位单价；无法换算时回落源单价刻度
     */
    public static BigDecimal convertUnitPricePackageAnchored(
        MedicationDefinition medication,
        String fromUnitCode,
        BigDecimal fromUnitPrice,
        String toUnitCode
    ) {
        if (fromUnitPrice == null) {
            return null;
        }
        BigDecimal scaled = MonetaryAmountSupport.scaleCalc(fromUnitPrice);
        // 缺药品或目标单位：无法换算
        if (medication == null || isEmpty(toUnitCode)) {
            return scaled;
        }
        String effectiveFrom = fromUnitCode;
        // 源单位未传：默认按包装单位价理解
        if (isEmpty(effectiveFrom)) {
            effectiveFrom = medication.getPackageUnitCode();
        }
        if (isEmpty(effectiveFrom)) {
            return scaled;
        }
        // 源目标单位相同：仅刻度
        if (Objects.equals(effectiveFrom.trim(), toUnitCode.trim())) {
            return scaled;
        }
        BigDecimal fromRatio = resolveRatioToSplit(medication, effectiveFrom);
        BigDecimal toRatio = resolveRatioToSplit(medication, toUnitCode);
        // 任一单位无倍数：无法换算
        if (fromRatio == null || toRatio == null) {
            return scaled;
        }
        if (fromRatio.compareTo(BigDecimal.ZERO) <= 0 || toRatio.compareTo(BigDecimal.ZERO) <= 0) {
            return scaled;
        }
        // 包装锚单步：P_to = P_from × R_to / R_from（禁止 P_split=P_from/R_from 再乘）
        BigDecimal converted = scaled.multiply(toRatio)
            .divide(
                fromRatio,
                ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE,
                MonetaryAmountSupport.MONETARY_ROUNDING);
        return MonetaryAmountSupport.scaleCalc(converted);
    }

    /**
     * 库房行：拆零数量 → 操作单位数量；单价按「与 unitCode 同单位」保留刻度（已是操作单位价则不换）。
     *
     * <p>若源单价实为包装价，传入 {@code priceFromUnitCode=packageUnitCode}，
     * 将按 {@code P_op = P_pkg × R_op / R_pkg} 换到操作单位。</p>
     *
     * @param medication        药品
     * @param operationUnitCode 操作单位
     * @param splitQuantity     拆零数量
     * @param priceOnFromUnit   源单价
     * @param priceFromUnitCode 源单价单位；空则视为已是操作单位价
     * @return 操作数量与操作单价
     */
    public static InventoryUploadQtyPriceResult resolveWarehouseQtyAndPrice(
        MedicationDefinition medication,
        String operationUnitCode,
        BigDecimal splitQuantity,
        BigDecimal priceOnFromUnit,
        String priceFromUnitCode
    ) {
        BigDecimal opQty = splitQuantityToOperationUnit(medication, operationUnitCode, splitQuantity);
        BigDecimal opPrice;
        boolean priceAlreadyOnOperationUnit = isEmpty(priceFromUnitCode);
        if (!priceAlreadyOnOperationUnit
            && isNotEmpty(operationUnitCode)
            && Objects.equals(priceFromUnitCode.trim(), operationUnitCode.trim())) {
            priceAlreadyOnOperationUnit = true;
        }
        // 单价已是操作单位价：只做金钱刻度
        if (priceAlreadyOnOperationUnit) {
            opPrice = MonetaryAmountSupport.scaleCalc(priceOnFromUnit);
        } else {
            // 源单价单位与操作单位不同：包装锚换到操作单位
            opPrice = convertUnitPricePackageAnchored(
                medication, priceFromUnitCode, priceOnFromUnit, operationUnitCode);
        }
        return new InventoryUploadQtyPriceResult(opQty, opPrice);
    }

    /**
     * 上报数量统一到平台 4 位刻度。
     *
     * @param quantity 原始数量
     * @return 刻度后数量
     */
    private static BigDecimal scaleUploadQuantity(BigDecimal quantity) {
        return quantity.setScale(
            ChsInventoryConstants.UPLOAD_QUANTITY_SCALE,
            MonetaryAmountSupport.MONETARY_ROUNDING);
    }

    /**
     * 判断单位是否为药品拆零单位。
     *
     * @param medication 药品
     * @param unitCode   单位编码
     * @return 是拆零单位时 true
     */
    private static boolean isSplitUnit(MedicationDefinition medication, String unitCode) {
        if (medication == null || isEmpty(unitCode) || isEmpty(medication.getSplitUnitCode())) {
            return false;
        }
        return unitCode.trim().equals(medication.getSplitUnitCode().trim());
    }

    /**
     * 解析单位相对拆零的倍数；拆零单位为 1。
     *
     * @param medication 药品
     * @param unitCode   单位
     * @return 倍数；未知单位 null
     */
    public static BigDecimal ratioToSplit(MedicationDefinition medication, String unitCode) {
        return resolveRatioToSplit(medication, unitCode);
    }

    /**
     * 操作单位数量 → 拆零数量：{@code Q_split = Q_op × R_op}。
     *
     * @param medication 药品定义
     * @param unitCode   操作单位
     * @param qty        操作数量
     * @return 拆零数量绝对值；无法换算时 null
     */
    public static BigDecimal operationQuantityToSplit(
        MedicationDefinition medication,
        String unitCode,
        BigDecimal qty
    ) {
        BigDecimal ratio = ratioToSplit(medication, unitCode);
        // 缺倍数或数量：无法换算
        if (ratio == null || qty == null) {
            return null;
        }
        return qty.abs().multiply(ratio);
    }

    /**
     * 解析单位相对拆零的倍数；拆零单位为 1。
     *
     * @param medication 药品
     * @param unitCode   单位
     * @return 倍数；未知单位 null
     */
    private static BigDecimal resolveRatioToSplit(MedicationDefinition medication, String unitCode) {
        if (medication == null || isEmpty(unitCode)) {
            return null;
        }
        String unit = unitCode.trim();
        // 拆零槽：倍数 1
        if (isNotEmpty(medication.getSplitUnitCode())
            && unit.equals(medication.getSplitUnitCode().trim())) {
            return BigDecimal.ONE;
        }
        // 剂量槽
        if (isNotEmpty(medication.getDoseUnitCode())
            && unit.equals(medication.getDoseUnitCode().trim())) {
            return medication.getDoseUnitRatio();
        }
        // 采购槽
        if (isNotEmpty(medication.getPurchaseUnitCode())
            && unit.equals(medication.getPurchaseUnitCode().trim())) {
            return medication.getPurchaseUnitRatio();
        }
        // 库存槽
        if (isNotEmpty(medication.getStockUnitCode())
            && unit.equals(medication.getStockUnitCode().trim())) {
            return medication.getStockUnitRatio();
        }
        // 包装槽
        if (isNotEmpty(medication.getPackageUnitCode())
            && unit.equals(medication.getPackageUnitCode().trim())) {
            return medication.getPackageUnitRatio();
        }
        return null;
    }
}
