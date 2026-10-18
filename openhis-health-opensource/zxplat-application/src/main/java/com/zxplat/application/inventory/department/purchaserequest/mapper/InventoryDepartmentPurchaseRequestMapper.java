/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.purchaserequest.mapper;

import com.zxplat.application.inventory.department.purchaserequest.dto.DeptPurchaseRequestListRowDto;
import com.zxplat.application.inventory.department.purchaserequest.request.AgreeDeptPurchaseRequestRequest;
import com.zxplat.application.inventory.department.purchaserequest.request.CreateDeptPurchaseRequestRequest;
import com.zxplat.application.inventory.department.purchaserequest.request.PageProductsForDeptPurchaseRequestRequest;
import com.zxplat.application.inventory.department.purchaserequest.request.RejectDeptPurchaseRequestRequest;
import com.zxplat.application.inventory.department.purchaserequest.request.RemoveDeptPurchaseRequestRequest;
import com.zxplat.application.inventory.department.purchaserequest.request.SubmitDeptPurchaseRequestApprovalRequest;
import com.zxplat.application.inventory.department.purchaserequest.request.UpdateDeptPurchaseRequestRequest;
import com.zxplat.application.inventory.department.purchaserequest.request.WithdrawDeptPurchaseRequestApprovalRequest;
import com.zxplat.application.inventory.department.purchaserequest.response.AgreeDeptPurchaseRequestResponse;
import com.zxplat.application.inventory.department.purchaserequest.response.CreateDeptPurchaseRequestResponse;
import com.zxplat.application.inventory.department.purchaserequest.response.GetDeptPurchaseRequestDetailResponse;
import com.zxplat.application.inventory.department.purchaserequest.response.LoadDeptPurchaseRequestFormInitResponse;
import com.zxplat.application.inventory.department.purchaserequest.response.PageProductsForDeptPurchaseRequestResponse;
import com.zxplat.application.inventory.department.purchaserequest.response.PageDeptPurchaseRequestsResponse;
import com.zxplat.application.inventory.department.purchaserequest.response.RejectDeptPurchaseRequestResponse;
import com.zxplat.application.inventory.department.purchaserequest.response.RemoveDeptPurchaseRequestResponse;
import com.zxplat.application.inventory.department.purchaserequest.response.SubmitDeptPurchaseRequestApprovalResponse;
import com.zxplat.application.inventory.department.purchaserequest.response.UpdateDeptPurchaseRequestResponse;
import com.zxplat.application.inventory.department.purchaserequest.response.WithdrawDeptPurchaseRequestApprovalResponse;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateDeptPurchaseRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.zxplat.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.zxplat.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.zxplat.application.shared.product.ProductDefinitionPageQuery;
import com.zxplat.domain.share.enums.SupplyStatus;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

/**
 * 科室采购申请单应用层装配（AS-INV-DEP-007）。
 *
 * <p>负责 Request/Response/DTO 与领域 Command/QueryModel 之间的转换；不在此注入业务 QueryService。</p>
 */
public interface InventoryDepartmentPurchaseRequestMapper {

    /**
     * 将启用位置列表映射为 {@link OptionDto} 下拉（B.10.5）。
     *
     * @param locations 启用库位列表
     * @return 下拉选项
     */
    List<OptionDto> toOptionDtoListFromLocations(List<Location> locations);

    /**
     * 将启用医务人员列表映射为 {@link OptionDto} 下拉（B.10.5）。
     *
     * @param practitioners 医务人员列表
     * @return 人员下拉选项
     */
    List<OptionDto> toOptionDtoListFromPractitioners(List<Practitioner> practitioners);

    /**
     * 装配科室采购申请页初始化响应。
     *
     * @param departmentPharmacyLocationOptions 科室药房选项（含「全部」由 AppService 前置）
     * @param statusOptions                     单据状态选项
     * @param itemTableOpts                     物品来源表选项
     * @param pharmacistOptions                 药师选项
     * @param warehouseKeeperOptions            库管选项
     * @return 页初始化响应
     */
    LoadDeptPurchaseRequestFormInitResponse toLoadDeptPurchaseRequestFormInitResponse(
        List<OptionDto> departmentPharmacyLocationOptions,
        List<OptionDto> statusOptions,
        List<OptionDto> itemTableOpts,
        List<OptionDto> pharmacistOptions,
        List<OptionDto> warehouseKeeperOptions);

    /**
     * 装配供应请求状态枚举选项（持久化 code）。
     *
     * @return 状态下拉
     */
    List<OptionDto> toOptionDtoListSupplyStatuses();

    /**
     * 装配物品来源表（药品/耗材）选项。
     *
     * @return 物品表下拉
     */
    List<OptionDto> toOptionDtoListPurchaseItemTableTypes();

    /**
     * 将选品分页请求转为产品定义读模型查询。
     *
     * @param request 选品分页请求
     * @return 读模型查询对象
     */
    ProductDefinitionPageQuery toProductDefinitionPageQuery(PageProductsForDeptPurchaseRequestRequest request);

    /**
     * 按 busNo 顺序聚合供应请求明细为列表行。
     *
     * @param busNoPageOrder 分页单号顺序
     * @param detailRows     同页明细行
     * @return 聚合列表行
     */
    List<DeptPurchaseRequestListRowDto> toDeptPurchaseRequestListAggregated(
        List<String> busNoPageOrder,
        List<SupplyRequestModel> detailRows);

    /**
     * 用聚合列表行构建分页响应（B.10.11）。
     *
     * @param mergedTotal 不重复 busNo 总数
     * @param pageNo      1-based 页码
     * @param pageSize    页大小
     * @param records     当前页聚合行
     * @return 分页响应
     */
    PageDeptPurchaseRequestsResponse toPageDeptPurchaseRequestsAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<DeptPurchaseRequestListRowDto> records);

    /**
     * 将同号供应请求明细与产品读模型装配为详情响应。
     *
     * @param lines        供应请求明细行
     * @param productByKey 产品定义读模型映射
     * @return 详情响应
     */
    GetDeptPurchaseRequestDetailResponse toGetDeptPurchaseRequestDetailResponse(
        List<SupplyRequestModel> lines,
        Map<String, ProductDefinitionDisplayDto> productByKey);

    /**
     * 将新建请求装配为领域 {@link CreateDeptPurchaseRequestCommand}。
     *
     * @param request     新建请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    CreateDeptPurchaseRequestCommand toCreateDeptPurchaseRequestCommand(
        CreateDeptPurchaseRequestRequest request,
        Long operatorId,
        Long applicantId);

    /**
     * 将修订请求装配为领域 {@link CreateDeptPurchaseRequestCommand}（同号全量对齐）。
     *
     * @param request     修订请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     * @throws IllegalArgumentException {@code rowVersion} 超出整型范围
     */
    CreateDeptPurchaseRequestCommand toCreateDeptPurchaseRequestCommandForUpdate(
        UpdateDeptPurchaseRequestRequest request,
        Long operatorId,
        Long applicantId);

    /**
     * 装配新建成功响应。
     *
     * @param busNo 领域返回的业务单号
     * @return 含 {@code busNo} 与待审批 {@code statusEnum} code
     */
    CreateDeptPurchaseRequestResponse toCreateDeptPurchaseRequestResponse(String busNo);

    /**
     * 装配修订成功响应。
     *
     * @param busNo 同号修订后的业务单号
     * @return 含 {@code busNo} 与待审批 {@code statusEnum} code
     */
    UpdateDeptPurchaseRequestResponse toUpdateDeptPurchaseRequestResponse(String busNo);

    /**
     * 将提交审批请求装配为领域 {@link SubmitApprovalCommand}。
     *
     * @param request     提交请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    SubmitApprovalCommand toSubmitApprovalCommand(
        SubmitDeptPurchaseRequestApprovalRequest request,
        Long operatorId,
        Long applicantId);

    /**
     * 装配提交审批成功响应。
     *
     * @param status 提交后头行状态，可为 null
     * @return 提交响应
     */
    SubmitDeptPurchaseRequestApprovalResponse toSubmitDeptPurchaseRequestApprovalResponse(SupplyStatus status);

    /**
     * 将撤回审批请求装配为领域 {@link WithdrawApprovalCommand}。
     *
     * @param request    撤回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    WithdrawApprovalCommand toWithdrawApprovalCommand(
        WithdrawDeptPurchaseRequestApprovalRequest request,
        Long operatorId);

    /**
     * 装配撤回审批成功响应。
     *
     * @param status 撤回后头行状态，可为 null
     * @return 撤回响应
     */
    WithdrawDeptPurchaseRequestApprovalResponse toWithdrawDeptPurchaseRequestApprovalResponse(SupplyStatus status);

    /**
     * 将同意请求装配为领域 {@link AgreeRequestCommand}。
     *
     * @param request    同意请求
     * @param operatorId 审批人/操作人
     * @return 领域写命令
     */
    AgreeRequestCommand toAgreeRequestCommand(AgreeDeptPurchaseRequestRequest request, Long operatorId);

    /**
     * 装配同意成功响应。
     *
     * @return 同意响应（{@code success=true}，与订货 agree 同构）
     */
    AgreeDeptPurchaseRequestResponse toAgreeDeptPurchaseRequestResponse();

    /**
     * 将驳回请求装配为领域 {@link RejectRequestCommand}。
     *
     * @param request    驳回请求
     * @param operatorId 审批人/操作人
     * @return 领域写命令
     */
    RejectRequestCommand toRejectRequestCommand(RejectDeptPurchaseRequestRequest request, Long operatorId);

    /**
     * 装配驳回成功响应。
     *
     * @return 驳回响应
     */
    RejectDeptPurchaseRequestResponse toRejectDeptPurchaseRequestResponse();

    /**
     * 将删除请求装配为领域 {@link RemoveByBusNoCommand}。
     *
     * @param request    删除请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveDeptPurchaseRequestRequest request, Long operatorId);

    /**
     * 装配删除成功响应。
     *
     * @return 删除响应
     */
    RemoveDeptPurchaseRequestResponse toRemoveDeptPurchaseRequestResponse();

    /**
     * 将产品定义读模型分页装配为选品分页响应（B.10.11）。
     *
     * @param page 产品定义读模型分页
     * @return 选品分页响应；进价/unitBands 由 AppService enrich
     */
    PageProductsForDeptPurchaseRequestResponse toPageProductsForDeptPurchaseRequestResponse(
        Page<ProductDefinitionDisplayDto> page);
}
