/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.transferin.mapper;

import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.zxplat.application.inventory.department.transferin.dto.TransferInDetailLineDto;
import com.zxplat.application.inventory.department.transferin.dto.TransferInListRowDto;
import com.zxplat.application.inventory.department.transferin.dto.ProductDefinitionItemDto;
import com.zxplat.application.inventory.department.transferin.response.*;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.zxplat.framework.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

/**
 * 科室调入单应用层装配：只读台账侧请求/响应与读模型之间的转换。
 *
 * <p>禁止承载业务流程判断；写路径由 {@code DEPT_TI_002} 在 AppService 拒绝，不在此装配领域写命令。</p>
 */
public interface InventoryDepartmentTransferInMapper {

    /**
     * 位置列表转 OptionDto。
     *
     * @param locations 位置列表，可为 null
     * @return 下拉项，永不为 null
     */
    List<OptionDto> toOptionDtoListFromLocations(List<Location> locations);

    /**
     * 医务人员列表转 OptionDto。
     *
     * @param practitioners 人员列表，可为 null
     * @return 下拉项，永不为 null
     */
    List<OptionDto> toOptionDtoListFromPractitioners(List<Practitioner> practitioners);

    /**
     * 调拨项目类型两档。
     *
     * @return 固定两项
     */
    List<OptionDto> toOptionDtoListInboundItemTableTypes();

    /**
     * 单据状态下拉。
     *
     * @return SupplyStatus 选项
     */
    List<OptionDto> toOptionDtoListSupplyStatuses();

    /**
     * 初始化响应装配（IF-DEPT-TI-01）。
     *
     * @param purposePharmacyOpts 目的科室药房
     * @param sourceWarehouseOpts  来源药库
     * @param itemTableTypeOpts   调拨项目类型
     * @param statusOpts     状态
     * @param pharmacistOpts 药师
     * @param keeperOpts     库管
     * @return 响应
     */
    LoadTransferInFormInitResponse toLoadTransferInFormInitResponse(
        List<OptionDto> purposePharmacyOpts,
        List<OptionDto> sourceWarehouseOpts,
        List<OptionDto> itemTableTypeOpts,
        List<OptionDto> statusOpts,
        List<OptionDto> pharmacistOpts,
        List<OptionDto> keeperOpts);

    /**
     * 科室调入列表聚合（IF-DEPT-TI-02）。
     *
     * @param busNoPageOrder 分页单号顺序
     * @param detailRows     明细
     * @return 聚合行
     */
    List<TransferInListRowDto> toTransferInListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows);

    /**
     * 科室调入分页响应装配。
     *
     * @param mergedTotal 去重总数
     * @param pageNo      1-based
     * @param pageSize    页大小
     * @param records     行
     * @return 响应
     */
    PageTransferInsResponse toPageTransferInsResponse(long mergedTotal, int pageNo, int pageSize, List<TransferInListRowDto> records);

    /**
     * 详情响应（IF-DEPT-TI-03）。
     *
     * @param lines        供应请求行
     * @param productHints 行 id → 目录提示
     * @return 响应
     */
    GetTransferInDetailResponse toGetTransferInDetailResponse(
        List<SupplyRequestModel> lines,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints);

    /**
     * 详情行 DTO（单行）。
     *
     * @param line 供应请求行
     * @return DTO；line 为 null 时返回 null
     */
    TransferInDetailLineDto toTransferInDetailLineDto(SupplyRequestModel line);

    /**
     * 选品分页响应装配（IF-DEPT-TI-11，pageNo 为 1-based）。
     *
     * @param page 库存分页
     * @return 响应
     */
    PageProductsForTransferInResponse toPageProductsForTransferInResponse(Page<InventoryItemModel> page);

    /**
     * 库存行 + 产品读模型转选品行（IF-DEPT-TI-11）。
     *
     * @param inv 库存行
     * @param pr  产品读模型，可 null
     * @return DTO
     */
    ProductDefinitionItemDto toProductDefinitionItemDtoFromInventory(InventoryItemModel inv, ProductDefinitionDisplayDto pr);
}
