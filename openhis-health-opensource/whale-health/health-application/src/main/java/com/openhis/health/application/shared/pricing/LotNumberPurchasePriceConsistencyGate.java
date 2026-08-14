/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.pricing;

import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionLotBatchKey;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 同表同品同批号进价一致性门禁（与供应商无关）。
 *
 * <p>仅当 {@code instanceTable + instanceId + lotNo} 已存在 ACTIVE {@code LOT_NUMBER_COST}，
 * 且与本次归一包装进价不一致时拒绝。跨品种、药/耗材之间批号字面值可重复。</p>
 *
 * <p>调用约定：进货行 {@code price} 为当前 {@code unitCode} 单价，须带单位以便归一；
 * 期初行 {@code price} 已是每包装单位价，{@code unitCode} 须留空，禁止再按操作单位放大。</p>
 */
@Component
@RequiredArgsConstructor
public class LotNumberPurchasePriceConsistencyGate {

    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 对一批进货/期初明细断言：同表同品同批号不得变更已有批号进价。
     *
     * @param lines     待校验行；空则直接返回
     * @param errorCode 拒绝时业务错误码（如 {@code WHS_IN_001} / {@code BAS_INV_OPN_002}）
     * @throws BusinessException 同批号进价冲突时
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
            // 同表同品同批号进价变更：拒绝（须换批号字面值）
            if (existingPurchase.compareTo(incomingPurchase) != 0) {
                throw new BusinessException(
                    errorCode,
                    "批号「"
                        + lotNo
                        + "」已存在进价 "
                        + existingPurchase.toPlainString()
                        + "，本次进价 "
                        + incomingPurchase.toPlainString()
                        + "，同一品种不允许同批号不同进价，请更换批号后入库。");
            }
        }
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
