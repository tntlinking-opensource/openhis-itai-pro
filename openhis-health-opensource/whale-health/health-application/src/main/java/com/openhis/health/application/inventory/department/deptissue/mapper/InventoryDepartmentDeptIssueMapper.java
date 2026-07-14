/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissue.mapper;

import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.application.inventory.department.deptissue.dto.DeptIssueDetailLineDto;
import com.openhis.health.application.inventory.department.deptissue.dto.DeptIssueListRowDto;
import com.openhis.health.application.inventory.department.deptissue.dto.DeptPharmacyOptionDto;
import com.openhis.health.application.inventory.department.deptissue.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.department.deptissue.request.*;
import com.openhis.health.application.inventory.department.deptissue.response.*;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.workflow.inventoryitem.command.DecreaseQuantityCommand;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.health.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateDeptIssueCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 科室领用单应用层装配契约：HTTP 请求/响应与领域命令、读模型行之间的转换。
 *
 * <p>职责边界：仅做字段搬运与枚举 code 归一；不承载库存、审批状态机等业务流程判断。</p>
 */
public interface InventoryDepartmentDeptIssueMapper {

    /**
     * 将启用科室药房位置列表映射为下拉选项（含上级科室只读字段 enrich）。
     *
     * @param locations 启用科室药房库位列表
     * @return 科室药房下拉选项
     */
    List<DeptPharmacyOptionDto> toDeptPharmacyOptionDtoListFromLocations(List<Location> locations);

    /**
     * 将启用人员列表映射为下拉选项。
     *
     * @param practitioners 人员列表
     * @return 人员下拉选项
     */
    List<OptionDto> toOptionDtoListFromPractitioners(List<Practitioner> practitioners);

    /**
     * 装配入库项目类型两档固定下拉选项。
     *
     * @return 药品/耗材类型选项
     */
    List<OptionDto> toOptionDtoListProductTableTypes();

    /**
     * 装配供应单据状态下拉选项。
     *
     * @return 单据状态选项
     */
    List<OptionDto> toOptionDtoListSupplyStatuses();

    /**
     * 装配科室领用页初始化响应。
     *
     * @param pharmacyOpts   科室药房选项
     * @param productTables  项目类型选项
     * @param statusOpts     状态选项
     * @param pharmacistOpts 药师选项
     * @return 页初始化响应
     */
    LoadDeptIssueFormInitResponse toLoadDeptIssueFormInitResponse(
        List<DeptPharmacyOptionDto> pharmacyOpts,
        List<OptionDto> productTables,
        List<OptionDto> statusOpts,
        List<OptionDto> pharmacistOpts);

    /**
     * 将分页单号顺序与同页明细行聚合为科室领用列表行。
     *
     * @param busNoPageOrder 当前页单号顺序
     * @param detailRows     当前页涉及的全部明细行
     * @return 聚合后的列表行
     */
    List<DeptIssueListRowDto> toDeptIssueListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows);

    /**
     * 装配科室领用列表分页响应（pageNo 为 1-based）。
     *
     * @param mergedTotal 去重后的总单数
     * @param pageNo      页码（1-based）
     * @param pageSize    页大小
     * @param records     当前页聚合行
     * @return 分页响应
     */
    PageDeptIssuesResponse toPageDeptIssuesAggregatedResponse(
        long mergedTotal, int pageNo, int pageSize, List<DeptIssueListRowDto> records);

    /**
     * 将同号供应请求明细与产品目录提示装配为科室领用详情响应。
     *
     * @param lines        供应请求明细行
     * @param productHints 行 id 到目录编辑提示的映射
     * @return 详情响应
     */
    GetDeptIssueDetailResponse toGetDeptIssueDetailResponse(
        List<SupplyRequestModel> lines,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints);

    /**
     * 将单行供应请求明细装配为科室领用详情行 DTO。
     *
     * @param line 供应请求明细行
     * @return 详情行 DTO
     */
    DeptIssueDetailLineDto toDeptIssueDetailLineDto(SupplyRequestModel line);

    /**
     * 将新建科室领用请求装配为领域 CreateDeptIssueCommand。
     *
     * @param request     新建请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @param busNo       预分配业务单号
     * @return 领域写命令
     */
    CreateDeptIssueCommand toCreateDeptIssueCommand(
        CreateDeptIssueRequest request,
        Long operatorId,
        Long applicantId,
        String busNo);

    /**
     * 装配新建科室领用成功响应。
     *
     * @param busNo 新建业务单号
     * @return 新建响应
     */
    CreateDeptIssueResponse toCreateDeptIssueResponse(String busNo);

    /**
     * 将修订科室领用请求装配为领域 CreateDeptIssueCommand（同号全量对齐）。
     *
     * @param request     修订请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    CreateDeptIssueCommand toCreateDeptIssueCommand(
        UpdateDeptIssueRequest request,
        Long operatorId,
        Long applicantId);

    /**
     * 装配修订科室领用成功响应。
     *
     * @param busNo 业务单号
     * @return 修订响应
     */
    UpdateDeptIssueResponse toUpdateDeptIssueResponse(String busNo);

    /**
     * 将提交审批请求装配为领域 SubmitApprovalCommand。
     *
     * @param request     提交请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    SubmitApprovalCommand toSubmitApprovalCommand(
        SubmitDeptIssueApprovalRequest request, Long operatorId, Long applicantId);

    /**
     * 装配提交审批成功响应（含提交后展示状态）。
     *
     * @param statusAfter 提交后供应状态
     * @return 提交响应
     */
    SubmitDeptIssueApprovalResponse toSubmitDeptIssueApprovalResponse(SupplyStatus statusAfter);

    /**
     * 将撤回审批请求装配为领域 WithdrawApprovalCommand。
     *
     * @param request    撤回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawDeptIssueApprovalRequest request, Long operatorId);

    /**
     * 装配撤回审批成功响应（含撤回后展示状态）。
     *
     * @param statusAfter 撤回后供应状态
     * @return 撤回响应
     */
    WithdrawDeptIssueApprovalResponse toWithdrawDeptIssueApprovalResponse(SupplyStatus statusAfter);

    /**
     * 将同意请求装配为领域 AgreeRequestCommand。
     *
     * @param request    同意请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    AgreeRequestCommand toAgreeRequestCommand(AgreeDeptIssueRequest request, Long operatorId);

    /**
     * 将已同意明细行装配为科室领用发放命令。
     *
     * @param lines      已同意的供应请求明细行
     * @param operatorId 操作人
     * @return 发放命令
     */
    CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommand(List<SupplyRequestModel> lines, Long operatorId);

    /**
     * 装配科室领用扣减库存命令。
     *
     * @param inventoryItemId 库存项主键
     * @param decreaseQty     减少数量
     * @param allowNegative   是否允许负库存
     * @param operatorId      操作人
     * @return 减库存命令
     */
    DecreaseQuantityCommand toDecreaseQuantityCommand(
        Long inventoryItemId, BigDecimal decreaseQty, boolean allowNegative, Long operatorId);

    /**
     * 装配同意科室领用成功响应。
     *
     * @param busNo      业务单号
     * @param statusEnum 同意后状态 code
     * @return 同意响应
     */
    AgreeDeptIssueResponse toAgreeDeptIssueResponse(String busNo, String statusEnum);

    /**
     * 将驳回请求装配为领域 RejectRequestCommand。
     *
     * @param request    驳回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    RejectRequestCommand toRejectRequestCommand(RejectDeptIssueRequest request, Long operatorId);

    /**
     * 装配驳回科室领用成功响应。
     *
     * @param ok 是否驳回成功
     * @return 驳回响应
     */
    RejectDeptIssueResponse toRejectDeptIssueResponse(boolean ok);

    /**
     * 将删除请求装配为领域 RemoveByBusNoCommand。
     *
     * @param request    删除请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveDeptIssueRequest request, Long operatorId);

    /**
     * 装配删除科室领用成功响应。
     *
     * @param ok 是否删除成功
     * @return 删除响应
     */
    RemoveDeptIssueResponse toRemoveDeptIssueResponse(boolean ok);

    /**
     * 将现库库存分页装配为科室领用选品分页响应。
     *
     * @param page 库存读模型分页
     * @return 选品分页响应
     */
    PageProductsForDeptIssueResponse toPageProductsForDeptIssueResponse(Page<InventoryItemModel> page);

    /**
     * 将库存行与产品读模型装配为选品行 DTO。
     *
     * @param inv 库存行
     * @param pr  产品读模型，可为 null
     * @return 选品行 DTO
     */
    ProductDefinitionItemDto toProductDefinitionItemDtoFromInventory(InventoryItemModel inv, ProductDefinitionDisplayDto pr);
}
