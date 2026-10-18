/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.gainloss.mapper;

import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.zxplat.application.inventory.warehouse.gainloss.dto.ProductDefinitionItemDto;
import com.zxplat.application.inventory.warehouse.gainloss.dto.GainLossDetailLineDto;
import com.zxplat.application.inventory.warehouse.gainloss.dto.GainLossListRowDto;
import com.zxplat.application.inventory.warehouse.gainloss.request.*;
import com.zxplat.application.inventory.warehouse.gainloss.response.*;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.enums.SupplyStatus;
import com.zxplat.domain.workflow.inventoryitem.command.DecreaseQuantityCommand;
import com.zxplat.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.zxplat.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand;
import com.zxplat.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateGainLossCommand;
import com.zxplat.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.zxplat.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.zxplat.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.zxplat.framework.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 药库损益单应用层装配契约：HTTP 请求/响应与领域命令、读模型行之间的转换。
 *
 * <p>职责边界：仅做字段搬运与枚举 code 归一；不承载库存、审批状态机等业务流程判断。</p>
 */
public interface InventoryWarehouseGainLossMapper {

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
     * 装配入库项目类型两档固定下拉选项。
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
     * 装配损益页初始化响应。
     *
     * @param warehouseOpts  药库选项
     * @param inboundTypes   入库项目类型选项
     * @param statusOpts     状态选项
     * @param pharmacistOpts 药师选项
     * @param keeperOpts     库管选项
     * @return 页初始化响应
     */
    LoadGainLossFormInitResponse toLoadGainLossFormInitResponse(
        List<OptionDto> warehouseOpts,
        List<OptionDto> inboundTypes,
        List<OptionDto> statusOpts,
        List<OptionDto> pharmacistOpts,
        List<OptionDto> keeperOpts);

    /**
     * 将分页单号顺序与同页明细行聚合为损益列表行。
     *
     * @param busNoPageOrder 当前页单号顺序
     * @param detailRows     当前页涉及的全部明细行
     * @return 聚合后的列表行
     */
    List<GainLossListRowDto> toGainLossListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows);

    /**
     * 装配损益列表分页响应（pageNo 为 1-based）。
     *
     * @param mergedTotal 去重后的总单数
     * @param pageNo      页码（1-based）
     * @param pageSize    页大小
     * @param records     当前页聚合行
     * @return 分页响应
     */
    PageGainLosssResponse toPageGainLosssAggregatedResponse(
        long mergedTotal, int pageNo, int pageSize, List<GainLossListRowDto> records);

    /**
     * 将同号供应请求明细与产品目录提示装配为损益详情响应。
     *
     * @param lines        供应请求明细行
     * @param productHints 行 id 到目录编辑提示的映射
     * @return 详情响应
     */
    GetGainLossDetailResponse toGetGainLossDetailResponse(
        List<SupplyRequestModel> lines,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints);

    /**
     * 将单行供应请求明细装配为损益详情行 DTO。
     *
     * @param line 供应请求明细行
     * @return 详情行 DTO
     */
    GainLossDetailLineDto toGainLossDetailLineDto(SupplyRequestModel line);

    /**
     * 将新建损益请求装配为领域 CreateGainLossCommand。
     *
     * @param request     新建请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @param busNo       预分配业务单号
     * @return 领域写命令
     */
    CreateGainLossCommand toCreateGainLossCommand(
        CreateGainLossRequest request,
        Long operatorId,
        Long applicantId,
        String busNo);

    /**
     * 装配新建损益成功响应。
     *
     * @param busNo 新建业务单号
     * @return 新建响应
     */
    CreateGainLossResponse toCreateGainLossResponse(String busNo);

    /**
     * 将修订损益请求装配为领域 CreateGainLossCommand（同号全量对齐）。
     *
     * @param request     修订请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    CreateGainLossCommand toCreateGainLossCommand(
        UpdateGainLossRequest request,
        Long operatorId,
        Long applicantId);

    /**
     * 装配修订损益成功响应。
     *
     * @param busNo 业务单号
     * @return 修订响应
     */
    UpdateGainLossResponse toUpdateGainLossResponse(String busNo);

    /**
     * 将提交审批请求装配为领域 SubmitApprovalCommand。
     *
     * @param request     提交请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    SubmitApprovalCommand toSubmitApprovalCommand(SubmitGainLossApprovalRequest request, Long operatorId, Long applicantId);

    /**
     * 装配提交审批成功响应（含提交后展示状态）。
     *
     * @param statusAfter 提交后供应状态
     * @return 提交响应
     */
    SubmitGainLossApprovalResponse toSubmitGainLossApprovalResponse(SupplyStatus statusAfter);

    /**
     * 将撤回审批请求装配为领域 WithdrawApprovalCommand。
     *
     * @param request    撤回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawGainLossApprovalRequest request, Long operatorId);

    /**
     * 装配撤回审批成功响应（含撤回后展示状态）。
     *
     * @param statusAfter 撤回后供应状态
     * @return 撤回响应
     */
    WithdrawGainLossApprovalResponse toWithdrawGainLossApprovalResponse(SupplyStatus statusAfter);

    /**
     * 将同意请求装配为领域 AgreeRequestCommand。
     *
     * @param request    同意请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    AgreeRequestCommand toAgreeRequestCommand(AgreeGainLossRequest request, Long operatorId);

    /**
     * 将已同意明细行装配为损益发放命令。
     *
     * @param lines      已同意的供应请求明细行
     * @param operatorId 操作人
     * @return 发放命令
     */
    CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommand(List<SupplyRequestModel> lines, Long operatorId);

    /**
     * 装配报溢增库存命令。
     *
     * @param inventoryItemId 库存项主键
     * @param increaseQty     增加数量
     * @param operatorId      操作人
     * @return 增库存命令
     */
    IncreaseQuantityCommand toIncreaseQuantityCommand(Long inventoryItemId, BigDecimal increaseQty, Long operatorId);

    /**
     * 装配报损减库存命令。
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
     * 装配同意损益成功响应。
     *
     * @param busNo      业务单号
     * @param statusEnum 同意后状态 code
     * @return 同意响应
     */
    AgreeGainLossResponse toAgreeGainLossResponse(String busNo, String statusEnum);

    /**
     * 将驳回请求装配为领域 RejectRequestCommand。
     *
     * @param request    驳回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    RejectRequestCommand toRejectRequestCommand(RejectGainLossRequest request, Long operatorId);

    /**
     * 装配驳回损益成功响应。
     *
     * @param ok 是否驳回成功
     * @return 驳回响应
     */
    RejectGainLossResponse toRejectGainLossResponse(boolean ok);

    /**
     * 将删除请求装配为领域 RemoveByBusNoCommand。
     *
     * @param request    删除请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveGainLossRequest request, Long operatorId);

    /**
     * 装配删除损益成功响应。
     *
     * @param ok 是否删除成功
     * @return 删除响应
     */
    RemoveGainLossResponse toRemoveGainLossResponse(boolean ok);

    /**
     * 将现库库存分页装配为损益选品分页响应。
     *
     * @param page 库存读模型分页
     * @return 选品分页响应
     */
    PageProductsForGainLossResponse toPageProductsForGainLossResponse(Page<InventoryItemModel> page);

    /**
     * 将库存行与产品读模型装配为选品行 DTO。
     *
     * @param inv 库存行
     * @param pr  产品读模型，可为 null
     * @return 选品行 DTO
     */
    ProductDefinitionItemDto toProductDefinitionItemDtoFromInventory(InventoryItemModel inv, ProductDefinitionDisplayDto pr);
}
