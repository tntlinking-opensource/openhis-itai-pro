/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustment.support;

import com.zxplat.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.zxplat.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.zxplat.domain.workflow.inventoryitem.queries.model.AvailableInventoryModel;
import com.zxplat.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.zxplat.application.finance.priceadjustment.support.dto.LogisticsDetailSnapshotRowDto;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物流调价子表快照组装：全院可用库存批号 × 定价读侧调前价（IF-PRC-05 / BR-PRC-SNAP-002）。
 */
@Component
@RequiredArgsConstructor
public class PriceAdjustmentLogisticsSnapshotAssembler {

    private final InventoryItemQueryService inventoryItemQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /**
     * 按项目列表组装批号明细草稿结构。
     *
     * @param itemTable 物品表名（药品/耗材）
     * @param itemIds   项目主键列表
     * @return 可编辑快照行（按项目+批号展开）
     */
    public List<LogisticsDetailSnapshotRowDto> assemble(String itemTable, List<Long> itemIds) {
        List<LogisticsDetailSnapshotRowDto> rows = new ArrayList<>();
        // Pre：表名或项目列表非法时返回空快照。
        if (StringUtils.isEmpty(itemTable) || itemIds == null || itemIds.isEmpty()) {
            return rows;
        }
        String table = itemTable.trim();
        for (Long itemId : itemIds) {
            if (itemId == null) {
                continue;
            }
            // Pre：全院可用库存按批号聚合数量。
            List<AvailableInventoryModel> inventories =
                this.inventoryItemQueryService.findAvailableInventories(table, itemId, null);
            Map<String, BigDecimal> quantityByLot = this.aggregateQuantityByLot(inventories);
            // Asm：批号行填充调前进价/售价读侧。
            for (Map.Entry<String, BigDecimal> lotEntry : quantityByLot.entrySet()) {
                LogisticsDetailSnapshotRowDto row = new LogisticsDetailSnapshotRowDto();
                row.setItemId(itemId);
                row.setLotNumber(lotEntry.getKey());
                row.setQuantity(lotEntry.getValue());
                row.setOldPurchaseAmount(
                    this.chargeItemDefinitionQueryService.resolveFirstLotNumberCostPriceValue(
                        table, itemId, lotEntry.getKey()));
                row.setOldSellingAmount(this.resolveLotSale(table, itemId, lotEntry.getKey()));
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * 可用库存按批号聚合数量（trim 批号，同批累加）。
     *
     * @param inventories 可用库存读模型
     * @return 批号 → 合计数量
     */
    private Map<String, BigDecimal> aggregateQuantityByLot(List<AvailableInventoryModel> inventories) {
        Map<String, BigDecimal> quantityByLot = new LinkedHashMap<>();
        for (AvailableInventoryModel inventory : inventories) {
            if (inventory == null) {
                continue;
            }
            String lot = inventory.getLotNumber();
            if (StringUtils.isEmpty(lot)) {
                continue;
            }
            BigDecimal qty = inventory.getQuantity();
            if (qty == null) {
                continue;
            }
            BigDecimal current = quantityByLot.get(lot.trim());
            if (current == null) {
                quantityByLot.put(lot.trim(), qty);
            } else {
                quantityByLot.put(lot.trim(), current.add(qty));
            }
        }
        return quantityByLot;
    }

    /**
     * 解析批号级售价（LOT_NUMBER_PRICE 首条非空 priceValue）。
     *
     * @param itemTable 实例表
     * @param itemId    实例 id
     * @param lotNumber 批号
     * @return 旧售价；无则 null
     */
    private BigDecimal resolveLotSale(String itemTable, Long itemId, String lotNumber) {
        ChargeItemDefinitionQueryCommand cmd = new ChargeItemDefinitionQueryCommand();
        cmd.setInstanceTable(itemTable);
        cmd.setInstanceId(itemId);
        cmd.setLotNo(lotNumber);
        List<com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition> lines =
            this.chargeItemDefinitionQueryService.findLotNumberPriceByInstanceIdAndLot(cmd);
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        for (com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition line : lines) {
            if (line != null && line.getPriceValue() != null) {
                return line.getPriceValue();
            }
        }
        return null;
    }
}
