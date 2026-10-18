/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.transferout.mapper;

import com.zxplat.application.inventory.warehouse.transferout.dto.ProductDefinitionItemDto;
import com.zxplat.application.inventory.warehouse.transferout.dto.TransferOutDetailLineDto;
import com.zxplat.application.inventory.warehouse.transferout.dto.TransferOutListRowDto;
import com.zxplat.application.inventory.warehouse.transferout.request.*;
import com.zxplat.application.inventory.warehouse.transferout.response.*;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.enums.SupplyStatus;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.zxplat.domain.workflow.supplydelivery.command.CreateTransferDeliveryCommand;
import com.zxplat.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateTransferCommand;
import com.zxplat.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.zxplat.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.zxplat.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.zxplat.framework.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

/**
 * 药库调拨调出单应用层装配契约：HTTP 请求/响应与领域命令、读模型行之间的转换。
 *
 * <p>职责边界：仅做字段搬运与枚举 code 归一；不承载库存、审批状态机等业务流程判断（见实现类与 {@link com.zxplat.application.inventory.warehouse.transferout.service.InventoryWarehouseTransferOutAppService}）。</p>
 */
public interface InventoryWarehouseTransferOutMapper {

    /**
     * 将启用药库位置列表映射为下拉选项。
     *
     * @param locations 启用库位列表
     * @return 药库下拉选项
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
     * 装配调拨项目类型两档固定下拉选项。
     *
     * @return 药品/耗材类型选项
     */
    List<OptionDto> toOptionDtoListInboundItemTableTypes();

    /**
     * 装配供应单据状态下拉选项。
     *
     * @return 单据状态选项
     */
    List<OptionDto> toOptionDtoListSupplyStatuses();

    /**
     * 装配调拨调出页初始化响应。
     *
     * @param warehouseOpts  药库选项
     * @param pharmacyOpts   目的药房选项
     * @param inboundTypes   调拨项目类型选项
     * @param statusOpts     状态选项
     * @param pharmacistOpts 药师选项
     * @param keeperOpts     库管选项
     * @return 页初始化响应
     */
    LoadTransferOutFormInitResponse toLoadTransferOutFormInitResponse(
        List<OptionDto> warehouseOpts,
        List<OptionDto> pharmacyOpts,
        List<OptionDto> inboundTypes,
        List<OptionDto> statusOpts,
        List<OptionDto> pharmacistOpts,
        List<OptionDto> keeperOpts);

    /**
     * 将分页单号顺序与同页明细行聚合为调拨调出列表行。
     *
     * @param busNoPageOrder 当前页单号顺序
     * @param detailRows     当前页涉及的全部明细行
     * @return 聚合后的列表行
     */
    List<TransferOutListRowDto> toTransferOutListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows);

    /**
     * 装配调拨调出列表分页响应（pageNo 为 1-based）。
     *
     * @param mergedTotal 去重后的总单数
     * @param pageNo      页码（1-based）
     * @param pageSize    页大小
     * @param records     当前页聚合行
     * @return 分页响应
     */
    PageTransferOutsResponse toPageTransferOutsAggregatedResponse(long mergedTotal, int pageNo, int pageSize, List<TransferOutListRowDto> records);

    /**
     * 将同号供应请求明细与产品目录提示装配为调拨调出详情响应。
     *
     * @param lines        供应请求明细行
     * @param productHints 行 id 到目录编辑提示的映射
     * @return 详情响应
     */
    GetTransferOutDetailResponse toGetTransferOutDetailResponse(
        List<SupplyRequestModel> lines,
        Map<Long, com.zxplat.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto> productHints);

    /**
     * 将单行供应请求明细装配为调拨调出详情行 DTO。
     *
     * @param line 供应请求明细行
     * @return 详情行 DTO
     */
    TransferOutDetailLineDto toTransferOutDetailLineDto(SupplyRequestModel line);

    /**
     * 将新建调拨调出请求装配为领域 CreateTransferCommand。
     *
     * @param request     新建请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @param busNo       预分配业务单号
     * @return 领域写命令
     */
    CreateTransferCommand toCreateTransferOutCommand(
        CreateTransferOutRequest request,
        Long operatorId,
        Long applicantId,
        String busNo);

    /**
     * 实现上塌缩为 {@link CreateTransferOutRequest} 后委托 {@link #toCreateTransferOutCommand(CreateTransferOutRequest, Long, Long, String)}。
     *
     * @param request     修订请求，不得为 null
     * @param operatorId  操作人主键
     * @param applicantId 申请人主键
     * @return 领域创建命令（携带既有 {@code busNo}）
     * @throws IllegalArgumentException 装配过程中枚举或必填字段非法
     */
    CreateTransferCommand toCreateTransferOutCommand(
        UpdateTransferOutRequest request,
        Long operatorId,
        Long applicantId);

    /**
     * 装配新建调拨调出成功响应。
     *
     * @param busNo 新建业务单号
     * @return 新建响应
     */
    CreateTransferOutResponse toCreateTransferOutResponse(String busNo);

    /**
     * 装配修订调拨调出成功响应。
     *
     * @param busNo 业务单号
     * @return 修订响应
     */
    UpdateTransferOutResponse toUpdateTransferOutResponse(String busNo);

    /**
     * 将提交审批请求装配为领域 SubmitApprovalCommand。
     *
     * @param request     提交请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    SubmitApprovalCommand toSubmitApprovalCommand(SubmitTransferOutApprovalRequest request, Long operatorId, Long applicantId);

    /**
     * 装配提交审批成功响应（含提交后展示状态）。
     *
     * @param statusAfter 提交后供应状态
     * @return 提交响应
     */
    SubmitTransferOutApprovalResponse toSubmitTransferOutApprovalResponse(SupplyStatus statusAfter);

    /**
     * 将撤回审批请求装配为领域 WithdrawApprovalCommand。
     *
     * @param request    撤回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawTransferOutApprovalRequest request, Long operatorId);

    /**
     * 装配撤回审批成功响应（含撤回后展示状态）。
     *
     * @param statusAfter 撤回后供应状态
     * @return 撤回响应
     */
    WithdrawTransferOutApprovalResponse toWithdrawTransferOutApprovalResponse(SupplyStatus statusAfter);

    /**
     * 将同意请求装配为领域 AgreeRequestCommand。
     *
     * @param request    同意请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    AgreeRequestCommand toAgreeRequestCommand(AgreeTransferOutRequest request, Long operatorId);

    /**
     * 将已同意明细行装配为调拨发放命令。
     *
     * @param lines      已同意的供应请求明细行
     * @param operatorId 操作人
     * @return 调拨发放命令
     */
    CreateTransferDeliveryCommand toCreateTransferDeliveryCommand(List<SupplyRequestModel> lines, Long operatorId);

    /**
     * 将已同意药库调出行装配为对侧调入壳单命令（初态 {@code AGREE}，只读台账）。
     *
     * <p>壳单须完整沿用调出头：制单人（行 applicant 优先）、申请时刻、审批人、审批时刻；
     * bootstrap AGREE 不走 {@code approvalToAgree}，故审批字段必须在此写入。</p>
     *
     * @param agreedOutLines   已同意的调出明细行
     * @param outboundBusNo    调出业务单号
     * @param counterpartyType 对侧调入 type 持久化码（如 {@code PHARMACY_TRANSFER_IN} / {@code CABINET_TRANSFER_IN}）
     * @param operatorId       操作人
     * @param applicantId      制单人员（调出行 applicant 缺省时回落）
     * @return 调拨创建命令
     */
    CreateTransferCommand toCreateTransferCommandForShellCounterpartyTransferIn(
        List<SupplyRequestModel> agreedOutLines,
        String outboundBusNo,
        String counterpartyType,
        Long operatorId,
        Long applicantId);

    /**
     * 装配同意药库调出成功响应（含联动生成的对侧调入壳单号）。
     *
     * @param outboundBusNo            调出业务单号
     * @param statusEnum               同意后状态 code
     * @param generatedTransferInBusNo 联动生成的调入壳单号，可为 null
     * @return 同意响应
     */
    AgreeTransferOutResponse toAgreeTransferOutResponse(
        String outboundBusNo, String statusEnum, String generatedTransferInBusNo);

    /**
     * 将驳回请求装配为领域 RejectRequestCommand。
     *
     * <p>原因合并：{@code changeReason} 非空白时写入领域驳回说明，否则回落 {@code changeReasonCode}（人读审计优先）。</p>
     *
     * @param request    驳回请求，不得为 null
     * @param operatorId 操作人主键（实现侧与审批人口径对齐）
     * @return 领域 {@link RejectRequestCommand}，永不为 null
     */
    RejectRequestCommand toRejectRequestCommand(RejectTransferOutRequest request, Long operatorId);

    /**
     * 装配驳回调拨调出成功响应。
     *
     * @param ok 是否驳回成功
     * @return 驳回响应
     */
    RejectTransferOutResponse toRejectTransferOutResponse(boolean ok);

    /**
     * 将删除请求装配为领域 RemoveByBusNoCommand。
     *
     * @param request    删除请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveTransferOutRequest request, Long operatorId);

    /**
     * 装配删除调拨调出成功响应。
     *
     * @param ok 是否删除成功
     * @return 删除响应
     */
    RemoveTransferOutResponse toRemoveTransferOutResponse(boolean ok);

    /**
     * 将现库库存分页装配为调拨选品分页响应。
     *
     * @param page 库存读模型分页
     * @return 选品分页响应
     */
    PageProductsForTransferOutResponse toPageProductsForTransferOutResponse(Page<InventoryItemModel> page);

    /**
     * 将库存行与产品读模型装配为选品行 DTO。
     *
     * @param inv 库存行
     * @param pr  产品读模型，可为 null
     * @return 选品行 DTO
     */
    ProductDefinitionItemDto toProductDefinitionItemDtoFromInventory(InventoryItemModel inv, ProductDefinitionDisplayDto pr);
}
