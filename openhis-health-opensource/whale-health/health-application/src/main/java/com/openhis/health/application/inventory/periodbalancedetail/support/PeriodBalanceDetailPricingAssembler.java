/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.periodbalancedetail.support;

import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentDetailQueryModel;
import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentLineQueryModel;
import com.openhis.health.domain.document.inventoryitemsnapshot.queries.model.DocInventoryItemSnapshotRowQueryModel;
import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * 库房期初期末明细行额计算（BR-INV-DTL-007 / BR-INV-SET-018/020）。
 */
@Component
public class PeriodBalanceDetailPricingAssembler {

    /**
     * 行总额：满包×包装价 + 拆零余量×拆零单价（BR-INV-DTL-007）。
     *
     * @param quantity      拆零最小单位数量
     * @param ratio         包装换算比
     * @param packageAmount 包装整包价
     * @return 行总额；参数缺失时 null
     */
    public BigDecimal computeLineTotal(BigDecimal quantity, BigDecimal ratio, BigDecimal packageAmount) {
        return MonetaryAmountSupport.computeSplitQuantityLineTotal(quantity, ratio, packageAmount);
    }

    /**
     * 发药行进价额：优先 BR-INV-DTL-007；包装价全缺时按快照冻结 {@code line_cost_total} 同比分摊（BR-INV-SET-020）。
     *
     * @param splitQty          拆零数量
     * @param ratio             包装换算比
     * @param costPackagePrice  包装进价
     * @param snapshotFallback  快照回落行
     * @return 行进价额或 null
     */
    public BigDecimal resolveDispenseLineCostAmount(
        BigDecimal splitQty,
        BigDecimal ratio,
        BigDecimal costPackagePrice,
        DocInventoryItemSnapshotRowQueryModel snapshotFallback
    ) {
        BigDecimal computed = this.computeLineTotal(splitQty, ratio, costPackagePrice);
        if (computed != null) {
            return computed;
        }
        if (snapshotFallback == null || splitQty == null) {
            return null;
        }
        BigDecimal snapshotQty = snapshotFallback.getQuantity();
        BigDecimal snapshotLineCost = snapshotFallback.getLineCostTotal();
        if (snapshotQty == null || snapshotLineCost == null || snapshotQty.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return snapshotLineCost.multiply(splitQty).divide(
            snapshotQty,
            ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE,
            RoundingMode.HALF_UP
        );
    }

    /**
     * 发药行售价额：优先 BR-INV-DTL-007；包装价全缺时按快照冻结 {@code line_price_total} 同比分摊。
     *
     * @param splitQty          拆零数量
     * @param ratio             包装换算比
     * @param salePackagePrice  包装售价
     * @param snapshotFallback  快照回落行
     * @return 行售价额或 null
     */
    public BigDecimal resolveDispenseLineSaleAmount(
        BigDecimal splitQty,
        BigDecimal ratio,
        BigDecimal salePackagePrice,
        DocInventoryItemSnapshotRowQueryModel snapshotFallback
    ) {
        BigDecimal computed = this.computeLineTotal(splitQty, ratio, salePackagePrice);
        if (computed != null) {
            return computed;
        }
        if (snapshotFallback == null || splitQty == null) {
            return null;
        }
        BigDecimal snapshotQty = snapshotFallback.getQuantity();
        BigDecimal snapshotLineSale = snapshotFallback.getLinePriceTotal();
        if (snapshotQty == null || snapshotLineSale == null || snapshotQty.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return snapshotLineSale.multiply(splitQty).divide(
            snapshotQty,
            ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE,
            RoundingMode.HALF_UP
        );
    }

    /**
     * 金额按收费域小数位舍入。
     *
     * @param amount 原金额
     * @return 舍入后金额
     */
    public BigDecimal scaleMoney(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return MonetaryAmountSupport.scaleCalc(amount);
    }

    /**
     * 解析有效包装换算比：行内优先，回落品种定义。
     *
     * @param rowRatio       行内换算比
     * @param fallbackRatio  品种换算比
     * @return 有效换算比（至少 1）
     */
    public BigDecimal resolveEffectivePackageUnitRatio(BigDecimal rowRatio, BigDecimal fallbackRatio) {
        if (rowRatio != null && rowRatio.compareTo(BigDecimal.ZERO) > 0) {
            return rowRatio;
        }
        if (fallbackRatio != null && fallbackRatio.compareTo(BigDecimal.ZERO) > 0) {
            return fallbackRatio;
        }
        return MonetaryAmountSupport.resolvePositivePackageUnitRatio(null);
    }

    /**
     * 快照批号行进价总额：批号包装进价 + 数量 + 换算比套 BR-INV-DTL-007；缺价回落冻结行额。
     *
     * @param row            快照行
     * @param fallbackRatio  品种包装换算比回落
     * @return 行进价总额或 null
     */
    public BigDecimal resolveSnapshotLineCostTotal(
        DocInventoryItemSnapshotRowQueryModel row,
        BigDecimal fallbackRatio
    ) {
        if (row == null || row.getQuantity() == null) {
            return null;
        }
        BigDecimal ratio = this.resolveEffectivePackageUnitRatio(row.getPackageUnitRatio(), fallbackRatio);
        BigDecimal costPkg = row.getLotNumberCostAmount();
        if (costPkg != null) {
            BigDecimal computed = this.computeLineTotal(row.getQuantity(), ratio, costPkg);
            if (computed != null) {
                return computed;
            }
        }
        return row.getLineCostTotal();
    }

    /**
     * 快照批号行售价总额：批号包装售价 + 数量 + 换算比套 BR-INV-DTL-007；缺价回落冻结行额。
     *
     * @param row            快照行
     * @param fallbackRatio  品种包装换算比回落
     * @return 行售价总额或 null
     */
    public BigDecimal resolveSnapshotLineSaleTotal(
        DocInventoryItemSnapshotRowQueryModel row,
        BigDecimal fallbackRatio
    ) {
        if (row == null || row.getQuantity() == null) {
            return null;
        }
        BigDecimal ratio = this.resolveEffectivePackageUnitRatio(row.getPackageUnitRatio(), fallbackRatio);
        BigDecimal salePkg = row.getLotNumberPriceAmount();
        if (salePkg != null) {
            BigDecimal computed = this.computeLineTotal(row.getQuantity(), ratio, salePkg);
            if (computed != null) {
                return computed;
            }
        }
        return row.getLinePriceTotal();
    }

    /**
     * 装配包装/拆零库存展示串（BR-INV-SET-019）。
     *
     * @param splitQty         最小单位数量
     * @param ratio            包装换算比
     * @param packageUnitLabel 包装单位展示
     * @param splitUnitLabel   拆零单位展示
     * @return 长度为 2 的数组：[包装串, 拆零串]
     */
    public String[] buildStockDisplayTexts(
        BigDecimal splitQty,
        BigDecimal ratio,
        String packageUnitLabel,
        String splitUnitLabel
    ) {
        String[] result = new String[2];
        if (splitQty == null) {
            return result;
        }
        BigDecimal effectiveRatio = ratio;
        if (effectiveRatio == null || effectiveRatio.compareTo(BigDecimal.ZERO) <= 0) {
            effectiveRatio = BigDecimal.ONE;
        }
        int packageQty = splitQty.divide(effectiveRatio, 0, RoundingMode.DOWN).intValue();
        StringBuilder packageText = new StringBuilder();
        if (packageQty > 0) {
            packageText.append(packageQty);
            if (StringUtils.isNotEmpty(packageUnitLabel)) {
                packageText.append(packageUnitLabel);
            }
        } else {
            packageText.append(GeneralSharedConstants.ZERO);
            if (StringUtils.isNotEmpty(packageUnitLabel)) {
                packageText.append(packageUnitLabel);
            }
        }
        result[0] = packageText.toString();
        StringBuilder splitText = new StringBuilder();
        splitText.append(splitQty.setScale(0, RoundingMode.DOWN).intValue());
        if (StringUtils.isNotEmpty(splitUnitLabel)) {
            splitText.append(splitUnitLabel);
        }
        result[1] = splitText.toString();
        return result;
    }

    /**
     * 解析批号定价行首个非空金额。
     *
     * @param lines 定价行
     * @return 金额或 null
     */
    public BigDecimal resolveFirstPriceValue(List<ChargeItemDefinition> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        for (ChargeItemDefinition line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getPriceValue() != null) {
                return line.getPriceValue();
            }
        }
        return null;
    }

    /**
     * 按发药时点解析批号进价（调价历史 + 快照回落，BR-INV-SET-020）。
     *
     * @param lotNumber          批号
     * @param dispenseTime       发药时刻
     * @param priceHistory       已生效调价历史
     * @param snapshotFallback   快照回落行
     * @param lotCostByKey       现价进价映射
     * @param compositeLotKey    批号复合键
     * @return 包装单位进价
     */
    public BigDecimal resolveHistoricalCostPrice(
        String lotNumber,
        LocalDateTime dispenseTime,
        List<PriceAdjustmentLineQueryModel> priceHistory,
        DocInventoryItemSnapshotRowQueryModel snapshotFallback,
        java.util.Map<String, BigDecimal> lotCostByKey,
        String compositeLotKey
    ) {
        BigDecimal fromHistory = this.resolveLotPriceFromAdjustmentHistory(
            lotNumber, dispenseTime, priceHistory, true);
        if (fromHistory != null) {
            return fromHistory;
        }
        if (snapshotFallback != null && snapshotFallback.getLotNumberCostAmount() != null) {
            return snapshotFallback.getLotNumberCostAmount();
        }
        if (lotCostByKey != null && compositeLotKey != null) {
            return lotCostByKey.get(compositeLotKey);
        }
        return null;
    }

    /**
     * 按发药时点解析批号售价。
     *
     * @param lotNumber          批号
     * @param dispenseTime       发药时刻
     * @param priceHistory       已生效调价历史
     * @param snapshotFallback   快照回落行
     * @param lotSaleByKey       现价售价映射
     * @param compositeLotKey    批号复合键
     * @return 包装单位售价
     */
    public BigDecimal resolveHistoricalSalePrice(
        String lotNumber,
        LocalDateTime dispenseTime,
        List<PriceAdjustmentLineQueryModel> priceHistory,
        DocInventoryItemSnapshotRowQueryModel snapshotFallback,
        java.util.Map<String, BigDecimal> lotSaleByKey,
        String compositeLotKey
    ) {
        BigDecimal fromHistory = this.resolveLotPriceFromAdjustmentHistory(
            lotNumber, dispenseTime, priceHistory, false);
        if (fromHistory != null) {
            return fromHistory;
        }
        if (snapshotFallback != null && snapshotFallback.getLotNumberPriceAmount() != null) {
            return snapshotFallback.getLotNumberPriceAmount();
        }
        if (lotSaleByKey != null && compositeLotKey != null) {
            return lotSaleByKey.get(compositeLotKey);
        }
        return null;
    }

    /**
     * 自调价历史按批号与发药时点还原包装价。
     *
     * @param lotNumber    批号
     * @param dispenseTime 发药时刻
     * @param priceHistory 调价历史
     * @param costSide     true 进价侧，false 售价侧
     * @return 包装单位价或 null
     */
    private BigDecimal resolveLotPriceFromAdjustmentHistory(
        String lotNumber,
        LocalDateTime dispenseTime,
        List<PriceAdjustmentLineQueryModel> priceHistory,
        boolean costSide
    ) {
        if (priceHistory == null || priceHistory.isEmpty() || dispenseTime == null) {
            return null;
        }
        if (StringUtils.isEmpty(lotNumber)) {
            return null;
        }
        String lot = lotNumber.trim();
        for (PriceAdjustmentLineQueryModel line : priceHistory) {
            if (line == null || line.getApproveTime() == null) {
                continue;
            }
            LocalDateTime approveTime = this.toLocalDateTime(line.getApproveTime());
            if (approveTime == null) {
                continue;
            }
            if (approveTime.isAfter(dispenseTime)) {
                continue;
            }
            List<PriceAdjustmentDetailQueryModel> details = line.getDetails();
            if (details == null) {
                continue;
            }
            for (PriceAdjustmentDetailQueryModel detail : details) {
                if (detail == null || detail.getLotNumber() == null) {
                    continue;
                }
                if (!lot.equals(detail.getLotNumber().trim())) {
                    continue;
                }
                if (costSide) {
                    if (detail.getOldPurchaseAmount() != null) {
                        return detail.getOldPurchaseAmount();
                    }
                } else {
                    if (detail.getOldSellingAmount() != null) {
                        return detail.getOldSellingAmount();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Date 转 LocalDateTime（系统默认时区）。
     *
     * @param date 日期
     * @return LocalDateTime 或 null
     */
    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        Instant instant = date.toInstant();
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
