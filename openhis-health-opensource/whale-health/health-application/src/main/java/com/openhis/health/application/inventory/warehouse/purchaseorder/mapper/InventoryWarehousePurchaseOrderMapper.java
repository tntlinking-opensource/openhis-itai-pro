/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.purchaseorder.mapper;

import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.LinkableDeptPurchaseRequestListRowDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderListRowDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.PurchaseWarningProductRowDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.AgreePurchaseOrderRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.CreatePurchaseOrderRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.RejectPurchaseOrderRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.RemovePurchaseOrderRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.SubmitPurchaseOrderApprovalRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.UpdatePurchaseOrderRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.WithdrawPurchaseOrderApprovalRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.AgreePurchaseOrderResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.CreatePurchaseOrderResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.GetPurchaseOrderDetailResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.LoadPurchaseOrderFormInitResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.GetLinkableDeptPurchaseRequestDetailResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.PageLinkableDeptPurchaseRequestsResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.PageProductsForPurchaseResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.PagePurchaseOrdersResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.PagePurchaseWarningProductsResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.RejectPurchaseOrderResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.RemovePurchaseOrderResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.SubmitPurchaseOrderApprovalResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.UpdatePurchaseOrderResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.WithdrawPurchaseOrderApprovalResponse;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreatePurchaseOrderCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.PageProductsForPurchaseRequest;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.application.shared.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

/**
 * 药库订货单应用层装配（AS-INV-WHS-001；与进货 {@code InventoryWarehouseInboundReceiptMapper} 同构）。
 *
 * <p>负责 Request/Response/DTO 与领域 Command/QueryModel 之间的转换；不在此注入业务 QueryService。</p>
 */
public interface InventoryWarehousePurchaseOrderMapper {

    /**
     * 将启用位置列表映射为 {@link OptionDto} 下拉（B.10.5）。
     *
     * @param locations 启用库位列表
     * @return 下拉选项
     */
    List<OptionDto> toOptionDtoListFromLocations(List<Location> locations);

    /**
     * 将供应商列表映射为 {@link OptionDto} 下拉。
     *
     * @param suppliers 供应商列表项
     * @return 下拉选项
     */
    List<OptionDto> toOptionDtoListFromSuppliers(List<SupplierListItemQueryModel> suppliers);

    /**
     * 装配订货页初始化响应。
     *
     * @param warehouseLocationOptions           药库选项（含「全部」由 AppService 前置）
     * @param departmentPharmacyLocationOptions  科室药房选项（关联申请弹窗，含「全部」）
     * @param supplierOptions                    供应商选项
     * @param warehouseKeeperOptions             库管员选项
     * @param statusOptions                      单据状态选项
     * @param purchaseItemTableTypeOptions       物品来源表选项
     * @return 页初始化响应
     */
    LoadPurchaseOrderFormInitResponse toLoadPurchaseOrderFormInitResponse(
        List<OptionDto> warehouseLocationOptions,
        List<OptionDto> departmentPharmacyLocationOptions,
        List<OptionDto> supplierOptions,
        List<OptionDto> warehouseKeeperOptions,
        List<OptionDto> statusOptions,
        List<OptionDto> purchaseItemTableTypeOptions);

    /**
     * 将医务人员列表映射为下拉选项。
     *
     * @param practitioners 医务人员列表
     * @return 下拉选项
     */
    List<OptionDto> toOptionDtoListFromPractitioners(List<Practitioner> practitioners);

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
    ProductDefinitionPageQuery toProductDefinitionPageQuery(PageProductsForPurchaseRequest request);

    /**
     * 按 busNo 顺序聚合供应请求明细为订货列表行。
     *
     * @param busNoPageOrder 分页单号顺序
     * @param detailRows         同页明细行
     * @return 聚合列表行
     */
    List<PurchaseOrderListRowDto> toPurchaseOrderListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows);

    /**
     * 用聚合列表行构建分页响应（B.10.11）。
     *
     * @param mergedTotal 不重复 busNo 总数
     * @param pageNo      1-based 页码
     * @param pageSize    页大小
     * @param records     当前页聚合行
     * @return 分页响应
     */
    PagePurchaseOrdersResponse toPagePurchaseOrdersAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<PurchaseOrderListRowDto> records);

    /**
     * 将同号供应请求明细与产品读模型装配为订货单详情响应。
     *
     * @param lines        供应请求明细行
     * @param productByKey 产品定义读模型映射
     * @return 详情响应
     */
    GetPurchaseOrderDetailResponse toGetPurchaseOrderDetailResponse(
        List<SupplyRequestModel> lines,
        Map<String, ProductDefinitionDisplayDto> productByKey);

    /**
     * 将新建订货请求装配为领域 CreatePurchaseOrderCommand。
     *
     * @param request     新建请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    CreatePurchaseOrderCommand toCreatePurchaseOrderCommand(
        CreatePurchaseOrderRequest request,
        Long operatorId,
        Long applicantId);

    /**
     * 将修订订货请求装配为领域 CreatePurchaseOrderCommand（同号全量对齐）。
     *
     * @param request     修订请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     * @throws IllegalArgumentException {@code rowVersion} 超出整型范围
     */
    CreatePurchaseOrderCommand toCreatePurchaseOrderCommandForUpdate(
        UpdatePurchaseOrderRequest request,
        Long operatorId,
        Long applicantId);

    /**
     * 装配新建订货成功响应。
     *
     * @param busNo 领域返回的业务单号
     * @return 含 {@code busNo} 与待审批 {@code statusEnum} code
     */
    CreatePurchaseOrderResponse toCreatePurchaseOrderResponse(String busNo);

    /**
     * 装配修订订货成功响应。
     *
     * @param busNo 同号修订后的业务单号
     * @return 含 {@code busNo} 与待审批 {@code statusEnum} code
     */
    UpdatePurchaseOrderResponse toUpdatePurchaseOrderResponse(String busNo);

    /**
     * 将提交审批请求装配为领域 {@link SubmitApprovalCommand}。
     *
     * @param request     提交请求
     * @param operatorId  操作人
     * @param applicantId 制单人员（申请人）
     * @return 领域写命令
     */
    SubmitApprovalCommand toSubmitApprovalCommand(SubmitPurchaseOrderApprovalRequest request, Long operatorId, Long applicantId);

    /**
     * 装配提交审批成功响应。
     *
     * @param status 提交后头行状态，可为 null
     * @return 提交响应
     */
    SubmitPurchaseOrderApprovalResponse toSubmitPurchaseOrderApprovalResponse(SupplyStatus status);

    /**
     * 将撤回审批请求装配为领域 {@link WithdrawApprovalCommand}。
     *
     * @param request    撤回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawPurchaseOrderApprovalRequest request, Long operatorId);

    /**
     * 装配撤回审批成功响应。
     *
     * @param status 撤回后头行状态，可为 null
     * @return 撤回响应
     */
    WithdrawPurchaseOrderApprovalResponse toWithdrawPurchaseOrderApprovalResponse(SupplyStatus status);

    /**
     * 将同意请求装配为领域 {@link AgreeRequestCommand}（订货同意不含发放链）。
     *
     * @param request    同意请求
     * @param operatorId 审批人/操作人
     * @return 领域写命令
     */
    AgreeRequestCommand toAgreeRequestCommand(AgreePurchaseOrderRequest request, Long operatorId);

    /**
     * 装配同意订货成功响应。
     *
     * @return 同意响应
     */
    AgreePurchaseOrderResponse toAgreePurchaseOrderResponse();

    /**
     * 将驳回请求装配为领域 {@link RejectRequestCommand}。
     *
     * @param request    驳回请求
     * @param operatorId 审批人/操作人
     * @return 领域写命令
     */
    RejectRequestCommand toRejectRequestCommand(RejectPurchaseOrderRequest request, Long operatorId);

    /**
     * 装配驳回订货成功响应。
     *
     * @return 驳回响应
     */
    RejectPurchaseOrderResponse toRejectPurchaseOrderResponse();

    /**
     * 将删除请求装配为领域 {@link RemoveByBusNoCommand}。
     *
     * @param request    删除请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    RemoveByBusNoCommand toRemoveByBusNoCommand(RemovePurchaseOrderRequest request, Long operatorId);

    /**
     * 装配删除订货成功响应。
     *
     * @return 删除响应
     */
    RemovePurchaseOrderResponse toRemovePurchaseOrderResponse();

    /**
     * 将产品定义读模型分页装配为订货选品分页响应（B.10.11）。
     *
     * @param page 产品定义读模型分页
     * @return 选品分页响应；进价/unitBands 由 AppService enrich
     */
    PageProductsForPurchaseResponse toPageProductsForPurchaseResponse(Page<ProductDefinitionDisplayDto> page);

    /**
     * 将产品定义列表装配为订货选品分页响应（预警 ids 快捷路径；B.10.11）。
     *
     * @param products 已过滤的产品定义列表（当前页切片前的全量或已切片）
     * @param total    总条数
     * @param pageNo   1-based 页码
     * @param pageSize 每页条数
     * @return 选品分页响应
     */
    PageProductsForPurchaseResponse toPageProductsForPurchaseResponse(
        List<ProductDefinitionDisplayDto> products,
        long total,
        int pageNo,
        int pageSize);

    /**
     * 装配订货预警快捷选品分页响应（IF-WHS-PO-14）。
     *
     * @param rows     当前页行
     * @param total    总条数
     * @param pageNo   1-based 页码
     * @param pageSize 每页条数
     * @return 预警分页响应
     */
    PagePurchaseWarningProductsResponse toPagePurchaseWarningProductsResponse(
        List<PurchaseWarningProductRowDto> rows,
        long total,
        int pageNo,
        int pageSize);

    /**
     * 按 busNo 顺序聚合科室采购申请明细为可关联列表行（IF-WHS-PO-12）。
     *
     * @param busNoPageOrder 分页单号顺序
     * @param detailRows     同页明细行
     * @return 聚合列表行
     */
    List<LinkableDeptPurchaseRequestListRowDto> toLinkableDeptPurchaseRequestListAggregated(
        List<String> busNoPageOrder,
        List<SupplyRequestModel> detailRows);

    /**
     * 用可关联申请聚合行构建分页响应（B.10.11）。
     *
     * @param mergedTotal 不重复 busNo 总数
     * @param pageNo      1-based 页码
     * @param pageSize    页大小
     * @param records     当前页聚合行
     * @return 分页响应
     */
    PageLinkableDeptPurchaseRequestsResponse toPageLinkableDeptPurchaseRequestsAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<LinkableDeptPurchaseRequestListRowDto> records);

    /**
     * 将已同意科室采购申请写模型行映射为订货新建预填响应（IF-WHS-PO-13；只读）。
     *
     * @param deptRequestLines 科室采购申请供应请求行，非空
     * @param productByKey     产品读模型 compositeKey 映射，可空
     * @return 订货表单预填 DTO
     */
    GetLinkableDeptPurchaseRequestDetailResponse toGetLinkableDeptPurchaseRequestDetailResponse(
        List<SupplyRequestModel> deptRequestLines,
        Map<String, ProductDefinitionDisplayDto> productByKey);
}
