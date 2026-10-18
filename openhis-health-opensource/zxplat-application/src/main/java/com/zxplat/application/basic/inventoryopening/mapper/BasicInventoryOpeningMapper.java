/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.inventoryopening.mapper;

import com.zxplat.application.basic.inventoryopening.dto.InventoryOpeningDetailLineDto;
import com.zxplat.application.basic.inventoryopening.dto.InventoryOpeningLineDto;
import com.zxplat.application.basic.inventoryopening.dto.InventoryOpeningListRowDto;
import com.zxplat.application.basic.inventoryopening.dto.InventoryOpeningProductRowDto;
import com.zxplat.application.basic.inventoryopening.dto.LocationOpeningOptionDto;
import com.zxplat.application.basic.inventoryopening.request.AgreeInventoryOpeningRequest;
import com.zxplat.application.basic.inventoryopening.request.CreateInventoryOpeningRequest;
import com.zxplat.application.basic.inventoryopening.request.RejectInventoryOpeningRequest;
import com.zxplat.application.basic.inventoryopening.request.RemoveInventoryOpeningRequest;
import com.zxplat.application.basic.inventoryopening.request.SubmitInventoryOpeningApprovalRequest;
import com.zxplat.application.basic.inventoryopening.request.UpdateInventoryOpeningRequest;
import com.zxplat.application.basic.inventoryopening.request.WithdrawInventoryOpeningApprovalRequest;
import com.zxplat.application.basic.inventoryopening.response.CloseInventoryOpeningResponse;
import com.zxplat.application.basic.inventoryopening.response.CreateInventoryOpeningResponse;
import com.zxplat.application.basic.inventoryopening.response.GetInventoryOpeningDetailResponse;
import com.zxplat.application.basic.inventoryopening.response.LoadInventoryOpeningFormInitResponse;
import com.zxplat.application.basic.inventoryopening.response.PageInventoryOpeningsResponse;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.workflow.inventoryitem.command.CreateInventoryItemCommand;
import com.zxplat.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.zxplat.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand;
import com.zxplat.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateOpeningCommand;
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
 * 库存期初应用层装配契约（AS-BAS-INV-001-SDD）。
 *
 * <p>仅做字段搬运与枚举 code 归一；不承载封锁/同意编排判断。</p>
 */
public interface BasicInventoryOpeningMapper {

    /**
     * 将位置与关闭标志映射为下拉项。
     *
     * @param locations     位置列表
     * @param closedByLocId 关闭态映射
     * @return 下拉项
     */
    List<LocationOpeningOptionDto> toLocationOpeningOptions(
        List<Location> locations, Map<Long, Boolean> closedByLocId);

    /**
     * 人员 → OptionDto。
     *
     * @param practitioners 人员
     * @return 选项
     */
    List<OptionDto> toOptionDtoListFromPractitioners(List<Practitioner> practitioners);

    /**
     * 供应状态下拉。
     *
     * @return 状态选项
     */
    List<OptionDto> toOptionDtoListSupplyStatuses();

    /**
     * 装配页初始化响应。
     *
     * @param warehouses 药库
     * @param pharmacies 药房
     * @param deptPharms 耗材房
     * @param statuses   状态
     * @param practitioners 经手人
     * @return 初始化响应
     */
    LoadInventoryOpeningFormInitResponse toFormInitResponse(
        List<LocationOpeningOptionDto> warehouses,
        List<LocationOpeningOptionDto> pharmacies,
        List<LocationOpeningOptionDto> deptPharms,
        List<OptionDto> statuses,
        List<OptionDto> practitioners,
        List<OptionDto> suppliers);

    /**
     * 按单号顺序聚合列表行。
     *
     * @param busNoPageOrder 页内单号序
     * @param detailRows     明细
     * @param currentEffectiveByLoc 库房 → 当前有效 busNo
     * @return 列表行
     */
    List<InventoryOpeningListRowDto> toListRows(
        List<String> busNoPageOrder,
        List<SupplyRequestModel> detailRows,
        Map<Long, String> currentEffectiveByLoc);

    /**
     * 装配分页响应。
     *
     * @param total   总单数
     * @param records 行
     * @return 分页响应
     */
    PageInventoryOpeningsResponse toPageResponse(long total, List<InventoryOpeningListRowDto> records);

    /**
     * 装配详情响应。
     *
     * @param lines    供应行
     * @param products 产品读模型
     * @return 详情
     */
    GetInventoryOpeningDetailResponse toDetailResponse(
        List<SupplyRequestModel> lines, Map<String, ProductDefinitionDisplayDto> products);

    /**
     * 新建请求 → CreateOpeningCommand。
     *
     * @param request         新建请求
     * @param operatorId      操作人
     * @param applicantId     制单人
     * @param openingTypeCode OPENING 类型
     * @param purposeTypeCode purpose 类型
     * @param busNo           修订时既有单号；新建传 null
     * @return 领域命令
     */
    CreateOpeningCommand toCreateOpeningCommand(
        CreateInventoryOpeningRequest request,
        Long operatorId,
        Long applicantId,
        String openingTypeCode,
        String purposeTypeCode,
        String busNo);

    /**
     * 编辑请求 → CreateOpeningCommand（同号修订）。
     *
     * @param request         编辑请求
     * @param operatorId      操作人
     * @param applicantId     制单人
     * @param openingTypeCode OPENING 类型
     * @param purposeTypeCode purpose 类型
     * @return 领域命令
     */
    CreateOpeningCommand toCreateOpeningCommand(
        UpdateInventoryOpeningRequest request,
        Long operatorId,
        Long applicantId,
        String openingTypeCode,
        String purposeTypeCode);

    /**
     * 由明细行装配 CreateOpeningCommand（导入/复制）。
     *
     * @param locationId      库房
     * @param practitionerId  经手人
     * @param occurrenceTime  业务时点
     * @param lines           明细
     * @param operatorId      操作人
     * @param applicantId     制单人
     * @param openingTypeCode 类型
     * @param purposeTypeCode purpose 类型
     * @param busNo           修订单号；新建 null
     * @return 领域命令
     */
    CreateOpeningCommand toCreateOpeningCommandFromLines(
        Long locationId,
        Long practitionerId,
        java.time.LocalDateTime occurrenceTime,
        List<InventoryOpeningLineDto> lines,
        Long operatorId,
        Long applicantId,
        String openingTypeCode,
        String purposeTypeCode,
        String busNo);

    /**
     * @param busNo 单号
     * @return 新建响应
     */
    CreateInventoryOpeningResponse toCreateResponse(String busNo);

    /**
     * @param request     提交请求
     * @param operatorId  操作人
     * @param applicantId 申请人
     * @return 提交命令
     */
    SubmitApprovalCommand toSubmitApprovalCommand(
        SubmitInventoryOpeningApprovalRequest request, Long operatorId, Long applicantId);

    /**
     * @param request    撤回请求
     * @param operatorId 操作人
     * @return 撤回命令
     */
    WithdrawApprovalCommand toWithdrawApprovalCommand(
        WithdrawInventoryOpeningApprovalRequest request, Long operatorId);

    /**
     * @param request    同意请求
     * @param operatorId 操作人
     * @return 同意命令
     */
    AgreeRequestCommand toAgreeRequestCommand(AgreeInventoryOpeningRequest request, Long operatorId);

    /**
     * @param request    驳回请求
     * @param operatorId 操作人
     * @return 驳回命令
     */
    RejectRequestCommand toRejectRequestCommand(RejectInventoryOpeningRequest request, Long operatorId);

    /**
     * @param request    删除请求
     * @param operatorId 操作人
     * @return 删除命令
     */
    RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveInventoryOpeningRequest request, Long operatorId);

    /**
     * 同意行 → 发放命令。
     *
     * @param lines      同意行
     * @param operatorId 操作人
     * @return 发放命令
     */
    CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommand(List<SupplyRequestModel> lines, Long operatorId);

    /**
     * @param inventoryItemId 库存项
     * @param increaseQty     增量
     * @param operatorId      操作人
     * @return 加量命令
     */
    IncreaseQuantityCommand toIncreaseQuantityCommand(Long inventoryItemId, BigDecimal increaseQty, Long operatorId);

    /**
     * @param line              供应行
     * @param purposeLocationId 库房
     * @param productName       品名
     * @param lotNumber         批号
     * @param quantity          数量
     * @param unitCode          单位
     * @param operatorId        操作人
     * @param product           产品
     * @param supplierId        供应商
     * @return 建库存命令
     */
    CreateInventoryItemCommand toCreateInventoryItemCommand(
        SupplyRequestModel line,
        Long purposeLocationId,
        String productName,
        String lotNumber,
        BigDecimal quantity,
        String unitCode,
        Long operatorId,
        ProductDefinitionDisplayDto product,
        Long supplierId);

    /**
     * 选品分页 → 行 DTO。
     *
     * @param page 产品页
     * @return 选品行
     */
    List<InventoryOpeningProductRowDto> toProductRows(Page<ProductDefinitionDisplayDto> page);

    /**
     * @param locationId 库房
     * @return 关闭响应
     */
    CloseInventoryOpeningResponse toCloseResponse(Long locationId);

    /**
     * 详情行装配。
     *
     * @param line    供应行
     * @param product 产品
     * @return 详情行
     */
    InventoryOpeningDetailLineDto toDetailLineDto(SupplyRequestModel line, ProductDefinitionDisplayDto product);
}
