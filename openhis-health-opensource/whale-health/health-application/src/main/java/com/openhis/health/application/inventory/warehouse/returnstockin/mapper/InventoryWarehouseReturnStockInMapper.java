/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returnstockin.mapper;

import com.openhis.health.application.inventory.warehouse.returnstockin.dto.ReturnStockInListRowDto;
import com.openhis.health.application.inventory.warehouse.returnstockin.dto.SupplierOptionDto;
import com.openhis.health.application.inventory.warehouse.returnstockin.dto.WarehouseOptionDto;
import com.openhis.health.application.inventory.warehouse.returnstockin.request.*;
import com.openhis.health.application.inventory.warehouse.returnstockin.response.*;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateReturnCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import org.springframework.data.domain.Page;
import java.util.List;
import java.util.Map;

/**
 * 退库入库单（{@link SupplyType#CABINET_RETURN_STOCK_IN}）应用层装配契约。
 *
 * <p>本包 HTTP 契约保留历史包名 {@code returnstockin}；业务单据中文名为<strong>退库入库单</strong>（系统壳单，只读）。
 * 职责边界：仅做字段搬运与枚举 code 归一；库存与审批编排在应用服务层（当前写路径已拒绝，见 {@code WHS_RSI_006}）。</p>
 */
public interface InventoryWarehouseReturnStockInMapper {

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
     * 装配退库入库单页初始化响应。
     *
     * @param warehouseOptions  药库选项
     * @param departmentOptions 科室选项
     * @param pharmacyOptions   药房选项
     * @param supplierOptions   供应商选项
     * @return 页初始化响应
     */
    LoadReturnStockInFormInitResponse toLoadReturnStockInFormInitResponse(
        List<WarehouseOptionDto> warehouseOptions,
        List<WarehouseOptionDto> departmentOptions,
        List<WarehouseOptionDto> pharmacyOptions,
        List<SupplierOptionDto> supplierOptions);

    /**
     * 将库房单据读模型分页装配为退库入库单列表响应。
     *
     * @param page 库房单据读模型分页
     * @return 列表分页响应
     */
    PageReturnStockInsResponse toPageReturnStockInsResponse(Page<WarehouseDocumentDetailQueryModel> page);

    /**
     * 按 busNo 顺序聚合退库入库供应请求明细为列表行 DTO。
     *
     * @param busNoPageOrder 分页单号顺序
     * @param detailRows     同页全部明细
     * @return 聚合列表行
     */
    List<ReturnStockInListRowDto> toReturnStockInListAggregated(
        List<String> busNoPageOrder,
        List<SupplyRequestModel> detailRows);

    /**
     * 用聚合列表行构建退库入库单分页响应。
     *
     * @param mergedTotal 去重后总单数
     * @param pageNo      页码
     * @param pageSize    页大小
     * @param records     列表行
     * @return 分页响应
     */
    PageReturnStockInsResponse toPageReturnStockInsAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<ReturnStockInListRowDto> records);

    /**
     * 将可关联出库/发放单读模型分页装配为选单响应。
     *
     * @param page 可关联单据分页
     * @return 可关联单据分页响应
     */
    PageLinkableDeliveriesResponse toPageLinkableDeliveriesResponse(Page<WarehouseDocumentDetailQueryModel> page);

    /**
     * 将同号供应请求明细与产品读模型装配为退库入库单详情响应。
     *
     * @param lines         供应请求明细行
     * @param productByKey  产品定义读模型映射
     * @param productHints  行 id → 目录编辑提示（unitBands）
     * @return 详情响应
     */
    GetReturnStockInDetailResponse toGetReturnStockInDetailResponse(
        List<SupplyRequestModel> lines,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto> productHints);

    /**
     * 将新建退库入库单请求装配为领域 CreateReturnCommand（历史写路径，当前 AppService 拒绝）。
     *
     * @param request               新建请求
     * @param operatorId            操作人
     * @param applicantId           制单人员
     * @param originalBusNoSnapshot 关联上游出库单号快照
     * @return 领域写命令
     */
    CreateReturnCommand toCreateReturnStockInCommand(
        CreateReturnStockInRequest request,
        Long operatorId,
        Long applicantId,
        String originalBusNoSnapshot);

    /**
     * 将修订退库入库单请求装配为领域 CreateReturnCommand（同号全量对齐；壳单禁止修订）。
     *
     * @param request               修订请求
     * @param operatorId            操作人
     * @param applicantId           制单人员
     * @param originalBusNoSnapshot 关联上游出库单号快照
     * @return 领域写命令
     */
    CreateReturnCommand toCreateReturnStockInCommandForUpdate(
        UpdateReturnStockInRequest request,
        Long operatorId,
        Long applicantId,
        String originalBusNoSnapshot);

    /**
     * 装配新建退库入库单成功响应（历史写路径）。
     *
     * @param busNo 新建业务单号
     * @return 新建响应
     */
    CreateReturnStockInResponse toCreateReturnStockInResponse(String busNo);

    /**
     * 装配修订退库入库单成功响应（历史写路径）。
     *
     * @param busNo 业务单号
     * @return 修订响应
     */
    UpdateReturnStockInResponse toUpdateReturnStockInResponse(String busNo);

    /**
     * 将提交审批请求装配为领域 SubmitApprovalCommand。
     *
     * @param request     提交请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    SubmitApprovalCommand toSubmitApprovalCommand(
        SubmitReturnStockInApprovalRequest request,
        Long operatorId,
        Long applicantId);

    /**
     * 装配提交审批成功响应。
     *
     * @return 提交响应
     */
    SubmitReturnStockInApprovalResponse toSubmitReturnStockInApprovalResponse();

    /**
     * 将撤回审批请求装配为领域 WithdrawApprovalCommand。
     *
     * @param request    撤回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    WithdrawApprovalCommand toWithdrawApprovalCommand(
        WithdrawReturnStockInApprovalRequest request,
        Long operatorId);

    /**
     * 装配撤回审批成功响应。
     *
     * @return 撤回响应
     */
    WithdrawReturnStockInApprovalResponse toWithdrawReturnStockInApprovalResponse();

    /**
     * 将同意请求装配为领域 AgreeRequestCommand。
     *
     * @param request    同意请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    AgreeRequestCommand toAgreeRequestCommand(AgreeReturnStockInRequest request, Long operatorId);

    /**
     * 将已同意明细行装配为退库入库发放命令（历史 agree 路径）。
     *
     * @param lines      已同意的供应请求明细行
     * @param operatorId 操作人
     * @return 发放命令
     */
    CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommandForReturnStockIn(
        List<SupplyRequestModel> lines,
        Long operatorId);

    /**
     * 装配同意退库入库单成功响应（历史写路径）。
     *
     * @param ok 是否同意成功
     * @return 同意响应
     */
    AgreeReturnStockInResponse toAgreeReturnStockInResponse(boolean ok);

    /**
     * 将驳回请求装配为领域 RejectRequestCommand。
     *
     * @param request    驳回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    RejectRequestCommand toRejectRequestCommand(RejectReturnStockInRequest request, Long operatorId);

    /**
     * 装配驳回退库入库单成功响应（历史写路径）。
     *
     * @param ok 是否驳回成功
     * @return 驳回响应
     */
    RejectReturnStockInResponse toRejectReturnStockInResponse(boolean ok);

    /**
     * 将删除请求装配为领域 RemoveByBusNoCommand。
     *
     * @param request    删除请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveReturnStockInRequest request, Long operatorId);

    /**
     * 装配删除退库入库单成功响应（历史写路径）。
     *
     * @param ok 是否删除成功
     * @return 删除响应
     */
    RemoveReturnStockInResponse toRemoveReturnStockInResponse(boolean ok);

    /**
     * 将产品定义读模型分页装配为退库入库选品分页响应（历史写路径，当前 AppService 拒绝自由选品）。
     *
     * @param page 产品定义读模型分页
     * @return 选品分页响应
     */
    PageProductsForReturnStockInResponse toPageProductsForReturnStockInResponse(
        Page<ProductDefinitionDisplayDto> page);
}
