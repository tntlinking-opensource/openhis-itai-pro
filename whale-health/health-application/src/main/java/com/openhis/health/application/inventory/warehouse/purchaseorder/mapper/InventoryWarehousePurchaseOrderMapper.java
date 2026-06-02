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
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
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
     * toWarehouseOptionDtoList。
     */
    List<WarehouseOptionDto> toWarehouseOptionDtoList(List<Location> locations);

    /**
     * toSupplierOptionDtoList。
     */
    List<SupplierOptionDto> toSupplierOptionDtoList(List<SupplierListItemQueryModel> suppliers);

    LoadPurchaseOrderFormInitResponse toLoadPurchaseOrderFormInitResponse(
        List<WarehouseOptionDto> warehouseOptions,
        List<SupplierOptionDto> supplierOptions);

    /**
     *
     * <p>返回 {@link PagePurchaseOrdersResponse}，继承 {@link PagedResult}，统一分页契约（B.10.11）。</p>
     */
    PagePurchaseOrdersResponse toPagePurchaseOrdersResponse(Page<WarehouseDocumentDetailQueryModel> page);

    GetPurchaseOrderDetailResponse toGetPurchaseOrderDetailResponse(
        List<SupplyRequestModel> lines,
        Map<String, ProductDefinitionDisplayDto> productByKey);

    CreatePurchaseOrderCommand toCreatePurchaseOrderCommand(
        CreatePurchaseOrderRequest request,
        Long operatorId,
        Long applicantId);

    CreatePurchaseOrderCommand toCreatePurchaseOrderCommandForUpdate(
        UpdatePurchaseOrderRequest request,
        Long operatorId,
        Long applicantId);

    /**
     * toCreatePurchaseOrderResponse。
     */
    CreatePurchaseOrderResponse toCreatePurchaseOrderResponse(String busNo);

    /**
     * toUpdatePurchaseOrderResponse。
     */
    UpdatePurchaseOrderResponse toUpdatePurchaseOrderResponse(String busNo);

    /**
     * toSubmitApprovalCommand。
     */
    SubmitApprovalCommand toSubmitApprovalCommand(SubmitPurchaseOrderApprovalRequest request, Long operatorId, Long applicantId);

    /**
     * toSubmitPurchaseOrderApprovalResponse。
     */
    SubmitPurchaseOrderApprovalResponse toSubmitPurchaseOrderApprovalResponse();

    /**
     * toWithdrawApprovalCommand。
     */
    WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawPurchaseOrderApprovalRequest request, Long operatorId);

    /**
     * toWithdrawPurchaseOrderApprovalResponse。
     */
    WithdrawPurchaseOrderApprovalResponse toWithdrawPurchaseOrderApprovalResponse();

    /**
     * toAgreeRequestCommand。
     */
    AgreeRequestCommand toAgreeRequestCommand(AgreePurchaseOrderRequest request, Long operatorId);

    /**
     * toAgreePurchaseOrderResponse。
     */
    AgreePurchaseOrderResponse toAgreePurchaseOrderResponse();

    /**
     * toRejectRequestCommand。
     */
    RejectRequestCommand toRejectRequestCommand(RejectPurchaseOrderRequest request, Long operatorId);

    /**
     * toRejectPurchaseOrderResponse。
     */
    RejectPurchaseOrderResponse toRejectPurchaseOrderResponse();

    /**
     * toRemoveByBusNoCommand。
     */
    RemoveByBusNoCommand toRemoveByBusNoCommand(RemovePurchaseOrderRequest request, Long operatorId);

    /**
     * toRemovePurchaseOrderResponse。
     */
    RemovePurchaseOrderResponse toRemovePurchaseOrderResponse();

    /**
     *
     * <p>返回 {@link PageProductsForPurchaseResponse}，继承 {@link PagedResult}，统一分页契约（B.10.11）。</p>
     */
    PageProductsForPurchaseResponse toPageProductsForPurchaseResponse(Page<ProductDefinitionDisplayDto> page);
}
