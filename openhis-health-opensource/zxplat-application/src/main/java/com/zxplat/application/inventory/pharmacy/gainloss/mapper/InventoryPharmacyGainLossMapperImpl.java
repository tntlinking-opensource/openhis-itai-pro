/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.gainloss.mapper;

import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.application.inventory.pharmacy.gainloss.dto.ProductDefinitionItemDto;
import com.zxplat.application.inventory.pharmacy.gainloss.dto.PharmacyGainLossDetailLineDto;
import com.zxplat.application.inventory.pharmacy.gainloss.dto.PharmacyGainLossLineDto;
import com.zxplat.application.inventory.pharmacy.gainloss.dto.PharmacyGainLossListRowDto;
import com.zxplat.application.inventory.pharmacy.gainloss.request.AgreePharmacyGainLossRequest;
import com.zxplat.application.inventory.pharmacy.gainloss.request.CreatePharmacyGainLossRequest;
import com.zxplat.application.inventory.pharmacy.gainloss.request.RejectPharmacyGainLossRequest;
import com.zxplat.application.inventory.pharmacy.gainloss.request.RemovePharmacyGainLossRequest;
import com.zxplat.application.inventory.pharmacy.gainloss.request.SubmitPharmacyGainLossApprovalRequest;
import com.zxplat.application.inventory.pharmacy.gainloss.request.UpdatePharmacyGainLossRequest;
import com.zxplat.application.inventory.pharmacy.gainloss.request.WithdrawPharmacyGainLossApprovalRequest;
import com.zxplat.application.inventory.pharmacy.gainloss.response.AgreePharmacyGainLossResponse;
import com.zxplat.application.inventory.pharmacy.gainloss.response.CreatePharmacyGainLossResponse;
import com.zxplat.application.inventory.pharmacy.gainloss.response.GetPharmacyGainLossDetailResponse;
import com.zxplat.application.inventory.pharmacy.gainloss.response.LoadPharmacyGainLossFormInitResponse;
import com.zxplat.application.inventory.pharmacy.gainloss.response.PageProductsForPharmacyGainLossResponse;
import com.zxplat.application.inventory.pharmacy.gainloss.response.PagePharmacyGainLosssResponse;
import com.zxplat.application.inventory.pharmacy.gainloss.response.RejectPharmacyGainLossResponse;
import com.zxplat.application.inventory.pharmacy.gainloss.response.RemovePharmacyGainLossResponse;
import com.zxplat.application.inventory.pharmacy.gainloss.response.SubmitPharmacyGainLossApprovalResponse;
import com.zxplat.application.inventory.pharmacy.gainloss.response.UpdatePharmacyGainLossResponse;
import com.zxplat.application.inventory.pharmacy.gainloss.response.WithdrawPharmacyGainLossApprovalResponse;
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
import com.zxplat.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand;
import com.zxplat.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateGainLossCommand;
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
 * 药房损益单装配实现：{@link InventoryPharmacyGainLossMapper}。
 *
 * <p>仅做字段搬运与枚举 code 归一；业务流程判断在 {@link InventoryPharmacyGainLossAppService}。
 * 各 {@code @Override} 方法 JavaDoc 与接口契约一致（B.1，禁止单独 {@code @inheritDoc} 占位）。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryPharmacyGainLossMapperImpl implements InventoryPharmacyGainLossMapper {

    /** 位置读服务：推导 {@code purposeTypeEnum} 与药房门控。 */
    private final LocationQueryService locationQueryService;

    /**
     * 将位置实体列表装配为损益页药房下拉。
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

    /**
     * 将医务人员列表映射为下拉选项。
     *
     * @param practitioners 人员列表，可为 null
     * @return 人员下拉选项，永不为 null
     */
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

    /**
     * 装配供应请求状态枚举选项（持久化 code，按 sortOrder 排序）。
     *
     * @return 单据状态下拉选项
     */
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

    /**
     * 装配物品来源表（药品/耗材）固定两档选项。
     *
     * @return 物品表下拉选项
     */
    @Override
    public List<OptionDto> toOptionDtoListInboundItemTableTypes() {
        List<OptionDto> list = new ArrayList<>();
        list.add(new OptionDto(1L, TableNameConstants.MED_MEDICATION_DEFINITION, "药品"));
        list.add(new OptionDto(2L, TableNameConstants.ADM_DEVICE_DEFINITION, "耗材"));
        return list;
    }

    /**
     * 装配损益页初始化响应（选项集合写入各字段）。
     *
     * @param pharmacyOpts   药房下拉
     * @param inboundTypes   物品表类型下拉
     * @param statusOpts     状态下拉
     * @param pharmacistOpts 药师下拉
     * @param keeperOpts     库管下拉
     * @return 页初始化响应
     */
    @Override
    public LoadPharmacyGainLossFormInitResponse toLoadPharmacyGainLossFormInitResponse(
        List<OptionDto> pharmacyOpts,
        List<OptionDto> inboundTypes,
        List<OptionDto> statusOpts,
        List<OptionDto> pharmacistOpts,
        List<OptionDto> keeperOpts) {
        LoadPharmacyGainLossFormInitResponse res = new LoadPharmacyGainLossFormInitResponse();
        res.setPharmacyLocationOptions(pharmacyOpts);
        res.setInboundItemTableTypeOptions(inboundTypes);
        res.setStatusOptions(statusOpts);
        res.setPharmacistOptions(pharmacistOpts);
        res.setWarehouseKeeperOptions(keeperOpts);
        return res;
    }

    /**
     * 按 busNo 顺序聚合供应请求明细为列表行 DTO。
     *
     * <p>同号各行状态/药房须一致；汇总盈亏量、实盘金额与最新申请时间。</p>
     *
     * @param busNoPageOrder 当前页单号顺序
     * @param detailRows     当前页涉及的全部明细行
     * @return 聚合后的列表行
     */
    @Override
    public List<PharmacyGainLossListRowDto> toPharmacyGainLossListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows) {
        List<PharmacyGainLossListRowDto> out = new ArrayList<>();
        if (busNoPageOrder == null || busNoPageOrder.isEmpty()) {
            return out;
        }
        if (detailRows == null || detailRows.isEmpty()) {
            return out;
        }
        // 按 busNo 分组明细行
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
            // 同号头字段一致性校验，防止列表聚合脏数据
            for (SupplyRequestModel line : lines) {
                if (line == null) {
                    continue;
                }
                if (!Objects.equals(head.getStatusEnum(), line.getStatusEnum())) {
                    throw new BusinessException(
                        ErrorCodeConstants.PHA_GL_102, "同号损益单状态头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getPurposeLocationId(), line.getPurposeLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.PHA_GL_102, "同号损益单药房头字段不一致: " + busNo);
                }
            }
            LocalDateTime maxApply = null;
            LocalDateTime maxOccurrence = null;
            BigDecimal profitLossSum = BigDecimal.ZERO;
            BigDecimal gainLossAmountSum = BigDecimal.ZERO;
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
                // 侧栏总金额：盈亏金额（可负），勿用实盘 totalPrice
                if (line.getProfitLossPrice() != null) {
                    gainLossAmountSum = gainLossAmountSum.add(line.getProfitLossPrice());
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
            PharmacyGainLossListRowDto dto = new PharmacyGainLossListRowDto();
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
            dto.setGainLossQuantitySum(profitLossSum);
            dto.setGainLossAmountSum(gainLossAmountSum);
            dto.setActualAmountSum(actualAmountSum);
            out.add(dto);
        }
        return out;
    }

    /**
     * 用聚合列表行构建分页响应。
     *
     * @param mergedTotal 去重后的总单数
     * @param pageNo      页码（1-based）
     * @param pageSize    页大小
     * @param records     当前页聚合行
     * @return 分页响应
     */
    @Override
    public PagePharmacyGainLosssResponse toPagePharmacyGainLosssAggregatedResponse(
        long mergedTotal, int pageNo, int pageSize, List<PharmacyGainLossListRowDto> records) {
        List<PharmacyGainLossListRowDto> content = new ArrayList<>();
        if (records != null) {
            content = records;
        }
        return PagedResultBuilder.build(new PagePharmacyGainLosssResponse(), content, mergedTotal, pageNo, pageSize);
    }

    /**
     * 将供应请求行列表映射为详情响应（含明细与目录提示）。
     *
     * @param lines        供应请求明细行
     * @param productHints 行 id 到目录编辑提示的映射
     * @return 详情响应
     */
    @Override
    public GetPharmacyGainLossDetailResponse toGetPharmacyGainLossDetailResponse(
        List<SupplyRequestModel> lines,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints) {
        GetPharmacyGainLossDetailResponse res = new GetPharmacyGainLossDetailResponse();
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
        List<PharmacyGainLossDetailLineDto> detailLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            PharmacyGainLossDetailLineDto dto = this.toPharmacyGainLossDetailLineDto(line);
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

    /**
     * 单条供应请求行映射为详情明细 DTO。
     *
     * @param line 供应请求明细行
     * @return 详情行 DTO；line 为 null 时返回 null
     */
    @Override
    public PharmacyGainLossDetailLineDto toPharmacyGainLossDetailLineDto(SupplyRequestModel line) {
        if (line == null) {
            return null;
        }
        PharmacyGainLossDetailLineDto dto = new PharmacyGainLossDetailLineDto();
        dto.setLineId(line.getId());
        dto.setItemTable(line.getItemTable());
        dto.setItemId(line.getItemId());
        dto.setLotNumber(line.getLotNumber());
        dto.setTraceNo(line.getTraceNo());
        dto.setExpirationDate(line.getEndTime());
        dto.setUnitCode(line.getUnitCode());
        dto.setGainLossQuantity(line.getItemQuantity());
        dto.setActualQuantity(line.getTotalQuantity());
        dto.setActualAmount(line.getTotalPrice());
        dto.setGainLossAmount(line.getProfitLossPrice());
        dto.setPrice(line.getPrice());
        dto.setReason(line.getReason());
        dto.setRemake(line.getRemake());
        dto.setOccurrenceTime(line.getOccurrenceTime());
        dto.setProductName(null);
        dto.setSpecification(null);
        dto.setManufacturer(null);
        dto.setBookQuantity(null);
        dto.setProductCatalog(null);
        return dto;
    }

    /**
     * 将新建药房损益请求装配为领域 CreateGainLossCommand。
     *
     * @param request     新建请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @param busNo       预分配业务单号；新建时为 null
     * @return 领域写命令
     */
    @Override
    public CreateGainLossCommand toCreateGainLossCommand(
        CreatePharmacyGainLossRequest request,
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
            String typeCode = SupplyType.PHARMACY_GAIN_LOSS.getCode();
            // 逐行映射损益明细为领域 item 命令
            for (PharmacyGainLossLineDto line : request.getLines()) {
                if (line == null) {
                    continue;
                }
                CreateGainLossCommand.CreateGainLossItemCommand item = new CreateGainLossCommand.CreateGainLossItemCommand();
                item.setBusNo(busNo);
                item.setTypeEnum(typeCode);
                item.setItemTable(line.getItemTable());
                item.setItemId(line.getItemId());
                item.setItemQuantity(line.getGainLossQuantity());
                item.setTotalQuantity(line.getActualQuantity());
                item.setUnitCode(line.getUnitCode());
                item.setCategoryEnum(categoryCode);
                item.setPurposeTypeEnum(purposeTypeCode);
                item.setPurposeLocationId(request.getPurposeLocationId());
                item.setOccurrenceTime(line.getOccurrenceTime());
                item.setPractitionerId(request.getPractitionerId());
                item.setReason(line.getReason());
                item.setRemake(line.getRemake());
                item.setLotNumber(line.getLotNumber());
                item.setTraceNo(line.getTraceNo());
                item.setPrice(line.getPrice());
                item.setTotalPrice(line.getActualAmount());
                item.setProfitLossPrice(line.getGainLossAmount());
                item.setApplicantId(applicantId);
                item.setApplyTime(null);
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

    /**
     * 将修订药房损益请求装配为领域 CreateGainLossCommand（同号全量对齐）。
     *
     * @param request     修订请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    @Override
    public CreateGainLossCommand toCreateGainLossCommand(
        UpdatePharmacyGainLossRequest request,
        Long operatorId,
        Long applicantId) {
        if (request == null) {
            throw new IllegalArgumentException("request 不能为空");
        }
        CreatePharmacyGainLossRequest synthetic = new CreatePharmacyGainLossRequest();
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
        return this.toCreateGainLossCommand(synthetic, operatorId, applicantId, busNo);
    }

    /**
     * 装配新建药房损益成功响应。
     *
     * @param busNo 新建业务单号
     * @return 新建响应
     */
    @Override
    public CreatePharmacyGainLossResponse toCreatePharmacyGainLossResponse(String busNo) {
        CreatePharmacyGainLossResponse res = new CreatePharmacyGainLossResponse();
        res.setBusNo(busNo);
        return res;
    }

    /**
     * 装配修订药房损益成功响应。
     *
     * @param busNo 业务单号
     * @return 修订响应
     */
    @Override
    public UpdatePharmacyGainLossResponse toUpdatePharmacyGainLossResponse(String busNo) {
        UpdatePharmacyGainLossResponse res = new UpdatePharmacyGainLossResponse();
        res.setBusNo(busNo);
        return res;
    }

    /**
     * 将提交审批请求装配为领域 SubmitApprovalCommand。
     *
     * @param request     提交请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    @Override
    public SubmitApprovalCommand toSubmitApprovalCommand(
        SubmitPharmacyGainLossApprovalRequest request, Long operatorId, Long applicantId) {
        SubmitApprovalCommand command = new SubmitApprovalCommand();
        command.setOperatorId(operatorId);
        SubmitApprovalCommand.SubmitApprovalItemCommand item = new SubmitApprovalCommand.SubmitApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApplicantId(applicantId);
        command.setItem(item);
        return command;
    }

    /**
     * 装配提交审批成功响应（含提交后展示状态）。
     *
     * @param statusAfter 提交后供应状态
     * @return 提交响应
     */
    @Override
    public SubmitPharmacyGainLossApprovalResponse toSubmitPharmacyGainLossApprovalResponse(SupplyStatus statusAfter) {
        SubmitPharmacyGainLossApprovalResponse res = new SubmitPharmacyGainLossApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        if (statusAfter != null) {
            res.setStatusEnum(statusAfter.getCode());
        }
        return res;
    }

    /**
     * 将撤回审批请求装配为领域 WithdrawApprovalCommand。
     *
     * @param request    撤回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    @Override
    public WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawPharmacyGainLossApprovalRequest request, Long operatorId) {
        WithdrawApprovalCommand command = new WithdrawApprovalCommand();
        command.setOperatorId(operatorId);
        WithdrawApprovalCommand.WithdrawApprovalItemCommand item = new WithdrawApprovalCommand.WithdrawApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /**
     * 装配撤回审批成功响应（含撤回后展示状态）。
     *
     * @param statusAfter 撤回后供应状态
     * @return 撤回响应
     */
    @Override
    public WithdrawPharmacyGainLossApprovalResponse toWithdrawPharmacyGainLossApprovalResponse(SupplyStatus statusAfter) {
        WithdrawPharmacyGainLossApprovalResponse res = new WithdrawPharmacyGainLossApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        if (statusAfter != null) {
            res.setStatusEnum(statusAfter.getCode());
        }
        return res;
    }

    /**
     * 将同意请求装配为领域 AgreeRequestCommand。
     *
     * @param request    同意请求
     * @param operatorId 操作人（同时作为 approverId）
     * @return 领域写命令
     */
    @Override
    public AgreeRequestCommand toAgreeRequestCommand(AgreePharmacyGainLossRequest request, Long operatorId) {
        AgreeRequestCommand command = new AgreeRequestCommand();
        command.setOperatorId(operatorId);
        AgreeRequestCommand.AgreeRequestItemCommand item = new AgreeRequestCommand.AgreeRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        command.setItem(item);
        return command;
    }

    /**
     * 将已同意明细行装配为损益发放命令（跳过盈亏量为零的行）。
     *
     * @param lines      已同意的供应请求明细行
     * @param operatorId 操作人
     * @return 发放命令
     */
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
                // 零盈亏行不参与发放
                if (profitLossQty == null || profitLossQty.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand item =
                    new CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand();
                item.setRequestId(line.getId());
                item.setTypeEnum(SupplyType.PHARMACY_GAIN_LOSS.getCode());
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

    /**
     * 装配报溢增库存命令。
     *
     * @param inventoryItemId 库存项主键
     * @param increaseQty     增加数量（拆零）
     * @param operatorId      操作人
     * @return 增库存命令
     */
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

    /**
     * 装配报损减库存命令。
     *
     * @param inventoryItemId 库存项主键
     * @param decreaseQty     减少数量（拆零，正数）
     * @param allowNegative   是否允许负库存（报损为 true）
     * @param operatorId      操作人
     * @return 减库存命令
     */
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

    /**
     * 装配同意药房损益成功响应。
     *
     * @param busNo      业务单号
     * @param statusEnum 同意后状态 code
     * @return 同意响应
     */
    @Override
    public AgreePharmacyGainLossResponse toAgreePharmacyGainLossResponse(String busNo, String statusEnum) {
        AgreePharmacyGainLossResponse res = new AgreePharmacyGainLossResponse();
        res.setBusNo(busNo);
        res.setStatusEnum(statusEnum);
        return res;
    }

    /**
     * 将驳回请求装配为领域 RejectRequestCommand。
     *
     * @param request    驳回请求
     * @param operatorId 操作人（同时作为 approverId）
     * @return 领域写命令
     */
    @Override
    public RejectRequestCommand toRejectRequestCommand(RejectPharmacyGainLossRequest request, Long operatorId) {
        RejectRequestCommand command = new RejectRequestCommand();
        command.setOperatorId(operatorId);
        RejectRequestCommand.RejectRequestItemCommand item = new RejectRequestCommand.RejectRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        item.setApprovalClock(null);
        // 应用层 rejectReason → 领域项 changeReasonCode（落供应行 reason）
        item.setChangeReasonCode(request.getRejectReason());
        command.setItem(item);
        return command;
    }

    /**
     * 装配驳回药房损益成功响应。
     *
     * @param ok 是否驳回成功
     * @return 驳回响应
     */
    @Override
    public RejectPharmacyGainLossResponse toRejectPharmacyGainLossResponse(boolean ok) {
        RejectPharmacyGainLossResponse res = new RejectPharmacyGainLossResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /**
     * 将删除请求装配为领域 RemoveByBusNoCommand。
     *
     * @param request    删除请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    @Override
    public RemoveByBusNoCommand toRemoveByBusNoCommand(RemovePharmacyGainLossRequest request, Long operatorId) {
        RemoveByBusNoCommand command = new RemoveByBusNoCommand();
        command.setOperatorId(operatorId);
        RemoveByBusNoCommand.RemoveByBusNoItemCommand item = new RemoveByBusNoCommand.RemoveByBusNoItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /**
     * 装配删除药房损益成功响应。
     *
     * @param ok 是否删除成功
     * @return 删除响应
     */
    @Override
    public RemovePharmacyGainLossResponse toRemovePharmacyGainLossResponse(boolean ok) {
        RemovePharmacyGainLossResponse res = new RemovePharmacyGainLossResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /**
     * 将现库库存分页装配为药房损益选品分页响应（不含价带 enrich，由 AppService 补全）。
     *
     * @param page 库存读模型分页
     * @return 选品分页响应
     */
    @Override
    public PageProductsForPharmacyGainLossResponse toPageProductsForPharmacyGainLossResponse(Page<InventoryItemModel> page) {
        List<ProductDefinitionItemDto> content = new ArrayList<>();
        for (InventoryItemModel inv : page.getContent()) {
            if (inv == null) {
                continue;
            }
            content.add(this.toProductDefinitionItemDtoFromInventory(inv, null));
        }
        return PagedResultBuilder.build(new PageProductsForPharmacyGainLossResponse(), content, page);
    }

    /**
     * 将库存行与产品读模型装配为选品行 DTO。
     *
     * @param inv 库存行
     * @param pr  产品读模型，可为 null
     * @return 选品行 DTO
     */
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
     * 供应类目 code 规范化：空则回落库存供应；非法枚举抛 {@link IllegalArgumentException}。
     *
     * @param categoryEnumCode 持久化 category_enum 或请求入参
     * @return {@link SupplyCategory#getCode()} 字符串
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
     * 供应请求排序：申请时间降序，null 排后；同刻按 id 升序 tie-break。
     *
     * @param a 左比较行
     * @param b 右比较行
     * @return Comparator 约定负/零/正
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
     * 供应请求 id 升序比较器；null 行或 null 主键排后（侧栏队列稳定次序）。
     *
     * @param a 左比较行
     * @param b 右比较行
     * @return Comparator 约定负/零/正
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