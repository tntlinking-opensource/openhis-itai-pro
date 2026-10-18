/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.stockin.mapper;

import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.zxplat.application.inventory.pharmacy.stockin.dto.PharmacyStockInDetailLineDto;
import com.zxplat.application.inventory.pharmacy.stockin.dto.PharmacyStockInListRowDto;
import com.zxplat.application.inventory.pharmacy.stockin.dto.ProductDefinitionItemDto;
import com.zxplat.application.inventory.pharmacy.stockin.request.*;
import com.zxplat.application.inventory.pharmacy.stockin.response.*;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.zxplat.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand;
import com.zxplat.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateRequisitionCommand;
import com.zxplat.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.zxplat.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.zxplat.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.zxplat.framework.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 药房入库单应用层装配：请求/响应与领域命令、库存读模型之间的转换（AS-INV-PHA-001-SDD）。
 *
 * <p>禁止承载业务流程判断；枚举 code 解析与装配规则见实现类。</p>
 */
public interface InventoryPharmacyStockInMapper {

    /**
     * 位置列表转 OptionDto。
     *
     * @param locations 位置列表，可为 null
     * @return 下拉项，永不为 null
     */
    List<OptionDto> toOptionDtoListFromLocations(List<Location> locations);

    /**
     * 医务人员列表转 OptionDto。
     *
     * @param practitioners 人员列表，可为 null
     * @return 下拉项，永不为 null
     */
    List<OptionDto> toOptionDtoListFromPractitioners(List<Practitioner> practitioners);

    /**
     * 入库项目类型两档。
     *
     * @return 固定两项
     */
    List<OptionDto> toOptionDtoListInboundItemTableTypes();

    /**
     * 单据状态下拉。
     *
     * @return SupplyStatus 选项
     */
    List<OptionDto> toOptionDtoListSupplyStatuses();

    /**
     * 初始化响应装配（IF-PHA-IN-01）。
     *
     * @param pharmacyOpts   目的药房
     * @param warehouseOpts  来源药库
     * @param inboundTypes   入库项目类型
     * @param statusOpts     状态
     * @param pharmacistOpts 药师
     * @param keeperOpts     库管
     * @return 响应
     */
    LoadPharmacyStockInFormInitResponse toLoadPharmacyStockInFormInitResponse(
        List<OptionDto> pharmacyOpts,
        List<OptionDto> warehouseOpts,
        List<OptionDto> inboundTypes,
        List<OptionDto> statusOpts,
        List<OptionDto> pharmacistOpts,
        List<OptionDto> keeperOpts);

    /**
     * 药房入库列表聚合（IF-PHA-IN-02）。
     *
     * @param busNoPageOrder 分页单号顺序
     * @param detailRows     明细
     * @return 聚合行
     */
    List<PharmacyStockInListRowDto> toPharmacyStockInListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows);

    /**
     * 药房入库分页响应装配。
     *
     * @param mergedTotal 去重总数
     * @param pageNo      1-based
     * @param pageSize    页大小
     * @param records     行
     * @return 响应
     */
    PagePharmacyStockInsResponse toPagePharmacyStockInsResponse(long mergedTotal, int pageNo, int pageSize, List<PharmacyStockInListRowDto> records);

    /**
     * 详情响应（IF-PHA-IN-03）。
     *
     * @param lines        供应请求行
     * @param productHints 行 id → 目录提示
     * @return 响应
     */
    GetPharmacyStockInDetailResponse toGetPharmacyStockInDetailResponse(
        List<SupplyRequestModel> lines,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints);

    /**
     * 详情行 DTO（单行）。
     *
     * @param line 供应请求行
     * @return DTO；line 为 null 时返回 null
     */
    PharmacyStockInDetailLineDto toPharmacyStockInDetailLineDto(SupplyRequestModel line);

    /**
     * 新建入库命令（IF-PHA-IN-04）。
     *
     * @param request     请求
     * @param operatorId  操作人
     * @param applicantId 申请人
     * @param applyTime   申请时间
     * @return 命令
     */
    CreateRequisitionCommand toCreateRequisitionCommand(
        CreatePharmacyStockInRequest request,
        Long operatorId,
        Long applicantId,
        LocalDateTime applyTime);

    /**
     * 修订入库命令（IF-PHA-IN-05）。
     *
     * @param request     请求
     * @param operatorId  操作人
     * @param applicantId 申请人
     * @param applyTime   申请时间
     * @return 命令
     */
    CreateRequisitionCommand toCreateRequisitionCommand(
        UpdatePharmacyStockInRequest request,
        Long operatorId,
        Long applicantId,
        LocalDateTime applyTime);

    /**
     * 新建成功响应装配（IF-PHA-IN-04）。
     *
     * @param busNo 业务单号
     * @return 响应
     */
    CreatePharmacyStockInResponse toCreatePharmacyStockInResponse(String busNo);

    /**
     * 编辑成功响应装配（IF-PHA-IN-05）。
     *
     * @param busNo 业务单号
     * @return 响应
     */
    UpdatePharmacyStockInResponse toUpdatePharmacyStockInResponse(String busNo);

    /**
     * 提交审批命令装配（IF-PHA-IN-06）。
     *
     * @param request    请求
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    SubmitApprovalCommand toSubmitApprovalCommand(SubmitPharmacyStockInApprovalRequest request, Long operatorId, Long applicantId);

    /**
     * 提交审批后状态出参装配（IF-PHA-IN-06）。
     *
     * @param status 头行状态枚举
     * @return 响应
     */
    SubmitPharmacyStockInApprovalResponse toSubmitPharmacyStockInApprovalResponse(com.zxplat.domain.share.enums.SupplyStatus status);

    /**
     * 撤回审批命令装配（IF-PHA-IN-07）。
     *
     * @param request    请求
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawPharmacyStockInApprovalRequest request, Long operatorId);

    /**
     * 撤回审批后状态出参装配（IF-PHA-IN-07）。
     *
     * @param status 头行状态枚举
     * @return 响应
     */
    WithdrawPharmacyStockInApprovalResponse toWithdrawPharmacyStockInApprovalResponse(com.zxplat.domain.share.enums.SupplyStatus status);

    /**
     * 将同意请求装配为领域 AgreeRequestCommand。
     *
     * @param request    同意请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    AgreeRequestCommand toAgreeRequestCommand(AgreePharmacyStockInRequest request, Long operatorId);

    /**
     * 药房入库发放命令装配（IF-PHA-IN-08，与同意后行快照对齐）。
     *
     * @param lines      已同意行
     * @param operatorId 操作人主键
     * @return 发放命令
     */
    CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommandForPharmacyStockIn(List<SupplyRequestModel> lines, Long operatorId);

    /**
     * 同意成功响应装配（IF-PHA-IN-08）。
     *
     * @param busNo       业务单号
     * @param statusEnum  状态持久化码
     * @return 响应
     */
    AgreePharmacyStockInResponse toAgreePharmacyStockInResponse(String busNo, String statusEnum);

    /**
     * 审批驳回命令装配（IF-PHA-IN-09）。
     *
     * @param request    请求
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    RejectRequestCommand toRejectRequestCommand(RejectPharmacyStockInRequest request, Long operatorId);

    /**
     * 驳回操作布尔结果出参装配（IF-PHA-IN-09）。
     *
     * @param ok 是否成功
     * @return 响应
     */
    RejectPharmacyStockInResponse toRejectPharmacyStockInResponse(boolean ok);

    /**
     * 删除命令装配（IF-PHA-IN-10）。
     *
     * @param request    请求
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    RemoveByBusNoCommand toRemoveByBusNoCommand(RemovePharmacyStockInRequest request, Long operatorId);

    /**
     * 删除操作布尔结果出参装配（IF-PHA-IN-10）。
     *
     * @param ok 是否成功
     * @return 响应
     */
    RemovePharmacyStockInResponse toRemovePharmacyStockInResponse(boolean ok);

    /**
     * 选品分页响应装配（IF-PHA-IN-11，pageNo 为 1-based）。
     *
     * @param page 库存分页
     * @return 响应
     */
    PageProductsForPharmacyStockInResponse toPageProductsForPharmacyStockInResponse(Page<InventoryItemModel> page);

    /**
     * 库存行 + 产品读模型转选品行（IF-PHA-IN-11）。
     *
     * @param inv 库存行
     * @param pr  产品读模型，可 null
     * @return DTO
     */
    ProductDefinitionItemDto toProductDefinitionItemDtoFromInventory(InventoryItemModel inv, ProductDefinitionDisplayDto pr);
}
