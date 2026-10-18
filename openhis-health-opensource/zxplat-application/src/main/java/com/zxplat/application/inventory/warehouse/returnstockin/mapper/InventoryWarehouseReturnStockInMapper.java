/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returnstockin.mapper;

import com.zxplat.application.inventory.warehouse.returnstockin.dto.ReturnStockInListRowDto;
import com.zxplat.application.inventory.warehouse.returnstockin.dto.SupplierOptionDto;
import com.zxplat.application.inventory.warehouse.returnstockin.dto.WarehouseOptionDto;
import com.zxplat.application.inventory.warehouse.returnstockin.response.GetReturnStockInDetailResponse;
import com.zxplat.application.inventory.warehouse.returnstockin.response.LoadReturnStockInFormInitResponse;
import com.zxplat.application.inventory.warehouse.returnstockin.response.PageReturnStockInsResponse;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.zxplat.domain.share.enums.SupplyType;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import java.util.List;
import java.util.Map;

/**
 * 退库入库单（{@link SupplyType#CABINET_RETURN_STOCK_IN}）应用层<strong>读路径</strong>装配契约。
 *
 * <p>本包 HTTP 契约保留历史包名 {@code returnstockin}；业务单据中文名为<strong>退库入库单</strong>（系统壳单，只读）。
 * 仅装配页初始化、聚合列表与详情；写路径 HTTP 仍由 AppService 以 {@code WHS_RSI_006} 拒绝，本 Mapper 不再提供写/选品/选单装配。</p>
 */
public interface InventoryWarehouseReturnStockInMapper {

    /**
     * 将启用位置列表映射为下拉选项 DTO。
     *
     * @param locations 启用库位列表
     * @return 位置下拉选项
     */
    List<WarehouseOptionDto> toWarehouseOptionDtoList(List<Location> locations);

    /**
     * 将启用供应商列表映射为下拉选项 DTO。
     *
     * @param suppliers 供应商列表项
     * @return 供应商下拉选项
     */
    List<SupplierOptionDto> toSupplierOptionDtoList(List<SupplierListItemQueryModel> suppliers);

    /**
     * 装配退库入库单页初始化响应。
     *
     * @param warehouseOptions  药库选项
     * @param departmentOptions 科室选项
     * @param pharmacyOptions   药房选项
     * @param supplierOptions   供应商选项
     * @return 页初始化响应
     */
    LoadReturnStockInFormInitResponse toLoadReturnStockInFormInitResponse(
        List<WarehouseOptionDto> warehouseOptions,
        List<WarehouseOptionDto> departmentOptions,
        List<WarehouseOptionDto> pharmacyOptions,
        List<SupplierOptionDto> supplierOptions);

    /**
     * 按 busNo 顺序聚合退库入库供应请求明细为列表行 DTO。
     *
     * @param busNoPageOrder 分页单号顺序
     * @param detailRows     同页全部明细
     * @return 聚合列表行
     */
    List<ReturnStockInListRowDto> toReturnStockInListAggregated(
        List<String> busNoPageOrder,
        List<SupplyRequestModel> detailRows);

    /**
     * 用聚合列表行构建退库入库单分页响应（AppService 列表唯一出参装配入口）。
     *
     * @param mergedTotal 去重后总单数
     * @param pageNo      页码
     * @param pageSize    页大小
     * @param records     列表行
     * @return 分页响应
     */
    PageReturnStockInsResponse toPageReturnStockInsAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<ReturnStockInListRowDto> records);

    /**
     * 将同号供应请求明细与产品读模型装配为退库入库单详情响应。
     *
     * @param lines         供应请求明细行
     * @param productByKey  产品定义读模型映射
     * @param productHints  行 id → 目录编辑提示（unitBands）
     * @return 详情响应
     */
    GetReturnStockInDetailResponse toGetReturnStockInDetailResponse(
        List<SupplyRequestModel> lines,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, com.zxplat.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto> productHints);
}
