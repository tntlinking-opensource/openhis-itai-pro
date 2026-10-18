/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.settlement.support;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.plugin.chs.bridge.inventory.assembler.InventoryUploadQuantityAssembler;
import com.zxplat.plugin.chs.domain.catalog.support.NationalCatalogPackHint;
import com.zxplat.plugin.chs.domain.share.constants.ChsInventoryConstants;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 结算费用上传（2204/2301）数量/单价单位换算。
 *
 * <p>限价口径以国家目录 {@code min_prcunt}（最小计价单位）为准；按包装收费时用目录
 * {@code min_pac_cnt} 折到计价单位。目录缺失时回落院内药典五槽。
 * 平台单价支持<strong>最多 6 位小数</strong>（2204/2301 {@code pric} 16,6）；折算时用行合计÷数量
 * 平摊到 6 位，保证 {@code det_item_fee_sumamt = round(pric×cnt, 2)} 与院内实收一致。</p>
 */
public final class SettlementFeeUnitConvertSupport {

    private SettlementFeeUnitConvertSupport() {
    }

    /**
     * 收费数量/单价 → 医保计价单位口径（供 2204/2301/3101）。
     *
     * @param medication      药品定义；可空
     * @param chargeUnitCode  收费单位（{@code ChargeItem.quantityUnit}）
     * @param chargeCnt       收费数量
     * @param chargeUnitPrice 收费单价
     * @return 数量、最多 6 位单价、单位码，以及与 {@code pric×cnt} 对齐的明细金额
     */
    public static FeeUploadQtyPrice toSplitUnit(
        MedicationDefinition medication,
        String chargeUnitCode,
        BigDecimal chargeCnt,
        BigDecimal chargeUnitPrice) {
        return toSplitUnit(medication, chargeUnitCode, chargeCnt, chargeUnitPrice, null);
    }

    /**
     * 收费数量/单价 → 医保计价单位口径；优先国家目录包装/计价单位。
     *
     * @param medication      药品定义；可空
     * @param chargeUnitCode  收费单位
     * @param chargeCnt       收费数量
     * @param chargeUnitPrice 收费单价
     * @param catalogHint     国家目录包装 hint；可空则仅走院内药典
     * @return 数量、最多 6 位单价、单位码，以及与 {@code pric×cnt} 对齐的明细金额
     */
    public static FeeUploadQtyPrice toSplitUnit(
        MedicationDefinition medication,
        String chargeUnitCode,
        BigDecimal chargeCnt,
        BigDecimal chargeUnitPrice,
        NationalCatalogPackHint catalogHint) {
        if (chargeCnt == null && chargeUnitPrice == null) {
            return of(null, null, chargeUnitCode);
        }
        FeeUploadQtyPrice catalogResult = tryCatalogConvert(
            medication, chargeUnitCode, chargeCnt, chargeUnitPrice, catalogHint);
        if (catalogResult != null) {
            return catalogResult;
        }
        return toHospitalSplitUnit(medication, chargeUnitCode, chargeCnt, chargeUnitPrice);
    }

    /**
     * 国家目录驱动：按 {@code min_prcunt}/{@code min_pac_cnt} 折算；无法决策时 null（回落院内）。
     */
    private static FeeUploadQtyPrice tryCatalogConvert(
        MedicationDefinition medication,
        String chargeUnitCode,
        BigDecimal chargeCnt,
        BigDecimal chargeUnitPrice,
        NationalCatalogPackHint catalogHint) {
        if (catalogHint == null || !catalogHint.hasPricingUnit() || chargeCnt == null) {
            return null;
        }
        // Pre：目录按包装单位限价（盒=盒）→ 禁止再折片
        if (catalogHint.pricesByPackUnit()) {
            return scaledAsIs(chargeUnitCode, chargeCnt, chargeUnitPrice);
        }
        String chargeUnit = chargeUnitCode == null ? null : chargeUnitCode.trim();
        if (isEmpty(chargeUnit)) {
            return null;
        }
        // 已是目录计价单位：原样
        if (matchesCatalogUnit(chargeUnit, catalogHint.minPrcunt(), medication, true)) {
            return scaledAsIs(chargeUnit, chargeCnt, chargeUnitPrice);
        }
        // 按目录包装单位（或院内销售单位）收费 → 用 min_pac_cnt 折到计价单位
        boolean chargingByPack = matchesCatalogUnit(chargeUnit, catalogHint.minPacunt(), medication, false)
            || isHospitalPackageUnit(medication, chargeUnit);
        if (!chargingByPack) {
            return null;
        }
        BigDecimal packCnt = catalogHint.minPacCntAsDecimal();
        if (packCnt == null) {
            return null;
        }
        BigDecimal scaledCnt = scaleUploadQty(chargeCnt.abs().multiply(packCnt));
        BigDecimal lineTotal = MonetaryAmountSupport.lineTotal(chargeUnitPrice, chargeCnt);
        BigDecimal splitPric = scalePlatformPric(
            lineTotal.divide(scaledCnt, ChsInventoryConstants.UPLOAD_PRICE_SCALE, RoundingMode.HALF_UP));
        String targetUnit = resolveCatalogTargetUnit(medication, chargeUnit, catalogHint.minPrcunt());
        // Pre：6 位单价平摊，det = round(pric×cnt, 2) 对齐院内行合计
        return of(scaledCnt, splitPric, targetUnit);
    }

    /** 院内药典五槽：收费单位 → 拆零单位。 */
    private static FeeUploadQtyPrice toHospitalSplitUnit(
        MedicationDefinition medication,
        String chargeUnitCode,
        BigDecimal chargeCnt,
        BigDecimal chargeUnitPrice) {
        BigDecimal scaledPric = scalePlatformPric(chargeUnitPrice);
        if (medication == null || chargeCnt == null) {
            return of(chargeCnt, scaledPric, chargeUnitCode);
        }
        String splitUnit = medication.getSplitUnitCode();
        if (isEmpty(splitUnit)) {
            return of(scaleUploadQty(chargeCnt), scaledPric, chargeUnitCode);
        }
        String fromUnit = chargeUnitCode;
        if (isEmpty(fromUnit)) {
            // 收费单位缺失：无法可靠换算，原样上传
            return of(scaleUploadQty(chargeCnt), scaledPric, chargeUnitCode);
        }
        String from = fromUnit.trim();
        String to = splitUnit.trim();
        // 已是拆零单位：仅刻度
        if (from.equals(to)) {
            return of(scaleUploadQty(chargeCnt), scaledPric, to);
        }
        BigDecimal ratio = InventoryUploadQuantityAssembler.ratioToSplit(medication, from);
        // 未知单位倍数：不臆造，原样上传
        if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
            return of(scaleUploadQty(chargeCnt), scaledPric, fromUnit);
        }
        BigDecimal splitCnt = InventoryUploadQuantityAssembler.operationQuantityToSplit(
            medication, from, chargeCnt);
        if (splitCnt == null) {
            return of(scaleUploadQty(chargeCnt), scaledPric, fromUnit);
        }
        BigDecimal scaledCnt = scaleUploadQty(splitCnt);
        // Pre：单价 = 行合计 ÷ 拆零数量，保留 6 位（2204 pric 16,6），避免两位价产生上传分差
        BigDecimal lineTotal = MonetaryAmountSupport.lineTotal(chargeUnitPrice, chargeCnt);
        BigDecimal splitPric = scalePlatformPric(
            lineTotal.divide(scaledCnt, ChsInventoryConstants.UPLOAD_PRICE_SCALE, RoundingMode.HALF_UP));
        return of(scaledCnt, splitPric, to);
    }

    private static FeeUploadQtyPrice scaledAsIs(
        String unitCode, BigDecimal chargeCnt, BigDecimal chargeUnitPrice) {
        return of(scaleUploadQty(chargeCnt), scalePlatformPric(chargeUnitPrice), unitCode);
    }

    private static boolean isHospitalPackageUnit(MedicationDefinition medication, String chargeUnit) {
        if (medication == null || isEmpty(chargeUnit) || isEmpty(medication.getPackageUnitCode())) {
            return false;
        }
        return chargeUnit.equals(medication.getPackageUnitCode().trim());
    }

    /**
     * 院内单位码与目录中文单位是否同一族。
     *
     * @param pricingSide true=对计价单位（片/粒…）；false=对包装单位（盒/箱…）
     */
    private static boolean matchesCatalogUnit(
        String hospitalUnit,
        String catalogLabel,
        MedicationDefinition medication,
        boolean pricingSide) {
        if (isEmpty(hospitalUnit) || isEmpty(catalogLabel)) {
            return false;
        }
        String unit = hospitalUnit.trim();
        String label = catalogLabel.trim();
        if (unit.equalsIgnoreCase(label)) {
            return true;
        }
        if (pricingSide) {
            if (medication != null
                && isNotEmpty(medication.getSplitUnitCode())
                && unit.equals(medication.getSplitUnitCode().trim())
                && isSplitLikeLabel(label)) {
                return true;
            }
            return isSplitLikeLabel(label) && isCommonSplitCode(unit);
        }
        if (medication != null
            && isNotEmpty(medication.getPackageUnitCode())
            && unit.equals(medication.getPackageUnitCode().trim())
            && isPackLikeLabel(label)) {
            return true;
        }
        return isPackLikeLabel(label) && isCommonPackCode(unit);
    }

    private static String resolveCatalogTargetUnit(
        MedicationDefinition medication, String chargeUnit, String minPrcunt) {
        if (medication != null && isNotEmpty(medication.getSplitUnitCode())) {
            return medication.getSplitUnitCode().trim();
        }
        if (isNotEmpty(minPrcunt)) {
            return minPrcunt.trim();
        }
        return chargeUnit;
    }

    private static boolean isPackLikeLabel(String label) {
        return label.contains("盒") || label.contains("箱");
    }

    private static boolean isSplitLikeLabel(String label) {
        return label.contains("片")
            || label.contains("粒")
            || label.contains("丸")
            || label.contains("枚")
            || label.contains("袋")
            || label.contains("支")
            || label.contains("瓶")
            || label.contains("喷");
    }

    private static boolean isCommonPackCode(String unit) {
        return "2".equals(unit)
            || "box".equalsIgnoreCase(unit)
            || "package".equalsIgnoreCase(unit);
    }

    private static boolean isCommonSplitCode(String unit) {
        return "1".equals(unit)
            || "tablet".equalsIgnoreCase(unit)
            || "pill".equalsIgnoreCase(unit);
    }

    /**
     * 平台单价：最多 6 位 HALF_UP（2204/2301 {@code pric} numeric(16,6)）。
     *
     * @param pric 原始单价；可空
     * @return 6 位单价；入参空则 null
     */
    public static BigDecimal scalePlatformPric(BigDecimal pric) {
        if (pric == null) {
            return null;
        }
        return pric.setScale(
            ChsInventoryConstants.UPLOAD_PRICE_SCALE,
            MonetaryAmountSupport.MONETARY_ROUNDING);
    }

    private static FeeUploadQtyPrice of(BigDecimal cnt, BigDecimal pric, String unitCode) {
        return new FeeUploadQtyPrice(cnt, pric, unitCode, syncSumamt(cnt, pric));
    }

    /**
     * 与平台校验对齐：明细金额 = 单价 × 数量（两位 HALF_UP）。
     *
     * @param cnt  数量
     * @param pric 单价（最多 6 位）
     * @return 对齐金额；任一方空则 null
     */
    public static BigDecimal syncSumamt(BigDecimal cnt, BigDecimal pric) {
        if (cnt == null || pric == null) {
            return null;
        }
        return MonetaryAmountSupport.scaleSettle(cnt.multiply(pric));
    }

    private static BigDecimal scaleUploadQty(BigDecimal quantity) {
        if (quantity == null) {
            return null;
        }
        return quantity.abs().setScale(
            ChsInventoryConstants.UPLOAD_QUANTITY_SCALE,
            MonetaryAmountSupport.MONETARY_ROUNDING);
    }

    /**
     * 费用上传数量/单价/金额（计价单位口径）。
     *
     * @param cnt              数量
     * @param pric             单价（最多 6 位）
     * @param unitCode         单位码
     * @param detItemFeeSumamt 明细金额（= round(pric×cnt, 2)，供 2204）
     */
    public record FeeUploadQtyPrice(
        BigDecimal cnt,
        BigDecimal pric,
        String unitCode,
        BigDecimal detItemFeeSumamt) {
        /** 单位码非空时返回 trim 后文本。 */
        public String unitCodeOrNull() {
            if (isNotEmpty(unitCode)) {
                return unitCode.trim();
            }
            return null;
        }

        /**
         * 优先用对齐后的明细金额，否则回落院内合计。
         *
         * @param fallback 院内 det_item_fee_sumamt
         * @return 上传用明细金额
         */
        public BigDecimal sumamtOr(BigDecimal fallback) {
            if (detItemFeeSumamt != null) {
                return detItemFeeSumamt;
            }
            return fallback;
        }
    }
}
