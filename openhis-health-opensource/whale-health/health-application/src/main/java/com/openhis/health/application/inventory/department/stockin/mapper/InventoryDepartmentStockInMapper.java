/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.stockin.mapper;

import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.application.inventory.department.stockin.dto.StockInDetailLineDto;
import com.openhis.health.application.inventory.department.stockin.dto.StockInListRowDto;
import com.openhis.health.application.inventory.department.stockin.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.department.stockin.request.*;
import com.openhis.health.application.inventory.department.stockin.response.*;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.health.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateRequisitionCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 科室入库单应用层装配：请求/响应与领域命令、库存读模型之间的转换（DM-WKF-006 · type {@link SupplyType#DEPT_STOCK_IN}）。
 *
 * <p>禁止承载业务流程判断；枚举 code 解析与装配规则见实现类。</p>
 */
public interface InventoryDepartmentStockInMapper {

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
     * 初始化响应装配（IF-DEPT-IN-01）。
     *
     * @param departmentPharmacyOpts   目的科室药房
     * @param warehouseOpts  来源药库
     * @param inboundTypes   入库项目类型
     * @param statusOpts     状态
     * @param pharmacistOpts 药师
     * @param keeperOpts     库管
     * @return 响应
     */
    LoadStockInFormInitResponse toLoadStockInFormInitResponse(
        List<OptionDto> departmentPharmacyOpts,
        List<OptionDto> warehouseOpts,
        List<OptionDto> inboundTypes,
        List<OptionDto> statusOpts,
        List<OptionDto> pharmacistOpts,
        List<OptionDto> keeperOpts);

    /**
     * 科室入库列表聚合（IF-DEPT-IN-02）。
     *
     * @param busNoPageOrder 分页单号顺序
     * @param detailRows     明细
     * @return 聚合行
     */
    List<StockInListRowDto> toStockInListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows);

    /**
     * 科室入库分页响应装配。
     *
     * @param mergedTotal 去重总数
     * @param pageNo      1-based
     * @param pageSize    页大小
     * @param records     行
     * @return 响应
     */
    PageStockInsResponse toPageStockInsResponse(long mergedTotal, int pageNo, int pageSize, List<StockInListRowDto> records);

    /**
     * 详情响应（IF-DEPT-IN-03）。
     *
     * @param lines        供应请求行
     * @param productHints 行 id → 目录提示
     * @return 响应
     */
    GetStockInDetailResponse toGetStockInDetailResponse(
        List<SupplyRequestModel> lines,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints);

    /**
     * 详情行 DTO（单行）。
     *
     * @param line 供应请求行
     * @return DTO；line 为 null 时返回 null
     */
    StockInDetailLineDto toStockInDetailLineDto(SupplyRequestModel line);

    /**
     * 新建入库命令（IF-DEPT-IN-04）。
     *
     * @param request     请求
     * @param operatorId  操作人
     * @param applicantId 申请人
     * @param applyTime   申请时间
     * @return 命令
     */
    CreateRequisitionCommand toCreateRequisitionCommand(
        CreateStockInRequest request,
        Long operatorId,
        Long applicantId,
        LocalDateTime applyTime);

    /**
     * 修订入库命令（IF-DEPT-IN-05）。
     *
     * @param request     请求
     * @param operatorId  操作人
     * @param applicantId 申请人
     * @param applyTime   申请时间
     * @return 命令
     */
    CreateRequisitionCommand toCreateRequisitionCommand(
        UpdateStockInRequest request,
        Long operatorId,
        Long applicantId,
        LocalDateTime applyTime);

    /**
     * 新建成功响应装配（IF-DEPT-IN-04）。
     *
     * @param busNo 业务单号
     * @return 响应
     */
    CreateStockInResponse toCreateStockInResponse(String busNo);

    /**
     * 编辑成功响应装配（IF-DEPT-IN-05）。
     *
     * @param busNo 业务单号
     * @return 响应
     */
    UpdateStockInResponse toUpdateStockInResponse(String busNo);

    /**
     * 提交审批命令装配（IF-DEPT-IN-06）。
     *
     * @param request    请求
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    SubmitApprovalCommand toSubmitApprovalCommand(SubmitStockInApprovalRequest request, Long operatorId, Long applicantId);

    /**
     * 提交审批后状态出参装配（IF-DEPT-IN-06）。
     *
     * @param status 头行状态枚举
     * @return 响应
     */
    SubmitStockInApprovalResponse toSubmitStockInApprovalResponse(com.openhis.health.domain.share.enums.SupplyStatus status);

    /**
     * 撤回审批命令装配（IF-DEPT-IN-07）。
     *
     * @param request    请求
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawStockInApprovalRequest request, Long operatorId);

    /**
     * 撤回审批后状态出参装配（IF-DEPT-IN-07）。
     *
     * @param status 头行状态枚举
     * @return 响应
     */
    WithdrawStockInApprovalResponse toWithdrawStockInApprovalResponse(com.openhis.health.domain.share.enums.SupplyStatus status);

    /**
     * 将同意请求装配为领域 AgreeRequestCommand。
     *
     * @param request    同意请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    AgreeRequestCommand toAgreeRequestCommand(AgreeStockInRequest request, Long operatorId);

    /**
     * 科室入库发放命令装配（IF-DEPT-IN-08，与同意后行快照对齐）。
     *
     * @param lines      已同意行
     * @param operatorId 操作人主键
     * @return 发放命令
     */
    CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommandForStockIn(List<SupplyRequestModel> lines, Long operatorId);

    /**
     * 同意成功响应装配（IF-DEPT-IN-08）。
     *
     * @param busNo       业务单号
     * @param statusEnum  状态持久化码
     * @return 响应
     */
    AgreeStockInResponse toAgreeStockInResponse(String busNo, String statusEnum);

    /**
     * 审批驳回命令装配（IF-DEPT-IN-09）。
     *
     * @param request    请求
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    RejectRequestCommand toRejectRequestCommand(RejectStockInRequest request, Long operatorId);

    /**
     * 驳回操作布尔结果出参装配（IF-DEPT-IN-09）。
     *
     * @param ok 是否成功
     * @return 响应
     */
    RejectStockInResponse toRejectStockInResponse(boolean ok);

    /**
     * 删除命令装配（IF-DEPT-IN-10）。
     *
     * @param request    请求
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveStockInRequest request, Long operatorId);

    /**
     * 删除操作布尔结果出参装配（IF-DEPT-IN-10）。
     *
     * @param ok 是否成功
     * @return 响应
     */
    RemoveStockInResponse toRemoveStockInResponse(boolean ok);

    /**
     * 选品分页响应装配（IF-DEPT-IN-11，pageNo 为 1-based）。
     *
     * @param page 库存分页
     * @return 响应
     */
    PageProductsForStockInResponse toPageProductsForStockInResponse(Page<InventoryItemModel> page);

    /**
     * 库存行 + 产品读模型转选品行（IF-DEPT-IN-11）。
     *
     * @param inv 库存行
     * @param pr  产品读模型，可 null
     * @return DTO
     */
    ProductDefinitionItemDto toProductDefinitionItemDtoFromInventory(InventoryItemModel inv, ProductDefinitionDisplayDto pr);
}
