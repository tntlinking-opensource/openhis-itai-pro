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

import com.openhis.health.module.common.pagination.PageQueryBinder;
import com.openhis.health.application.inventory.warehouse.inventorydetail.dto.InventoryDetailCurrentExportRowDto;
import com.openhis.health.application.inventory.warehouse.inventorydetail.dto.InventoryDetailListRowDto;
import com.openhis.health.application.inventory.warehouse.inventorydetail.request.ExportInventoryDetailCurrentRequest;
import com.openhis.health.application.inventory.warehouse.inventorydetail.request.PageInventoryDetailCurrentRequest;
import com.openhis.health.application.inventory.warehouse.inventorydetail.request.SumInventoryDetailCurrentRequest;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InventoryWarehouseDetailExportConstants;
import com.openhis.health.domain.workflow.inventoryitem.queries.WarehouseInventoryDetailPageQuery;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.WarehouseInventoryDetailItemModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 库存明细 Mapper 实现：将 HTTP 请求投影为 {@link WarehouseInventoryDetailPageQuery}，并把现库装载委托给
 * {@link InventoryItemQueryService}（JPA {@code Specification} 下推，非 UNION SQL）。
 */
@Component
public class InventoryWarehouseInventoryDetailMapperImpl implements InventoryWarehouseInventoryDetailMapper {

    private final InventoryItemQueryService inventoryItemQueryService;

    /**
     * 构造注入库存项读侧服务。
     *
     * @param inventoryItemQueryService 库存项查询服务，不可为 null
     */
    public InventoryWarehouseInventoryDetailMapperImpl(InventoryItemQueryService inventoryItemQueryService) {
        this.inventoryItemQueryService = inventoryItemQueryService;
    }

    /**
     * 将现库分页请求映射为领域分页查询：复制分页与排序父类字段，写入筛选条件，并附着应用层已求交的库房主键列表。
     *
     * @param request               分页请求，调用方保证非 null
     * @param effectiveWarehouseIds 门控后的库房范围，允许为 null 或空（表示不按库房收窄，与产品策略一致）
     * @return 新构造的 {@link WarehouseInventoryDetailPageQuery}
     */
    @Override
    public WarehouseInventoryDetailPageQuery toWarehouseDetailPageQuery(
        PageInventoryDetailCurrentRequest request,
        List<Long> effectiveWarehouseIds
    ) {
        WarehouseInventoryDetailPageQuery q = new WarehouseInventoryDetailPageQuery();
        PageQueryBinder.bind(request, q);
        this.applyFilters(q, request.getMedicationCategoryCodes(), request.getDeviceCategoryCodes(),
            request.getSupplierIds(), request.getChrgitmLvList(),
            request.getZeroStockOnly(), request.getExpireWithinDays(), request.getInventoryStatusEnums());
        q.setEffectiveWarehouseIds(effectiveWarehouseIds);
        return q;
    }

    /**
     * 将现库汇总请求映射为领域查询：不含分页字段，仅筛选与关键字及门控库房范围。
     *
     * @param request               汇总请求，调用方保证非 null
     * @param effectiveWarehouseIds 门控后的库房范围，允许为 null 或空
     * @return 新构造的 {@link WarehouseInventoryDetailPageQuery}
     */
    @Override
    public WarehouseInventoryDetailPageQuery toWarehouseDetailPageQuery(
        SumInventoryDetailCurrentRequest request,
        List<Long> effectiveWarehouseIds
    ) {
        WarehouseInventoryDetailPageQuery q = new WarehouseInventoryDetailPageQuery();
        q.setKeyword(request.getKeyword());
        this.applyFilters(q, request.getMedicationCategoryCodes(), request.getDeviceCategoryCodes(),
            request.getSupplierIds(), request.getChrgitmLvList(),
            request.getZeroStockOnly(), request.getExpireWithinDays(), request.getInventoryStatusEnums());
        q.setEffectiveWarehouseIds(effectiveWarehouseIds);
        return q;
    }

    /**
     * 将现库导出请求映射为领域查询：与汇总请求同构，无分页语义。
     *
     * @param request               导出请求，调用方保证非 null
     * @param effectiveWarehouseIds 门控后的库房范围，允许为 null 或空
     * @return 新构造的 {@link WarehouseInventoryDetailPageQuery}
     */
    @Override
    public WarehouseInventoryDetailPageQuery toWarehouseDetailPageQuery(
        ExportInventoryDetailCurrentRequest request,
        List<Long> effectiveWarehouseIds
    ) {
        WarehouseInventoryDetailPageQuery q = new WarehouseInventoryDetailPageQuery();
        q.setKeyword(request.getKeyword());
        this.applyFilters(q, request.getMedicationCategoryCodes(), request.getDeviceCategoryCodes(),
            request.getSupplierIds(), request.getChrgitmLvList(),
            request.getZeroStockOnly(), request.getExpireWithinDays(), request.getInventoryStatusEnums());
        q.setEffectiveWarehouseIds(effectiveWarehouseIds);
        return q;
    }

    /**
     * 将筛选字段写入领域查询对象，避免在多个 toWarehouse 重载中重复赋值。
     *
     * @param q                       目标查询对象
     * @param medicationCategoryCodes 药品分类编码列表
     * @param deviceCategoryCodes     耗材分类编码列表
     * @param supplierIds             供应商主键列表
     * @param chrgitmLvList           医保等级列表（与读模型列语义一致）
     * @param zeroStockOnly           库存范围（true 数量等于0，false 数量大于0）
     * @param expireWithinDays        近效期天数
     * @param inventoryStatusEnums    供应状态持久化码列表
     */
    private void applyFilters(
        WarehouseInventoryDetailPageQuery q,
        List<String> medicationCategoryCodes,
        List<String> deviceCategoryCodes,
        List<Long> supplierIds,
        List<Integer> chrgitmLvList,
        Boolean zeroStockOnly,
        Integer expireWithinDays,
        List<String> inventoryStatusEnums
    ) {
        q.setMedicationCategoryCodes(medicationCategoryCodes);
        q.setDeviceCategoryCodes(deviceCategoryCodes);
        q.setSupplierIds(supplierIds);
        q.setChrgitmLvList(chrgitmLvList);
        q.setZeroStockOnly(zeroStockOnly);
        q.setExpireWithinDays(expireWithinDays);
        q.setInventoryStatusEnums(inventoryStatusEnums);
    }

    /**
     * 现库明细分页装载：委托 {@link InventoryItemQueryService#queryWarehouseInventoryDetailPage}。
     *
     * @param query    领域筛选条件，调用方保证非 null
     * @param pageable 分页与排序，调用方保证非 null
     * @return Spring Data 分页结果
     */
    @Override
    public Page<WarehouseInventoryDetailItemModel> selectCurrentDetailPage(
        WarehouseInventoryDetailPageQuery query,
        Pageable pageable
    ) {
        return this.inventoryItemQueryService.queryWarehouseInventoryDetailPage(query, pageable);
    }

    /**
     * 现库汇总候选行装载：至多 {@code maxRows} 行，无排序要求时使用 {@link Sort#unsorted()}。
     *
     * @param query   领域筛选条件，调用方保证非 null
     * @param maxRows 最大行数，须为正数
     * @return 行集合
     */
    @Override
    public List<WarehouseInventoryDetailItemModel> selectCurrentDetailForSum(
        WarehouseInventoryDetailPageQuery query,
        int maxRows
    ) {
        return this.inventoryItemQueryService.queryWarehouseInventoryDetailTopN(query, maxRows, Sort.unsorted());
    }

    /**
     * 现库导出候选行装载：行数上限为 {@link InventoryWarehouseDetailExportConstants#INVENTORY_DETAIL_SUM_MAX_ROWS}，
     * 与底部汇总（IF-03）一致，避免单次响应过大。
     *
     * @param query 领域筛选条件，调用方保证非 null
     * @return 行集合
     */
    @Override
    public List<WarehouseInventoryDetailItemModel> selectCurrentDetailForExport(WarehouseInventoryDetailPageQuery query) {
        int cap = InventoryWarehouseDetailExportConstants.INVENTORY_DETAIL_SUM_MAX_ROWS;
        return this.inventoryItemQueryService.queryWarehouseInventoryDetailTopN(query, cap, Sort.unsorted());
    }

    /**
     * 将领域行模型转为列表 DTO 骨架；定价、字典与金额列由应用层 {@code enrichRows} 回填。
     *
     * @param model 读侧行模型；若为 {@code null} 则返回 {@code null}
     * @return 列表行 DTO 或 null
     */
    @Override
    public InventoryDetailListRowDto toListRowDto(WarehouseInventoryDetailItemModel model) {
        if (model == null) {
            return null;
        }
        InventoryDetailListRowDto dto = new InventoryDetailListRowDto();
        dto.setId(model.getId());
        dto.setItemTable(model.getItemTable());
        dto.setItemId(model.getItemId());
        dto.setName(model.getName());
        dto.setPyStr(model.getPyStr());
        dto.setWbStr(model.getWbStr());
        dto.setLotNumber(model.getLotNumber());
        dto.setQuantity(model.getQuantity());
        dto.setUnitCode(model.getUnitCode());
        dto.setWarehouseId(model.getWarehouseId());
        dto.setSupplierId(model.getSupplierId());
        dto.setInventoryStatusEnum(model.getInventoryStatusEnum());
        if (model.getExpirationDate() != null) {
            dto.setExpirationDate(GeneralSharedConstants.DATE_TIME_FORMATTER.format(model.getExpirationDate()));
        }
        if (model.getProductionDate() != null) {
            dto.setProductionDate(GeneralSharedConstants.DATE_TIME_FORMATTER.format(model.getProductionDate()));
        }
        return dto;
    }

    /**
     * 将已装配列表行投影为导出行 DTO（列映射见 {@link InventoryDetailCurrentExportRowDto}）。
     *
     * @param row 列表行，调用方保证非 null
     * @return 导出行
     */
    @Override
    public InventoryDetailCurrentExportRowDto toCurrentExportRowDto(InventoryDetailListRowDto row) {
        return new InventoryDetailCurrentExportRowDto()
            .setName(row.getName())
            .setLotNumber(row.getLotNumber())
            .setQuantityText(StringUtils.defaultString(row.getDisplayQuantityText()))
            .setWarehouseName(row.getWarehouseName())
            .setSupplierName(row.getSupplierName())
            .setExpirationDate(row.getExpirationDate())
            .setInventoryStatusText(row.getInventoryStatusText())
            .setLineCostTotal(row.getLineCostTotal())
            .setLinePriceTotal(row.getLinePriceTotal());
    }
}
