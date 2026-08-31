/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.documentdetail.support;

import com.openhis.health.application.inventory.documentdetail.dto.DocumentDetailRowDto;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.ProductDefinitionCatalogUnitBandConstants;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 单据详情操作单位数量换算（BR-INV-DDC-010/011）；并装配包装/最小单位双数量展示。
 *
 * <p>换算失败抛 {@code INV_DDC_204}，禁止降级。</p>
 */
@Component
@Slf4j
public class DocumentDetailQuantityConversionHelper {

    /**
     * 对列表行执行展示数量换算（含盘点/损益扩展列与包装/最小双数量）。
     *
     * @param rows         已 enrich 的行列表
     * @param productByKey 产品定义 Map（compositeKey → display）
     * @throws BusinessException {@code INV_DDC_204} 换算失败
     */
    public void applyDisplayQuantities(
        List<DocumentDetailRowDto> rows,
        Map<String, ProductDefinitionDisplayDto> productByKey
    ) {
        // 空列表无需换算
        if (rows == null || rows.isEmpty()) {
            return;
        }
        // 逐行换算：失败即整批抛 INV_DDC_204，禁止部分降级
        for (DocumentDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            this.applyDisplayQuantitiesForRow(row, productByKey);
        }
    }

    /**
     * 单行换算：操作单位 {@code itemQuantity}、包装/最小双数量，及盘点/损益拆零源字段。
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
        // Pre：按物品复合键取目录，供单位槽与包装/拆零展示名
        if (productByKey != null && compositeKey != null) {
            product = productByKey.get(compositeKey);
        }
        List<ProductDefinitionCatalogUnitBandDto> bands =
            ProductDefinitionCatalogUnitBandsAssembler.buildInboundSelectionBands(product);
        String unitCode = row.getUnitCode();
        // Asm：主数量 → 操作单位（BR-INV-DDC-010）
        row.setItemQuantity(this.requireDisplayQuantity(
            row.getBusNo(), row.getLineId(), row.getItemId(), row.getItemTable(),
            row.getProductName(), row.getProductBusNo(), unitCode, row.getSplitItemQuantity(), bands));
        // Asm：包装单位数量 + 最小单位数量（与操作单位并存，单价仍按操作单位）
        this.applyPackageAndMinUnitQuantities(row, product, bands);
        String typeEnum = row.getTypeEnum();
        // 盘点/损益扩展列同样按操作单位换算（BR-INV-DDC-011）
        if (SupplyType.isStocktakeTypeCode(typeEnum) || SupplyType.isGainLossTypeCode(typeEnum)) {
            row.setActualQuantity(this.requireDisplayQuantity(
                row.getBusNo(), row.getLineId(), row.getItemId(), row.getItemTable(),
                row.getProductName(), row.getProductBusNo(), unitCode, row.getSplitTotalQuantity(), bands));
            row.setGainLossQuantity(this.requireDisplayQuantity(
                row.getBusNo(), row.getLineId(), row.getItemId(), row.getItemTable(),
                row.getProductName(), row.getProductBusNo(), unitCode, row.getSplitItemQuantity(), bands));
            // 盘点才有盘前账面列
            if (SupplyType.isStocktakeTypeCode(typeEnum)) {
                row.setBookQuantity(this.requireDisplayQuantity(
                    row.getBusNo(), row.getLineId(), row.getItemId(), row.getItemTable(),
                    row.getProductName(), row.getProductBusNo(), unitCode, row.getSplitBookQuantity(), bands));
            }
        }
    }

    /**
     * 按拆零持久量装配包装单位数量与最小单位数量。
     *
     * <p>最小量 = 拆零源；包装量 = 拆零源 ÷ 包装倍数；无有效倍数时包装量回落为最小量。</p>
     *
     * @param row     目标行
     * @param product 产品读模型，可 null
     * @param bands   进货四槽
     */
    private void applyPackageAndMinUnitQuantities(
        DocumentDetailRowDto row,
        ProductDefinitionDisplayDto product,
        List<ProductDefinitionCatalogUnitBandDto> bands
    ) {
        BigDecimal splitQty = row.getSplitItemQuantity();
        // 无拆零源则不填双数量列
        if (splitQty == null) {
            return;
        }
        // 最小单位：库内拆零持久量原样
        row.setMinUnitQuantity(splitQty);
        row.setMinUnitName(this.resolveUnitNameByBandKind(
            bands, product, ProductDefinitionCatalogUnitBandConstants.BAND_SPLIT));
        row.setPackageUnitName(this.resolveUnitNameByBandKind(
            bands, product, ProductDefinitionCatalogUnitBandConstants.BAND_PACKAGE));
        BigDecimal packageRatio = this.resolvePackageRatioToSplit(product, bands);
        // 无有效包装倍数：包装列与最小列同量，避免空白
        if (packageRatio == null || packageRatio.compareTo(BigDecimal.ZERO) <= 0) {
            row.setPackageQuantity(splitQty);
            return;
        }
        // 倍数为 1：包装=拆零，无需除法
        if (packageRatio.compareTo(BigDecimal.ONE) == 0) {
            row.setPackageQuantity(splitQty);
            return;
        }
        // 包装量 = 拆零 ÷ 包装倍数（与操作单位换算同刻度）
        row.setPackageQuantity(splitQty.divide(
            packageRatio,
            ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE,
            RoundingMode.HALF_UP));
    }

    /**
     * 解析包装相对拆零倍数：优先 PACKAGE 槽，再读模型 {@code packageUnitRatio}。
     *
     * @param product 产品读模型，可 null
     * @param bands   单位槽，可 null
     * @return 正倍数；无法解析时 null
     */
    private BigDecimal resolvePackageRatioToSplit(
        ProductDefinitionDisplayDto product,
        List<ProductDefinitionCatalogUnitBandDto> bands
    ) {
        BigDecimal fromBand = this.findBandRatioToSplit(
            bands, ProductDefinitionCatalogUnitBandConstants.BAND_PACKAGE);
        // 优先进货四槽 PACKAGE
        if (fromBand != null) {
            return fromBand;
        }
        if (product == null) {
            return null;
        }
        BigDecimal fromProduct = product.getPackageUnitRatio();
        // 回落读模型 package_unit_ratio
        if (fromProduct != null && fromProduct.compareTo(BigDecimal.ZERO) > 0) {
            return fromProduct;
        }
        return null;
    }

    /**
     * 按槽语义取相对拆零倍数。
     *
     * @param bands    单位槽
     * @param bandKind 槽语义
     * @return 正倍数；未命中 null
     */
    private BigDecimal findBandRatioToSplit(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        String bandKind
    ) {
        if (bands == null || bands.isEmpty() || StringUtils.isEmpty(bandKind)) {
            return null;
        }
        // 遍历单位槽：命中语义且倍数 > 0 即返回
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null) {
                continue;
            }
            if (!bandKind.equals(band.getBandKind())) {
                continue;
            }
            BigDecimal ratio = band.getRatioToSplit();
            if (ratio != null && ratio.compareTo(BigDecimal.ZERO) > 0) {
                return ratio;
            }
        }
        return null;
    }

    /**
     * 解析包装/拆零单位展示名：优先槽 {@code unitName}，再读模型字典文案。
     *
     * @param bands    单位槽
     * @param product  产品读模型，可 null
     * @param bandKind PACKAGE 或 SPLIT
     * @return 展示名；无法解析时 null
     */
    private String resolveUnitNameByBandKind(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        ProductDefinitionDisplayDto product,
        String bandKind
    ) {
        String fromBand = this.findBandUnitName(bands, bandKind);
        // 槽上已有展示名则直接用
        if (StringUtils.isNotEmpty(fromBand)) {
            return fromBand;
        }
        if (product == null) {
            return null;
        }
        // PACKAGE：字典文案 → 目录解析名
        if (ProductDefinitionCatalogUnitBandConstants.BAND_PACKAGE.equals(bandKind)) {
            return StringUtils.firstNonBlank(
                product.getPackageUnitCodeDictText(),
                ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                    null, product.getPackageUnitCode(), product));
        }
        // SPLIT：同上
        if (ProductDefinitionCatalogUnitBandConstants.BAND_SPLIT.equals(bandKind)) {
            return StringUtils.firstNonBlank(
                product.getSplitUnitCodeDictText(),
                ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                    null, product.getSplitUnitCode(), product));
        }
        return null;
    }

    /**
     * 按槽语义取单位展示名。
     *
     * @param bands    单位槽
     * @param bandKind 槽语义
     * @return 展示名；未命中 null
     */
    private String findBandUnitName(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        String bandKind
    ) {
        if (bands == null || bands.isEmpty() || StringUtils.isEmpty(bandKind)) {
            return null;
        }
        // 遍历单位槽：命中语义且 unitName 非空即返回
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null) {
                continue;
            }
            if (!bandKind.equals(band.getBandKind())) {
                continue;
            }
            if (StringUtils.isNotEmpty(band.getUnitName())) {
                return band.getUnitName().trim();
            }
        }
        return null;
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
