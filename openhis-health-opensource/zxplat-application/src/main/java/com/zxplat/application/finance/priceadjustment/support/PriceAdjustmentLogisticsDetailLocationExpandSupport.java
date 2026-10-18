/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustment.support;

import com.zxplat.domain.custom.priceadjustment.command.SavePriceAdjustmentDraftCommand;
import com.zxplat.domain.custom.priceadjustment.support.PriceAdjustmentItemTableSupport;
import com.zxplat.domain.workflow.inventoryitem.queries.model.AvailableInventoryModel;
import com.zxplat.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物流调价草稿子表按库房+批号展开（落库用；调价单回显仍按批号合并）。
 *
 * <p>不改变盈亏公式与审批写价；仅在保存前把全院批号行拆成有 {@code locationId} 的多行，
 * 无库存命中时保留原行且 loc 空。</p>
 */
@Component
@RequiredArgsConstructor
public class PriceAdjustmentLogisticsDetailLocationExpandSupport {

    private final InventoryItemQueryService inventoryItemQueryService;

    /**
     * 将命令中物流行的批号明细按当前可用库存拆成库房+批号行。
     *
     * <p>调用时机：权威调前价回填之后、领域保存之前。诊疗行跳过。</p>
     *
     * @param command 草稿命令；null 或无线时直接返回
     */
    public void expandLogisticsDetailsByLocation(SavePriceAdjustmentDraftCommand command) {
        // 空命令：无明细可拆
        if (command == null || command.getLines() == null || command.getLines().isEmpty()) {
            return;
        }
        for (SavePriceAdjustmentDraftCommand.Line line : command.getLines()) {
            // 空行跳过，避免 NPE
            if (line == null) {
                continue;
            }
            // 诊疗：子表一对一，不写 loc
            if (!PriceAdjustmentItemTableSupport.isLogisticsItemTable(line.getItemTable())) {
                continue;
            }
            // 缺项目主键：无法查库存，保持请求明细原样交领域校验
            if (line.getItemId() == null) {
                continue;
            }
            List<SavePriceAdjustmentDraftCommand.DetailLine> sourceDetails = line.getDetails();
            // 无批号明细：领域层再拦「物流须有 details」
            if (sourceDetails == null || sourceDetails.isEmpty()) {
                continue;
            }
            List<AvailableInventoryModel> inventories =
                this.inventoryItemQueryService.findAvailableInventories(
                    line.getItemTable().trim(),
                    line.getItemId(),
                    null
                );
            Map<String, Map<Long, BigDecimal>> qtyByLotAndLocation =
                this.groupQuantityByLotAndLocation(inventories);
            List<SavePriceAdjustmentDraftCommand.DetailLine> expanded = new ArrayList<>();
            for (SavePriceAdjustmentDraftCommand.DetailLine source : sourceDetails) {
                // 空明细跳过
                if (source == null) {
                    continue;
                }
                String lot = trimLot(source.getLotNumber());
                // 批号非法：原样保留（领域层再拦）
                if (lot == null) {
                    expanded.add(this.copyDetail(source, source.getLocationId(), source.getQuantity()));
                    continue;
                }
                Map<Long, BigDecimal> byLocation = qtyByLotAndLocation.get(lot);
                // 当前无该批可用库存：保留一行且 loc 空（未入库/期初等）
                if (byLocation == null || byLocation.isEmpty()) {
                    expanded.add(this.copyDetail(source, null, source.getQuantity()));
                    continue;
                }
                int addedForLot = 0;
                for (Map.Entry<Long, BigDecimal> locEntry : byLocation.entrySet()) {
                    Long locationId = locEntry.getKey();
                    BigDecimal qty = locEntry.getValue();
                    // 数量无效：跳过该库位片
                    if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    expanded.add(this.copyDetail(source, locationId, qty));
                    addedForLot++;
                }
                // 有库位键但数量全无效：回落 loc 空，避免子表被掏空导致无法提交
                if (addedForLot == 0) {
                    expanded.add(this.copyDetail(source, null, source.getQuantity()));
                }
            }
            line.setDetails(expanded);
        }
    }

    /**
     * 全院可用库存按批号再按库位聚合数量。
     *
     * @param inventories 可用库存简表
     * @return 批号 →（库位 → 数量）；无库位的库存行不入图（由调用方回落 loc 空）
     */
    private Map<String, Map<Long, BigDecimal>> groupQuantityByLotAndLocation(
        List<AvailableInventoryModel> inventories
    ) {
        Map<String, Map<Long, BigDecimal>> result = new LinkedHashMap<>();
        // 无库存列表：调用方按批号回落 loc 空
        if (inventories == null) {
            return result;
        }
        for (AvailableInventoryModel inventory : inventories) {
            // 空行跳过
            if (inventory == null) {
                continue;
            }
            String lot = trimLot(inventory.getLotNumber());
            // 无批号无法与调价子表对齐
            if (lot == null) {
                continue;
            }
            Long locationId = inventory.getLocationId();
            // 库存行缺库位：无法按库拆，跳过（该批若仅有此类行则外层回落 loc 空）
            if (locationId == null) {
                continue;
            }
            BigDecimal qty = inventory.getQuantity();
            // 数量空：不参与聚合
            if (qty == null) {
                continue;
            }
            Map<Long, BigDecimal> byLocation = result.get(lot);
            // 该批号首次出现：新建库位桶
            if (byLocation == null) {
                byLocation = new LinkedHashMap<>();
                result.put(lot, byLocation);
            }
            BigDecimal current = byLocation.get(locationId);
            // 同库同批累加（多库存行合并）
            if (current == null) {
                byLocation.put(locationId, qty);
            } else {
                byLocation.put(locationId, current.add(qty));
            }
        }
        return result;
    }

    /**
     * 复制调前价字段并写入库位与数量。
     *
     * @param source     源明细（调前价来源）
     * @param locationId 库位；可空
     * @param quantity   数量
     * @return 新明细行
     */
    private SavePriceAdjustmentDraftCommand.DetailLine copyDetail(
        SavePriceAdjustmentDraftCommand.DetailLine source,
        Long locationId,
        BigDecimal quantity
    ) {
        SavePriceAdjustmentDraftCommand.DetailLine target = new SavePriceAdjustmentDraftCommand.DetailLine();
        target.setLocationId(locationId);
        target.setLotNumber(source.getLotNumber());
        target.setQuantity(quantity);
        target.setOldPurchaseAmount(source.getOldPurchaseAmount());
        target.setOldSellingAmount(source.getOldSellingAmount());
        return target;
    }

    /**
     * 批号 trim；空串视为无效。
     *
     * @param lotNumber 批号
     * @return 非空批号或 null
     */
    private String trimLot(String lotNumber) {
        // 空批号：无法拆库
        if (StringUtils.isEmpty(lotNumber)) {
            return null;
        }
        String trimmed = lotNumber.trim();
        // 全空白：与空串同义
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }
}
