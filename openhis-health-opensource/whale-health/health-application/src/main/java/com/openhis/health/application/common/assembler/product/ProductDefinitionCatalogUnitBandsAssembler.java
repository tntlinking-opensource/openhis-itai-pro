/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.common.assembler.product;

import com.openhis.health.application.common.assembler.order.OrderCatalogDisplayAssembler;
import com.openhis.health.application.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import com.openhis.health.domain.share.constants.ProductDefinitionCatalogUnitBandConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.PriceType;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * <p>医生站药品/耗材使用五槽（采购、拆零、包装、库存、剂量），各槽 {@code ratioToSplit} 为<strong>该命名单位相对拆零（最小）单位的个数</strong>；
 * 诊疗项目（{@code wkf_activity_definition}）同样返回五槽 DTO，仅 {@code BAND_PACKAGE} 有值，其余槽字段为 {@code null}。
 * <strong>选品与目录侧多槽单价</strong>一律按<strong>线性比例链</strong>换算，**禁止**先对「拆零单价」做业务标度舍入再乘回各槽——否则包装端整数价（如 20 元/盒）会在来回乘除中漂成 20.0002 等噪声。
 * 医生站目录在能解析<strong>包装槽</strong>（{@code BAND_PACKAGE}，同源读模型 {@code package_unit_ratio}）时，先将目录计价单价换为<strong>高精度下的每包装单位价</strong>，再按 {@code unitPrice_slot = P_pkg × R_slot / R_pkg} 写各槽；
 * 进货四槽则以 {@code BAND_PACKAGE} 为包装锚；若包装槽缺失则退化为「计价单位 → 各槽」一步除法 {@code P_quote × R_slot / R_quote}，仍不经拆零中转舍入。
 * <strong>定价换算硬约束（P0）</strong>：凡 {@code PURCHASE}/{@code SALE}/{@code LOT_NUMBER_*} 之 {@code price_value} 均为<strong>每包装单位价</strong>；
 * 槽价与账单单价换算<strong>必须以包装单位为唯一输入锚</strong>，只允许<strong>包装价 → 其它单位价</strong>（{@code P_slot = P_pkg × R_slot / R_pkg}），
 * <strong>禁止</strong>以拆零/默认开立单位为锚再「往回乘」还原包装价，<strong>禁止</strong>把包装价误当作拆零价后再除倍数纠错。</p>
 *
 * <p>库房入/出/盘选品与详情（{@link #applyInboundWarehouseBandPrices} / {@link #applyInboundWarehouseDetailLineBandPrices}）：
 * <strong>读路径包装锚仅两档</strong>——无批号时定价表 {@code PURCHASE}、有批号时 {@code LOT_NUMBER_COST}（均为每包装单位单价）。
 * 各槽 {@code unitPrice} 按 {@code unitPrice_slot = P_pkg × R_slot / R_pkg} 线性写入。
 * {@code SALE}、{@code LOT_NUMBER_PRICE} <strong>不参与</strong>库房单据读路径与槽价装配（{@code LOT_NUMBER_PRICE} 仅在同意入库时写入，供门诊/收费下游消费）。</p>
 *
 * <p>槽语义编码一律取 {@link ProductDefinitionCatalogUnitBandConstants} 中 {@code BAND_*} 字面量，与持久化身份无关的展示字段分离。</p>
 */
public final class ProductDefinitionCatalogUnitBandsAssembler {

    /**
     * 包装锚点中间换算标度：在「计价单位价 → 每包装价」一步除法上高于出参标度，降低再按比例分配时的累积误差。
     */
    private static final int PACKAGE_ANCHOR_INTERMEDIATE_SCALE =
        ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE + 12;

    /**
     * 工具类禁止实例化；单位槽装配均为 {@code static} 方法。
     */
    private ProductDefinitionCatalogUnitBandsAssembler() {
    }

    /**
     * 选品目录行主单位编码：默认开立单位优先，其次包装单位；与采购单位无关。
     *
     * @param productRow 产品定义读模型行
     * @return 主单位编码，可能为 {@code null}
     */
    public static String resolveCatalogPrimaryUnitCode(ProductDefinitionDisplayDto productRow) {
        if (productRow == null) {
            return null;
        }
        return StringUtils.firstNonBlank(
            productRow.getDefEncounterUnitCode(), productRow.getPackageUnitCode());
    }

    /**
     * 选品目录行主单位展示名，与 {@link #resolveCatalogPrimaryUnitCode} 列优先级一致。
     *
     * @param productRow 产品定义读模型行
     * @return 主单位展示名，可能为 {@code null}
     */
    public static String resolveCatalogPrimaryUnitName(ProductDefinitionDisplayDto productRow) {
        if (productRow == null) {
            return null;
        }
        return StringUtils.firstNonBlank(
            productRow.getDefEncounterUnitCodeDictText(), productRow.getPackageUnitCodeDictText());
    }

    /**
     * 定价表落库单价是否为「每包装单位」价型（与 DM-ADM-010 §1.5 一致）。
     *
     * @param priceTypeCode 价类持久化码（{@link PriceType#getCode()}）
     * @return {@code true} 表示 {@code price_value} 须以包装单位为换算锚
     */
    public static boolean isPackagePersistedUnitPriceType(String priceTypeCode) {
        if (StringUtils.isBlank(priceTypeCode)) {
            return false;
        }
        return PriceType.SALE.getCode().equals(priceTypeCode)
            || PriceType.PURCHASE.getCode().equals(priceTypeCode)
            || PriceType.isLotNumberPriceType(priceTypeCode);
    }

    /**
     * 槽价/账单换算时定价行 {@code price_value} 的包装锚点单位编码。
     *
     * <p>包装价型优先取 {@link ProductDefinitionDisplayDto#getPackageUnitCode()}；诊疗等活动定义无包装槽时回落
     * {@link #resolveCatalogPrimaryUnitCode}。</p>
     *
     * @param productRow    产品读模型
     * @param priceTypeCode 价类持久化码
     * @return 作为 {@code buildDoctorStationBands} 计价单位入参的编码；可能为 {@code null}
     */
    public static String resolvePackageAnchoredPricingUnitCode(
        ProductDefinitionDisplayDto productRow,
        String priceTypeCode
    ) {
        if (isPackagePersistedUnitPriceType(priceTypeCode) && productRow != null) {
            String packageUnitCode = StringUtils.trimToNull(productRow.getPackageUnitCode());
            if (packageUnitCode != null) {
                return packageUnitCode;
            }
        }
        return resolveCatalogPrimaryUnitCode(productRow);
    }

    /**
     * 医生站/护士站目录 {@code unitBands} 槽价装配锚点：有定价行时按其价类取包装锚，否则按产品表分流。
     *
     * @param productRow    产品读模型
     * @param priceTypeCode 本行已选定价价类持久化码；可为 {@code null}
     * @return 传入 {@link #buildDoctorStationBands} 的计价单位 code
     */
    public static String resolveDoctorStationBandPricingUnitCode(
        ProductDefinitionDisplayDto productRow,
        String priceTypeCode
    ) {
        if (StringUtils.isNotBlank(priceTypeCode)) {
            return resolvePackageAnchoredPricingUnitCode(productRow, priceTypeCode);
        }
        if (productRow == null) {
            return null;
        }
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(productRow.getProductTableName())) {
            return resolvePackageAnchoredPricingUnitCode(productRow, PriceType.SALE.getCode());
        }
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productRow.getProductTableName())
            || TableNameConstants.ADM_DEVICE_DEFINITION.equals(productRow.getProductTableName())) {
            return resolvePackageAnchoredPricingUnitCode(productRow, PriceType.LOT_NUMBER_PRICE.getCode());
        }
        return resolveCatalogPrimaryUnitCode(productRow);
    }

    /**
     * 单位展示名：术语 {@code UNIT_CODE} 预热映射优先，未命中时回落产品 DTO 各槽字典文本（B.10.8.1）。
     */
    public static String resolveUnitDisplayName(
        Map<String, String> unitLabelByCode,
        String unitCode,
        ProductDefinitionDisplayDto productRow
    ) {
        String fromTerminology = OrderCatalogDisplayAssembler.resolveCategoryDisplay(unitLabelByCode, unitCode);
        if (StringUtils.isNotBlank(fromTerminology)) {
            return fromTerminology.trim();
        }
        return resolveUnitDisplayNameFromProduct(productRow, unitCode);
    }

    /**
     * 剂量单位展示名：术语 {@code DOSE_UNIT} 预热映射优先，未命中时回落产品 DTO 剂量单位字典文本。
     */
    public static String resolveDoseUnitDisplayName(
        Map<String, String> doseUnitLabelByCode,
        String doseUnitCode,
        ProductDefinitionDisplayDto productRow
    ) {
        String fromTerminology = OrderCatalogDisplayAssembler.resolveCategoryDisplay(doseUnitLabelByCode, doseUnitCode);
        if (StringUtils.isNotBlank(fromTerminology)) {
            return fromTerminology.trim();
        }
        if (productRow == null || StringUtils.isBlank(doseUnitCode)) {
            return null;
        }
        if (StringUtils.equals(doseUnitCode.trim(), StringUtils.trimToEmpty(productRow.getDoseUnitCode()))) {
            return StringUtils.trimToNull(productRow.getDoseUnitCodeDictText());
        }
        return null;
    }

    /**
     * 从产品 DTO 各槽单位列解析展示名（不含术语预热）。
     */
    /**
     * 物资分类展示名：按产品表路由药/耗类目术语映射（B.10.8.1）。
     *
     * @param productTableName 物品物理表名
     * @param categoryCode     类目持久化码
     * @param terminologyMaps  预热术语映射；可为 null
     * @return 展示名；未命中时为 {@code null}
     */
    public static String resolveProductCategoryDictText(
        String productTableName,
        String categoryCode,
        ProductDefinitionSelectionTerminologyMaps terminologyMaps
    ) {
        if (terminologyMaps == null || StringUtils.isBlank(categoryCode)) {
            return null;
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(productTableName)) {
            return OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                terminologyMaps.getDeviceCategoryLabelByCode(), categoryCode);
        }
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productTableName)) {
            return OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                terminologyMaps.getMedicationCategoryLabelByCode(), categoryCode);
        }
        return null;
    }

    /**
     * 为进货四槽回填单位展示名：术语 {@code UNIT_CODE} 优先，未命中回落产品 DTO 各槽字典列。
     *
     * @param bands           单位槽列表
     * @param productRow      产品定义展示 DTO
     * @param terminologyMaps 预热术语映射；可为 null
     */
    public static void applyInboundUnitBandDisplayNames(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        ProductDefinitionDisplayDto productRow,
        ProductDefinitionSelectionTerminologyMaps terminologyMaps
    ) {
        if (bands == null || bands.isEmpty()) {
            return;
        }
        Map<String, String> unitLabelByCode = null;
        if (terminologyMaps != null) {
            unitLabelByCode = terminologyMaps.getUnitLabelByCode();
        }
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null || StringUtils.isBlank(band.getUnitCode())) {
                continue;
            }
            String displayName = resolveUnitDisplayName(unitLabelByCode, band.getUnitCode(), productRow);
            band.setUnitName(displayName);
        }
    }

    public static String resolveUnitDisplayNameFromProduct(ProductDefinitionDisplayDto product, String unitCode) {
        if (product == null || StringUtils.isBlank(unitCode)) {
            return null;
        }
        String normalized = unitCode.trim();
        if (StringUtils.equals(normalized, product.getPackageUnitCode())) {
            return StringUtils.trimToNull(product.getPackageUnitCodeDictText());
        }
        if (StringUtils.equals(normalized, product.getSplitUnitCode())) {
            return StringUtils.trimToNull(product.getSplitUnitCodeDictText());
        }
        if (StringUtils.equals(normalized, product.getPurchaseUnitCode())) {
            return StringUtils.trimToNull(product.getPurchaseUnitCodeDictText());
        }
        if (StringUtils.equals(normalized, product.getStockUnitCode())) {
            return StringUtils.trimToNull(product.getStockUnitCodeDictText());
        }
        if (StringUtils.equals(normalized, product.getDoseUnitCode())) {
            return StringUtils.trimToNull(product.getDoseUnitCodeDictText());
        }
        if (StringUtils.equals(normalized, product.getDefEncounterUnitCode())) {
            return StringUtils.trimToNull(product.getDefEncounterUnitCodeDictText());
        }
        if (StringUtils.equals(normalized, product.getDefInhospitalUnitCode())) {
            return StringUtils.trimToNull(product.getDefInhospitalUnitCodeDictText());
        }
        return null;
    }

    /**
     * 装配医生站医嘱开立目录用的五类单位槽（{@link ProductDefinitionDisplayDto} 版）。
     */
    public static List<ProductDefinitionCatalogUnitBandDto> buildDoctorStationBands(
        ProductDefinitionDisplayDto productRow,
        String pricingUnitCode,
        BigDecimal pricingUnitPrice
    ) {
        if (productRow == null) {
            return new ArrayList<>();
        }
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(productRow.getProductTableName())) {
            return buildActivityDoctorStationBandsFromDisplay(productRow, pricingUnitCode, pricingUnitPrice);
        }
        List<ProductDefinitionCatalogUnitBandDto> bands = new ArrayList<>(5);
        bands.add(singleBand(
            ProductDefinitionCatalogUnitBandConstants.BAND_PURCHASE,
            productRow.getPurchaseUnitCode(),
            productRow.getPurchaseUnitCodeDictText(),
            productRow.getPurchaseUnitRatio()));
        bands.add(singleBand(
            ProductDefinitionCatalogUnitBandConstants.BAND_SPLIT,
            productRow.getSplitUnitCode(),
            productRow.getSplitUnitCodeDictText(),
            BigDecimal.ONE));
        bands.add(singleBand(
            ProductDefinitionCatalogUnitBandConstants.BAND_PACKAGE,
            productRow.getPackageUnitCode(),
            productRow.getPackageUnitCodeDictText(),
            productRow.getPackageUnitRatio()));
        bands.add(singleBand(
            ProductDefinitionCatalogUnitBandConstants.BAND_STOCK,
            productRow.getStockUnitCode(),
            productRow.getStockUnitCodeDictText(),
            productRow.getStockUnitRatio()));
        bands.add(singleBand(
            ProductDefinitionCatalogUnitBandConstants.BAND_DOSE,
            productRow.getDoseUnitCode(),
            productRow.getDoseUnitCodeDictText(),
            productRow.getDoseUnitRatio()));
        applyPricingToBandsIfPresentFromDisplay(bands, pricingUnitCode, pricingUnitPrice, productRow);
        return bands;
    }

    private static List<ProductDefinitionCatalogUnitBandDto> buildActivityDoctorStationBandsFromDisplay(
        ProductDefinitionDisplayDto productRow,
        String pricingUnitCode,
        BigDecimal pricingUnitPrice
    ) {
        List<ProductDefinitionCatalogUnitBandDto> bands = new ArrayList<>(5);
        String unitCode = productRow.getPackageUnitCode();
        String unitName = productRow.getPackageUnitCodeDictText();
        bands.add(singleBand(ProductDefinitionCatalogUnitBandConstants.BAND_PURCHASE, null, null, null));
        bands.add(singleBand(ProductDefinitionCatalogUnitBandConstants.BAND_SPLIT, null, null, null));
        if (StringUtils.isNotBlank(unitCode)) {
            bands.add(singleBand(
                ProductDefinitionCatalogUnitBandConstants.BAND_PACKAGE,
                unitCode.trim(),
                unitName,
                BigDecimal.ONE));
        } else {
            bands.add(singleBand(ProductDefinitionCatalogUnitBandConstants.BAND_PACKAGE, null, null, null));
        }
        bands.add(singleBand(ProductDefinitionCatalogUnitBandConstants.BAND_STOCK, null, null, null));
        bands.add(singleBand(ProductDefinitionCatalogUnitBandConstants.BAND_DOSE, null, null, null));
        applyPricingToBandsIfPresent(bands, pricingUnitCode, pricingUnitPrice, null, null);
        return bands;
    }

    private static void applyPricingToBandsIfPresentFromDisplay(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        String pricingUnitCode,
        BigDecimal pricingUnitPrice,
        ProductDefinitionDisplayDto productRow
    ) {
        // 定价换算仅以包装价为锚向下写各槽；禁止拆零锚 + 采购倍数「往回乘」纠错
        applyPricingToBandsIfPresent(bands, pricingUnitCode, pricingUnitPrice, null, null);
    }

    /**
     * 装配进货选品单位槽（主表 DTO）。
     */
    public static List<ProductDefinitionCatalogUnitBandDto> buildInboundSelectionBands(ProductDefinitionDisplayDto productRow) {
        if (productRow == null) {
            return new ArrayList<>();
        }
        return buildInboundSelectionBands(
            productRow.getProductTableName(),
            productRow.getPurchaseUnitCode(),
            productRow.getPurchaseUnitCodeDictText(),
            productRow.getPurchaseUnitRatio(),
            productRow.getPackageUnitCode(),
            productRow.getPackageUnitCodeDictText(),
            productRow.getPackageUnitRatio(),
            productRow.getSplitUnitCode(),
            productRow.getSplitUnitCodeDictText(),
            productRow.getStockUnitCode(),
            productRow.getStockUnitCodeDictText(),
            productRow.getStockUnitRatio());
    }

    /**
     * 装配进货/库房选品四槽，并按 {@code UNIT_CODE} 术语预热映射回填各槽 {@code unitName}（B.10.8.1）。
     *
     * @param productRow        产品展示 DTO
     * @param terminologyMaps   术语映射；为 null 时仅装配槽结构（展示名依赖读模型 {@code *DictText} 列）
     * @return 单位槽列表
     */
    public static List<ProductDefinitionCatalogUnitBandDto> buildInboundSelectionBandsWithTerminology(
        ProductDefinitionDisplayDto productRow,
        ProductDefinitionSelectionTerminologyMaps terminologyMaps
    ) {
        List<ProductDefinitionCatalogUnitBandDto> bands = buildInboundSelectionBands(productRow);
        applyInboundUnitBandDisplayNames(bands, productRow, terminologyMaps);
        return bands;
    }

    private static List<ProductDefinitionCatalogUnitBandDto> buildInboundSelectionBands(
        String productTableName,
        String purchaseUnitCode,
        String purchaseUnitCodeDictText,
        BigDecimal purchaseUnitRatio,
        String packageUnitCode,
        String packageUnitCodeDictText,
        BigDecimal packageUnitRatio,
        String splitUnitCode,
        String splitUnitCodeDictText,
        String stockUnitCode,
        String stockUnitCodeDictText,
        BigDecimal stockUnitRatio
    ) {
        List<ProductDefinitionCatalogUnitBandDto> bands = new ArrayList<>(4);
        appendInboundPurchaseOrStockBand(
            bands,
            ProductDefinitionCatalogUnitBandConstants.BAND_PURCHASE,
            purchaseUnitCode,
            purchaseUnitCodeDictText,
            purchaseUnitRatio);
        appendInboundPurchaseOrStockBand(
            bands,
            ProductDefinitionCatalogUnitBandConstants.BAND_PACKAGE,
            packageUnitCode,
            packageUnitCodeDictText,
            packageUnitRatio);
        appendInboundSplitBand(bands, splitUnitCode, splitUnitCodeDictText);
        appendInboundPurchaseOrStockBand(
            bands,
            ProductDefinitionCatalogUnitBandConstants.BAND_STOCK,
            stockUnitCode,
            stockUnitCodeDictText,
            stockUnitRatio);
        return bands;
    }

    /**
     * 解析「采购包装单位 → 拆零单位」换算倍数：优先已组槽 {@code PURCHASE} 的 {@code ratioToSplit}，再读模型 {@code purchase_unit_ratio}。
     *
     * <p>与 {@link #writeBandUnitPricesFromAnchoredUnitAndRatio} 写槽倍数同源，避免同意落库换算与展示槽比例不一致。</p>
     *
     * @param productRow 产品定义读模型，可 null
     * @param bands      进货四槽或同源列表，可 null
     * @return 正倍数；无法解析时为 {@code null}
     */
    public static BigDecimal resolvePurchaseUnitRatioForInboundPricing(
        ProductDefinitionDisplayDto productRow,
        List<ProductDefinitionCatalogUnitBandDto> bands
    ) {
        BigDecimal fromBands = resolvePurchaseUnitRatioFromBands(bands);
        if (fromBands != null) {
            return fromBands;
        }
        if (productRow != null && productRow.getPurchaseUnitRatio() != null) {
            if (productRow.getPurchaseUnitRatio().compareTo(BigDecimal.ZERO) > 0) {
                return productRow.getPurchaseUnitRatio();
            }
        }
        return null;
    }

    /**
     * 解析包装单位相对拆零倍数：优先已组槽 {@code BAND_PACKAGE}，再读模型 {@code package_unit_ratio}。
     */
    private static BigDecimal resolvePackageUnitRatioForInboundPricing(
        ProductDefinitionDisplayDto productRow,
        List<ProductDefinitionCatalogUnitBandDto> bands
    ) {
        BigDecimal fromBand = resolvePackagingAnchorRatioToSplit(bands);
        if (fromBand != null) {
            return fromBand;
        }
        if (productRow != null && productRow.getPackageUnitRatio() != null) {
            if (productRow.getPackageUnitRatio().compareTo(BigDecimal.ZERO) > 0) {
                return productRow.getPackageUnitRatio();
            }
        }
        return null;
    }

    /**
     * 将某计价单位下单价线性换算为每包装单位单价：{@code P_pkg = P_line × R_pkg / R_line}。
     *
     * <p>除法使用 {@link #PACKAGE_ANCHOR_INTERMEDIATE_SCALE}，落库前仅一次 {@link ChargeItemAmountScaleConstants#CHARGE_MONETARY_SCALE} 舍入；
     * {@code R_line = R_pkg} 时原样返回，<strong>禁止</strong>经槽价装配往返换算。</p>
     */
    private static BigDecimal convertUnitPriceToPackageUnitPrice(
        BigDecimal priceOnLineUnit,
        BigDecimal lineRatioToSplit,
        BigDecimal packageRatioToSplit
    ) {
        if (priceOnLineUnit == null) {
            return null;
        }
        if (lineRatioToSplit == null || packageRatioToSplit == null) {
            return normalizePackageUnitPriceForPersist(priceOnLineUnit);
        }
        if (lineRatioToSplit.compareTo(BigDecimal.ZERO) <= 0
            || packageRatioToSplit.compareTo(BigDecimal.ZERO) <= 0) {
            return normalizePackageUnitPriceForPersist(priceOnLineUnit);
        }
        if (lineRatioToSplit.compareTo(packageRatioToSplit) == 0) {
            return normalizePackageUnitPriceForPersist(priceOnLineUnit);
        }
        BigDecimal converted = priceOnLineUnit.multiply(packageRatioToSplit)
            .divide(lineRatioToSplit, PACKAGE_ANCHOR_INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
        return normalizePackageUnitPriceForPersist(converted);
    }

    /**
     * 批号维定价等落库前，将每包装单价归一到收费金额标度（唯一一次 HALF_UP）。
     */
    private static BigDecimal normalizePackageUnitPriceForPersist(BigDecimal pricePerPackageUnit) {
        if (pricePerPackageUnit == null) {
            return null;
        }
        return pricePerPackageUnit.setScale(
            ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal resolvePurchaseUnitRatioFromBands(List<ProductDefinitionCatalogUnitBandDto> bands) {
        if (bands == null || bands.isEmpty()) {
            return null;
        }
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null) {
                continue;
            }
            if (!ProductDefinitionCatalogUnitBandConstants.BAND_PURCHASE.equals(band.getBandKind())) {
                continue;
            }
            BigDecimal r = band.getRatioToSplit();
            if (r != null && r.compareTo(BigDecimal.ZERO) > 0) {
                return r;
            }
        }
        return null;
    }

    /**
     * 库房选品/详情：以<strong>每包装单位单价</strong>为唯一锚，线性写入四槽 {@code unitPrice}（含 {@code PURCHASE}/{@code STOCK}/{@code SPLIT}）。
     *
     * <p>{@code pricePerPackageUnit} 典型来源：定价表 {@code PURCHASE}（维护态每包装进价）、批号 {@code LOT_NUMBER_COST}、或行价归一后的包装锚。
     * <strong>禁止</strong>传入「每采购包装单位」目录价或调用 {@link #applyInboundBandsPriceFromCatalogPurchase}。</p>
     *
     * @param bands                 进货四槽
     * @param productRow            产品读模型，可 null
     * @param pricePerPackageUnit   每 {@code BAND_PACKAGE} 单位单价（元）；null 时不修改各槽
     */
    public static void applyInboundWarehouseBandPrices(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        ProductDefinitionDisplayDto productRow,
        BigDecimal pricePerPackageUnit
    ) {
        applyInboundBandsPriceUsingPackageAnchor(bands, productRow, pricePerPackageUnit);
    }

    /**
     * 库房详情行：批号进价或目录 {@code PURCHASE}（均为每包装）为默认锚；行 {@code price}/{@code unitCode} 优先并归一为包装锚后写槽。
     *
     * @param bands                            进货四槽
     * @param productRow                       产品读模型，可 null
     * @param catalogPurchasePricePerPackage   定价表 {@code PURCHASE}（每包装），可 null
     * @param lotNumberCostPricePerPackage     批号 {@code LOT_NUMBER_COST}（每包装），可 null
     * @param linePrice                        明细行单价，可 null
     * @param lineUnitCode                     明细行计价单位编码，可 null
     */
    public static void applyInboundWarehouseDetailLineBandPrices(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        ProductDefinitionDisplayDto productRow,
        BigDecimal catalogPurchasePricePerPackage,
        BigDecimal lotNumberCostPricePerPackage,
        BigDecimal linePrice,
        String lineUnitCode
    ) {
        if (bands == null || bands.isEmpty()) {
            return;
        }
        BigDecimal packageAnchor = lotNumberCostPricePerPackage;
        if (packageAnchor == null) {
            packageAnchor = catalogPurchasePricePerPackage;
        }
        if (linePrice != null) {
            BigDecimal fromLine = resolvePackageAnchorUnitPriceFromLine(
                linePrice, lineUnitCode, productRow, bands);
            if (fromLine != null) {
                packageAnchor = fromLine;
            }
        }
        applyInboundWarehouseBandPrices(bands, productRow, packageAnchor);
    }

    /**
     * 将供应行/表单行单价归一为<strong>每包装单位（{@code BAND_PACKAGE}）单价</strong>，供槽价装配或批号维定价落库。
     *
     * <p>计价单位命中 {@code PACKAGE} 槽时原样返回；其它单位按 {@code P_pkg = P_line × R_pkg / R_line} 单次线性换算（与写槽公式对称，禁止往返装配）。</p>
     *
     * @param linePrice    行单价（元）
     * @param lineUnitCode 行持久化单位码，可 null
     * @param productRow   产品读模型，可 null
     * @param bands        已组四槽；可 null（则从 {@code productRow} 临时装配）
     * @return 每包装单位单价；{@code linePrice} 为 null 时为 null
     */
    public static BigDecimal resolvePackageAnchorUnitPriceFromLine(
        BigDecimal linePrice,
        String lineUnitCode,
        ProductDefinitionDisplayDto productRow,
        List<ProductDefinitionCatalogUnitBandDto> bands
    ) {
        if (linePrice == null) {
            return null;
        }
        List<ProductDefinitionCatalogUnitBandDto> workingBands = bands;
        if (workingBands == null || workingBands.isEmpty()) {
            if (productRow == null) {
                return normalizePackageUnitPriceForPersist(linePrice);
            }
            workingBands = buildInboundSelectionBands(productRow);
        }
        BigDecimal packageRatio = resolvePackageUnitRatioForInboundPricing(productRow, workingBands);
        if (packageRatio == null) {
            return normalizePackageUnitPriceForPersist(linePrice);
        }
        if (StringUtils.isBlank(lineUnitCode)) {
            return normalizePackageUnitPriceForPersist(linePrice);
        }
        String normalizedUnit = lineUnitCode.trim();
        if (bandContainsUnitCode(
            workingBands, ProductDefinitionCatalogUnitBandConstants.BAND_PACKAGE, normalizedUnit)) {
            return normalizePackageUnitPriceForPersist(linePrice);
        }
        BigDecimal lineRatio = resolveRatioToSplitByUnitCode(workingBands, normalizedUnit);
        if (lineRatio == null) {
            return normalizePackageUnitPriceForPersist(linePrice);
        }
        return convertUnitPriceToPackageUnitPrice(linePrice, lineRatio, packageRatio);
    }

    /**
     * 在进货类四槽上，将<strong>每包装单位（{@code BAND_PACKAGE}）</strong>标价换算为各槽 {@code unitPrice}。
     */
    public static void applyInboundBandsPriceUsingPackageAnchor(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        ProductDefinitionDisplayDto productRow,
        BigDecimal pricePerPackageUnit
    ) {
        if (bands == null || bands.isEmpty()) {
            return;
        }
        if (pricePerPackageUnit == null) {
            return;
        }
        if (productRow != null && StringUtils.isNotBlank(productRow.getPackageUnitCode())) {
            applyUnitPricingToBandsIfPresent(bands, productRow.getPackageUnitCode().trim(), pricePerPackageUnit);
            return;
        }
        BigDecimal packageRatio = resolvePackagingAnchorRatioToSplit(bands);
        if (packageRatio == null) {
            return;
        }
        if (packageRatio.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        writeBandUnitPricesFromAnchoredUnitAndRatio(bands, pricePerPackageUnit, packageRatio);
    }

    /**
     * 在进货类四槽上，将<strong>单个采购包装单位</strong>对应的标价（元）换算为各槽 {@code unitPrice}。
     *
     * <p><strong>库房选品/详情禁止调用</strong>；仅保留供非库房场景或历史兼容。库房须 {@link #applyInboundWarehouseBandPrices}。</p>
     *
     * @param bands                         进货单位槽列表
     * @param purchasePackagingRatioToSplit 采购包装单位相对拆零倍数，须大于 0（与读模型 {@code purchase_unit_ratio} 一致）
     * @param pricePerPurchasePackagingUnit 每采购包装单位的标价（元）
     */
    public static void applyInboundBandsPriceUsingPurchasePackagingRatio(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        BigDecimal purchasePackagingRatioToSplit,
        BigDecimal pricePerPurchasePackagingUnit
    ) {
        if (bands == null || bands.isEmpty()) {
            return;
        }
        if (pricePerPurchasePackagingUnit == null) {
            return;
        }
        if (purchasePackagingRatioToSplit == null) {
            return;
        }
        if (purchasePackagingRatioToSplit.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        writeBandUnitPricesFromAnchoredUnitAndRatio(
            bands,
            pricePerPurchasePackagingUnit,
            purchasePackagingRatioToSplit
        );
    }

    /**
     * 将定价表 {@code PURCHASE} 按采购包装锚线性写入<strong>全部</strong>槽 {@code unitPrice}。
     *
     * <p><strong>库房选品/详情禁止调用</strong>；库房须 {@link #applyInboundWarehouseBandPrices}（仅以每包装价为锚写全槽）。</p>
     *
     * @param bands                         进货四槽列表
     * @param productRow                    产品读模型；可 null，此时仅从 {@code bands} 解析采购倍数
     * @param pricePerPurchasePackagingUnit 每采购包装单位的目录采购价（元）
     */
    public static void applyInboundBandsPriceFromCatalogPurchase(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        ProductDefinitionDisplayDto productRow,
        BigDecimal pricePerPurchasePackagingUnit
    ) {
        if (bands == null || bands.isEmpty()) {
            return;
        }
        if (pricePerPurchasePackagingUnit == null) {
            return;
        }
        BigDecimal purchaseRatio = resolvePurchaseUnitRatioForInboundPricing(productRow, bands);
        if (purchaseRatio == null) {
            return;
        }
        if (purchaseRatio.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        applyInboundBandsPriceUsingPurchasePackagingRatio(
            bands, purchaseRatio, pricePerPurchasePackagingUnit);
    }

    /**
     * 将<strong>采购槽单位</strong>下单价换算为每包装单位（{@code BAND_PACKAGE}）单价。
     *
     * <p>{@code P_pkg = P_band × R_pkg / R_band}；用于表单行价落在采购计量带、同意批号落库等场景。
     * 定价表 {@code PURCHASE} 本身已是每包装价，<strong>不得</strong>对本方法入参再套一层换算。</p>
     *
     * @param priceOnPurchaseBandUnit 采购槽单位下单价（元）
     * @param productRow              产品读模型，可 null
     * @param bands                   进货四槽，可 null
     * @return 每包装单位单价；入参为 null 时为 null
     */
    public static BigDecimal convertBandUnitPriceToPackageUnitPrice(
        BigDecimal priceOnPurchaseBandUnit,
        ProductDefinitionDisplayDto productRow,
        List<ProductDefinitionCatalogUnitBandDto> bands
    ) {
        return convertPurchasePackagingPriceToPackageUnitPrice(priceOnPurchaseBandUnit, productRow, bands);
    }

    /**
     * 将采购槽单位下单价换算为每包装单位标价（与 {@link #convertBandUnitPriceToPackageUnitPrice} 同义，保留旧名兼容）。
     *
     * @param pricePerPurchasePackagingUnit 采购槽单位下单价（元），非定价表 {@code PURCHASE} 直取值
     * @param productRow                    产品读模型，可 null
     * @param bands                         进货四槽，可 null
     * @return 每包装单位单价
     */
    public static BigDecimal convertPurchasePackagingPriceToPackageUnitPrice(
        BigDecimal pricePerPurchasePackagingUnit,
        ProductDefinitionDisplayDto productRow,
        List<ProductDefinitionCatalogUnitBandDto> bands
    ) {
        if (pricePerPurchasePackagingUnit == null) {
            return null;
        }
        List<ProductDefinitionCatalogUnitBandDto> workingBands = bands;
        if ((workingBands == null || workingBands.isEmpty()) && productRow != null) {
            workingBands = buildInboundSelectionBands(productRow);
        }
        BigDecimal packageRatio = resolvePackageUnitRatioForInboundPricing(productRow, workingBands);
        BigDecimal purchaseRatio = resolvePurchaseUnitRatioForInboundPricing(productRow, workingBands);
        return convertUnitPriceToPackageUnitPrice(
            pricePerPurchasePackagingUnit, purchaseRatio, packageRatio);
    }

    /**
     * 进货同意批号维定价：将供应行 {@code price} 归一为每 {@code BAND_PACKAGE} 单价后落 {@code LOT_NUMBER_*}。
     *
     * @param linePrice    供应行单价
     * @param lineUnitCode 供应行持久化单位码
     * @param productRow   产品读模型
     * @return 批号维定价 {@code priceValue}；{@code linePrice} 为 null 时返回 {@link BigDecimal#ZERO}
     */
    public static BigDecimal resolveLotNumberPackageUnitPriceForInboundAgree(
        BigDecimal linePrice,
        String lineUnitCode,
        ProductDefinitionDisplayDto productRow
    ) {
        if (linePrice == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal resolved = resolvePackageAnchorUnitPriceFromLine(linePrice, lineUnitCode, productRow, null);
        if (resolved == null) {
            return normalizePackageUnitPriceForPersist(linePrice);
        }
        return resolved;
    }

    /**
     * 在已组好的进货单位槽上，按<strong>计价单位编码</strong>及其<strong>单价（元）</strong>线性写入各槽 {@code unitPrice}。
     * 与 {@link #buildDoctorStationBands} 内定价换算同算法；计价单位须在 {@code bands} 中存在且 {@code ratioToSplit} 有效。
     *
     * @param bands             进货四槽列表（通常来自 {@link #buildInboundSelectionBands}）
     * @param pricingUnitCode   当前行计价单位持久化码（与某槽 {@code unitCode} 一致）
     * @param pricingUnitPrice  该计价单位下单价（元）；空或 {@code pricingUnitCode} 空白时不修改各槽单价
     */
    public static void applyUnitPricingToBandsIfPresent(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        String pricingUnitCode,
        BigDecimal pricingUnitPrice
    ) {
        applyPricingToBandsIfPresent(bands, pricingUnitCode, pricingUnitPrice, null, null);
    }

    /**
     * 在已组好的槽列表上，按目录计价单位将单价线性写入各槽；能解析包装锚时与目录「每包装价」对齐。
     *
     * @param bands                          槽列表（医生站五槽或进货四槽）
     * @param pricingUnitCode                目录当前计价单位编码
     * @param pricingUnitPrice               该计价单位对应的单价（元）
     * @param purchaseUnitRatioHint          已废止：禁止拆零锚纠错，须由调用方传入包装锚 {@code pricingUnitCode}
     * @param splitUnitCodeHint              已废止，保留签名仅为编译兼容
     */
    private static void applyPricingToBandsIfPresent(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        String pricingUnitCode,
        BigDecimal pricingUnitPrice,
        BigDecimal purchaseUnitRatioHint,
        String splitUnitCodeHint
    ) {
        if (bands == null || bands.isEmpty()) {
            return;
        }
        if (StringUtils.isBlank(pricingUnitCode)) {
            return;
        }
        if (pricingUnitPrice == null) {
            return;
        }
        BigDecimal pricingRatio = null;
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null) {
                continue;
            }
            if (!pricingUnitCode.equals(band.getUnitCode())) {
                continue;
            }
            BigDecimal bandRatio = band.getRatioToSplit();
            if (bandRatio != null) {
                pricingRatio = bandRatio;
            }
            break;
        }
        if (pricingRatio == null) {
            return;
        }
        if (pricingRatio.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal packageLikeRatio = resolvePackagingAnchorRatioToSplit(bands);
        if (packageLikeRatio != null) {
            BigDecimal pricePerPackageUnit = pricingUnitPrice.multiply(packageLikeRatio)
                .divide(pricingRatio, PACKAGE_ANCHOR_INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
            writeBandUnitPricesFromAnchoredUnitAndRatio(bands, pricePerPackageUnit, packageLikeRatio);
            return;
        }
        writeBandUnitPricesFromAnchoredUnitAndRatio(bands, pricingUnitPrice, pricingRatio);
    }

    /**
     * 自锚定价：{@code unitPrice_slot = anchorPrice × R_slot / R_anchor}，出参元金额按 {@link ChargeItemAmountScaleConstants#CHARGE_MONETARY_SCALE} HALF_UP。
     *
     * @param bands            单位槽列表
     * @param anchorUnitPrice  锚定单位（采购包装或包装单位等）下单价（元）
     * @param anchorRatioToSplit 锚定单位相对拆零倍数，须大于 0
     */
    private static void writeBandUnitPricesFromAnchoredUnitAndRatio(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        BigDecimal anchorUnitPrice,
        BigDecimal anchorRatioToSplit
    ) {
        if (bands == null || bands.isEmpty()) {
            return;
        }
        if (anchorUnitPrice == null) {
            return;
        }
        if (anchorRatioToSplit == null) {
            return;
        }
        if (anchorRatioToSplit.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        int outScale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE;
        // Loop：各槽按与锚比之线性关系写单价
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null) {
                continue;
            }
            BigDecimal ratio = band.getRatioToSplit();
            if (ratio == null) {
                continue;
            }
            if (ratio.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal scaled = anchorUnitPrice.multiply(ratio)
                .divide(anchorRatioToSplit, outScale, RoundingMode.HALF_UP);
            band.setUnitPrice(scaled);
        }
    }

    /**
     * 解析用于<strong>目录定价锚</strong>的包装相对拆零倍数：取 {@code BAND_PACKAGE} 槽。
     *
     * @param bands 已组装槽列表
     * @return 正倍数；无有效包装槽时为 {@code null}
     */
    private static BigDecimal resolvePackagingAnchorRatioToSplit(List<ProductDefinitionCatalogUnitBandDto> bands) {
        if (bands == null || bands.isEmpty()) {
            return null;
        }
        // Loop：命中 PACKAGE（包装）
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null) {
                continue;
            }
            if (!ProductDefinitionCatalogUnitBandConstants.BAND_PACKAGE.equals(band.getBandKind())) {
                continue;
            }
            BigDecimal r = band.getRatioToSplit();
            if (r != null && r.compareTo(BigDecimal.ZERO) > 0) {
                return r;
            }
        }
        return null;
    }

    /**
     * 判断某槽是否配置了与 {@code unitCode} 一致的单位编码。
     *
     * @param bands    单位槽列表
     * @param bandKind 槽语义
     * @param unitCode 持久化单位码
     * @return 命中时为 true
     */
    private static boolean bandContainsUnitCode(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        String bandKind,
        String unitCode
    ) {
        if (bands == null || bands.isEmpty()) {
            return false;
        }
        if (StringUtils.isBlank(bandKind) || StringUtils.isBlank(unitCode)) {
            return false;
        }
        String normalized = unitCode.trim();
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null) {
                continue;
            }
            if (!bandKind.equals(band.getBandKind())) {
                continue;
            }
            if (StringUtils.isBlank(band.getUnitCode())) {
                continue;
            }
            if (normalized.equals(band.getUnitCode().trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 进货选品：拆零槽固定倍数 1，仅要求单位编码存在。
     *
     * @param bands    输出集合
     * @param unitCode 拆零单位编码
     * @param unitName 拆零单位展示名
     */
    private static void appendInboundSplitBand(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        String unitCode,
        String unitName
    ) {
        if (bands == null) {
            return;
        }
        if (StringUtils.isBlank(unitCode)) {
            return;
        }
        ProductDefinitionCatalogUnitBandDto band = new ProductDefinitionCatalogUnitBandDto();
        band.setBandKind(ProductDefinitionCatalogUnitBandConstants.BAND_SPLIT);
        band.setUnitCode(unitCode.trim());
        if (unitName != null) {
            band.setUnitName(unitName.trim());
        } else {
            band.setUnitName(null);
        }
        band.setRatioToSplit(BigDecimal.ONE);
        bands.add(band);
    }

    /**
     * 进货选品：采购或库存槽；单位编码与非正倍数时不追加。
     *
     * @param bands         输出集合
     * @param bandKind      {@link ProductDefinitionCatalogUnitBandConstants} 槽语义
     * @param unitCode      单位编码
     * @param unitName      展示名
     * @param ratioToSplit  相对拆零倍数
     */
    private static void appendInboundPurchaseOrStockBand(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        String bandKind,
        String unitCode,
        String unitName,
        BigDecimal ratioToSplit
    ) {
        if (bands == null) {
            return;
        }
        if (StringUtils.isBlank(unitCode)) {
            return;
        }
        if (ratioToSplit == null) {
            return;
        }
        if (ratioToSplit.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        ProductDefinitionCatalogUnitBandDto band = new ProductDefinitionCatalogUnitBandDto();
        band.setBandKind(bandKind);
        band.setUnitCode(unitCode.trim());
        if (unitName != null) {
            band.setUnitName(unitName.trim());
        } else {
            band.setUnitName(null);
        }
        band.setRatioToSplit(ratioToSplit);
        bands.add(band);
    }

    /**
     * 医生站目录「可开总量」展示单位：优先包装槽，否则 {@link #resolveCatalogPrimaryUnitCode}。
     */
    public static String resolveDoctorStationCatalogDisplayUnitCode(
        ProductDefinitionDisplayDto productRow,
        List<ProductDefinitionCatalogUnitBandDto> bands
    ) {
        if (bands != null) {
            for (ProductDefinitionCatalogUnitBandDto band : bands) {
                if (band == null) {
                    continue;
                }
                if (!ProductDefinitionCatalogUnitBandConstants.BAND_PACKAGE.equals(band.getBandKind())) {
                    continue;
                }
                if (StringUtils.isNotBlank(band.getUnitCode())) {
                    return band.getUnitCode().trim();
                }
            }
        }
        return resolveCatalogPrimaryUnitCode(productRow);
    }

    /**
     * 在已组装的单位槽中，按持久化单位编码解析相对拆零倍数 {@code ratioToSplit}。
     */
    public static BigDecimal resolveRatioToSplitByUnitCode(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        String unitCode
    ) {
        if (bands == null || bands.isEmpty()) {
            return null;
        }
        if (StringUtils.isBlank(unitCode)) {
            return null;
        }
        String normalized = unitCode.trim();
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null) {
                continue;
            }
            if (StringUtils.isBlank(band.getUnitCode())) {
                continue;
            }
            if (!normalized.equals(band.getUnitCode().trim())) {
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
     * 将库存域「拆零（最小）单位」数量换算为医生站目录展示单位数量。
     */
    public static BigDecimal convertSplitQuantityToDisplayUnit(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        BigDecimal splitQuantity,
        String displayUnitCode
    ) {
        if (splitQuantity == null) {
            return null;
        }
        if (StringUtils.isBlank(displayUnitCode)) {
            return splitQuantity;
        }
        BigDecimal targetRatio = resolveRatioToSplitByUnitCode(bands, displayUnitCode);
        if (targetRatio == null) {
            return splitQuantity;
        }
        if (targetRatio.compareTo(BigDecimal.ONE) == 0) {
            return splitQuantity;
        }
        int scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE;
        return splitQuantity.divide(targetRatio, scale, RoundingMode.HALF_UP);
    }

    /**
     * 医生站目录行可开总量：预减后拆零净量加总，再换算为展示单位。
     */
    public static BigDecimal convertDoctorStationCatalogAvailableQuantity(
        ProductDefinitionDisplayDto productRow,
        List<ProductDefinitionCatalogUnitBandDto> bands,
        BigDecimal netSplitQuantitySum
    ) {
        if (netSplitQuantitySum == null) {
            return null;
        }
        String displayUnitCode = resolveDoctorStationCatalogDisplayUnitCode(productRow, bands);
        return convertSplitQuantityToDisplayUnit(bands, netSplitQuantitySum, displayUnitCode);
    }

    /**
     * 构造单个单位槽（医生站五槽装配片段）。
     *
     * @param bandKind     槽语义
     * @param unitCode     单位编码
     * @param unitName     展示名
     * @param ratioToSplit 相对拆零倍数
     * @return 槽 DTO
     */
    private static ProductDefinitionCatalogUnitBandDto singleBand(
        String bandKind,
        String unitCode,
        String unitName,
        BigDecimal ratioToSplit
    ) {
        ProductDefinitionCatalogUnitBandDto band = new ProductDefinitionCatalogUnitBandDto();
        band.setBandKind(bandKind);
        band.setUnitCode(unitCode);
        band.setUnitName(unitName);
        band.setRatioToSplit(ratioToSplit);
        return band;
    }
}
