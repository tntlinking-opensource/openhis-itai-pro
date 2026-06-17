/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inventory.warehouse.purchaseorder.mapper;

import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.SupplierOptionDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.WarehouseOptionDto;
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
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.PageProductsForPurchaseResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.PagePurchaseOrdersResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.RejectPurchaseOrderResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.RemovePurchaseOrderResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.SubmitPurchaseOrderApprovalResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.UpdatePurchaseOrderResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.WithdrawPurchaseOrderApprovalResponse;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel;
import com.openhis.health.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreatePurchaseOrderCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

/**
 * 药库订货单应用层装配。
 */
public interface InventoryWarehousePurchaseOrderMapper {

    /**
     * 将启用药库位置列表映射为下拉选项 DTO。
     *
     * @param locations 启用库位列表
     * @return 药库下拉选项
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
     * 装配订货页初始化响应。
     *
     * @param warehouseOptions 药库选项
     * @param supplierOptions  供应商选项
     * @return 页初始化响应
     */
    LoadPurchaseOrderFormInitResponse toLoadPurchaseOrderFormInitResponse(
        List<WarehouseOptionDto> warehouseOptions,
        List<SupplierOptionDto> supplierOptions);

    /**
     * 将库房单据读模型分页装配为订货单列表响应。
     *
     * <p>返回 {@link PagePurchaseOrdersResponse}，继承统一分页契约（B.10.11）。</p>
     *
     * @param page 库房单据读模型分页
     * @return 订货单列表分页响应
     */
    PagePurchaseOrdersResponse toPagePurchaseOrdersResponse(Page<WarehouseDocumentDetailQueryModel> page);

    /**
     * 将同号供应请求明细与产品读模型装配为订货单详情响应。
     *
     * @param lines         供应请求明细行
     * @param productByKey  产品定义读模型映射
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
     */
    CreatePurchaseOrderCommand toCreatePurchaseOrderCommandForUpdate(
        UpdatePurchaseOrderRequest request,
        Long operatorId,
        Long applicantId);

    /**
     * 装配新建订货成功响应。
     *
     * @param busNo 新建业务单号
     * @return 新建响应
     */
    CreatePurchaseOrderResponse toCreatePurchaseOrderResponse(String busNo);

    /**
     * 装配修订订货成功响应。
     *
     * @param busNo 业务单号
     * @return 修订响应
     */
    UpdatePurchaseOrderResponse toUpdatePurchaseOrderResponse(String busNo);

    /**
     * 将提交审批请求装配为领域 SubmitApprovalCommand。
     *
     * @param request     提交请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    SubmitApprovalCommand toSubmitApprovalCommand(SubmitPurchaseOrderApprovalRequest request, Long operatorId, Long applicantId);

    /**
     * 装配提交审批成功响应。
     *
     * @return 提交响应
     */
    SubmitPurchaseOrderApprovalResponse toSubmitPurchaseOrderApprovalResponse();

    /**
     * 将撤回审批请求装配为领域 WithdrawApprovalCommand。
     *
     * @param request    撤回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawPurchaseOrderApprovalRequest request, Long operatorId);

    /**
     * 装配撤回审批成功响应。
     *
     * @return 撤回响应
     */
    WithdrawPurchaseOrderApprovalResponse toWithdrawPurchaseOrderApprovalResponse();

    /**
     * 将同意请求装配为领域 AgreeRequestCommand。
     *
     * @param request    同意请求
     * @param operatorId 操作人
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
     * 将驳回请求装配为领域 RejectRequestCommand。
     *
     * @param request    驳回请求
     * @param operatorId 操作人
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
     * 将删除请求装配为领域 RemoveByBusNoCommand。
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
     * 将产品定义读模型分页装配为订货选品分页响应。
     *
     * <p>返回 {@link PageProductsForPurchaseResponse}，继承统一分页契约（B.10.11）。</p>
     *
     * @param page 产品定义读模型分页
     * @return 选品分页响应
     */
    PageProductsForPurchaseResponse toPageProductsForPurchaseResponse(Page<ProductDefinitionDisplayDto> page);
}
