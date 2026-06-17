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

package com.openhis.health.module.querysupport.inventory;

import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.workflow.inventoryitem.queries.WarehouseInventoryDetailPageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 库存明细筛选：按药品/耗材定义 QueryService 预解析 {@code item_id}，避免 inventory 领域 Criteria 跨包 JOIN（B.6.1）。
 */
@Component
@RequiredArgsConstructor
public class InventoryCatalogFilterSupport {

    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;

    /**
     * 将类型/医保等级筛选解析为定义主键列表并写入 {@link WarehouseInventoryDetailPageQuery}。
     *
     * @param query 药库库存明细筛选入参（原地写入 resolved* 字段）
     */
    public void enrichResolvedDefinitionIds(WarehouseInventoryDetailPageQuery query) {
        if (query == null) {
            return;
        }
        boolean hasMedicationCategory = query.getMedicationCategoryCodes() != null
            && !query.getMedicationCategoryCodes().isEmpty();
        boolean hasDeviceCategory = query.getDeviceCategoryCodes() != null
            && !query.getDeviceCategoryCodes().isEmpty();
        boolean hasChrgitmLv = query.getChrgitmLvList() != null && !query.getChrgitmLvList().isEmpty();
        if (hasMedicationCategory) {
            query.setResolvedMedicationDefinitionIds(
                this.medicationDefinitionQueryService.listIdsByCategoryCodesIn(query.getMedicationCategoryCodes()));
        }
        if (hasDeviceCategory) {
            query.setResolvedDeviceDefinitionIds(
                this.deviceDefinitionQueryService.listIdsByCategoryCodesIn(query.getDeviceCategoryCodes()));
        }
        if (hasChrgitmLv) {
            List<Long> medByLv = this.medicationDefinitionQueryService.listIdsByChrgitmLvIn(query.getChrgitmLvList());
            List<Long> devByLv = this.deviceDefinitionQueryService.listIdsByChrgitmLvIn(query.getChrgitmLvList());
            if (hasMedicationCategory) {
                query.setResolvedMedicationDefinitionIds(
                    this.intersectIds(query.getResolvedMedicationDefinitionIds(), medByLv));
            } else {
                query.setResolvedMedicationDefinitionIds(medByLv);
            }
            if (hasDeviceCategory) {
                query.setResolvedDeviceDefinitionIds(
                    this.intersectIds(query.getResolvedDeviceDefinitionIds(), devByLv));
            } else {
                query.setResolvedDeviceDefinitionIds(devByLv);
            }
        }
    }

    /**
     * 药品/耗材定义主键列表求交（医保等级与类目筛选叠加时使用）。
     *
     * @param left  已按类目收窄的 id 列表，可为 null
     * @param right 按医保等级解析的 id 列表，可为 null
     * @return 交集；任一侧为空时返回空列表（避免误返回「全量」）
     */
    private List<Long> intersectIds(List<Long> left, List<Long> right) {
        if (left == null || left.isEmpty()) {
            if (right == null || right.isEmpty()) {
                return new ArrayList<>();
            }
            return new ArrayList<>(right);
        }
        if (right == null || right.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> rightSet = new HashSet<>(right);
        List<Long> intersection = new ArrayList<>();
        for (Long id : left) {
            if (id != null && rightSet.contains(id)) {
                intersection.add(id);
            }
        }
        return intersection;
    }
}
