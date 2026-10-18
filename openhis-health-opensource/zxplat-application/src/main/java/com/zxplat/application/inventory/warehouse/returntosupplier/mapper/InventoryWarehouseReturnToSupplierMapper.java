/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returntosupplier.mapper;

import com.zxplat.application.inventory.warehouse.returntosupplier.dto.LinkableGoodsReceiptListRowDto;
import com.zxplat.application.inventory.warehouse.returntosupplier.dto.ReturnToSupplierListRowDto;
import com.zxplat.application.inventory.warehouse.returntosupplier.dto.SupplierOptionDto;
import com.zxplat.application.inventory.warehouse.returntosupplier.dto.WarehouseOptionDto;
import com.zxplat.application.inventory.warehouse.returntosupplier.request.*;
import com.zxplat.application.inventory.warehouse.returntosupplier.response.*;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.zxplat.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand;
import com.zxplat.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateReturnToSupplierCommand;
import com.zxplat.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.zxplat.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.zxplat.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import java.util.List;
import java.util.Map;

/**
 * 药库退供应商单应用层装配契约：HTTP 请求/响应与领域命令、读模型行之间的转换。
 *
 * <p>职责边界：仅做字段搬运与枚举 code 归一；不承载库存、审批状态机等业务流程判断。</p>
 */
public interface InventoryWarehouseReturnToSupplierMapper {

    /**
     * 将启用药库位置列表映射为下拉选项 DTO。
     *
     * @param locations 启用库位列表
     * @return 药库下拉选项
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
     * 装配退供应商页初始化响应。
     *
     * @param warehouseOptions 药库选项
     * @param supplierOptions  供应商选项
     * @return 页初始化响应
     */
    LoadReturnToSupplierFormInitResponse toLoadReturnToSupplierFormInitResponse(
        List<WarehouseOptionDto> warehouseOptions,
        List<SupplierOptionDto> supplierOptions);

    /**
     * 按 busNo 顺序聚合供应请求明细为退货列表行 DTO。
     *
     * @param busNoPageOrder 分页单号顺序
     * @param detailRows     同页全部明细
     * @return 聚合列表行
     */
    List<ReturnToSupplierListRowDto> toReturnToSupplierListAggregated(
        List<String> busNoPageOrder,
        List<SupplyRequestModel> detailRows);

    /**
     * 用聚合列表行构建退货单分页响应。
     *
     * @param mergedTotal 去重后总单数
     * @param pageNo      页码
     * @param pageSize    页大小
     * @param records     列表行
     * @return 分页响应
     */
    PageReturnToSuppliersResponse toPageReturnToSuppliersAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<ReturnToSupplierListRowDto> records);

    /**
     * 用可关联进货单聚合行构建分页响应。
     *
     * @param mergedTotal 去重后总单数
     * @param pageNo      页码
     * @param pageSize    页大小
     * @param records     列表行
     * @return 分页响应
     */
    PageLinkableGoodsReceiptsResponse toPageLinkableGoodsReceiptsAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<LinkableGoodsReceiptListRowDto> records);

    /**
     * 将已同意进货单与可退余量映射为退货预填响应。
     *
     * @param inboundLines      进货明细
     * @param returnableByKey   行键 → 可退余量
     * @param returnedByKey     行键 → 已退占用量
     * @param availableByKey    行键 → 源库可用量
     * @param productByKey      产品读模型
     * @return 预填响应
     */
    GetLinkableGoodsReceiptDetailResponse toGetLinkableGoodsReceiptDetailResponse(
        List<SupplyRequestModel> inboundLines,
        Map<String, java.math.BigDecimal> returnableByKey,
        Map<String, java.math.BigDecimal> returnedByKey,
        Map<String, java.math.BigDecimal> availableByKey,
        Map<String, ProductDefinitionDisplayDto> productByKey);

    /**
     * 将同号供应请求明细与产品读模型装配为退供应商详情响应。
     *
     * @param lines        供应请求明细行
     * @param productByKey 产品定义读模型映射
     * @return 详情响应
     */
    GetReturnToSupplierDetailResponse toGetReturnToSupplierDetailResponse(
        List<SupplyRequestModel> lines,
        Map<String, ProductDefinitionDisplayDto> productByKey);

    /**
     * 将新建退供应商请求装配为领域 CreateReturnToSupplierCommand。
     *
     * @param request               新建请求
     * @param operatorId            操作人
     * @param applicantId           制单人员
     * @param originalBusNoSnapshot 关联上游进货单号快照
     * @return 领域写命令
     */
    CreateReturnToSupplierCommand toCreateReturnToSupplierCommand(
        CreateReturnToSupplierRequest request,
        Long operatorId,
        Long applicantId,
        String originalBusNoSnapshot);

    /**
     * 将修订退供应商请求装配为领域 CreateReturnToSupplierCommand（同号全量对齐）。
     *
     * @param request               修订请求
     * @param operatorId            操作人
     * @param applicantId           制单人员
     * @param originalBusNoSnapshot 关联上游进货单号快照
     * @return 领域写命令
     */
    CreateReturnToSupplierCommand toCreateReturnToSupplierCommandForUpdate(
        UpdateReturnToSupplierRequest request,
        Long operatorId,
        Long applicantId,
        String originalBusNoSnapshot);

    /**
     * 装配新建退供应商成功响应。
     *
     * @param busNo 新建业务单号
     * @return 新建响应
     */
    CreateReturnToSupplierResponse toCreateReturnToSupplierResponse(String busNo);

    /**
     * 装配修订退供应商成功响应。
     *
     * @param busNo 业务单号
     * @return 修订响应
     */
    UpdateReturnToSupplierResponse toUpdateReturnToSupplierResponse(String busNo);

    /**
     * 将提交审批请求装配为领域 SubmitApprovalCommand。
     *
     * @param request     提交请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    SubmitApprovalCommand toSubmitApprovalCommand(
        SubmitReturnToSupplierApprovalRequest request,
        Long operatorId,
        Long applicantId);

    /**
     * 装配提交审批成功响应。
     *
     * @return 提交响应
     */
    SubmitReturnToSupplierApprovalResponse toSubmitReturnToSupplierApprovalResponse();

    /**
     * 将撤回审批请求装配为领域 WithdrawApprovalCommand。
     *
     * @param request    撤回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    WithdrawApprovalCommand toWithdrawApprovalCommand(
        WithdrawReturnToSupplierApprovalRequest request,
        Long operatorId);

    /**
     * 装配撤回审批成功响应。
     *
     * @return 撤回响应
     */
    WithdrawReturnToSupplierApprovalResponse toWithdrawReturnToSupplierApprovalResponse();

    /**
     * 将同意请求装配为领域 AgreeRequestCommand。
     *
     * @param request    同意请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    AgreeRequestCommand toAgreeRequestCommand(AgreeReturnToSupplierRequest request, Long operatorId);

    /**
     * 将已同意明细行装配为退供应商发放命令。
     *
     * @param lines      已同意的供应请求明细行
     * @param operatorId 操作人
     * @return 发放命令
     */
    CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommandForReturnToSupplier(
        List<SupplyRequestModel> lines,
        Long operatorId);

    /**
     * 装配同意退供应商成功响应。
     *
     * @param ok 是否同意成功
     * @return 同意响应
     */
    AgreeReturnToSupplierResponse toAgreeReturnToSupplierResponse(boolean ok);

    /**
     * 将驳回请求装配为领域 RejectRequestCommand。
     *
     * @param request    驳回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    RejectRequestCommand toRejectRequestCommand(RejectReturnToSupplierRequest request, Long operatorId);

    /**
     * 装配驳回退供应商成功响应。
     *
     * @param ok 是否驳回成功
     * @return 驳回响应
     */
    RejectReturnToSupplierResponse toRejectReturnToSupplierResponse(boolean ok);

    /**
     * 将删除请求装配为领域 RemoveByBusNoCommand。
     *
     * @param request    删除请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveReturnToSupplierRequest request, Long operatorId);

    /**
     * 装配删除退供应商成功响应。
     *
     * @param ok 是否删除成功
     * @return 删除响应
     */
    RemoveReturnToSupplierResponse toRemoveReturnToSupplierResponse(boolean ok);
}
