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

package com.openhis.health.application.inventory.department.gainloss.mapper;

import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.module.common.pagination.PagedResultBuilder;
import com.openhis.health.application.inventory.department.gainloss.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.department.gainloss.dto.DeptGainLossDetailLineDto;
import com.openhis.health.application.inventory.department.gainloss.dto.DeptGainLossLineDto;
import com.openhis.health.application.inventory.department.gainloss.dto.DeptGainLossListRowDto;
import com.openhis.health.application.inventory.department.gainloss.request.AgreeDeptGainLossRequest;
import com.openhis.health.application.inventory.department.gainloss.request.CreateDeptGainLossRequest;
import com.openhis.health.application.inventory.department.gainloss.request.RejectDeptGainLossRequest;
import com.openhis.health.application.inventory.department.gainloss.request.RemoveDeptGainLossRequest;
import com.openhis.health.application.inventory.department.gainloss.request.SubmitDeptGainLossApprovalRequest;
import com.openhis.health.application.inventory.department.gainloss.request.WithdrawDeptGainLossApprovalRequest;
import com.openhis.health.application.inventory.department.gainloss.response.AgreeDeptGainLossResponse;
import com.openhis.health.application.inventory.department.gainloss.response.CreateDeptGainLossResponse;
import com.openhis.health.application.inventory.department.gainloss.response.GetDeptGainLossDetailResponse;
import com.openhis.health.application.inventory.department.gainloss.response.LoadDeptGainLossFormInitResponse;
import com.openhis.health.application.inventory.department.gainloss.response.PageProductsForDeptGainLossResponse;
import com.openhis.health.application.inventory.department.gainloss.response.PageDeptGainLosssResponse;
import com.openhis.health.application.inventory.department.gainloss.response.RejectDeptGainLossResponse;
import com.openhis.health.application.inventory.department.gainloss.response.RemoveDeptGainLossResponse;
import com.openhis.health.application.inventory.department.gainloss.response.SubmitDeptGainLossApprovalResponse;
import com.openhis.health.application.inventory.department.gainloss.response.WithdrawDeptGainLossApprovalResponse;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.SupplyCategory;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.inventoryitem.command.DecreaseQuantityCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.health.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateGainLossCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 科室盘点单装配实现（AS-INV-DEP-006-SDD）。
 */
@Component
public class InventoryDeptGainLossMapperImpl implements InventoryDeptGainLossMapper {

    /** 位置读服务：推导 {@code purposeTypeEnum} 与科室门控。 */
    private final LocationQueryService locationQueryService;

    public InventoryDeptGainLossMapperImpl(LocationQueryService locationQueryService) {
        this.locationQueryService = locationQueryService;
    }

    /**
     * 将位置实体列表装配为盘点页科室下拉（IF-DEP-ST-01）。
     *
     * @param locations 位置实体列表，可为 null
     * @return 下拉项列表，永不为 null
     */
    @Override
    public List<OptionDto> toOptionDtoListFromLocations(List<Location> locations) {
        List<OptionDto> list = new ArrayList<>();
        if (locations == null) {
            return list;
        }
        for (Location location : locations) {
            if (location == null || location.getId() == null) {
                continue;
            }
            Long id = location.getId();
            String label = location.getName();
            if (StringUtils.isBlank(label)) {
                label = String.valueOf(id);
            }
            list.add(new OptionDto(id, String.valueOf(id), label));
        }
        return list;
    }

    /** 领域实体转下拉选项。 */
    @Override
    public List<OptionDto> toOptionDtoListFromPractitioners(List<Practitioner> practitioners) {
        List<OptionDto> list = new ArrayList<>();
        if (practitioners == null) {
            return list;
        }
        for (Practitioner p : practitioners) {
            if (p == null || p.getId() == null) {
                continue;
            }
            Long id = p.getId();
            String label = p.getName();
            if (StringUtils.isBlank(label)) {
                label = String.valueOf(id);
            }
            list.add(new OptionDto(id, String.valueOf(id), label));
        }
        return list;
    }

    /** 领域实体转下拉选项。 */
    @Override
    public List<OptionDto> toOptionDtoListSupplyStatuses() {
        List<SupplyStatus> ordered = new ArrayList<>();
        for (SupplyStatus st : SupplyStatus.values()) {
            ordered.add(st);
        }
        ordered.sort(Comparator.comparing(SupplyStatus::getSortOrder));
        List<OptionDto> list = new ArrayList<>();
        for (SupplyStatus st : ordered) {
            list.add(new OptionDto(st.getSortOrder(), st.getCode(), st.getDisplay()));
        }
        return list;
    }

    /** 领域实体转下拉选项。 */
    @Override
    public List<OptionDto> toOptionDtoListInboundItemTableTypes() {
        List<OptionDto> list = new ArrayList<>();
        list.add(new OptionDto(1L, TableNameConstants.MED_MEDICATION_DEFINITION, "药品"));
        list.add(new OptionDto(2L, TableNameConstants.ADM_DEVICE_DEFINITION, "耗材"));
        return list;
    }

    /** 装配页主数据初始化 Response。 */
    @Override
    public LoadDeptGainLossFormInitResponse toLoadDeptGainLossFormInitResponse(
        List<OptionDto> deptLocationOpts,
        List<OptionDto> inboundTypes,
        List<OptionDto> statusOpts,
        List<OptionDto> nurseOpts,
        List<OptionDto> keeperOpts) {
        LoadDeptGainLossFormInitResponse res = new LoadDeptGainLossFormInitResponse();
        res.setDeptLocationOptions(deptLocationOpts);
        res.setInboundItemTableTypeOptions(inboundTypes);
        res.setStatusOptions(statusOpts);
        res.setNurseOptions(nurseOpts);
        res.setWarehouseKeeperOptions(keeperOpts);
        return res;
    }

    /** toDeptGainLossListAggregated 映射装配。 */
    @Override
    public List<DeptGainLossListRowDto> toDeptGainLossListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows) {
        List<DeptGainLossListRowDto> out = new ArrayList<>();
        if (busNoPageOrder == null || busNoPageOrder.isEmpty()) {
            return out;
        }
        if (detailRows == null || detailRows.isEmpty()) {
            return out;
        }
        LinkedHashMap<String, List<SupplyRequestModel>> byBusNo = new LinkedHashMap<>();
        for (SupplyRequestModel row : detailRows) {
            if (row == null || StringUtils.isBlank(row.getBusNo())) {
                continue;
            }
            String bn = row.getBusNo().trim();
            byBusNo.computeIfAbsent(bn, k -> new ArrayList<>()).add(row);
        }
        for (String busNoRaw : busNoPageOrder) {
            if (StringUtils.isBlank(busNoRaw)) {
                continue;
            }
            String busNo = busNoRaw.trim();
            List<SupplyRequestModel> lines = byBusNo.get(busNo);
            if (lines == null || lines.isEmpty()) {
                continue;
            }
            List<SupplyRequestModel> sortedByApply = new ArrayList<>(lines);
            sortedByApply.sort(this::compareSupplyModelApplyTimeDescNullsLast);
            SupplyRequestModel head = sortedByApply.get(0);
            for (SupplyRequestModel line : lines) {
                if (line == null) {
                    continue;
                }
                if (!Objects.equals(head.getStatusEnum(), line.getStatusEnum())) {
                    throw new BusinessException(
                        ErrorCodeConstants.DEP_GL_102, "同号盘点单状态头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getPurposeLocationId(), line.getPurposeLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.DEP_GL_102, "同号盘点单盘点科室头字段不一致: " + busNo);
                }
            }
            LocalDateTime maxApply = null;
            LocalDateTime maxOccurrence = null;
            BigDecimal profitLossSum = BigDecimal.ZERO;
            BigDecimal actualAmountSum = BigDecimal.ZERO;
            int itemCount = 0;
            for (SupplyRequestModel line : lines) {
                if (line == null) {
                    continue;
                }
                itemCount++;
                if (line.getApplyTime() != null) {
                    if (maxApply == null || line.getApplyTime().isAfter(maxApply)) {
                        maxApply = line.getApplyTime();
                    }
                }
                if (line.getOccurrenceTime() != null) {
                    if (maxOccurrence == null || line.getOccurrenceTime().isAfter(maxOccurrence)) {
                        maxOccurrence = line.getOccurrenceTime();
                    }
                }
                if (line.getItemQuantity() != null) {
                    profitLossSum = profitLossSum.add(line.getItemQuantity());
                }
                if (line.getTotalPrice() != null) {
                    actualAmountSum = actualAmountSum.add(line.getTotalPrice());
                }
            }
            LocalDateTime approvalTime = null;
            List<SupplyRequestModel> sortedById = new ArrayList<>(lines);
            sortedById.sort(this::compareSupplyModelIdAscNullsLast);
            for (SupplyRequestModel line : sortedById) {
                if (line != null && line.getApprovalTime() != null) {
                    approvalTime = line.getApprovalTime();
                    break;
                }
            }
            DeptGainLossListRowDto dto = new DeptGainLossListRowDto();
            dto.setBusNo(busNo);
            dto.setStatusEnum(head.getStatusEnum());
            dto.setPurposeLocationId(head.getPurposeLocationId());
            dto.setApplicantId(head.getApplicantId());
            dto.setApproverId(head.getApproverId());
            dto.setApplyTime(maxApply);
            dto.setApprovalTime(approvalTime);
            dto.setOccurrenceTime(maxOccurrence);
            dto.setItemCount(Integer.valueOf(itemCount));
            dto.setGainLossQuantitySum(profitLossSum);
            dto.setActualAmountSum(actualAmountSum);
            out.add(dto);
        }
        return out;
    }

    /** 装配分页查询 Response。 */
    @Override
    public PageDeptGainLosssResponse toPageDeptGainLosssAggregatedResponse(
        long mergedTotal, int pageNo, int pageSize, List<DeptGainLossListRowDto> records) {
        List<DeptGainLossListRowDto> content = new ArrayList<>();
        if (records != null) {
            content = records;
        }
        return PagedResultBuilder.build(new PageDeptGainLosssResponse(), content, mergedTotal, pageNo, pageSize);
    }

    /** 装配单据详情 Response。 */
    @Override
    public GetDeptGainLossDetailResponse toGetDeptGainLossDetailResponse(
        List<SupplyRequestModel> lines,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints) {
        GetDeptGainLossDetailResponse res = new GetDeptGainLossDetailResponse();
        if (lines == null || lines.isEmpty()) {
            return res;
        }
        List<SupplyRequestModel> sortedByApply = new ArrayList<>(lines);
        sortedByApply.sort(this::compareSupplyModelApplyTimeDescNullsLast);
        SupplyRequestModel head = sortedByApply.get(0);
        res.setBusNo(head.getBusNo());
        res.setStatusEnum(head.getStatusEnum());
        res.setPurposeLocationId(head.getPurposeLocationId());
        res.setPractitionerId(head.getPractitionerId());
        res.setApplicantId(head.getApplicantId());
        res.setApplyTime(head.getApplyTime());
        res.setApproverId(head.getApproverId());
        LocalDateTime approvalTime = null;
        for (SupplyRequestModel line : lines) {
            if (line != null && line.getApprovalTime() != null) {
                approvalTime = line.getApprovalTime();
                break;
            }
        }
        res.setApprovalTime(approvalTime);
        List<DeptGainLossDetailLineDto> detailLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            DeptGainLossDetailLineDto dto = this.toDeptGainLossDetailLineDto(line);
            if (dto != null && productHints != null && line.getId() != null) {
                ProductDefinitionCatalogEditorHintsDto hint = productHints.get(line.getId());
                if (hint != null) {
                    dto.setProductCatalog(hint);
                }
            }
            if (dto != null) {
                detailLines.add(dto);
            }
        }
        res.setLines(detailLines);
        return res;
    }

    /** 供应行转详情明细 DTO。 */
    @Override
    public DeptGainLossDetailLineDto toDeptGainLossDetailLineDto(SupplyRequestModel line) {
        if (line == null) {
            return null;
        }
        DeptGainLossDetailLineDto dto = new DeptGainLossDetailLineDto();
        dto.setLineId(line.getId());
        dto.setItemTable(line.getItemTable());
        dto.setItemId(line.getItemId());
        dto.setLotNumber(line.getLotNumber());
        dto.setExpirationDate(line.getEndTime());
        dto.setUnitCode(line.getUnitCode());
        dto.setGainLossQuantity(line.getItemQuantity());
        dto.setActualQuantity(line.getTotalQuantity());
        dto.setActualAmount(line.getTotalPrice());
        dto.setGainLossAmount(line.getProfitLossPrice());
        dto.setPrice(line.getPrice());
        dto.setRemake(line.getRemake());
        dto.setOccurrenceTime(line.getOccurrenceTime());
        dto.setProductName(null);
        dto.setSpecification(null);
        dto.setManufacturer(null);
        dto.setBookQuantity(null);
        dto.setProductCatalog(null);
        return dto;
    }

    /** 装配新建成功 Response。 */
    @Override
    public CreateGainLossCommand toCreateGainLossCommand(
        CreateDeptGainLossRequest request,
        Long operatorId,
        Long applicantId,
        String busNo) {
        CreateGainLossCommand command = new CreateGainLossCommand();
        command.setOperatorId(operatorId);
        List<CreateGainLossCommand.CreateGainLossItemCommand> items = new ArrayList<>();
        if (request.getLines() != null) {
            String purposeTypeCode =
                this.locationQueryService.requireSupplyRequestTypeForLocationId(request.getPurposeLocationId()).getCode();
            String categoryCode = this.resolveSupplyCategoryCode(request.getCategoryEnum());
            String typeCode = SupplyType.DEPT_GAIN_LOSS.getCode();
            for (DeptGainLossLineDto line : request.getLines()) {
                if (line == null) {
                    continue;
                }
                CreateGainLossCommand.CreateGainLossItemCommand item = new CreateGainLossCommand.CreateGainLossItemCommand();
                item.setBusNo(busNo);
                item.setTypeEnum(typeCode);
                item.setItemTable(line.getItemTable());
                item.setItemId(line.getItemId());
                item.setItemQuantity(line.getGainLossQuantity());
                item.setUnitCode(line.getUnitCode());
                item.setCategoryEnum(categoryCode);
                item.setPurposeTypeEnum(purposeTypeCode);
                item.setPurposeLocationId(request.getPurposeLocationId());
                item.setOccurrenceTime(line.getOccurrenceTime());
                item.setLotNumber(line.getLotNumber());
                item.setReason(line.getRemake());
                item.setApplicantId(applicantId);
                item.setApplyTime(null);
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }@Override
    public CreateDeptGainLossResponse toCreateDeptGainLossResponse(String busNo) {
        CreateDeptGainLossResponse res = new CreateDeptGainLossResponse();
        res.setBusNo(busNo);
        return res;
    }@Override
    public SubmitApprovalCommand toSubmitApprovalCommand(
        SubmitDeptGainLossApprovalRequest request, Long operatorId, Long applicantId) {
        SubmitApprovalCommand command = new SubmitApprovalCommand();
        command.setOperatorId(operatorId);
        SubmitApprovalCommand.SubmitApprovalItemCommand item = new SubmitApprovalCommand.SubmitApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApplicantId(applicantId);
        command.setItem(item);
        return command;
    }

    /** 装配提交审批 Response。 */
    @Override
    public SubmitDeptGainLossApprovalResponse toSubmitDeptGainLossApprovalResponse(SupplyStatus statusAfter) {
        SubmitDeptGainLossApprovalResponse res = new SubmitDeptGainLossApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        if (statusAfter != null) {
            res.setStatusEnum(statusAfter.getCode());
        }
        return res;
    }

    /** 装配撤回审批 Response。 */
    @Override
    public WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawDeptGainLossApprovalRequest request, Long operatorId) {
        WithdrawApprovalCommand command = new WithdrawApprovalCommand();
        command.setOperatorId(operatorId);
        WithdrawApprovalCommand.WithdrawApprovalItemCommand item = new WithdrawApprovalCommand.WithdrawApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /** 装配撤回审批 Response。 */
    @Override
    public WithdrawDeptGainLossApprovalResponse toWithdrawDeptGainLossApprovalResponse(SupplyStatus statusAfter) {
        WithdrawDeptGainLossApprovalResponse res = new WithdrawDeptGainLossApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        if (statusAfter != null) {
            res.setStatusEnum(statusAfter.getCode());
        }
        return res;
    }

    /** 装配同意成功 Response。 */
    @Override
    public AgreeRequestCommand toAgreeRequestCommand(AgreeDeptGainLossRequest request, Long operatorId) {
        AgreeRequestCommand command = new AgreeRequestCommand();
        command.setOperatorId(operatorId);
        AgreeRequestCommand.AgreeRequestItemCommand item = new AgreeRequestCommand.AgreeRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        command.setItem(item);
        return command;
    }

    /** 装配新建成功 Response。 */
    @Override
    public CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommand(
        List<SupplyRequestModel> lines, Long operatorId) {
        CreateSupplyDeliveryCommand cmd = new CreateSupplyDeliveryCommand();
        cmd.setOperatorId(operatorId);
        List<CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand> items = new ArrayList<>();
        if (lines != null) {
            for (SupplyRequestModel line : lines) {
                if (line == null || line.getId() == null) {
                    continue;
                }
                BigDecimal profitLossQty = line.getItemQuantity();
                if (profitLossQty == null || profitLossQty.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand item =
                    new CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand();
                item.setRequestId(line.getId());
                item.setTypeEnum(SupplyType.DEPT_GAIN_LOSS.getCode());
                item.setItemTable(line.getItemTable());
                item.setItemId(line.getItemId());
                item.setQuantity(profitLossQty.abs());
                item.setUnitCode(line.getUnitCode());
                item.setLotNumber(line.getLotNumber());
                item.setOccurrenceTime(line.getOccurrenceTime());
                items.add(item);
            }
        }
        cmd.setItems(items);
        return cmd;
    }

    /** 装配领域写命令。 */
    @Override
    public IncreaseQuantityCommand toIncreaseQuantityCommand(Long inventoryItemId, BigDecimal increaseQty, Long operatorId) {
        IncreaseQuantityCommand command = new IncreaseQuantityCommand();
        command.setOperatorId(operatorId);
        IncreaseQuantityCommand.IncreaseQuantityItemCommand item = new IncreaseQuantityCommand.IncreaseQuantityItemCommand();
        item.setId(inventoryItemId);
        item.setIncreaseQty(increaseQty);
        command.setItem(item);
        return command;
    }

    /** 装配领域写命令。 */
    @Override
    public DecreaseQuantityCommand toDecreaseQuantityCommand(
        Long inventoryItemId, BigDecimal decreaseQty, boolean allowNegative, Long operatorId) {
        DecreaseQuantityCommand command = new DecreaseQuantityCommand();
        command.setOperatorId(operatorId);
        DecreaseQuantityCommand.DecreaseQuantityItemCommand item = new DecreaseQuantityCommand.DecreaseQuantityItemCommand();
        item.setId(inventoryItemId);
        item.setDecreaseQty(decreaseQty);
        item.setAllowNegative(Boolean.valueOf(allowNegative));
        command.setItem(item);
        return command;
    }

    /** 装配同意成功 Response。 */
    @Override
    public AgreeDeptGainLossResponse toAgreeDeptGainLossResponse(String busNo, String statusEnum) {
        AgreeDeptGainLossResponse res = new AgreeDeptGainLossResponse();
        res.setBusNo(busNo);
        res.setStatusEnum(statusEnum);
        return res;
    }

    /** 装配驳回成功 Response。 */
    @Override
    public RejectRequestCommand toRejectRequestCommand(RejectDeptGainLossRequest request, Long operatorId) {
        RejectRequestCommand command = new RejectRequestCommand();
        command.setOperatorId(operatorId);
        RejectRequestCommand.RejectRequestItemCommand item = new RejectRequestCommand.RejectRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        item.setApprovalClock(null);
        String mergedReason;
        if (StringUtils.isBlank(request.getChangeReason())) {
            mergedReason = request.getChangeReasonCode();
        } else {
            mergedReason = request.getChangeReason();
        }
        item.setChangeReasonCode(mergedReason);
        command.setItem(item);
        return command;
    }

    /** 装配驳回成功 Response。 */
    @Override
    public RejectDeptGainLossResponse toRejectDeptGainLossResponse(boolean ok) {
        RejectDeptGainLossResponse res = new RejectDeptGainLossResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /** 装配删除成功 Response。 */
    @Override
    public RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveDeptGainLossRequest request, Long operatorId) {
        RemoveByBusNoCommand command = new RemoveByBusNoCommand();
        command.setOperatorId(operatorId);
        RemoveByBusNoCommand.RemoveByBusNoItemCommand item = new RemoveByBusNoCommand.RemoveByBusNoItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /** 装配删除成功 Response。 */
    @Override
    public RemoveDeptGainLossResponse toRemoveDeptGainLossResponse(boolean ok) {
        RemoveDeptGainLossResponse res = new RemoveDeptGainLossResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /** 装配分页查询 Response。 */
    @Override
    public PageProductsForDeptGainLossResponse toPageProductsForDeptGainLossResponse(Page<InventoryItemModel> page) {
        List<ProductDefinitionItemDto> content = new ArrayList<>();
        for (InventoryItemModel inv : page.getContent()) {
            if (inv == null) {
                continue;
            }
            content.add(this.toProductDefinitionItemDtoFromInventory(inv, null));
        }
        return PagedResultBuilder.build(new PageProductsForDeptGainLossResponse(), content, page);
    }

    /** toProductDefinitionItemDtoFromInventory 映射装配。 */
    @Override
    public ProductDefinitionItemDto toProductDefinitionItemDtoFromInventory(
        InventoryItemModel inv, ProductDefinitionDisplayDto pr) {
        ProductDefinitionItemDto dto = new ProductDefinitionItemDto();
        dto.setInventoryItemId(inv.getId());
        dto.setProductTableName(inv.getItemTable());
        dto.setProductDefinitionId(inv.getItemId());
        dto.setAvailableQuantity(inv.getQuantity());
        dto.setLotNumber(inv.getLotNumber());
        dto.setExpirationDate(inv.getExpirationDate());
        if (pr != null) {
            dto.setProductBusNo(pr.getProductBusNo());
            dto.setProductName(pr.getProductName());
            dto.setSpecification(pr.getTotalVolume());
            dto.setManufacturer(pr.getManufacturer());
        } else {
            dto.setProductName(null);
            dto.setSpecification(null);
            dto.setManufacturer(null);
        }
        dto.setUnitBands(null);
        dto.setPurchaseUnitPrice(null);
        return dto;
    }

    private String resolveSupplyCategoryCode(String categoryEnumCode) {
        if (StringUtils.isBlank(categoryEnumCode)) {
            return SupplyCategory.STOCK_SUPPLY.getCode();
        }
        SupplyCategory cat = EnumUtil.fromCode(SupplyCategory.class, categoryEnumCode.trim());
        if (cat == null) {
            throw new IllegalArgumentException("categoryEnum 非法");
        }
        return cat.getCode();
    }

    private int compareSupplyModelApplyTimeDescNullsLast(SupplyRequestModel a, SupplyRequestModel b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        LocalDateTime ta = a.getApplyTime();
        LocalDateTime tb = b.getApplyTime();
        if (ta == null && tb == null) {
            return this.compareSupplyModelIdAscNullsLast(a, b);
        }
        if (ta == null) {
            return 1;
        }
        if (tb == null) {
            return -1;
        }
        int cmp = tb.compareTo(ta);
        if (cmp != 0) {
            return cmp;
        }
        return this.compareSupplyModelIdAscNullsLast(a, b);
    }

    private int compareSupplyModelIdAscNullsLast(SupplyRequestModel a, SupplyRequestModel b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        Long ida = a.getId();
        Long idb = b.getId();
        if (ida == null && idb == null) {
            return 0;
        }
        if (ida == null) {
            return 1;
        }
        if (idb == null) {
            return -1;
        }
        return ida.compareTo(idb);
    }
}
