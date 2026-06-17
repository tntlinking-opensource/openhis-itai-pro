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

package com.openhis.health.application.inventory.warehouse.gainloss.mapper;

import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.module.common.pagination.PagedResultBuilder;
import com.openhis.health.application.inventory.warehouse.gainloss.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.warehouse.gainloss.dto.GainLossDetailLineDto;
import com.openhis.health.application.inventory.warehouse.gainloss.dto.GainLossLineDto;
import com.openhis.health.application.inventory.warehouse.gainloss.dto.GainLossListRowDto;
import com.openhis.health.application.inventory.warehouse.gainloss.request.AgreeGainLossRequest;
import com.openhis.health.application.inventory.warehouse.gainloss.request.CreateGainLossRequest;
import com.openhis.health.application.inventory.warehouse.gainloss.request.RejectGainLossRequest;
import com.openhis.health.application.inventory.warehouse.gainloss.request.RemoveGainLossRequest;
import com.openhis.health.application.inventory.warehouse.gainloss.request.SubmitGainLossApprovalRequest;
import com.openhis.health.application.inventory.warehouse.gainloss.request.WithdrawGainLossApprovalRequest;
import com.openhis.health.application.inventory.warehouse.gainloss.response.AgreeGainLossResponse;
import com.openhis.health.application.inventory.warehouse.gainloss.response.CreateGainLossResponse;
import com.openhis.health.application.inventory.warehouse.gainloss.response.GetGainLossDetailResponse;
import com.openhis.health.application.inventory.warehouse.gainloss.response.LoadGainLossFormInitResponse;
import com.openhis.health.application.inventory.warehouse.gainloss.response.PageProductsForGainLossResponse;
import com.openhis.health.application.inventory.warehouse.gainloss.response.PageGainLosssResponse;
import com.openhis.health.application.inventory.warehouse.gainloss.response.RejectGainLossResponse;
import com.openhis.health.application.inventory.warehouse.gainloss.response.RemoveGainLossResponse;
import com.openhis.health.application.inventory.warehouse.gainloss.response.SubmitGainLossApprovalResponse;
import com.openhis.health.application.inventory.warehouse.gainloss.response.WithdrawGainLossApprovalResponse;
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
 * 药库盘库单装配实现：{@link InventoryWarehouseGainLossMapper}。
 *
 * <p>仅做字段搬运与枚举 code 归一；业务流程判断在 {@link InventoryWarehouseGainLossAppService}。
 * 各 {@code @Override} 方法 JavaDoc 与接口契约一致（B.1，禁止单独 {@code @inheritDoc} 占位）。</p>
 */
@Component
public class InventoryWarehouseGainLossMapperImpl implements InventoryWarehouseGainLossMapper {

    /** 位置读服务：按库位推导 {@code purposeTypeEnum}（请求不传类型枚举）。 */
    private final LocationQueryService locationQueryService;

    public InventoryWarehouseGainLossMapperImpl(LocationQueryService locationQueryService) {
        this.locationQueryService = locationQueryService;
    }

    /** 将启用位置列表映射为下拉选项（id/编码/展示名） */
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

    /** 将医务人员列表映射为下拉选项 */
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

    /** 装配供应请求状态枚举选项（持久化 code） */
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

    /** 装配物品来源表（药品/耗材）选项 */
    @Override
    public List<OptionDto> toOptionDtoListInboundItemTableTypes() {
        List<OptionDto> list = new ArrayList<>();
        list.add(new OptionDto(1L, TableNameConstants.MED_MEDICATION_DEFINITION, "药品"));
        list.add(new OptionDto(2L, TableNameConstants.ADM_DEVICE_DEFINITION, "耗材"));
        return list;
    }

    /** 装配页初始化响应（选项集合写入各字段）。 */
    @Override
    public LoadGainLossFormInitResponse toLoadGainLossFormInitResponse(
        List<OptionDto> warehouseOpts,
        List<OptionDto> inboundTypes,
        List<OptionDto> statusOpts,
        List<OptionDto> pharmacistOpts,
        List<OptionDto> keeperOpts) {
        LoadGainLossFormInitResponse res = new LoadGainLossFormInitResponse();
        res.setWarehouseLocationOptions(warehouseOpts);
        res.setInboundItemTableTypeOptions(inboundTypes);
        res.setStatusOptions(statusOpts);
        res.setPharmacistOptions(pharmacistOpts);
        res.setWarehouseKeeperOptions(keeperOpts);
        return res;
    }

    /** 按 busNo 顺序聚合供应请求明细为列表行 DTO。 */
    @Override
    public List<GainLossListRowDto> toGainLossListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows) {
        List<GainLossListRowDto> out = new ArrayList<>();
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
                        ErrorCodeConstants.WHS_GL_102, "同号盘库单状态头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getPurposeLocationId(), line.getPurposeLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_GL_102, "同号盘库单盘点库房头字段不一致: " + busNo);
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
            GainLossListRowDto dto = new GainLossListRowDto();
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

    /** 用聚合列表行构建分页响应。 */
    @Override
    public PageGainLosssResponse toPageGainLosssAggregatedResponse(
        long mergedTotal, int pageNo, int pageSize, List<GainLossListRowDto> records) {
        List<GainLossListRowDto> content = new ArrayList<>();
        if (records != null) {
            content = records;
        }
        return PagedResultBuilder.build(new PageGainLosssResponse(), content, mergedTotal, pageNo, pageSize);
    }

    /** 将供应请求行列表映射为详情响应（含明细增强字段）。 */
    @Override
    public GetGainLossDetailResponse toGetGainLossDetailResponse(
        List<SupplyRequestModel> lines,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints) {
        GetGainLossDetailResponse res = new GetGainLossDetailResponse();
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
        List<GainLossDetailLineDto> detailLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            GainLossDetailLineDto dto = this.toGainLossDetailLineDto(line);
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

    /** 单条供应请求行映射为详情明细 DTO。 */
    @Override
    public GainLossDetailLineDto toGainLossDetailLineDto(SupplyRequestModel line) {
        if (line == null) {
            return null;
        }
        GainLossDetailLineDto dto = new GainLossDetailLineDto();
        dto.setLineId(line.getId());
        dto.setItemTable(line.getItemTable());
        dto.setItemId(line.getItemId());
        dto.setLotNumber(line.getLotNumber());
        dto.setExpirationDate(line.getEndTime());
        dto.setUnitCode(line.getUnitCode());
        dto.setGainLossQuantity(line.getItemQuantity());
        dto.setPrice(line.getPrice());
        if (line.getRemake() != null) {
            dto.setRemake(line.getRemake());
        } else {
            dto.setRemake(line.getReason());
        }
        dto.setOccurrenceTime(line.getOccurrenceTime());
        return dto;
    }

    /** 将写请求映射为领域 Create 命令（含操作人与申请人）。 */
    @Override
    public CreateGainLossCommand toCreateGainLossCommand(
        CreateGainLossRequest request,
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
            String typeCode = SupplyType.CABINET_GAIN_LOSS.getCode();
            for (GainLossLineDto line : request.getLines()) {
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
                item.setReason(line.getReason());
                item.setApplicantId(applicantId);
                item.setApplyTime(null);
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

    /** 装配创建成功响应（写入 busNo 等）。 */
    @Override
    public CreateGainLossResponse toCreateGainLossResponse(String busNo) {
        CreateGainLossResponse res = new CreateGainLossResponse();
        res.setBusNo(busNo);
        return res;
    }

    /** 将提交审批请求映射为 SubmitApprovalCommand。 */
    @Override
    public SubmitApprovalCommand toSubmitApprovalCommand(
        SubmitGainLossApprovalRequest request, Long operatorId, Long applicantId) {
        SubmitApprovalCommand command = new SubmitApprovalCommand();
        command.setOperatorId(operatorId);
        SubmitApprovalCommand.SubmitApprovalItemCommand item = new SubmitApprovalCommand.SubmitApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApplicantId(applicantId);
        command.setItem(item);
        return command;
    }

    /** 装配提交审批响应。 */
    @Override
    public SubmitGainLossApprovalResponse toSubmitGainLossApprovalResponse(SupplyStatus statusAfter) {
        SubmitGainLossApprovalResponse res = new SubmitGainLossApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        if (statusAfter != null) {
            res.setStatusEnum(statusAfter.getCode());
        }
        return res;
    }

    /** 将撤回审批请求映射为 WithdrawApprovalCommand。 */
    @Override
    public WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawGainLossApprovalRequest request, Long operatorId) {
        WithdrawApprovalCommand command = new WithdrawApprovalCommand();
        command.setOperatorId(operatorId);
        WithdrawApprovalCommand.WithdrawApprovalItemCommand item = new WithdrawApprovalCommand.WithdrawApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /** 装配撤回审批响应。 */
    @Override
    public WithdrawGainLossApprovalResponse toWithdrawGainLossApprovalResponse(SupplyStatus statusAfter) {
        WithdrawGainLossApprovalResponse res = new WithdrawGainLossApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        if (statusAfter != null) {
            res.setStatusEnum(statusAfter.getCode());
        }
        return res;
    }

    /** 将同意请求映射为领域 AgreeRequestCommand。 */
    @Override
    public AgreeRequestCommand toAgreeRequestCommand(AgreeGainLossRequest request, Long operatorId) {
        AgreeRequestCommand command = new AgreeRequestCommand();
        command.setOperatorId(operatorId);
        AgreeRequestCommand.AgreeRequestItemCommand item = new AgreeRequestCommand.AgreeRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        command.setItem(item);
        return command;
    }

    /** 将写请求映射为领域 Create 命令（含操作人与申请人）。 将已同意供应请求行装配为发放命令。 */
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
                item.setTypeEnum(SupplyType.CABINET_GAIN_LOSS.getCode());
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

    /** 装配库存加量命令（库存行 id、拆零量、操作人）。 */
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

    /** 装配库存减量命令（库存行 id、拆零量、操作人）。 */
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

    /** 装配同意成功响应。 */
    @Override
    public AgreeGainLossResponse toAgreeGainLossResponse(String busNo, String statusEnum) {
        AgreeGainLossResponse res = new AgreeGainLossResponse();
        res.setBusNo(busNo);
        res.setStatusEnum(statusEnum);
        return res;
    }

    /** 将驳回请求映射为 RejectRequestCommand。 */
    @Override
    public RejectRequestCommand toRejectRequestCommand(RejectGainLossRequest request, Long operatorId) {
        RejectRequestCommand command = new RejectRequestCommand();
        command.setOperatorId(operatorId);
        RejectRequestCommand.RejectRequestItemCommand item = new RejectRequestCommand.RejectRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        item.setApprovalClock(null);
        String mergedReason = request.getChangeReasonCode();
        if (StringUtils.isNotBlank(request.getChangeReason())) {
            mergedReason = request.getChangeReason().trim();
        }
        item.setChangeReasonCode(mergedReason);
        command.setItem(item);
        return command;
    }

    /** 装配驳回响应。 */
    @Override
    public RejectGainLossResponse toRejectGainLossResponse(boolean ok) {
        RejectGainLossResponse res = new RejectGainLossResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /** 将删除请求映射为 RemoveByBusNoCommand。 */
    @Override
    public RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveGainLossRequest request, Long operatorId) {
        RemoveByBusNoCommand command = new RemoveByBusNoCommand();
        command.setOperatorId(operatorId);
        RemoveByBusNoCommand.RemoveByBusNoItemCommand item = new RemoveByBusNoCommand.RemoveByBusNoItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /** 装配删除响应。 */
    @Override
    public RemoveGainLossResponse toRemoveGainLossResponse(boolean ok) {
        RemoveGainLossResponse res = new RemoveGainLossResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /** 将产品/库存分页映射为选品分页响应。 */
    @Override
    public PageProductsForGainLossResponse toPageProductsForGainLossResponse(Page<InventoryItemModel> page) {
        List<ProductDefinitionItemDto> content = new ArrayList<>();
        for (InventoryItemModel inv : page.getContent()) {
            if (inv == null) {
                continue;
            }
            content.add(this.toProductDefinitionItemDtoFromInventory(inv, null));
        }
        return PagedResultBuilder.build(new PageProductsForGainLossResponse(), content, page);
    }

    /** 产品读模型映射为选品行 DTO。 */
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
