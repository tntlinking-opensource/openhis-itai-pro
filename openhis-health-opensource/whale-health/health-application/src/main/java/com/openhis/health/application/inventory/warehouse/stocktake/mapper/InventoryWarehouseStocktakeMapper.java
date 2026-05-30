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

package com.openhis.health.application.inventory.warehouse.stocktake.mapper;

import com.openhis.health.application.common.dto.product.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.application.inventory.warehouse.stocktake.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.warehouse.stocktake.dto.StocktakeDetailLineDto;
import com.openhis.health.application.inventory.warehouse.stocktake.dto.StocktakeListRowDto;
import com.openhis.health.application.inventory.warehouse.stocktake.request.*;
import com.openhis.health.application.inventory.warehouse.stocktake.response.*;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.workflow.inventoryitem.command.DecreaseQuantityCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.health.domain.workflow.supplydelivery.command.CreateStocktakingDeliveryCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateStocktakeCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 *
 * <p>职责边界：仅做字段搬运与枚举 code 归一；不承载库存、审批状态机等业务流程判断。</p>
 */
public interface InventoryWarehouseStocktakeMapper {

    List<OptionDto> toOptionDtoListFromLocations(List<Location> locations);

    List<OptionDto> toOptionDtoListFromPractitioners(List<Practitioner> practitioners);

    List<OptionDto> toOptionDtoListInboundItemTableTypes();

    List<OptionDto> toOptionDtoListSupplyStatuses();

    LoadStocktakeFormInitResponse toLoadStocktakeFormInitResponse(
        List<OptionDto> warehouseOpts,
        List<OptionDto> inboundTypes,
        List<OptionDto> statusOpts,
        List<OptionDto> pharmacistOpts,
        List<OptionDto> keeperOpts);

    List<StocktakeListRowDto> toStocktakeListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows);

    PageStocktakesResponse toPageStocktakesAggregatedResponse(
        long mergedTotal, int pageNo, int pageSize, List<StocktakeListRowDto> records);

    GetStocktakeDetailResponse toGetStocktakeDetailResponse(
        List<SupplyRequestModel> lines,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints);

    StocktakeDetailLineDto toStocktakeDetailLineDto(SupplyRequestModel line);

    CreateStocktakeCommand toCreateStocktakeCommand(
        CreateStocktakeRequest request,
        Long operatorId,
        Long applicantId,
        String busNo);

    CreateStocktakeCommand toCreateStocktakeCommand(
        UpdateStocktakeRequest request,
        Long operatorId,
        Long applicantId);

    CreateStocktakeResponse toCreateStocktakeResponse(String busNo);

    UpdateStocktakeResponse toUpdateStocktakeResponse(String busNo);

    SubmitApprovalCommand toSubmitApprovalCommand(SubmitStocktakeApprovalRequest request, Long operatorId, Long applicantId);

    SubmitStocktakeApprovalResponse toSubmitStocktakeApprovalResponse(SupplyStatus statusAfter);

    WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawStocktakeApprovalRequest request, Long operatorId);

    WithdrawStocktakeApprovalResponse toWithdrawStocktakeApprovalResponse(SupplyStatus statusAfter);

    AgreeRequestCommand toAgreeRequestCommand(AgreeStocktakeRequest request, Long operatorId);

    CreateStocktakingDeliveryCommand toCreateStocktakingDeliveryCommand(List<SupplyRequestModel> lines, Long operatorId);

    IncreaseQuantityCommand toIncreaseQuantityCommand(Long inventoryItemId, BigDecimal increaseQty, Long operatorId);

    DecreaseQuantityCommand toDecreaseQuantityCommand(
        Long inventoryItemId, BigDecimal decreaseQty, boolean allowNegative, Long operatorId);

    AgreeStocktakeResponse toAgreeStocktakeResponse(String busNo, String statusEnum);

    RejectRequestCommand toRejectRequestCommand(RejectStocktakeRequest request, Long operatorId);

    RejectStocktakeResponse toRejectStocktakeResponse(boolean ok);

    RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveStocktakeRequest request, Long operatorId);

    RemoveStocktakeResponse toRemoveStocktakeResponse(boolean ok);

    PageProductsForStocktakeResponse toPageProductsForStocktakeResponse(Page<InventoryItemModel> page);

    ProductDefinitionItemDto toProductDefinitionItemDtoFromInventory(InventoryItemModel inv, ProductDefinitionDisplayDto pr);
}
