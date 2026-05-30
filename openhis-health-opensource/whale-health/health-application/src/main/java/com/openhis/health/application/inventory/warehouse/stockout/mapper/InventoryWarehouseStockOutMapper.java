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

package com.openhis.health.application.inventory.warehouse.stockout.mapper;

import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.WarehouseDocumentDetailRowDto;
import com.openhis.health.application.inventory.warehouse.stockout.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.warehouse.stockout.dto.StockOutDetailLineDto;
import com.openhis.health.application.inventory.warehouse.stockout.dto.StockOutListRowDto;
import com.openhis.health.application.inventory.warehouse.stockout.request.*;
import com.openhis.health.application.inventory.warehouse.stockout.response.*;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.health.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateRequisitionCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateStockOutCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

/**
 *
 * <p>职责边界：仅做字段搬运与枚举 code 归一；不承载库存、审批状态机等业务流程判断（见实现类与 {@link com.openhis.health.application.inventory.warehouse.stockout.service.InventoryWarehouseStockOutAppService}）。</p>
 */
public interface InventoryWarehouseStockOutMapper {

    List<OptionDto> toOptionDtoListFromLocations(List<Location> locations);

    List<OptionDto> toOptionDtoListFromPractitioners(List<Practitioner> practitioners);

    List<OptionDto> toOptionDtoListInboundItemTableTypes();

    List<OptionDto> toOptionDtoListSupplyStatuses();

    LoadStockOutFormInitResponse toLoadStockOutFormInitResponse(
        List<OptionDto> warehouseOpts,
        List<OptionDto> pharmacyOpts,
        List<OptionDto> inboundTypes,
        List<OptionDto> statusOpts,
        List<OptionDto> pharmacistOpts,
        List<OptionDto> keeperOpts);

    List<StockOutListRowDto> toStockOutListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows);

    PageStockOutsResponse toPageStockOutsAggregatedResponse(long mergedTotal, int pageNo, int pageSize, List<StockOutListRowDto> records);

    GetStockOutDetailResponse toGetStockOutDetailResponse(
        List<SupplyRequestModel> lines,
        Map<Long, com.openhis.health.application.common.dto.product.ProductDefinitionCatalogEditorHintsDto> productHints);

    StockOutDetailLineDto toStockOutDetailLineDto(SupplyRequestModel line);

    CreateStockOutCommand toCreateStockOutCommand(
        CreateStockOutRequest request,
        Long operatorId,
        Long applicantId,
        String busNo);

    /**
     * 实现上塌缩为 {@link CreateStockOutRequest} 后委托 {@link #toCreateStockOutCommand(CreateStockOutRequest, Long, Long, String)}。
     *
     * @param request     修订请求，不得为 null
     * @param operatorId  操作人主键
     * @param applicantId 申请人主键
     * @return 领域创建命令（携带既有 {@code busNo}）
     * @throws IllegalArgumentException 装配过程中枚举或必填字段非法
     */
    CreateStockOutCommand toCreateStockOutCommand(
        UpdateStockOutRequest request,
        Long operatorId,
        Long applicantId);

    CreateStockOutResponse toCreateStockOutResponse(String busNo);

    UpdateStockOutResponse toUpdateStockOutResponse(String busNo);

    SubmitApprovalCommand toSubmitApprovalCommand(SubmitStockOutApprovalRequest request, Long operatorId, Long applicantId);

    SubmitStockOutApprovalResponse toSubmitStockOutApprovalResponse(SupplyStatus statusAfter);

    WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawStockOutApprovalRequest request, Long operatorId);

    WithdrawStockOutApprovalResponse toWithdrawStockOutApprovalResponse(SupplyStatus statusAfter);

    AgreeRequestCommand toAgreeRequestCommand(AgreeStockOutRequest request, Long operatorId);

    CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommandForStockOut(List<SupplyRequestModel> lines, Long operatorId);

    CreateRequisitionCommand toCreateRequisitionCommandForShellPharmacyStockIn(
        List<SupplyRequestModel> agreedOutLines,
        String outboundBusNo,
        Long operatorId,
        Long applicantId);

    AgreeStockOutResponse toAgreeStockOutResponse(String outboundBusNo, String statusEnum, String generatedInBusNo);

    /**
     *
     * <p>原因合并：{@code changeReason} 非空白时写入领域驳回说明，否则回落 {@code changeReasonCode}（人读审计优先）。</p>
     *
     * @param request    驳回请求，不得为 null
     * @param operatorId 操作人主键（实现侧与审批人口径对齐）
     * @return 领域 {@link RejectRequestCommand}，永不为 null
     */
    RejectRequestCommand toRejectRequestCommand(RejectStockOutRequest request, Long operatorId);

    RejectStockOutResponse toRejectStockOutResponse(boolean ok);

    RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveStockOutRequest request, Long operatorId);

    RemoveStockOutResponse toRemoveStockOutResponse(boolean ok);

    PageProductsForStockOutResponse toPageProductsForStockOutResponse(Page<InventoryItemModel> page);

    PageLinkableRequisitionsResponse toPageLinkableRequisitionsResponse(Page<WarehouseDocumentDetailQueryModel> page);

    WarehouseDocumentDetailRowDto toWarehouseDocumentDetailRowDto(WarehouseDocumentDetailQueryModel rm);

    ProductDefinitionItemDto toProductDefinitionItemDtoFromInventory(InventoryItemModel inv, ProductDefinitionDisplayDto pr);
}
