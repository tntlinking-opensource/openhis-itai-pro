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

package com.openhis.health.application.inventory.pharmacy.stocktake.mapper;

import com.openhis.health.application.common.dto.product.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.application.inventory.pharmacy.stocktake.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.pharmacy.stocktake.dto.PharmacyStocktakeDetailLineDto;
import com.openhis.health.application.inventory.pharmacy.stocktake.dto.PharmacyStocktakeListRowDto;
import com.openhis.health.application.inventory.pharmacy.stocktake.request.*;
import com.openhis.health.application.inventory.pharmacy.stocktake.response.*;
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
 * 药房盘点单应用层装配（AS-INV-PHA-006 / SDD IF-PHA-ST-01～11）。
 *
 * <p>仅做 Request/Response 与领域 Command/Query 模型之间的字段搬运；禁止承载业务流程判断。</p>
 */
public interface InventoryPharmacyStocktakeMapper {

    List<OptionDto> toOptionDtoListFromLocations(List<Location> locations);

    List<OptionDto> toOptionDtoListFromPractitioners(List<Practitioner> practitioners);

    List<OptionDto> toOptionDtoListInboundItemTableTypes();

    List<OptionDto> toOptionDtoListSupplyStatuses();

    LoadPharmacyStocktakeFormInitResponse toLoadPharmacyStocktakeFormInitResponse(
        List<OptionDto> pharmacyOpts,
        List<OptionDto> inboundTypes,
        List<OptionDto> statusOpts,
        List<OptionDto> pharmacistOpts,
        List<OptionDto> keeperOpts);

    List<PharmacyStocktakeListRowDto> toPharmacyStocktakeListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows);

    PagePharmacyStocktakesResponse toPagePharmacyStocktakesAggregatedResponse(
        long mergedTotal, int pageNo, int pageSize, List<PharmacyStocktakeListRowDto> records);

    GetPharmacyStocktakeDetailResponse toGetPharmacyStocktakeDetailResponse(
        List<SupplyRequestModel> lines,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints);

    PharmacyStocktakeDetailLineDto toPharmacyStocktakeDetailLineDto(SupplyRequestModel line);

    CreateStocktakeCommand toCreateStocktakeCommand(
        CreatePharmacyStocktakeRequest request,
        Long operatorId,
        Long applicantId,
        String busNo);

    CreateStocktakeCommand toCreateStocktakeCommand(
        UpdatePharmacyStocktakeRequest request,
        Long operatorId,
        Long applicantId);

    CreatePharmacyStocktakeResponse toCreatePharmacyStocktakeResponse(String busNo);

    UpdatePharmacyStocktakeResponse toUpdatePharmacyStocktakeResponse(String busNo);

    SubmitApprovalCommand toSubmitApprovalCommand(SubmitPharmacyStocktakeApprovalRequest request, Long operatorId, Long applicantId);

    SubmitPharmacyStocktakeApprovalResponse toSubmitPharmacyStocktakeApprovalResponse(SupplyStatus statusAfter);

    WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawPharmacyStocktakeApprovalRequest request, Long operatorId);

    WithdrawPharmacyStocktakeApprovalResponse toWithdrawPharmacyStocktakeApprovalResponse(SupplyStatus statusAfter);

    AgreeRequestCommand toAgreeRequestCommand(AgreePharmacyStocktakeRequest request, Long operatorId);

    CreateStocktakingDeliveryCommand toCreateStocktakingDeliveryCommand(List<SupplyRequestModel> lines, Long operatorId);

    IncreaseQuantityCommand toIncreaseQuantityCommand(Long inventoryItemId, BigDecimal increaseQty, Long operatorId);

    DecreaseQuantityCommand toDecreaseQuantityCommand(
        Long inventoryItemId, BigDecimal decreaseQty, boolean allowNegative, Long operatorId);

    AgreePharmacyStocktakeResponse toAgreePharmacyStocktakeResponse(String busNo, String statusEnum);

    RejectRequestCommand toRejectRequestCommand(RejectPharmacyStocktakeRequest request, Long operatorId);

    RejectPharmacyStocktakeResponse toRejectPharmacyStocktakeResponse(boolean ok);

    RemoveByBusNoCommand toRemoveByBusNoCommand(RemovePharmacyStocktakeRequest request, Long operatorId);

    RemovePharmacyStocktakeResponse toRemovePharmacyStocktakeResponse(boolean ok);

    PageProductsForPharmacyStocktakeResponse toPageProductsForPharmacyStocktakeResponse(Page<InventoryItemModel> page);

    ProductDefinitionItemDto toProductDefinitionItemDtoFromInventory(InventoryItemModel inv, ProductDefinitionDisplayDto pr);
}
