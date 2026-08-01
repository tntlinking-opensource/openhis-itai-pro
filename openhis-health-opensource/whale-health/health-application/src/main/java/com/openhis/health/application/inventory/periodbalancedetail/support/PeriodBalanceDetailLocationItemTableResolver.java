/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.periodbalancedetail.support;

import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.document.inventoryitemsnapshot.service.DocInventoryItemSnapshotQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 库房期初期末明细：按库位现库/期初快照中的 {@code item_table} 解析物品主表（选库房即可，无需前端传表名）。
 */
@Component
@RequiredArgsConstructor
public class PeriodBalanceDetailLocationItemTableResolver {

    private final InventoryItemQueryService inventoryItemQueryService;

    private final DocInventoryItemSnapshotQueryService docInventoryItemSnapshotQueryService;

    /**
     * 解析库位对应物品主表：先查现库 DISTINCT {@code item_table}，无数据再查期初备份快照；取首个即可。
     *
     * @param location         库位
     * @param tenantId         租户
     * @param startBackupBusNo 期初备份单号（快照回落用）
     * @return 药品或耗材定义表名
     */
    public String resolveItemTable(Location location, Long tenantId, String startBackupBusNo) {
        if (location == null || location.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.INV_SET_202, "请选择库房");
        }
        Long locationId = location.getId();
        List<String> itemTables = this.filterSupportedItemTables(
            this.inventoryItemQueryService.listDistinctItemTablesByLocationId(locationId));
        if (itemTables.isEmpty() && StringUtils.isNotEmpty(startBackupBusNo)) {
            itemTables = this.filterSupportedItemTables(
                this.docInventoryItemSnapshotQueryService.findDistinctItemTablesByTenantBusNoAndWarehouse(
                    tenantId, startBackupBusNo.trim(), locationId));
        }
        if (itemTables.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.INV_SET_204, "该库房没有库存数据");
        }
        return itemTables.get(0);
    }

    /**
     * 仅保留日结支持的药品/耗材定义表，去重并保持顺序。
     *
     * @param raw 原始 item_table 列表
     * @return 过滤后的表名列表
     */
    private List<String> filterSupportedItemTables(List<String> raw) {
        Set<String> ordered = new LinkedHashSet<>();
        if (raw == null) {
            return new ArrayList<>();
        }
        for (String itemTable : raw) {
            if (StringUtils.isEmpty(itemTable)) {
                continue;
            }
            String trimmed = itemTable.trim();
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(trimmed)
                || TableNameConstants.ADM_DEVICE_DEFINITION.equals(trimmed)) {
                ordered.add(trimmed);
            }
        }
        return new ArrayList<>(ordered);
    }
}
