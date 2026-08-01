/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.catalog.support;

import com.openhis.health.application.basic.catalog.dto.CatalogLotPriceRowDto;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 药品/耗材目录批号价装配：从当前 ACTIVE 定价行中抽取 {@code LOT_NUMBER_COST}/{@code LOT_NUMBER_PRICE} 并按批号归并。
 *
 * <p>读路径为<strong>当前价</strong>（仅调用方已过滤的 ACTIVE 行）；禁止用于历史单据槽价回填（BR-PRICE-SRC-001）。</p>
 */
public final class CatalogLotPriceAssembler {

    /**
     * 禁止实例化：仅静态装配。
     */
    private CatalogLotPriceAssembler() {
    }

    /**
     * 将实例维度定价行归并为批号进价/售价列表。
     *
     * @param priceLines       当前 ACTIVE 定价行（可为 null）
     * @param lotNumberFilter  可选批号过滤；空白则返回该实例全部批号价行
     * @return 按批号排序的行列表；无命中时为空列表
     */
    public static List<CatalogLotPriceRowDto> assembleFromActivePriceLines(
        List<ChargeItemDefinition> priceLines,
        String lotNumberFilter
    ) {
        // 无定价行时直接空列表，避免无意义归并
        if (priceLines == null || priceLines.isEmpty()) {
            return List.of();
        }
        String normalizedFilter = null;
        // 调用方传入批号时做精确过滤；空白表示查该实例全部批号价
        if (StringUtils.isNotEmpty(lotNumberFilter)) {
            normalizedFilter = lotNumberFilter.trim();
        }
        Map<String, MutableLotPrice> byLot = new LinkedHashMap<>();
        for (ChargeItemDefinition row : priceLines) {
            // 跳过空行与非批号价类
            if (row == null) {
                continue;
            }
            String priceTypeCode = row.getPriceTypeCode();
            // 仅批号维进价/售价；跳过维护态 PURCHASE/SALE/LIMIT
            if (!PriceType.isLotNumberPriceType(priceTypeCode)) {
                continue;
            }
            // 无批号无法作为真实批次价
            if (StringUtils.isEmpty(row.getLotNo())) {
                continue;
            }
            String lotNumber = row.getLotNo().trim();
            // 指定批号过滤时跳过不匹配行
            if (normalizedFilter != null && !normalizedFilter.equals(lotNumber)) {
                continue;
            }
            MutableLotPrice bucket = byLot.get(lotNumber);
            // 同批号首次出现时建桶
            if (bucket == null) {
                bucket = new MutableLotPrice(lotNumber);
                byLot.put(lotNumber, bucket);
            }
            // 进价行写入桶（多行择优在 preferCost）
            if (PriceType.LOT_NUMBER_COST.getCode().equals(priceTypeCode)) {
                bucket.preferCost(row.getPriceValue(), row.getId(), row.getPriority());
                continue;
            }
            // 售价行写入桶
            if (PriceType.LOT_NUMBER_PRICE.getCode().equals(priceTypeCode)) {
                bucket.preferSale(row.getPriceValue(), row.getId(), row.getPriority());
            }
        }
        List<CatalogLotPriceRowDto> result = new ArrayList<>(byLot.size());
        List<String> lotKeys = new ArrayList<>(byLot.keySet());
        lotKeys.sort(Comparator.naturalOrder());
        for (String lotKey : lotKeys) {
            MutableLotPrice bucket = byLot.get(lotKey);
            CatalogLotPriceRowDto dto = new CatalogLotPriceRowDto();
            dto.setLotNumber(bucket.lotNumber);
            dto.setCostPrice(bucket.costPrice);
            dto.setSalePrice(bucket.salePrice);
            result.add(dto);
        }
        return result;
    }

    /**
     * 单批号下进价/售价择优桶：priority 升序、同 priority 时 id 升序（与收费项当前价择优一致）。
     */
    private static final class MutableLotPrice {
        private final String lotNumber;
        private BigDecimal costPrice;
        private Long costLineId;
        private Integer costPriority;
        private BigDecimal salePrice;
        private Long saleLineId;
        private Integer salePriority;

        private MutableLotPrice(String lotNumber) {
            this.lotNumber = lotNumber;
        }

        private void preferCost(BigDecimal priceValue, Long lineId, Integer priority) {
            // 同批号多条进价行时保留更优 priority/id
            if (this.shouldReplace(this.costLineId, this.costPriority, lineId, priority)) {
                this.costPrice = priceValue;
                this.costLineId = lineId;
                this.costPriority = priority;
            }
        }

        private void preferSale(BigDecimal priceValue, Long lineId, Integer priority) {
            // 同批号多条售价行时保留更优 priority/id
            if (this.shouldReplace(this.saleLineId, this.salePriority, lineId, priority)) {
                this.salePrice = priceValue;
                this.saleLineId = lineId;
                this.salePriority = priority;
            }
        }

        /**
         * 尚无命中，或候选 priority/id 更优时替换。
         */
        private boolean shouldReplace(Long currentId, Integer currentPriority, Long candidateId, Integer candidatePriority) {
            // 尚无已选行则采纳候选
            if (currentId == null) {
                return true;
            }
            // 候选缺主键时不覆盖已选（防御脏数据）
            if (candidateId == null) {
                return false;
            }
            int currentPri;
            // null priority 视为最低优先级
            if (currentPriority == null) {
                currentPri = Integer.MAX_VALUE;
            } else {
                currentPri = currentPriority;
            }
            int candidatePri;
            if (candidatePriority == null) {
                candidatePri = Integer.MAX_VALUE;
            } else {
                candidatePri = candidatePriority;
            }
            // priority 更小更优
            if (candidatePri < currentPri) {
                return true;
            }
            if (candidatePri > currentPri) {
                return false;
            }
            // 同 priority 取 id 更小以稳定择优
            return candidateId < currentId;
        }
    }
}
