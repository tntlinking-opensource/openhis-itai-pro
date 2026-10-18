/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.stocktake.mapper;

import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.application.inventory.pharmacy.stocktake.dto.ProductDefinitionItemDto;
import com.zxplat.application.inventory.pharmacy.stocktake.dto.PharmacyStocktakeDetailLineDto;
import com.zxplat.application.inventory.pharmacy.stocktake.dto.PharmacyStocktakeLineDto;
import com.zxplat.application.inventory.pharmacy.stocktake.dto.PharmacyStocktakeListRowDto;
import com.zxplat.application.inventory.pharmacy.stocktake.request.AgreePharmacyStocktakeRequest;
import com.zxplat.application.inventory.pharmacy.stocktake.request.CreatePharmacyStocktakeRequest;
import com.zxplat.application.inventory.pharmacy.stocktake.request.RejectPharmacyStocktakeRequest;
import com.zxplat.application.inventory.pharmacy.stocktake.request.RemovePharmacyStocktakeRequest;
import com.zxplat.application.inventory.pharmacy.stocktake.request.SubmitPharmacyStocktakeApprovalRequest;
import com.zxplat.application.inventory.pharmacy.stocktake.request.UpdatePharmacyStocktakeRequest;
import com.zxplat.application.inventory.pharmacy.stocktake.request.WithdrawPharmacyStocktakeApprovalRequest;
import com.zxplat.application.inventory.pharmacy.stocktake.response.AgreePharmacyStocktakeResponse;
import com.zxplat.application.inventory.pharmacy.stocktake.response.CreatePharmacyStocktakeResponse;
import com.zxplat.application.inventory.pharmacy.stocktake.response.GetPharmacyStocktakeDetailResponse;
import com.zxplat.application.inventory.pharmacy.stocktake.response.LoadPharmacyStocktakeFormInitResponse;
import com.zxplat.application.inventory.pharmacy.stocktake.response.PageProductsForPharmacyStocktakeResponse;
import com.zxplat.application.inventory.pharmacy.stocktake.response.PagePharmacyStocktakesResponse;
import com.zxplat.application.inventory.pharmacy.stocktake.response.RejectPharmacyStocktakeResponse;
import com.zxplat.application.inventory.pharmacy.stocktake.response.RemovePharmacyStocktakeResponse;
import com.zxplat.application.inventory.pharmacy.stocktake.response.SubmitPharmacyStocktakeApprovalResponse;
import com.zxplat.application.inventory.pharmacy.stocktake.response.UpdatePharmacyStocktakeResponse;
import com.zxplat.application.inventory.pharmacy.stocktake.response.WithdrawPharmacyStocktakeApprovalResponse;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.SupplyCategory;
import com.zxplat.domain.share.enums.SupplyStatus;
import com.zxplat.domain.share.enums.SupplyType;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.domain.workflow.inventoryitem.command.DecreaseQuantityCommand;
import com.zxplat.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.zxplat.domain.workflow.supplydelivery.command.CreateStocktakingDeliveryCommand;
import com.zxplat.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateStocktakeCommand;
import com.zxplat.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.zxplat.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.zxplat.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
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
 * 药房盘点单装配实现（AS-INV-PHA-006-SDD）。
 */
@Component
@RequiredArgsConstructor
public class InventoryPharmacyStocktakeMapperImpl implements InventoryPharmacyStocktakeMapper {

    /** 位置读服务：推导 {@code purposeTypeEnum} 与药房门控。 */
    private final LocationQueryService locationQueryService;

    /**
     * 将位置实体列表装配为盘点页药房下拉（IF-PHA-ST-01）。
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
            if (StringUtils.isEmpty(label)) {
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
            if (StringUtils.isEmpty(label)) {
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
    public LoadPharmacyStocktakeFormInitResponse toLoadPharmacyStocktakeFormInitResponse(
        List<OptionDto> pharmacyOpts,
        List<OptionDto> inboundTypes,
        List<OptionDto> statusOpts,
        List<OptionDto> pharmacistOpts,
        List<OptionDto> keeperOpts) {
        LoadPharmacyStocktakeFormInitResponse res = new LoadPharmacyStocktakeFormInitResponse();
        res.setPharmacyLocationOptions(pharmacyOpts);
        res.setInboundItemTableTypeOptions(inboundTypes);
        res.setStatusOptions(statusOpts);
        res.setPharmacistOptions(pharmacistOpts);
        res.setWarehouseKeeperOptions(keeperOpts);
        return res;
    }

    /** toPharmacyStocktakeListAggregated 映射装配。 */
    @Override
    public List<PharmacyStocktakeListRowDto> toPharmacyStocktakeListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows) {
        List<PharmacyStocktakeListRowDto> out = new ArrayList<>();
        if (busNoPageOrder == null || busNoPageOrder.isEmpty()) {
            return out;
        }
        if (detailRows == null || detailRows.isEmpty()) {
            return out;
        }
        LinkedHashMap<String, List<SupplyRequestModel>> byBusNo = new LinkedHashMap<>();
        for (SupplyRequestModel row : detailRows) {
            if (row == null || StringUtils.isEmpty(row.getBusNo())) {
                continue;
            }
            String bn = row.getBusNo().trim();
            byBusNo.computeIfAbsent(bn, k -> new ArrayList<>()).add(row);
        }
        for (String busNoRaw : busNoPageOrder) {
            if (StringUtils.isEmpty(busNoRaw)) {
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
                        ErrorCodeConstants.PHA_ST_102, "同号盘点单状态头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getPurposeLocationId(), line.getPurposeLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.PHA_ST_102, "同号盘点单盘点药房头字段不一致: " + busNo);
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
            PharmacyStocktakeListRowDto dto = new PharmacyStocktakeListRowDto();
            dto.setBusNo(busNo);
            dto.setStatusEnum(head.getStatusEnum());
            // 驳回态：把供应行 reason 带到列表 rejectReason，供侧栏卡片展示
            if (SupplyStatus.REJECT.getCode().equals(head.getStatusEnum())) {
                dto.setRejectReason(head.getReason());
            }
            dto.setPurposeLocationId(head.getPurposeLocationId());
            dto.setApplicantId(head.getApplicantId());
            dto.setApproverId(head.getApproverId());
            dto.setApplyTime(maxApply);
            dto.setApprovalTime(approvalTime);
            dto.setOccurrenceTime(maxOccurrence);
            dto.setItemCount(Integer.valueOf(itemCount));
            dto.setProfitLossQuantitySum(profitLossSum);
            dto.setActualAmountSum(actualAmountSum);
            out.add(dto);
        }
        return out;
    }

    /** 装配分页查询 Response。 */
    @Override
    public PagePharmacyStocktakesResponse toPagePharmacyStocktakesAggregatedResponse(
        long mergedTotal, int pageNo, int pageSize, List<PharmacyStocktakeListRowDto> records) {
        List<PharmacyStocktakeListRowDto> content = new ArrayList<>();
        if (records != null) {
            content = records;
        }
        return PagedResultBuilder.build(new PagePharmacyStocktakesResponse(), content, mergedTotal, pageNo, pageSize);
    }

    /** 装配单据详情 Response。 */
    @Override
    public GetPharmacyStocktakeDetailResponse toGetPharmacyStocktakeDetailResponse(
        List<SupplyRequestModel> lines,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints) {
        GetPharmacyStocktakeDetailResponse res = new GetPharmacyStocktakeDetailResponse();
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
        List<PharmacyStocktakeDetailLineDto> detailLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            PharmacyStocktakeDetailLineDto dto = this.toPharmacyStocktakeDetailLineDto(line);
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
    public PharmacyStocktakeDetailLineDto toPharmacyStocktakeDetailLineDto(SupplyRequestModel line) {
        if (line == null) {
            return null;
        }
        PharmacyStocktakeDetailLineDto dto = new PharmacyStocktakeDetailLineDto();
        dto.setLineId(line.getId());
        dto.setItemTable(line.getItemTable());
        dto.setItemId(line.getItemId());
        dto.setLotNumber(line.getLotNumber());
        dto.setTraceNo(line.getTraceNo());
        dto.setExpirationDate(line.getEndTime());
        dto.setUnitCode(line.getUnitCode());
        dto.setProfitLossQuantity(line.getItemQuantity());
        dto.setActualQuantity(line.getTotalQuantity());
        dto.setActualAmount(line.getTotalPrice());
        dto.setProfitLossAmount(line.getProfitLossPrice());
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
    public CreateStocktakeCommand toCreateStocktakeCommand(
        CreatePharmacyStocktakeRequest request,
        Long operatorId,
        Long applicantId,
        String busNo) {
        CreateStocktakeCommand command = new CreateStocktakeCommand();
        command.setOperatorId(operatorId);
        List<CreateStocktakeCommand.CreateStocktakeItemCommand> items = new ArrayList<>();
        if (request.getLines() != null) {
            String purposeTypeCode =
                this.locationQueryService.requireSupplyRequestTypeForLocationId(request.getPurposeLocationId()).getCode();
            String categoryCode = this.resolveSupplyCategoryCode(request.getCategoryEnum());
            String typeCode = SupplyType.PHARMACY_STOCKTAKE.getCode();
            for (PharmacyStocktakeLineDto line : request.getLines()) {
                if (line == null) {
                    continue;
                }
                CreateStocktakeCommand.CreateStocktakeItemCommand item = new CreateStocktakeCommand.CreateStocktakeItemCommand();
                item.setBusNo(busNo);
                item.setTypeEnum(typeCode);
                item.setItemTable(line.getItemTable());
                item.setItemId(line.getItemId());
                item.setItemQuantity(line.getProfitLossQuantity());
                item.setTotalQuantity(line.getActualQuantity());
                item.setUnitCode(line.getUnitCode());
                item.setCategoryEnum(categoryCode);
                item.setPurposeTypeEnum(purposeTypeCode);
                item.setPurposeLocationId(request.getPurposeLocationId());
                item.setOccurrenceTime(line.getOccurrenceTime());
                item.setReason(null);
                // 与药库盘点一致：行备注 remake 落库（过渡期日终对齐用其做幂等标记）
                item.setRemake(line.getRemake());
                item.setLotNumber(line.getLotNumber());
                item.setTraceNo(line.getTraceNo());
                item.setPrice(line.getPrice());
                item.setTotalPrice(line.getActualAmount());
                item.setProfitLossPrice(line.getProfitLossAmount());
                // 经手人落库：与药库盘点一致，写入请求中的 practitionerId（勿与制单人 applicantId 混用）
                item.setPractitionerId(request.getPractitionerId());
                item.setApplicantId(applicantId);
                item.setApplyTime(null);
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

    /** 装配新建成功 Response。 */
    @Override
    public CreateStocktakeCommand toCreateStocktakeCommand(
        UpdatePharmacyStocktakeRequest request,
        Long operatorId,
        Long applicantId) {
        if (request == null) {
            throw new IllegalArgumentException("request 不能为空");
        }
        CreatePharmacyStocktakeRequest synthetic = new CreatePharmacyStocktakeRequest();
        synthetic.setPurposeLocationId(request.getPurposeLocationId());
        synthetic.setPractitionerId(request.getPractitionerId());
        synthetic.setCategoryEnum(request.getCategoryEnum());
        synthetic.setLines(request.getLines());
        String busNo = null;
        if (request.getBusNo() != null) {
            busNo = request.getBusNo().trim();
        }
        if (busNo != null && busNo.isEmpty()) {
            busNo = null;
        }
        return this.toCreateStocktakeCommand(synthetic, operatorId, applicantId, busNo);
    }

    /** 装配新建成功 Response。 */
    @Override
    public CreatePharmacyStocktakeResponse toCreatePharmacyStocktakeResponse(String busNo) {
        CreatePharmacyStocktakeResponse res = new CreatePharmacyStocktakeResponse();
        res.setBusNo(busNo);
        return res;
    }

    /** 装配更新成功 Response。 */
    @Override
    public UpdatePharmacyStocktakeResponse toUpdatePharmacyStocktakeResponse(String busNo) {
        UpdatePharmacyStocktakeResponse res = new UpdatePharmacyStocktakeResponse();
        res.setBusNo(busNo);
        return res;
    }

    /** 装配提交审批 Response。 */
    @Override
    public SubmitApprovalCommand toSubmitApprovalCommand(
        SubmitPharmacyStocktakeApprovalRequest request, Long operatorId, Long applicantId) {
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
    public SubmitPharmacyStocktakeApprovalResponse toSubmitPharmacyStocktakeApprovalResponse(SupplyStatus statusAfter) {
        SubmitPharmacyStocktakeApprovalResponse res = new SubmitPharmacyStocktakeApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        if (statusAfter != null) {
            res.setStatusEnum(statusAfter.getCode());
        }
        return res;
    }

    /** 装配撤回审批 Response。 */
    @Override
    public WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawPharmacyStocktakeApprovalRequest request, Long operatorId) {
        WithdrawApprovalCommand command = new WithdrawApprovalCommand();
        command.setOperatorId(operatorId);
        WithdrawApprovalCommand.WithdrawApprovalItemCommand item = new WithdrawApprovalCommand.WithdrawApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /** 装配撤回审批 Response。 */
    @Override
    public WithdrawPharmacyStocktakeApprovalResponse toWithdrawPharmacyStocktakeApprovalResponse(SupplyStatus statusAfter) {
        WithdrawPharmacyStocktakeApprovalResponse res = new WithdrawPharmacyStocktakeApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        if (statusAfter != null) {
            res.setStatusEnum(statusAfter.getCode());
        }
        return res;
    }

    /** 装配同意成功 Response。 */
    @Override
    public AgreeRequestCommand toAgreeRequestCommand(AgreePharmacyStocktakeRequest request, Long operatorId) {
        AgreeRequestCommand command = new AgreeRequestCommand();
        command.setOperatorId(operatorId);
        AgreeRequestCommand.AgreeRequestItemCommand item = new AgreeRequestCommand.AgreeRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        command.setItem(item);
        return command;
    }

    /** 装配盘点发放命令（跳过零盈亏行；全零时 items 为空，由 AppService 跳过落库）。 */
    @Override
    public CreateStocktakingDeliveryCommand toCreateStocktakingDeliveryCommand(
        List<SupplyRequestModel> lines, Long operatorId) {
        CreateStocktakingDeliveryCommand cmd = new CreateStocktakingDeliveryCommand();
        cmd.setOperatorId(operatorId);
        List<CreateStocktakingDeliveryCommand.CreateStocktakingDeliveryItemCommand> items = new ArrayList<>();
        if (lines != null) {
            for (SupplyRequestModel line : lines) {
                if (line == null || line.getId() == null) {
                    continue;
                }
                BigDecimal profitLossQty = line.getItemQuantity();
                if (profitLossQty == null || profitLossQty.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                CreateStocktakingDeliveryCommand.CreateStocktakingDeliveryItemCommand item =
                    new CreateStocktakingDeliveryCommand.CreateStocktakingDeliveryItemCommand();
                item.setRequestId(line.getId());
                item.setTypeEnum(SupplyType.PHARMACY_STOCKTAKE.getCode());
                CreateStocktakingDeliveryCommand.DifferenceLineCommand diff =
                    new CreateStocktakingDeliveryCommand.DifferenceLineCommand();
                diff.setItemTable(line.getItemTable());
                diff.setItemId(line.getItemId());
                diff.setQuantity(profitLossQty);
                diff.setUnitCode(line.getUnitCode());
                diff.setLotNumber(line.getLotNumber());
                List<CreateStocktakingDeliveryCommand.DifferenceLineCommand> diffLines = new ArrayList<>();
                diffLines.add(diff);
                item.setDifferenceLines(diffLines);
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
    public AgreePharmacyStocktakeResponse toAgreePharmacyStocktakeResponse(String busNo, String statusEnum) {
        AgreePharmacyStocktakeResponse res = new AgreePharmacyStocktakeResponse();
        res.setBusNo(busNo);
        res.setStatusEnum(statusEnum);
        return res;
    }

    /** 装配驳回成功 Response。 */
    @Override
    public RejectRequestCommand toRejectRequestCommand(RejectPharmacyStocktakeRequest request, Long operatorId) {
        RejectRequestCommand command = new RejectRequestCommand();
        command.setOperatorId(operatorId);
        RejectRequestCommand.RejectRequestItemCommand item = new RejectRequestCommand.RejectRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        item.setApprovalClock(null);
        String mergedReason;
        if (StringUtils.isEmpty(request.getChangeReason())) {
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
    public RejectPharmacyStocktakeResponse toRejectPharmacyStocktakeResponse(boolean ok) {
        RejectPharmacyStocktakeResponse res = new RejectPharmacyStocktakeResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /** 装配删除成功 Response。 */
    @Override
    public RemoveByBusNoCommand toRemoveByBusNoCommand(RemovePharmacyStocktakeRequest request, Long operatorId) {
        RemoveByBusNoCommand command = new RemoveByBusNoCommand();
        command.setOperatorId(operatorId);
        RemoveByBusNoCommand.RemoveByBusNoItemCommand item = new RemoveByBusNoCommand.RemoveByBusNoItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /** 装配删除成功 Response。 */
    @Override
    public RemovePharmacyStocktakeResponse toRemovePharmacyStocktakeResponse(boolean ok) {
        RemovePharmacyStocktakeResponse res = new RemovePharmacyStocktakeResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /** 装配分页查询 Response。 */
    @Override
    public PageProductsForPharmacyStocktakeResponse toPageProductsForPharmacyStocktakeResponse(Page<InventoryItemModel> page) {
        List<ProductDefinitionItemDto> content = new ArrayList<>();
        for (InventoryItemModel inv : page.getContent()) {
            if (inv == null) {
                continue;
            }
            content.add(this.toProductDefinitionItemDtoFromInventory(inv, null));
        }
        return PagedResultBuilder.build(new PageProductsForPharmacyStocktakeResponse(), content, page);
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

    /**
     * 解析供应类别请求码；未传时回落默认「库存供应」语义。
     *
     * @param categoryEnumCode 请求 categoryEnum，可 blank
     * @return 持久化类别码
     * @throws IllegalArgumentException 码不在枚举白名单
     */
    private String resolveSupplyCategoryCode(String categoryEnumCode) {
        if (StringUtils.isEmpty(categoryEnumCode)) {
            return SupplyCategory.STOCK_SUPPLY.getCode();
        }
        SupplyCategory cat = EnumUtil.fromCode(SupplyCategory.class, categoryEnumCode.trim());
        if (cat == null) {
            throw new IllegalArgumentException("categoryEnum 非法");
        }
        return cat.getCode();
    }

    /**
     * 供应请求行按申请时间降序比较器；申请时间为 null 的排后（列表/详情头选取）。
     *
     * @param a 左侧行
     * @param b 右侧行
     * @return 比较结果
     */
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

    /**
     * 供应请求行按主键升序比较器；主键为 null 的排后（审批时间选取时稳定次序）。
     *
     * @param a 左侧行
     * @param b 右侧行
     * @return 比较结果
     */
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