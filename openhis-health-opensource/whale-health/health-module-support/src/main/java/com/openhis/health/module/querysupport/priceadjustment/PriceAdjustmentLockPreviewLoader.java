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

package com.openhis.health.module.querysupport.priceadjustment;

import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentDocumentQueryModel;
import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentLineQueryModel;
import com.openhis.health.domain.custom.priceadjustment.service.PriceAdjustmentQueryService;
import com.openhis.health.domain.custom.priceadjustment.support.PriceAdjustmentItemTableSupport;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.module.querysupport.priceadjustment.dto.PriceAdjustmentLockPreviewRowDto;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 物流调价提交锁预览：库存候选行 + 锁定冲突读侧组装（BR-PRC-LOCK-008）。
 */
@Component
@RequiredArgsConstructor
public class PriceAdjustmentLockPreviewLoader {

    private final PriceAdjustmentQueryService priceAdjustmentQueryService;
    private final InventoryItemQueryService inventoryItemQueryService;

    /**
     * 加载将参与 {@code inventory_lock} 的库存行及冲突单号。
     *
     * @param busNo 调价单号
     * @return 预览行列表
     */
    public List<PriceAdjustmentLockPreviewRowDto> loadByBusNo(String busNo) {
        List<PriceAdjustmentLockPreviewRowDto> rows = new ArrayList<>();
        // Pre：空单号不加载预览行。
        if (StringUtils.isBlank(busNo)) {
            return rows;
        }
        PriceAdjustmentDocumentQueryModel document =
            this.priceAdjustmentQueryService.getDocumentByBusNo(busNo.trim());
        if (document.getLines() == null) {
            return rows;
        }
        // Pre：按物品表聚合物流项目主键，避免重复查库存。
        Map<String, LinkedHashSet<Long>> itemIdsByTable = new HashMap<>();
        for (PriceAdjustmentLineQueryModel line : document.getLines()) {
            if (line == null || !PriceAdjustmentItemTableSupport.isLogisticsItemTable(line.getItemTable())) {
                continue;
            }
            String table = line.getItemTable().trim();
            LinkedHashSet<Long> ids = itemIdsByTable.computeIfAbsent(table, ignored -> new LinkedHashSet<>());
            ids.add(line.getItemId());
        }
        String currentBusNo = busNo.trim();
        for (Map.Entry<String, LinkedHashSet<Long>> entry : itemIdsByTable.entrySet()) {
            // Pre：加载全院 ACTIVE 正数量库存候选行（含已锁行）。
            List<InventoryCatalogAvailabilityRowModel> inventoryRows =
                this.inventoryItemQueryService.findActiveInventoryRowsForPriceAdjustmentLockPreview(
                    entry.getKey(),
                    entry.getValue()
                );
            for (InventoryCatalogAvailabilityRowModel inventoryRow : inventoryRows) {
                PriceAdjustmentLockPreviewRowDto dto = new PriceAdjustmentLockPreviewRowDto();
                dto.setInventoryId(inventoryRow.getId());
                dto.setItemTable(inventoryRow.getItemTable());
                dto.setItemId(inventoryRow.getItemId());
                dto.setLocationId(inventoryRow.getLocationId());
                dto.setLotNumber(inventoryRow.getLotNumber());
                dto.setQuantity(inventoryRow.getQuantity());
                String lockedBusNo = this.inventoryItemQueryService.findActiveLockBusNo(inventoryRow.getId());
                dto.setLockedBusNo(lockedBusNo);
                boolean lockedByOther = false;
                // Gate：其它调价单已锁定时标记冲突（本单已锁不算冲突）。
                if (StringUtils.isNotBlank(lockedBusNo) && !currentBusNo.equals(lockedBusNo.trim())) {
                    lockedByOther = true;
                }
                dto.setLockedByOtherBusNo(lockedByOther);
                rows.add(dto);
            }
        }
        return rows;
    }
}
