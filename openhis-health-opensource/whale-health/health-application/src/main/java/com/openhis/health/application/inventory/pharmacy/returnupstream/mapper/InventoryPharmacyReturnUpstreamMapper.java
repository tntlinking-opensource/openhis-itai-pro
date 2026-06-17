/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.pharmacy.returnupstream.mapper;

import com.openhis.health.application.inventory.pharmacy.returnupstream.dto.SupplierOptionDto;
import com.openhis.health.application.inventory.pharmacy.returnupstream.dto.WarehouseOptionDto;
import com.openhis.health.application.inventory.pharmacy.returnupstream.request.*;
import com.openhis.health.application.inventory.pharmacy.returnupstream.response.*;
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
 * 药房退上游（退库）单应用层装配契约：HTTP 请求/响应与领域命令、读模型行之间的转换。
 *
 * <p>职责边界：仅做字段搬运与枚举 code 归一；不承载库存、审批状态机等业务流程判断。</p>
 */
public interface InventoryPharmacyReturnUpstreamMapper {

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
     * 装配药房退上游页初始化响应。
     *
     * @param pharmacyLocationOptions  源药房位置选项
     * @param warehouseLocationOptions 目的药库选项
     * @param supplierOptions          供应商选项
     * @return 页初始化响应
     */
    LoadReturnUpstreamFormInitResponse toLoadReturnUpstreamFormInitResponse(
        List<WarehouseOptionDto> pharmacyLocationOptions,
        List<WarehouseOptionDto> warehouseLocationOptions,
        List<SupplierOptionDto> supplierOptions);

    /**
     * 将库房单据读模型分页装配为退上游列表响应。
     *
     * @param page 库房单据读模型分页
     * @return 列表分页响应
     */
    PageReturnUpstreamsResponse toPageReturnUpstreamsResponse(Page<WarehouseDocumentDetailQueryModel> page);

    /**
     * 将可关联入库单读模型分页装配为选单响应。
     *
     * @param page 可关联入库单分页
     * @return 可关联单据分页响应
     */
    PageLinkableReturnSourceDocumentsResponse toPageLinkableReturnSourceDocumentsResponse(Page<WarehouseDocumentDetailQueryModel> page);

    /**
     * 将同号供应请求明细与产品读模型装配为退上游详情响应。
     *
     * @param lines        供应请求明细行
     * @param productByKey 产品定义读模型映射
     * @return 详情响应
     */
    GetReturnUpstreamDetailResponse toGetReturnUpstreamDetailResponse(
        List<SupplyRequestModel> lines,
        Map<String, ProductDefinitionDisplayDto> productByKey);

    /**
     * 将新建退上游请求装配为领域 CreateReturnCommand。
     *
     * @param request               新建请求
     * @param operatorId            操作人
     * @param applicantId           制单人员
     * @param originalBusNoSnapshot 关联上游入库单号快照
     * @return 领域写命令
     */
    CreateReturnCommand toCreateReturnUpstreamCommand(
        CreateReturnUpstreamRequest request,
        Long operatorId,
        Long applicantId,
        String originalBusNoSnapshot);

    /**
     * 将修订退上游请求装配为领域 CreateReturnCommand（同号全量对齐）。
     *
     * @param request               修订请求
     * @param operatorId            操作人
     * @param applicantId           制单人员
     * @param originalBusNoSnapshot 关联上游入库单号快照
     * @return 领域写命令
     */
    CreateReturnCommand toCreateReturnUpstreamCommandForUpdate(
        UpdateReturnUpstreamRequest request,
        Long operatorId,
        Long applicantId,
        String originalBusNoSnapshot);

    /**
     * 装配新建退上游成功响应。
     *
     * @param busNo 新建业务单号
     * @return 新建响应
     */
    CreateReturnUpstreamResponse toCreateReturnUpstreamResponse(String busNo);

    /**
     * 装配修订退上游成功响应。
     *
     * @param busNo 业务单号
     * @return 修订响应
     */
    UpdateReturnUpstreamResponse toUpdateReturnUpstreamResponse(String busNo);

    /**
     * 将提交审批请求装配为领域 SubmitApprovalCommand。
     *
     * @param request     提交请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    SubmitApprovalCommand toSubmitApprovalCommand(
        SubmitReturnUpstreamApprovalRequest request,
        Long operatorId,
        Long applicantId);

    /**
     * 装配提交审批成功响应。
     *
     * @return 提交响应
     */
    SubmitReturnUpstreamApprovalResponse toSubmitReturnUpstreamApprovalResponse();

    /**
     * 将撤回审批请求装配为领域 WithdrawApprovalCommand。
     *
     * @param request    撤回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    WithdrawApprovalCommand toWithdrawApprovalCommand(
        WithdrawReturnUpstreamApprovalRequest request,
        Long operatorId);

    /**
     * 装配撤回审批成功响应。
     *
     * @return 撤回响应
     */
    WithdrawReturnUpstreamApprovalResponse toWithdrawReturnUpstreamApprovalResponse();

    /**
     * 将同意请求装配为领域 AgreeRequestCommand。
     *
     * @param request    同意请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    AgreeRequestCommand toAgreeRequestCommand(AgreeReturnUpstreamRequest request, Long operatorId);

    /**
     * 将已同意明细行装配为退上游发放命令。
     *
     * @param lines      已同意的供应请求明细行
     * @param operatorId 操作人
     * @return 发放命令
     */
    CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommandForReturnUpstream(
        List<SupplyRequestModel> lines,
        Long operatorId);

    /**
     * 装配同意退上游成功响应。
     *
     * @param ok 是否同意成功
     * @return 同意响应
     */
    AgreeReturnUpstreamResponse toAgreeReturnUpstreamResponse(boolean ok);

    /**
     * 将驳回请求装配为领域 RejectRequestCommand。
     *
     * @param request    驳回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    RejectRequestCommand toRejectRequestCommand(RejectReturnUpstreamRequest request, Long operatorId);

    /**
     * 装配驳回退上游成功响应。
     *
     * @param ok 是否驳回成功
     * @return 驳回响应
     */
    RejectReturnUpstreamResponse toRejectReturnUpstreamResponse(boolean ok);

    /**
     * 将删除请求装配为领域 RemoveByBusNoCommand。
     *
     * @param request    删除请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveReturnUpstreamRequest request, Long operatorId);

    /**
     * 装配删除退上游成功响应。
     *
     * @param ok 是否删除成功
     * @return 删除响应
     */
    RemoveReturnUpstreamResponse toRemoveReturnUpstreamResponse(boolean ok);

    /**
     * 将产品定义读模型分页装配为退上游选品分页响应。
     *
     * @param page 产品定义读模型分页
     * @return 选品分页响应
     */
    PageProductsForReturnUpstreamResponse toPageProductsForReturnUpstreamResponse(
        Page<ProductDefinitionDisplayDto> page);
}
