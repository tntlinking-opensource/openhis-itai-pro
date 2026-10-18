/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.pricing;

import com.zxplat.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.zxplat.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionLotBatchKey;
import com.zxplat.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.zxplat.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 同表同品同批号进价一致性门禁（与供应商无关；BR-WHS-IN-018）。
 *
 * <p>已存在 ACTIVE {@code LOT_NUMBER_COST} 且与本次归一包装进价<strong>相同</strong>：放行（写路径跳过）。
 * 尚无批号进价：放行首次写入。</p>
 *
 * <p>已存在 ACTIVE {@code LOT_NUMBER_COST} 且进价<strong>不同</strong>时：仅当全院该批号库存合计为 0、
 * 从未产生过该实例+批号定价定义下的收费行、且无同品同批号发药/发耗行时允许改价（写路径停用旧 ACTIVE 再写入）；
 * 否则按库存/收费/发放原因拒绝。供应单（SupplyRequest）不构成阻塞。</p>
 *
 * <p>调用约定：进货行 {@code price} 为当前 {@code unitCode} 单价，须带单位以便归一；
 * 期初行 {@code price} 已是每包装单位价，{@code unitCode} 须留空，禁止再按操作单位放大。</p>
 */
@Component
@RequiredArgsConstructor
public class LotNumberPurchasePriceConsistencyGate {

    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final DeviceDispenseQueryService deviceDispenseQueryService;

    /**
     * 对一批进货/期初明细断言：同表同品同批号异价时须满足「零库存且无收费且无发放」。
     *
     * @param lines     待校验行；空则直接返回
     * @param errorCode 拒绝时业务错误码（如 {@code WHS_IN_001} / {@code BAS_INV_OPN_002}）
     * @throws BusinessException 同批号进价冲突且仍有库存/收费/发放时
     */
    public void assertIncomingLotPurchasePricesConsistent(List<LotPurchasePriceCheckLine> lines, String errorCode) {
        // Gate：无明细无需比对
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<LotPurchasePriceCheckLine> checkable = new ArrayList<>();
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        List<ChargeItemDefinitionLotBatchKey> costKeys = new ArrayList<>();
        for (LotPurchasePriceCheckLine line : lines) {
            if (line == null) {
                continue;
            }
            String lotNo = StringUtils.trimToNull(line.getLotNumber());
            // 无批号：写价路径本身会失败，本门禁跳过
            if (lotNo == null) {
                continue;
            }
            if (StringUtils.isEmpty(line.getItemTable()) || line.getItemId() == null) {
                continue;
            }
            // 无本次进价：药房/耗材期初可空，跳过比对
            if (line.getPrice() == null) {
                continue;
            }
            checkable.add(line);
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(line.getItemId());
            lookup.setProductTableName(line.getItemTable().trim());
            lookups.add(lookup);
            ChargeItemDefinitionLotBatchKey key =
                ChargeItemDefinitionLotBatchKey.maybeOf(line.getItemTable(), line.getItemId(), lotNo);
            if (key != null) {
                costKeys.add(key);
            }
        }
        if (checkable.isEmpty()) {
            return;
        }
        // Pre：批量产品定义（包装价归一）与已有批号进价
        Map<String, ProductDefinitionDisplayDto> productByKey =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        Map<String, List<ChargeItemDefinition>> costByKey =
            this.chargeItemDefinitionQueryService.findLotNumberCostLinesGroupedByLotBatchKey(costKeys);

        for (LotPurchasePriceCheckLine line : checkable) {
            String lotNo = StringUtils.trimToNull(line.getLotNumber());
            ChargeItemDefinitionLotBatchKey key =
                ChargeItemDefinitionLotBatchKey.maybeOf(line.getItemTable(), line.getItemId(), lotNo);
            if (key == null) {
                continue;
            }
            List<ChargeItemDefinition> existingCosts = costByKey.get(key.compositeKey());
            BigDecimal existingPurchase = this.selectFirstNonNullPriceValue(existingCosts);
            // 同批号尚无进价：允许首次写入
            if (existingPurchase == null) {
                continue;
            }
            ProductDefinitionDisplayDto product = productByKey.get(
                ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable().trim(), line.getItemId()));
            BigDecimal incomingPurchase =
                ProductDefinitionCatalogUnitBandsAssembler.resolveLotNumberPackageUnitPriceForInboundAgree(
                    line.getPrice(), line.getUnitCode(), product);
            // 同表同品同批号进价相同：放行（写路径跳过）
            if (existingPurchase.compareTo(incomingPurchase) == 0) {
                continue;
            }
            // 异价：须零库存且无收费且无发放，否则按原因拒绝
            this.assertLotPriceChangeAllowed(
                line.getItemTable().trim(),
                line.getItemId(),
                lotNo,
                existingPurchase,
                incomingPurchase,
                errorCode);
        }
    }

    /**
     * 同批号异价改价前置条件：全院库存合计为 0、无收费行、无发药/发耗行。
     *
     * @param itemTable         物品表
     * @param itemId            物品主键
     * @param lotNo             批号
     * @param existingPurchase  已有批号进价
     * @param incomingPurchase  本次归一包装进价
     * @param errorCode         业务错误码
     */
    private void assertLotPriceChangeAllowed(
        String itemTable,
        Long itemId,
        String lotNo,
        BigDecimal existingPurchase,
        BigDecimal incomingPurchase,
        String errorCode
    ) {
        // Gate：全院该批号仍有账面量 → 拒绝改价（sum 为空按 0）
        BigDecimal inventorySum =
            this.inventoryItemQueryService.sumQuantityByItemTableAndItemIdAndLotNumber(itemTable, itemId, lotNo);
        if (inventorySum == null) {
            inventorySum = BigDecimal.ZERO;
        }
        if (inventorySum.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(
                errorCode,
                "批号「"
                    + lotNo
                    + "」已存在进价 "
                    + existingPurchase.toPlainString()
                    + "，本次进价 "
                    + incomingPurchase.toPlainString()
                    + "，且全院该批号仍有库存（合计 "
                    + inventorySum.toPlainString()
                    + "），不允许同批号变更进价，请更换批号或先清零库存。");
        }
        // Gate：无定价定义主键则从未挂费，跳过收费存在性查询（禁止空 IN）
        List<Long> definitionIds =
            this.chargeItemDefinitionQueryService.listDefinitionIdsByInstanceAndLot(itemTable, itemId, lotNo);
        if (definitionIds != null && !definitionIds.isEmpty()) {
            // Gate：该实例+批号定价定义下曾产生过收费（含退费；软删草稿除外）→ 拒绝
            if (this.chargeItemQueryService.existsByDefinitionIdIn(definitionIds)) {
                throw new BusinessException(
                    errorCode,
                    "批号「"
                        + lotNo
                        + "」已存在进价 "
                        + existingPurchase.toPlainString()
                        + "，本次进价 "
                        + incomingPurchase.toPlainString()
                        + "，且该批号定价曾产生过收费记录，不允许同批号变更进价，请更换批号。");
            }
        }
        // Gate：同品同批号已有发药/发耗 → 拒绝（供应单不阻塞）
        if (this.existsDispenseForItemAndLot(itemTable, itemId, lotNo)) {
            throw new BusinessException(
                errorCode,
                "批号「"
                    + lotNo
                    + "」已存在进价 "
                    + existingPurchase.toPlainString()
                    + "，本次进价 "
                    + incomingPurchase.toPlainString()
                    + "，且该批号已有发药/发耗记录，不允许同批号变更进价，请更换批号。");
        }
    }

    /**
     * 按物品表判断是否存在同品同批号未软删发放行。
     *
     * @param itemTable 物品表
     * @param itemId    物品主键
     * @param lotNo     批号
     * @return 存在发放则为 true；非药/耗材表返回 false
     */
    private boolean existsDispenseForItemAndLot(String itemTable, Long itemId, String lotNo) {
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(itemTable)) {
            return this.medicationDispenseQueryService.existsByMedicationIdAndLotNumber(itemId, lotNo);
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(itemTable)) {
            return this.deviceDispenseQueryService.existsByDeviceDefIdAndLotNumber(itemId, lotNo);
        }
        return false;
    }

    /**
     * 从 ACTIVE 定价行列表取首个非空 {@code priceValue}（按列表顺序稳定择优）。
     *
     * @param rows 定价行；可空
     * @return 首个非空进价；无则 null
     */
    private BigDecimal selectFirstNonNullPriceValue(List<ChargeItemDefinition> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        for (ChargeItemDefinition row : rows) {
            if (row == null || row.getPriceValue() == null) {
                continue;
            }
            return row.getPriceValue();
        }
        return null;
    }

    /**
     * 同批号进价比对行（应用层装配；禁止依赖 feature Request/Response）。
     */
    public static final class LotPurchasePriceCheckLine {

        /** 物品定义表名（instance_table）。 */
        private String itemTable;

        /** 物品定义主键（instance_id）。 */
        private Long itemId;

        /** 批号字面值。 */
        private String lotNumber;

        /** 行进价（进货：当前单位单价；期初：已是每包装单位价）。 */
        private BigDecimal price;

        /**
         * 行计量单位编码（进货：用于归一为包装进价；期初：须 null/空，表示 {@link #price} 已是包装锚）。
         */
        private String unitCode;

        /**
         * @return 物品表
         */
        public String getItemTable() {
            return itemTable;
        }

        /**
         * @param itemTable 物品表
         */
        public void setItemTable(String itemTable) {
            this.itemTable = itemTable;
        }

        /**
         * @return 物品主键
         */
        public Long getItemId() {
            return itemId;
        }

        /**
         * @param itemId 物品主键
         */
        public void setItemId(Long itemId) {
            this.itemId = itemId;
        }

        /**
         * @return 批号
         */
        public String getLotNumber() {
            return lotNumber;
        }

        /**
         * @param lotNumber 批号
         */
        public void setLotNumber(String lotNumber) {
            this.lotNumber = lotNumber;
        }

        /**
         * @return 行进价
         */
        public BigDecimal getPrice() {
            return price;
        }

        /**
         * @param price 行进价
         */
        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        /**
         * @return 单位编码
         */
        public String getUnitCode() {
            return unitCode;
        }

        /**
         * @param unitCode 单位编码
         */
        public void setUnitCode(String unitCode) {
            this.unitCode = unitCode;
        }
    }
}
