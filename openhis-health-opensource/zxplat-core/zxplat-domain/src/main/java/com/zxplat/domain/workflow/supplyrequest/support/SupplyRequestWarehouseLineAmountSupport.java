/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.supplyrequest.support;

import com.zxplat.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.zxplat.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 库房供应单行金额：拆零数量 + 包装单价套 BR-INV-DTL-007（**BR-INV-SET-018**）。
 *
 * <p>{@code wkf_supply_request.item_quantity} 恒为拆零最小单位；定价/批号价为每包装单位价，
 * 行总额禁止 {@code 拆零数量 × 包装单价} 直乘。</p>
 */
@Component
@RequiredArgsConstructor
public class SupplyRequestWarehouseLineAmountSupport {

    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;

    /**
     * 按拆零数量与行单价（可带行单位码）计算行总额。
     *
     * @param itemTable      物品表
     * @param itemId         物品主键
     * @param lineUnitCode   行计价单位码；权威包装价路径可 null
     * @param splitQuantity  拆零最小单位数量
     * @param lineUnitPrice  行单价（与 {@code lineUnitCode} 一致；权威路径为每包装价）
     * @return 行总额（6 位）；参数不足时 null
     */
    public BigDecimal computeWarehouseSplitLineTotal(
        String itemTable,
        Long itemId,
        String lineUnitCode,
        BigDecimal splitQuantity,
        BigDecimal lineUnitPrice
    ) {
        if (splitQuantity == null || lineUnitPrice == null) {
            return null;
        }
        WarehouseProductUnitContext context = this.loadContext(itemTable, itemId);
        BigDecimal packageUnitPrice = this.normalizeLinePriceToPackageUnit(lineUnitPrice, lineUnitCode, context);
        if (packageUnitPrice == null) {
            return null;
        }
        return MonetaryAmountSupport.computeSplitQuantityLineTotal(
            splitQuantity,
            context.resolvePackageRatioToSplit(),
            packageUnitPrice
        );
    }

    /**
     * 行计价单位单价归一为每包装单位单价。
     *
     * @param lineUnitPrice 行单价
     * @param lineUnitCode  行单位码，可 null（视为已是包装价）
     * @param context       品种单位上下文
     * @return 每包装单位单价（6 位）
     */
    public BigDecimal normalizeLinePriceToPackageUnit(
        BigDecimal lineUnitPrice,
        String lineUnitCode,
        WarehouseProductUnitContext context
    ) {
        if (lineUnitPrice == null) {
            return null;
        }
        if (StringUtils.isEmpty(lineUnitCode)) {
            return MonetaryAmountSupport.scaleCalc(lineUnitPrice);
        }
        String normalizedUnit = lineUnitCode.trim();
        if (context.isPackageUnitCode(normalizedUnit)) {
            return MonetaryAmountSupport.scaleCalc(lineUnitPrice);
        }
        BigDecimal lineRatio = context.resolveRatioToSplitForUnitCode(normalizedUnit);
        if (lineRatio == null) {
            return MonetaryAmountSupport.scaleCalc(lineUnitPrice);
        }
        return MonetaryAmountSupport.convertLineUnitPriceToPackageUnitPrice(
            lineUnitPrice,
            lineRatio,
            context.resolvePackageRatioToSplit()
        );
    }

    /**
     * 加载品种包装/计量单位上下文。
     *
     * @param itemTable 物品表
     * @param itemId    物品主键
     * @return 单位上下文（缺品种时仅回落包装比 1）
     */
    public WarehouseProductUnitContext loadContext(String itemTable, Long itemId) {
        if (StringUtils.isEmpty(itemTable) || itemId == null) {
            return WarehouseProductUnitContext.empty();
        }
        String table = itemTable.trim();
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)) {
            return this.loadMedicationContext(itemId);
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            return this.loadDeviceContext(itemId);
        }
        return WarehouseProductUnitContext.empty();
    }

    /**
     * 药品定义 → 单位上下文。
     *
     * @param itemId 药品定义主键
     * @return 单位上下文
     */
    private WarehouseProductUnitContext loadMedicationContext(Long itemId) {
        java.util.Optional<MedicationDefinition> optional =
            medicationDefinitionQueryService.findOptionalMedicationDefinitionEntityById(itemId);
        if (optional.isEmpty()) {
            return WarehouseProductUnitContext.empty();
        }
        MedicationDefinition definition = optional.get();
        return WarehouseProductUnitContext.from(
            definition.getPurchaseUnitCode(),
            definition.getPurchaseUnitRatio(),
            definition.getPackageUnitCode(),
            definition.getPackageUnitRatio(),
            definition.getSplitUnitCode(),
            definition.getStockUnitCode(),
            definition.getStockUnitRatio()
        );
    }

    /**
     * 耗材定义 → 单位上下文。
     *
     * @param itemId 耗材定义主键
     * @return 单位上下文
     */
    private WarehouseProductUnitContext loadDeviceContext(Long itemId) {
        DeviceDefinition definition = deviceDefinitionQueryService.getDeviceDefinition(itemId);
        if (definition == null) {
            return WarehouseProductUnitContext.empty();
        }
        return WarehouseProductUnitContext.from(
            definition.getPurchaseUnitCode(),
            definition.getPurchaseUnitRatio(),
            definition.getPackageUnitCode(),
            definition.getPackageUnitRatio(),
            definition.getSplitUnitCode(),
            definition.getStockUnitCode(),
            definition.getStockUnitRatio()
        );
    }

    /**
     * 品种计量单位快照（供应请求写侧行额用）。
     */
    @Getter
    public static final class WarehouseProductUnitContext {

        /** 采购单位码。 */
        private final String purchaseUnitCode;

        /** 采购单位相对拆零倍数。 */
        private final BigDecimal purchaseUnitRatio;

        /** 包装单位码。 */
        private final String packageUnitCode;

        /** 包装单位相对拆零倍数。 */
        private final BigDecimal packageUnitRatio;

        /** 拆零单位码。 */
        private final String splitUnitCode;

        /** 库存单位码。 */
        private final String stockUnitCode;

        /** 库存单位相对拆零倍数。 */
        private final BigDecimal stockUnitRatio;

        private WarehouseProductUnitContext(
            String purchaseUnitCode,
            BigDecimal purchaseUnitRatio,
            String packageUnitCode,
            BigDecimal packageUnitRatio,
            String splitUnitCode,
            String stockUnitCode,
            BigDecimal stockUnitRatio
        ) {
            this.purchaseUnitCode = purchaseUnitCode;
            this.purchaseUnitRatio = purchaseUnitRatio;
            this.packageUnitCode = packageUnitCode;
            this.packageUnitRatio = packageUnitRatio;
            this.splitUnitCode = splitUnitCode;
            this.stockUnitCode = stockUnitCode;
            this.stockUnitRatio = stockUnitRatio;
        }

        /**
         * 空上下文（包装比回落 1）。
         *
         * @return 空上下文
         */
        public static WarehouseProductUnitContext empty() {
            return new WarehouseProductUnitContext(null, null, null, null, null, null, null);
        }

        /**
         * 由品种计量字段构建上下文。
         *
         * @param purchaseUnitCode   采购单位码
         * @param purchaseUnitRatio  采购→拆零倍数
         * @param packageUnitCode    包装单位码
         * @param packageUnitRatio   包装→拆零倍数
         * @param splitUnitCode      拆零单位码
         * @param stockUnitCode      库存单位码
         * @param stockUnitRatio     库存→拆零倍数
         * @return 上下文
         */
        public static WarehouseProductUnitContext from(
            String purchaseUnitCode,
            BigDecimal purchaseUnitRatio,
            String packageUnitCode,
            BigDecimal packageUnitRatio,
            String splitUnitCode,
            String stockUnitCode,
            BigDecimal stockUnitRatio
        ) {
            return new WarehouseProductUnitContext(
                purchaseUnitCode,
                purchaseUnitRatio,
                packageUnitCode,
                packageUnitRatio,
                splitUnitCode,
                stockUnitCode,
                stockUnitRatio
            );
        }

        /**
         * 解析包装单位相对拆零倍数。
         *
         * @return 正倍数，至少 1
         */
        public BigDecimal resolvePackageRatioToSplit() {
            return MonetaryAmountSupport.resolvePositivePackageUnitRatio(this.packageUnitRatio);
        }

        /**
         * 判断单位码是否为包装单位。
         *
         * @param unitCode 单位码
         * @return 命中包装单位码时为 true
         */
        public boolean isPackageUnitCode(String unitCode) {
            if (StringUtils.isEmpty(unitCode) || StringUtils.isEmpty(this.packageUnitCode)) {
                return false;
            }
            return this.packageUnitCode.trim().equals(unitCode.trim());
        }

        /**
         * 按持久化单位码解析相对拆零倍数。
         *
         * @param unitCode 行单位码
         * @return 正倍数；无法匹配时 null
         */
        public BigDecimal resolveRatioToSplitForUnitCode(String unitCode) {
            if (StringUtils.isEmpty(unitCode)) {
                return null;
            }
            String normalized = unitCode.trim();
            if (matchesUnitCode(this.splitUnitCode, normalized)) {
                return BigDecimal.ONE;
            }
            if (matchesUnitCode(this.packageUnitCode, normalized)) {
                return positiveRatio(this.packageUnitRatio);
            }
            if (matchesUnitCode(this.purchaseUnitCode, normalized)) {
                return positiveRatio(this.purchaseUnitRatio);
            }
            if (matchesUnitCode(this.stockUnitCode, normalized)) {
                return positiveRatio(this.stockUnitRatio);
            }
            return null;
        }

        /**
         * 单位码精确匹配（trim 后相等）。
         *
         * @param candidate 候选单位码
         * @param unitCode  目标单位码
         * @return 匹配为 true
         */
        private static boolean matchesUnitCode(String candidate, String unitCode) {
            if (StringUtils.isEmpty(candidate)) {
                return false;
            }
            return candidate.trim().equals(unitCode);
        }

        /**
         * 取正倍数，无效时 null。
         *
         * @param ratio 换算比
         * @return 正倍数或 null
         */
        private static BigDecimal positiveRatio(BigDecimal ratio) {
            if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return ratio;
        }
    }
}
