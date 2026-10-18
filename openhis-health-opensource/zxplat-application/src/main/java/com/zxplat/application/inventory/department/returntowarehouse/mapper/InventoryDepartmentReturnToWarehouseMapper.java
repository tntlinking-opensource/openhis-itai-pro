/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.returntowarehouse.mapper;

import com.zxplat.application.inventory.department.returntowarehouse.dto.LinkableStockInListRowDto;
import com.zxplat.application.inventory.department.returntowarehouse.dto.ProductDefinitionItemDto;
import com.zxplat.application.inventory.department.returntowarehouse.dto.ReturnToWarehouseListRowDto;
import com.zxplat.application.inventory.department.returntowarehouse.dto.SupplierOptionDto;
import com.zxplat.application.inventory.department.returntowarehouse.dto.WarehouseOptionDto;
import com.zxplat.application.inventory.department.returntowarehouse.request.*;
import com.zxplat.application.inventory.department.returntowarehouse.response.*;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.zxplat.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand;
import com.zxplat.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateReturnCommand;
import com.zxplat.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.zxplat.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.zxplat.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.zxplat.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel;
import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.zxplat.framework.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 科室退药库（退库）单应用层装配契约：HTTP 请求/响应与领域命令、读模型行之间的转换（DM §6.1.5 · type {@link SupplyType#DEPT_RETURN}）。
 *
 * <p>职责边界：仅做字段搬运与枚举 code 归一；不承载库存、审批状态机等业务流程判断。</p>
 */
public interface InventoryDepartmentReturnToWarehouseMapper {

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
     * 将启用医务人员列表映射为下拉选项。
     *
     * @param practitioners 医务人员列表
     * @return 人员下拉选项
     */
    List<OptionDto> toOptionDtoListFromPractitioners(List<Practitioner> practitioners);

    /**
     * 装配科室退药库页初始化响应。
     *
     * @param departmentPharmacyLocationOptions 源科室药房位置选项
     * @param warehouseLocationOptions          目的药库选项
     * @param supplierOptions                   供应商选项
     * @param pharmacistOptions                 药师选项
     * @param warehouseKeeperOptions            库管选项
     * @return 页初始化响应
     */
    LoadReturnToWarehouseFormInitResponse toLoadReturnToWarehouseFormInitResponse(
        List<WarehouseOptionDto> departmentPharmacyLocationOptions,
        List<WarehouseOptionDto> warehouseLocationOptions,
        List<SupplierOptionDto> supplierOptions,
        List<OptionDto> pharmacistOptions,
        List<OptionDto> warehouseKeeperOptions);

    /**
     * 将库房单据读模型分页装配为退药库列表响应。
     *
     * @param page 库房单据读模型分页
     * @return 列表分页响应
     */
    PageReturnToWarehousesResponse toPageReturnToWarehousesResponse(Page<WarehouseDocumentDetailQueryModel> page);

    /**
     * 按 busNo 顺序聚合科室退库供应请求明细为列表行 DTO。
     *
     * @param busNoPageOrder 分页单号顺序
     * @param detailRows     同页全部明细
     * @return 聚合列表行
     */
    List<ReturnToWarehouseListRowDto> toReturnToWarehouseListAggregated(
        List<String> busNoPageOrder,
        List<SupplyRequestModel> detailRows);

    /**
     * 用聚合列表行构建科室退库单分页响应。
     *
     * @param mergedTotal 去重后总单数
     * @param pageNo      页码
     * @param pageSize    页大小
     * @param records     列表行
     * @return 分页响应
     */
    PageReturnToWarehousesResponse toPageReturnToWarehousesAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<ReturnToWarehouseListRowDto> records);

    /**
     * 用可关联入库单聚合行构建分页响应。
     *
     * @param mergedTotal 去重后总单数
     * @param pageNo      页码
     * @param pageSize    页大小
     * @param records     列表行
     * @return 分页响应
     */
    PageLinkableReturnSourceDocumentsResponse toPageLinkableStockInsAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<LinkableStockInListRowDto> records);

    /**
     * 将已同意入库单与可退余量映射为退库预填响应。
     *
     * @param stockInLines     入库明细
     * @param returnableByKey  行键 → 可退余量
     * @param returnedByKey    行键 → 已退占用量
     * @param availableByKey   行键 → 源科室药房可用量
     * @param productByKey     产品读模型
     * @return 预填响应
     */
    GetLinkableStockInDetailResponse toGetLinkableStockInDetailResponse(
        List<SupplyRequestModel> stockInLines,
        Map<String, BigDecimal> returnableByKey,
        Map<String, BigDecimal> returnedByKey,
        Map<String, BigDecimal> availableByKey,
        Map<String, ProductDefinitionDisplayDto> productByKey);

    /**
     * 将同号供应请求明细、产品读模型与目录编辑提示装配为退药库详情响应。
     *
     * @param lines        供应请求明细行
     * @param productByKey 产品定义读模型映射（复合键）
     * @param productHints 行 id → 目录编辑提示
     * @return 详情响应
     */
    GetReturnToWarehouseDetailResponse toGetReturnToWarehouseDetailResponse(
        List<SupplyRequestModel> lines,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints);

    /**
     * 将新建退药库请求装配为领域 CreateReturnCommand。
     *
     * @param request               新建请求
     * @param operatorId            操作人
     * @param applicantId           制单人员
     * @param practitionerId        经手人
     * @param originalBusNoSnapshot 关联科室入库单号快照
     * @return 领域写命令
     */
    CreateReturnCommand toCreateReturnToWarehouseCommand(
        CreateReturnToWarehouseRequest request,
        Long operatorId,
        Long applicantId,
        Long practitionerId,
        String originalBusNoSnapshot);

    /**
     * 将修订退药库请求装配为领域 CreateReturnCommand（同号全量对齐）。
     *
     * @param request               修订请求
     * @param operatorId            操作人
     * @param applicantId           制单人员
     * @param practitionerId        经手人
     * @param originalBusNoSnapshot 关联科室入库单号快照
     * @return 领域写命令
     */
    CreateReturnCommand toCreateReturnToWarehouseCommandForUpdate(
        UpdateReturnToWarehouseRequest request,
        Long operatorId,
        Long applicantId,
        Long practitionerId,
        String originalBusNoSnapshot);

    /**
     * 装配新建退药库成功响应。
     *
     * @param busNo 新建业务单号
     * @return 新建响应
     */
    CreateReturnToWarehouseResponse toCreateReturnToWarehouseResponse(String busNo);

    /**
     * 装配修订退药库成功响应。
     *
     * @param busNo 业务单号
     * @return 修订响应
     */
    UpdateReturnToWarehouseResponse toUpdateReturnToWarehouseResponse(String busNo);

    /**
     * 将提交审批请求装配为领域 SubmitApprovalCommand。
     *
     * @param request     提交请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    SubmitApprovalCommand toSubmitApprovalCommand(
        SubmitReturnToWarehouseApprovalRequest request,
        Long operatorId,
        Long applicantId);

    /**
     * 装配提交审批成功响应。
     *
     * @return 提交响应
     */
    SubmitReturnToWarehouseApprovalResponse toSubmitReturnToWarehouseApprovalResponse();

    /**
     * 将撤回审批请求装配为领域 WithdrawApprovalCommand。
     *
     * @param request    撤回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    WithdrawApprovalCommand toWithdrawApprovalCommand(
        WithdrawReturnToWarehouseApprovalRequest request,
        Long operatorId);

    /**
     * 装配撤回审批成功响应。
     *
     * @return 撤回响应
     */
    WithdrawReturnToWarehouseApprovalResponse toWithdrawReturnToWarehouseApprovalResponse();

    /**
     * 将同意请求装配为领域 AgreeRequestCommand。
     *
     * @param request    同意请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    AgreeRequestCommand toAgreeRequestCommand(AgreeReturnToWarehouseRequest request, Long operatorId);

    /**
     * 将已同意明细行装配为退药库发放命令。
     *
     * @param lines      已同意的供应请求明细行
     * @param operatorId 操作人
     * @return 发放命令
     */
    CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommandForReturnToWarehouse(
        List<SupplyRequestModel> lines,
        Long operatorId);

    /**
     * 将已同意科室退库明细装配为退库入库壳单的 {@link CreateReturnCommand}。
     *
     * @param agreedReturnLines  已同意科室退库明细
     * @param deptReturnBusNo 科室退库单号（写入壳单 {@code original_bus_no}）
     * @param operatorId         操作人
     * @param applicantId        制单人
     * @return 领域写命令（含 bootstrap 终态已同意）
     */
    CreateReturnCommand toCreateReturnCommandForShellCounterpartyReturnInbound(
        List<SupplyRequestModel> agreedReturnLines,
        String deptReturnBusNo,
        Long operatorId,
        Long applicantId);

    /**
     * 装配同意科室退库成功响应。
     *
     * @param deptReturnBusNo       科室退库单号
     * @param statusEnum                同意后的状态码
     * @param generatedReturnInboundBusNo 系统生成的退库入库壳单号
     * @return 同意响应
     */
    AgreeReturnToWarehouseResponse toAgreeReturnToWarehouseResponse(
        String deptReturnBusNo,
        String statusEnum,
        String generatedReturnInboundBusNo);

    /**
     * 将驳回请求装配为领域 RejectRequestCommand。
     *
     * @param request    驳回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    RejectRequestCommand toRejectRequestCommand(RejectReturnToWarehouseRequest request, Long operatorId);

    /**
     * 装配驳回退药库成功响应。
     *
     * @param ok 是否驳回成功
     * @return 驳回响应
     */
    RejectReturnToWarehouseResponse toRejectReturnToWarehouseResponse(boolean ok);

    /**
     * 将删除请求装配为领域 RemoveByBusNoCommand。
     *
     * @param request    删除请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveReturnToWarehouseRequest request, Long operatorId);

    /**
     * 装配删除退药库成功响应。
     *
     * @param ok 是否删除成功
     * @return 删除响应
     */
    RemoveReturnToWarehouseResponse toRemoveReturnToWarehouseResponse(boolean ok);

    /**
     * 库存读模型与产品读模型映射为退库选品行 DTO。
     *
     * @param inv 库存项读模型
     * @param pr  产品读模型，可 null
     * @return 选品行 DTO
     */
    ProductDefinitionItemDto toProductDefinitionItemDtoFromInventory(
        InventoryItemModel inv, ProductDefinitionDisplayDto pr);
}
