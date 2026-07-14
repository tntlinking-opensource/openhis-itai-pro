/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissuereturn.mapper;

import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.inventory.department.deptissuereturn.dto.DeptIssueReturnDetailLineDto;
import com.openhis.health.application.inventory.department.deptissuereturn.dto.DeptIssueReturnListRowDto;
import com.openhis.health.application.inventory.department.deptissuereturn.dto.LinkableIssueListRowDto;
import com.openhis.health.application.inventory.department.deptissuereturn.dto.LinkableIssueReturnLineDto;
import com.openhis.health.application.inventory.department.deptissuereturn.request.*;
import com.openhis.health.application.inventory.department.deptissuereturn.response.*;
import com.openhis.health.application.inventory.department.deptissuereturn.support.DeptIssueReturnLineHelper;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.health.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateDeptIssueReturnCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.whale.core.application.dto.OptionDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 科室退领单应用层装配契约：HTTP 请求/响应与领域命令、读模型行之间的转换。
 *
 * <p>职责边界：仅做字段搬运与枚举 code 归一；不承载库存、审批状态机与可退余量等业务流程判断。</p>
 */
public interface InventoryDepartmentDeptIssueReturnMapper {

    /**
     * 将启用科室药房位置列表映射为下拉选项。
     *
     * @param locations 启用科室药房库位列表
     * @return 科室药房下拉选项
     */
    List<OptionDto> toOptionDtoListFromLocations(List<Location> locations);

    /**
     * 将启用人员列表映射为下拉选项。
     *
     * @param practitioners 人员列表
     * @return 人员下拉选项
     */
    List<OptionDto> toOptionDtoListFromPractitioners(List<Practitioner> practitioners);

    /**
     * 装配供应单据状态下拉选项。
     *
     * @return 单据状态选项
     */
    List<OptionDto> toOptionDtoListSupplyStatuses();

    /**
     * 装配科室退领页初始化响应。
     *
     * @param departmentPharmacies 科室药房选项
     * @param statusOpts           状态选项
     * @param pharmacistOpts       药师选项
     * @return 页初始化响应
     */
    LoadDeptIssueReturnFormInitResponse toLoadDeptIssueReturnFormInitResponse(
        List<OptionDto> departmentPharmacies,
        List<OptionDto> statusOpts,
        List<OptionDto> pharmacistOpts);

    /**
     * 将分页单号顺序与同页明细行聚合为科室退领列表行。
     *
     * @param busNoPageOrder 当前页单号顺序
     * @param detailRows     当前页涉及的全部明细行
     * @return 聚合后的列表行
     */
    List<DeptIssueReturnListRowDto> toDeptIssueReturnListAggregated(
        List<String> busNoPageOrder,
        List<SupplyRequestModel> detailRows);

    /**
     * 装配科室退领列表分页响应（pageNo 为 1-based）。
     *
     * @param mergedTotal 去重后的总单数
     * @param pageNo      页码（1-based）
     * @param pageSize    页大小
     * @param records     当前页聚合行
     * @return 分页响应
     */
    PageDeptIssueReturnsResponse toPageDeptIssueReturnsAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<DeptIssueReturnListRowDto> records);

    /**
     * 将同号供应请求明细与产品目录提示装配为科室退领详情响应。
     *
     * @param lines        供应请求明细行
     * @param productHints 行 id 到目录编辑提示的映射
     * @return 详情响应
     */
    GetDeptIssueReturnDetailResponse toGetDeptIssueReturnDetailResponse(
        List<SupplyRequestModel> lines,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints);

    /**
     * 将单行供应请求明细装配为科室退领详情行 DTO。
     *
     * @param line 供应请求明细行
     * @return 详情行 DTO
     */
    DeptIssueReturnDetailLineDto toDeptIssueReturnDetailLineDto(SupplyRequestModel line);

    /**
     * 将新建科室退领请求装配为领域 CreateDeptIssueReturnCommand。
     *
     * @param request     新建请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @param busNo       预分配业务单号；新建时为 null
     * @return 领域写命令
     */
    CreateDeptIssueReturnCommand toCreateDeptIssueReturnCommand(
        CreateDeptIssueReturnRequest request,
        Long operatorId,
        Long applicantId,
        String busNo);

    /**
     * 将修订科室退领请求装配为领域 CreateDeptIssueReturnCommand（首行 busNo 表示同号全量对齐）。
     *
     * @param request               修订请求
     * @param operatorId            操作人
     * @param applicantId           制单人员
     * @param originalBusNoSnapshot 源领用单号快照（自既有退领单头读取）
     * @return 领域写命令
     */
    CreateDeptIssueReturnCommand toCreateDeptIssueReturnCommand(
        UpdateDeptIssueReturnRequest request,
        Long operatorId,
        Long applicantId,
        String originalBusNoSnapshot);

    /**
     * 装配新建科室退领成功响应。
     *
     * @param busNo 新建业务单号
     * @return 新建响应
     */
    CreateDeptIssueReturnResponse toCreateDeptIssueReturnResponse(String busNo);

    /**
     * 装配修订科室退领成功响应。
     *
     * @param busNo 业务单号
     * @return 修订响应
     */
    UpdateDeptIssueReturnResponse toUpdateDeptIssueReturnResponse(String busNo);

    /**
     * 将提交审批请求装配为领域 SubmitApprovalCommand。
     *
     * @param request     提交请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    SubmitApprovalCommand toSubmitApprovalCommand(
        SubmitDeptIssueReturnApprovalRequest request,
        Long operatorId,
        Long applicantId);

    /**
     * 装配提交审批成功响应（含提交后展示状态）。
     *
     * @param statusAfter 提交后供应状态
     * @return 提交响应
     */
    SubmitDeptIssueReturnApprovalResponse toSubmitDeptIssueReturnApprovalResponse(SupplyStatus statusAfter);

    /**
     * 将撤回审批请求装配为领域 WithdrawApprovalCommand。
     *
     * @param request    撤回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    WithdrawApprovalCommand toWithdrawApprovalCommand(
        WithdrawDeptIssueReturnApprovalRequest request,
        Long operatorId);

    /**
     * 装配撤回审批成功响应（含撤回后展示状态）。
     *
     * @param statusAfter 撤回后供应状态
     * @return 撤回响应
     */
    WithdrawDeptIssueReturnApprovalResponse toWithdrawDeptIssueReturnApprovalResponse(SupplyStatus statusAfter);

    /**
     * 将同意请求装配为领域 AgreeRequestCommand。
     *
     * @param request    同意请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    AgreeRequestCommand toAgreeRequestCommand(AgreeDeptIssueReturnRequest request, Long operatorId);

    /**
     * 将已同意明细行装配为科室退领发放命令。
     *
     * @param lines      已同意的供应请求明细行
     * @param operatorId 操作人
     * @return 发放命令
     */
    CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommand(List<SupplyRequestModel> lines, Long operatorId);

    /**
     * 装配科室退领增库存命令。
     *
     * @param inventoryItemId 库存项主键
     * @param increaseQty     增加数量（拆零，正数）
     * @param operatorId      操作人
     * @return 增库存命令
     */
    IncreaseQuantityCommand toIncreaseQuantityCommand(
        Long inventoryItemId,
        BigDecimal increaseQty,
        Long operatorId);

    /**
     * 装配同意科室退领成功响应。
     *
     * @param busNo      业务单号
     * @param statusEnum 同意后状态 code
     * @return 同意响应
     */
    AgreeDeptIssueReturnResponse toAgreeDeptIssueReturnResponse(String busNo, String statusEnum);

    /**
     * 将驳回请求装配为领域 RejectRequestCommand。
     *
     * @param request    驳回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    RejectRequestCommand toRejectRequestCommand(RejectDeptIssueReturnRequest request, Long operatorId);

    /**
     * 装配驳回科室退领成功响应。
     *
     * @param ok 是否驳回成功
     * @return 驳回响应
     */
    RejectDeptIssueReturnResponse toRejectDeptIssueReturnResponse(boolean ok);

    /**
     * 将删除请求装配为领域 RemoveByBusNoCommand。
     *
     * @param request    删除请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveDeptIssueReturnRequest request, Long operatorId);

    /**
     * 装配删除科室退领成功响应。
     *
     * @param ok 是否删除成功
     * @return 删除响应
     */
    RemoveDeptIssueReturnResponse toRemoveDeptIssueReturnResponse(boolean ok);

    /**
     * 将源领用明细与可退行快照装配为拉取预填响应。
     *
     * @param issueLines      已同意源领用明细
     * @param returnableLines 可退行快照
     * @return 拉取预填响应
     */
    GetLinkableIssueDetailResponse toGetLinkableIssueDetailResponse(
        List<SupplyRequestModel> issueLines,
        List<DeptIssueReturnLineHelper.ReturnableLineSnapshot> returnableLines,
        Map<String, ProductDefinitionDisplayDto> productByKey);

    /**
     * 将可退行快照装配为可退领用行 DTO。
     *
     * @param snapshot 可退行快照
     * @param product  产品读模型（可 null）
     * @return 可退领用行 DTO
     */
    LinkableIssueReturnLineDto toLinkableIssueReturnLineDto(
        DeptIssueReturnLineHelper.ReturnableLineSnapshot snapshot,
        ProductDefinitionDisplayDto product);

    /**
     * 用可关联领用单聚合行构建分页响应。
     *
     * @param mergedTotal 去重后总单数
     * @param pageNo      页码（1-based）
     * @param pageSize    页大小
     * @param records     列表行
     * @return 分页响应
     */
    PageLinkableIssueDocumentsResponse toPageLinkableIssueDocumentsAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<LinkableIssueListRowDto> records);
}
