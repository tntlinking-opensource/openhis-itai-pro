/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.documentdetail.support;

import com.openhis.health.application.inventory.documentdetail.dto.DocumentDetailRowDto;
import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 单据详情操作单位数量换算（BR-INV-DDC-010/011）；失败抛 {@code INV_DDC_204}，禁止降级。
 */
@Component
@Slf4j
public class DocumentDetailQuantityConversionHelper {

    /**
     * 对列表行执行展示数量换算（含盘点/损益扩展列）。
     *
     * @param rows        已 enrich 的行列表
     * @param productByKey 产品定义 Map（compositeKey → display）
     * @throws BusinessException {@code INV_DDC_204} 换算失败
     */
    public void applyDisplayQuantities(
        List<DocumentDetailRowDto> rows,
        Map<String, ProductDefinitionDisplayDto> productByKey
    ) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (DocumentDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            this.applyDisplayQuantitiesForRow(row, productByKey);
        }
    }

    /**
     * 单行换算：主数量 {@code itemQuantity} 及盘点/损益拆零源字段。
     *
     * @param row          目标行
     * @param productByKey 产品 Map
     */
    private void applyDisplayQuantitiesForRow(
        DocumentDetailRowDto row,
        Map<String, ProductDefinitionDisplayDto> productByKey
    ) {
        String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(row.getItemTable(), row.getItemId());
        ProductDefinitionDisplayDto product = null;
        if (productByKey != null && compositeKey != null) {
            product = productByKey.get(compositeKey);
        }
        List<ProductDefinitionCatalogUnitBandDto> bands =
            ProductDefinitionCatalogUnitBandsAssembler.buildInboundSelectionBands(product);
        String unitCode = row.getUnitCode();
        row.setItemQuantity(this.requireDisplayQuantity(
            row.getBusNo(), row.getLineId(), row.getItemId(), row.getItemTable(),
            row.getProductName(), row.getProductBusNo(), unitCode, row.getSplitItemQuantity(), bands));
        String typeEnum = row.getTypeEnum();
        if (SupplyType.isStocktakeTypeCode(typeEnum) || SupplyType.isGainLossTypeCode(typeEnum)) {
            row.setActualQuantity(this.requireDisplayQuantity(
                row.getBusNo(), row.getLineId(), row.getItemId(), row.getItemTable(),
                row.getProductName(), row.getProductBusNo(), unitCode, row.getSplitTotalQuantity(), bands));
            row.setGainLossQuantity(this.requireDisplayQuantity(
                row.getBusNo(), row.getLineId(), row.getItemId(), row.getItemTable(),
                row.getProductName(), row.getProductBusNo(), unitCode, row.getSplitItemQuantity(), bands));
            if (SupplyType.isStocktakeTypeCode(typeEnum)) {
                row.setBookQuantity(this.requireDisplayQuantity(
                    row.getBusNo(), row.getLineId(), row.getItemId(), row.getItemTable(),
                    row.getProductName(), row.getProductBusNo(), unitCode, row.getSplitBookQuantity(), bands));
            }
        }
    }

    /**
     * 拆零量按行 {@code unit_code} 换算为操作单位数量；ratio 缺失或 ≤0 时失败。
     *
     * @param busNo         单据号
     * @param lineId        明细行主键
     * @param itemId        物品主键
     * @param itemTable     物品表
     * @param productName   品名
     * @param productBusNo  院内编码
     * @param unitCode      操作单位码
     * @param splitQuantity 拆零持久化量
     * @param bands         单位槽
     * @return 操作单位数量；拆零源为 null 时返回 null
     * @throws BusinessException {@code INV_DDC_204}
     */
    public BigDecimal requireDisplayQuantity(
        String busNo,
        Long lineId,
        Long itemId,
        String itemTable,
        String productName,
        String productBusNo,
        String unitCode,
        BigDecimal splitQuantity,
        List<ProductDefinitionCatalogUnitBandDto> bands
    ) {
        if (splitQuantity == null) {
            return null;
        }
        if (StringUtils.isEmpty(unitCode)) {
            return splitQuantity;
        }
        BigDecimal ratio = ProductDefinitionCatalogUnitBandsAssembler.resolveRatioToSplitByUnitCode(bands, unitCode);
        if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn(
                "单据详情换算失败 lineId={} itemTable={} itemId={} splitQty={} unitCode={} ratio={}",
                lineId, itemTable, itemId, splitQuantity, unitCode, ratio);
            String name = productName;
            if (name == null) {
                name = "";
            }
            String code = productBusNo;
            if (code == null) {
                code = "";
            }
            String message = String.format(
                "单据 %s 中物品「%s」（编码 %s）无法按操作单位「%s」换算数量，请检查产品单位维护",
                busNo, name, code, unitCode);
            throw new BusinessException(ErrorCodeConstants.INV_DDC_204, message);
        }
        if (ratio.compareTo(BigDecimal.ONE) == 0) {
            return splitQuantity;
        }
        return splitQuantity.divide(ratio, ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE, RoundingMode.HALF_UP);
    }
}
