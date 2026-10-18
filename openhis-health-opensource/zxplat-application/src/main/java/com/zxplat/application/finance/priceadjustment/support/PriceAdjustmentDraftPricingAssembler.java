/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustment.support;

import com.zxplat.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.zxplat.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.zxplat.domain.custom.priceadjustment.command.SavePriceAdjustmentDraftCommand;
import com.zxplat.domain.custom.priceadjustment.support.PriceAdjustmentItemTableSupport;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 调价草稿落库前定价回填（**BR-MON-001** / **BR-PRICE-SRC-001**）。
 *
 * <p>调前价 {@code old*} 必须由读侧定价解析后写入；目标价 {@code new*} 为业务录入保留请求体，仅做 6 位刻度。</p>
 */
@Component
@RequiredArgsConstructor
public class PriceAdjustmentDraftPricingAssembler {

    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /**
     * 保存草稿前：回填调前价并重刻度目标价。
     *
     * @param command 领域保存命令（Mapper 装配后、领域持久化前调用）
     */
    public void applyAuthoritativeDraftPricing(SavePriceAdjustmentDraftCommand command) {
        // 空命令或无明细时不改写，避免误清空领域已装配结构
        if (command == null || command.getLines() == null) {
            return;
        }
        for (SavePriceAdjustmentDraftCommand.Line line : command.getLines()) {
            if (line == null) {
                continue;
            }
            // 目标价仅做 6 位刻度，业务录入值保留（BR-MON-001）
            line.setNewPurchaseAmount(scaleIfPresent(line.getNewPurchaseAmount()));
            line.setNewSellingAmount(scaleIfPresent(line.getNewSellingAmount()));
            String itemTable = StringUtils.trimToEmpty(line.getItemTable());
            // 诊疗行：调前售价走当前 ACTIVE 目录价（BR-PRICE-SRC-001 新开/调价写路径）
            if (PriceAdjustmentItemTableSupport.isClinicalItemTable(itemTable)) {
                this.applyClinicalOldSelling(line, itemTable, line.getItemId());
                continue;
            }
            // 物流行：按批号回填调前进价与调前售价
            if (PriceAdjustmentItemTableSupport.isLogisticsItemTable(itemTable)) {
                this.applyLogisticsDetailOldAmounts(line, itemTable, line.getItemId());
            }
        }
    }

    /**
     * 诊疗调价行：按实例表+主键解析当前 ACTIVE 售价写入 {@code oldSellingAmount}（BR-PRICE-SRC-001）。
     *
     * @param line      草稿明细行
     * @param itemTable 实例表名
     * @param itemId    实例主键
     */
    private void applyClinicalOldSelling(
        SavePriceAdjustmentDraftCommand.Line line,
        String itemTable,
        Long itemId) {
        // 无项目主键时无法解析目录价，保留请求体已有 old* 或 null
        if (itemId == null) {
            return;
        }
        BigDecimal oldSelling = chargeItemDefinitionQueryService.resolveFirstSalePriceValue(itemTable, itemId);
        line.setOldSellingAmount(scaleIfPresent(oldSelling));
    }

    /**
     * 物流调价行：逐批号回填调前进价与调前售价（批号为空跳过）。
     *
     * @param line      草稿明细行
     * @param itemTable 实例表名
     * @param itemId    实例主键
     */
    private void applyLogisticsDetailOldAmounts(
        SavePriceAdjustmentDraftCommand.Line line,
        String itemTable,
        Long itemId) {
        List<SavePriceAdjustmentDraftCommand.DetailLine> details = line.getDetails();
        if (details == null || itemId == null) {
            return;
        }
        for (SavePriceAdjustmentDraftCommand.DetailLine detail : details) {
            // 批号为空无法定位批号价行，跳过该明细
            if (detail == null || trimToNull(detail.getLotNumber()) == null) {
                continue;
            }
            String lotNumber = trimToNull(detail.getLotNumber());
            BigDecimal oldPurchase = chargeItemDefinitionQueryService.resolveFirstLotNumberCostPriceValue(
                itemTable, itemId, lotNumber);
            detail.setOldPurchaseAmount(scaleIfPresent(oldPurchase));
            detail.setOldSellingAmount(scaleIfPresent(
                this.resolveLotSalePrice(itemTable, itemId, lotNumber)));
        }
    }

    /**
     * 按实例+批号取首条非空 {@code price_value} 作为批号调前售价。
     *
     * @param itemTable 实例表名
     * @param itemId    实例主键
     * @param lotNumber 批号（已 trim）
     * @return 调前售价；无批号价行时 null
     */
    private BigDecimal resolveLotSalePrice(String itemTable, Long itemId, String lotNumber) {
        ChargeItemDefinitionQueryCommand cmd = new ChargeItemDefinitionQueryCommand();
        cmd.setInstanceTable(itemTable);
        cmd.setInstanceId(itemId);
        cmd.setLotNo(lotNumber);
        List<com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition> lines =
            chargeItemDefinitionQueryService.findLotNumberPriceByInstanceIdAndLot(cmd);
        // 无批号价行时调前售价留空，前端可手填
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        for (com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition priceLine : lines) {
            // 取首条非空 price_value 作为批号调前售价
            if (priceLine != null && priceLine.getPriceValue() != null) {
                return priceLine.getPriceValue();
            }
        }
        return null;
    }

    /**
     * 金额 6 位刻度归一；null 原样返回（BR-MON-001）。
     *
     * @param value 原始金额
     * @return 刻度后金额；入参 null 时 null
     */
    private static BigDecimal scaleIfPresent(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return MonetaryAmountSupport.scaleCalc(value);
    }
}
