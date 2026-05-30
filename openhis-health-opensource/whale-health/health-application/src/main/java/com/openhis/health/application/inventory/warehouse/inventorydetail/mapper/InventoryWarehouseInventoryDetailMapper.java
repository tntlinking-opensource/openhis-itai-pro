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

package com.openhis.health.application.inventory.warehouse.inventorydetail.mapper;

import com.openhis.health.application.inventory.warehouse.inventorydetail.dto.InventoryDetailCurrentExportRowDto;
import com.openhis.health.application.inventory.warehouse.inventorydetail.dto.InventoryDetailListRowDto;
import com.openhis.health.application.inventory.warehouse.inventorydetail.request.ExportInventoryDetailCurrentRequest;
import com.openhis.health.application.inventory.warehouse.inventorydetail.request.PageInventoryDetailCurrentRequest;
import com.openhis.health.application.inventory.warehouse.inventorydetail.request.SumInventoryDetailCurrentRequest;
import com.openhis.health.domain.workflow.inventoryitem.queries.WarehouseInventoryDetailPageQuery;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.WarehouseInventoryDetailItemModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 药库库存明细：HTTP 契约与领域分页查询、导出行 DTO 之间的映射。
 *
 * <p>禁止承载业务流程判断；现库数据装载委托 {@link com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService}
 * （JPA {@code Specification} 下推，非 UNION SQL）。</p>
 */
public interface InventoryWarehouseInventoryDetailMapper {

    /**
     * 将分页请求与门控后的库房范围装配为领域分页查询。
     *
     * @param request               HTTP 入参
     * @param effectiveWarehouseIds 应用层 Gate 解析后的库房主键范围，允许为空
     * @return 领域查询对象
     */
    WarehouseInventoryDetailPageQuery toWarehouseDetailPageQuery(
        PageInventoryDetailCurrentRequest request,
        List<Long> effectiveWarehouseIds
    );

    /**
     * 将汇总请求装配为领域查询（不含分页语义）。
     *
     * @param request               HTTP 入参
     * @param effectiveWarehouseIds 门控后的库房范围
     * @return 领域查询对象
     */
    WarehouseInventoryDetailPageQuery toWarehouseDetailPageQuery(
        SumInventoryDetailCurrentRequest request,
        List<Long> effectiveWarehouseIds
    );

    /**
     * 将导出请求装配为领域查询。
     *
     * @param request               HTTP 入参
     * @param effectiveWarehouseIds 门控后的库房范围
     * @return 领域查询对象
     */
    WarehouseInventoryDetailPageQuery toWarehouseDetailPageQuery(
        ExportInventoryDetailCurrentRequest request,
        List<Long> effectiveWarehouseIds
    );

    /**
     * 现库明细分页装载（IF-INV-DTL-02 Pre）。
     *
     * @param query    领域筛选
     * @param pageable 分页与排序
     * @return 分页库存行
     */
    Page<WarehouseInventoryDetailItemModel> selectCurrentDetailPage(
        WarehouseInventoryDetailPageQuery query,
        Pageable pageable
    );

    /**
     * 现库汇总候选行装载（IF-INV-DTL-03 Pre）。
     *
     * @param query   领域筛选
     * @param maxRows 最大行数
     * @return 行集合
     */
    List<WarehouseInventoryDetailItemModel> selectCurrentDetailForSum(WarehouseInventoryDetailPageQuery query, int maxRows);

    /**
     * 现库导出候选行装载（IF-INV-DTL-06 Pre）。
     *
     * @param query 领域筛选
     * @return 行集合（上限由应用层控制）
     */
    List<WarehouseInventoryDetailItemModel> selectCurrentDetailForExport(WarehouseInventoryDetailPageQuery query);

    /**
     * 领域行 → 列表 DTO 骨架字段（定价与字典列由应用层后续回填）。
     *
     * @param model 领域行，调用方保证非空
     * @return DTO
     */
    InventoryDetailListRowDto toListRowDto(WarehouseInventoryDetailItemModel model);

    /**
     * 已装配列表行 → 现库导出行（IF-INV-DTL-06 Asm）。
     *
     * @param row 列表行，调用方保证非 null
     * @return 导出行 DTO
     */
    InventoryDetailCurrentExportRowDto toCurrentExportRowDto(InventoryDetailListRowDto row);
}
