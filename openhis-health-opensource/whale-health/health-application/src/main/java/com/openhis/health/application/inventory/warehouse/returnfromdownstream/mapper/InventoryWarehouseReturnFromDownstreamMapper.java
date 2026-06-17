/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.warehouse.returnfromdownstream.mapper;

import com.openhis.health.application.inventory.warehouse.returnfromdownstream.dto.SupplierOptionDto;
import com.openhis.health.application.inventory.warehouse.returnfromdownstream.dto.WarehouseOptionDto;
import com.openhis.health.application.inventory.warehouse.returnfromdownstream.request.*;
import com.openhis.health.application.inventory.warehouse.returnfromdownstream.response.*;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.openhis.health.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateReturnCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

/**
 * 药库下游退回单应用层装配契约：HTTP 请求/响应与领域命令、读模型行之间的转换。
 *
 * <p>职责边界：仅做字段搬运与枚举 code 归一；不承载库存、审批状态机等业务流程判断。</p>
 */
public interface InventoryWarehouseReturnFromDownstreamMapper {

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
     * 装配下游退回页初始化响应。
     *
     * @param warehouseOptions  药库选项
     * @param departmentOptions 科室选项
     * @param pharmacyOptions   药房选项
     * @param supplierOptions   供应商选项
     * @return 页初始化响应
     */
    LoadReturnFromDownstreamFormInitResponse toLoadReturnFromDownstreamFormInitResponse(
        List<WarehouseOptionDto> warehouseOptions,
        List<WarehouseOptionDto> departmentOptions,
        List<WarehouseOptionDto> pharmacyOptions,
        List<SupplierOptionDto> supplierOptions);

    /**
     * 将库房单据读模型分页装配为下游退回列表响应。
     *
     * @param page 库房单据读模型分页
     * @return 列表分页响应
     */
    PageReturnFromDownstreamsResponse toPageReturnFromDownstreamsResponse(Page<WarehouseDocumentDetailQueryModel> page);

    /**
     * 将可关联出库/发放单读模型分页装配为选单响应。
     *
     * @param page 可关联单据分页
     * @return 可关联单据分页响应
     */
    PageLinkableDeliveriesResponse toPageLinkableDeliveriesResponse(Page<WarehouseDocumentDetailQueryModel> page);

    /**
     * 将同号供应请求明细与产品读模型装配为下游退回详情响应。
     *
     * @param lines        供应请求明细行
     * @param productByKey 产品定义读模型映射
     * @return 详情响应
     */
    GetReturnFromDownstreamDetailResponse toGetReturnFromDownstreamDetailResponse(
        List<SupplyRequestModel> lines,
        Map<String, ProductDefinitionDisplayDto> productByKey);

    /**
     * 将新建下游退回请求装配为领域 CreateReturnCommand。
     *
     * @param request               新建请求
     * @param operatorId            操作人
     * @param applicantId           制单人员
     * @param originalBusNoSnapshot 关联上游出库单号快照
     * @return 领域写命令
     */
    CreateReturnCommand toCreateReturnFromDownstreamCommand(
        CreateReturnFromDownstreamRequest request,
        Long operatorId,
        Long applicantId,
        String originalBusNoSnapshot);

    /**
     * 将修订下游退回请求装配为领域 CreateReturnCommand（同号全量对齐）。
     *
     * @param request               修订请求
     * @param operatorId            操作人
     * @param applicantId           制单人员
     * @param originalBusNoSnapshot 关联上游出库单号快照
     * @return 领域写命令
     */
    CreateReturnCommand toCreateReturnFromDownstreamCommandForUpdate(
        UpdateReturnFromDownstreamRequest request,
        Long operatorId,
        Long applicantId,
        String originalBusNoSnapshot);

    /**
     * 装配新建下游退回成功响应。
     *
     * @param busNo 新建业务单号
     * @return 新建响应
     */
    CreateReturnFromDownstreamResponse toCreateReturnFromDownstreamResponse(String busNo);

    /**
     * 装配修订下游退回成功响应。
     *
     * @param busNo 业务单号
     * @return 修订响应
     */
    UpdateReturnFromDownstreamResponse toUpdateReturnFromDownstreamResponse(String busNo);

    /**
     * 将提交审批请求装配为领域 SubmitApprovalCommand。
     *
     * @param request     提交请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    SubmitApprovalCommand toSubmitApprovalCommand(
        SubmitReturnFromDownstreamApprovalRequest request,
        Long operatorId,
        Long applicantId);

    /**
     * 装配提交审批成功响应。
     *
     * @return 提交响应
     */
    SubmitReturnFromDownstreamApprovalResponse toSubmitReturnFromDownstreamApprovalResponse();

    /**
     * 将撤回审批请求装配为领域 WithdrawApprovalCommand。
     *
     * @param request    撤回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    WithdrawApprovalCommand toWithdrawApprovalCommand(
        WithdrawReturnFromDownstreamApprovalRequest request,
        Long operatorId);

    /**
     * 装配撤回审批成功响应。
     *
     * @return 撤回响应
     */
    WithdrawReturnFromDownstreamApprovalResponse toWithdrawReturnFromDownstreamApprovalResponse();

    /**
     * 将同意请求装配为领域 AgreeRequestCommand。
     *
     * @param request    同意请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    AgreeRequestCommand toAgreeRequestCommand(AgreeReturnFromDownstreamRequest request, Long operatorId);

    /**
     * 将已同意明细行装配为下游退回发放命令。
     *
     * @param lines      已同意的供应请求明细行
     * @param operatorId 操作人
     * @return 发放命令
     */
    CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommandForReturnFromDownstream(
        List<SupplyRequestModel> lines,
        Long operatorId);

    /**
     * 装配同意下游退回成功响应。
     *
     * @param ok 是否同意成功
     * @return 同意响应
     */
    AgreeReturnFromDownstreamResponse toAgreeReturnFromDownstreamResponse(boolean ok);

    /**
     * 将驳回请求装配为领域 RejectRequestCommand。
     *
     * @param request    驳回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    RejectRequestCommand toRejectRequestCommand(RejectReturnFromDownstreamRequest request, Long operatorId);

    /**
     * 装配驳回下游退回成功响应。
     *
     * @param ok 是否驳回成功
     * @return 驳回响应
     */
    RejectReturnFromDownstreamResponse toRejectReturnFromDownstreamResponse(boolean ok);

    /**
     * 将删除请求装配为领域 RemoveByBusNoCommand。
     *
     * @param request    删除请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveReturnFromDownstreamRequest request, Long operatorId);

    /**
     * 装配删除下游退回成功响应。
     *
     * @param ok 是否删除成功
     * @return 删除响应
     */
    RemoveReturnFromDownstreamResponse toRemoveReturnFromDownstreamResponse(boolean ok);

    /**
     * 将产品定义读模型分页装配为下游退回选品分页响应。
     *
     * @param page 产品定义读模型分页
     * @return 选品分页响应
     */
    PageProductsForReturnFromDownstreamResponse toPageProductsForReturnFromDownstreamResponse(
        Page<ProductDefinitionDisplayDto> page);
}
