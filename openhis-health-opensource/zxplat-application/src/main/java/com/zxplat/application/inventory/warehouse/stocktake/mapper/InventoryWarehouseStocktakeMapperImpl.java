/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.stocktake.mapper;

import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.application.inventory.warehouse.stocktake.dto.ProductDefinitionItemDto;
import com.zxplat.application.inventory.warehouse.stocktake.dto.StocktakeDetailLineDto;
import com.zxplat.application.inventory.warehouse.stocktake.dto.StocktakeLineDto;
import com.zxplat.application.inventory.warehouse.stocktake.dto.StocktakeListRowDto;
import com.zxplat.application.inventory.warehouse.stocktake.request.AgreeStocktakeRequest;
import com.zxplat.application.inventory.warehouse.stocktake.request.CreateStocktakeRequest;
import com.zxplat.application.inventory.warehouse.stocktake.request.RejectStocktakeRequest;
import com.zxplat.application.inventory.warehouse.stocktake.request.RemoveStocktakeRequest;
import com.zxplat.application.inventory.warehouse.stocktake.request.SubmitStocktakeApprovalRequest;
import com.zxplat.application.inventory.warehouse.stocktake.request.UpdateStocktakeRequest;
import com.zxplat.application.inventory.warehouse.stocktake.request.WithdrawStocktakeApprovalRequest;
import com.zxplat.application.inventory.warehouse.stocktake.response.AgreeStocktakeResponse;
import com.zxplat.application.inventory.warehouse.stocktake.response.CreateStocktakeResponse;
import com.zxplat.application.inventory.warehouse.stocktake.response.GetStocktakeDetailResponse;
import com.zxplat.application.inventory.warehouse.stocktake.response.LoadStocktakeFormInitResponse;
import com.zxplat.application.inventory.warehouse.stocktake.response.PageProductsForStocktakeResponse;
import com.zxplat.application.inventory.warehouse.stocktake.response.PageStocktakesResponse;
import com.zxplat.application.inventory.warehouse.stocktake.response.RejectStocktakeResponse;
import com.zxplat.application.inventory.warehouse.stocktake.response.RemoveStocktakeResponse;
import com.zxplat.application.inventory.warehouse.stocktake.response.SubmitStocktakeApprovalResponse;
import com.zxplat.application.inventory.warehouse.stocktake.response.UpdateStocktakeResponse;
import com.zxplat.application.inventory.warehouse.stocktake.response.WithdrawStocktakeApprovalResponse;
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
 * 药库盘点单装配实现：{@link InventoryWarehouseStocktakeMapper}。
 *
 * <p>仅做字段搬运与枚举 code 归一；业务流程判断在 {@link InventoryWarehouseStocktakeAppService}。
 * 各 {@code @Override} 方法 JavaDoc 与接口契约一致（B.1，禁止单独 {@code @inheritDoc} 占位）。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryWarehouseStocktakeMapperImpl implements InventoryWarehouseStocktakeMapper {

    /** 位置读服务：按库位推导 {@code purposeTypeEnum}（请求不传类型枚举）。 */
    private final LocationQueryService locationQueryService;

    /**
     * 将启用位置列表映射为下拉选项（id/编码/展示名）。
     *
     * @param locations 启用库位列表，可为 null
     * @return 位置下拉选项，永不为 null
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
     * @param practitioners 医务人员列表，可为 null
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
     * 装配供应请求状态枚举选项（持久化 code）。
     *
     * @return 按 {@link SupplyStatus#getSortOrder()} 升序的选项列表
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
     * 装配物品来源表（药品/耗材）选项。
     *
     * @return 两档物理表名下拉
     */
    @Override
    public List<OptionDto> toOptionDtoListInboundItemTableTypes() {
        List<OptionDto> list = new ArrayList<>();
        list.add(new OptionDto(1L, TableNameConstants.MED_MEDICATION_DEFINITION, "药品"));
        list.add(new OptionDto(2L, TableNameConstants.ADM_DEVICE_DEFINITION, "耗材"));
        return list;
    }

    /**
     * 装配页初始化响应（选项集合写入各字段）。
     *
     * @param warehouseOpts  药库位置下拉
     * @param inboundTypes   物品来源表类型
     * @param statusOpts     单据状态
     * @param pharmacistOpts 药师下拉
     * @param keeperOpts     库管员下拉
     * @return 初始化响应体
     */
    @Override
    public LoadStocktakeFormInitResponse toLoadStocktakeFormInitResponse(
        List<OptionDto> warehouseOpts,
        List<OptionDto> inboundTypes,
        List<OptionDto> statusOpts,
        List<OptionDto> pharmacistOpts,
        List<OptionDto> keeperOpts) {
        LoadStocktakeFormInitResponse res = new LoadStocktakeFormInitResponse();
        res.setPharmacyLocationOptions(warehouseOpts);
        res.setInboundItemTableTypeOptions(inboundTypes);
        res.setStatusOptions(statusOpts);
        res.setPharmacistOptions(pharmacistOpts);
        res.setWarehouseKeeperOptions(keeperOpts);
        return res;
    }

    /**
     * 按 busNo 顺序聚合供应请求明细为列表行 DTO。
     *
     * @param busNoPageOrder 当前页单号顺序
     * @param detailRows     同页明细读模型行
     * @return 聚合列表行
     */
    @Override
    public List<StocktakeListRowDto> toStocktakeListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows) {
        List<StocktakeListRowDto> out = new ArrayList<>();
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
                        ErrorCodeConstants.WHS_ST_102, "同号盘点单状态头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getPurposeLocationId(), line.getPurposeLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_ST_102, "同号盘点单盘点库房头字段不一致: " + busNo);
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
            StocktakeListRowDto dto = new StocktakeListRowDto();
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

    /**
     * 用聚合列表行构建分页响应。
     *
     * @param mergedTotal 合并后总条数
     * @param pageNo      页码（从 1 开始）
     * @param pageSize    页大小
     * @param records     当前页聚合行
     * @return 分页响应
     */
    @Override
    public PageStocktakesResponse toPageStocktakesAggregatedResponse(
        long mergedTotal, int pageNo, int pageSize, List<StocktakeListRowDto> records) {
        List<StocktakeListRowDto> content = new ArrayList<>();
        if (records != null) {
            content = records;
        }
        return PagedResultBuilder.build(new PageStocktakesResponse(), content, mergedTotal, pageNo, pageSize);
    }

    /**
     * 将供应请求行列表映射为详情响应（含明细增强字段）。
     *
     * @param lines        同号全部明细行
     * @param productHints 行 id → 产品目录提示
     * @return 详情响应
     */
    @Override
    public GetStocktakeDetailResponse toGetStocktakeDetailResponse(
        List<SupplyRequestModel> lines,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints) {
        GetStocktakeDetailResponse res = new GetStocktakeDetailResponse();
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
        List<StocktakeDetailLineDto> detailLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            StocktakeDetailLineDto dto = this.toStocktakeDetailLineDto(line);
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
     * @param line 供应请求读模型行
     * @return 详情明细 DTO；{@code line} 为 null 时返回 null
     */
    @Override
    public StocktakeDetailLineDto toStocktakeDetailLineDto(SupplyRequestModel line) {
        if (line == null) {
            return null;
        }
        StocktakeDetailLineDto dto = new StocktakeDetailLineDto();
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

    /**
     * 将新建请求映射为领域 Create 命令（含操作人与申请人）。
     *
     * @param request     新建请求
     * @param operatorId  操作人主键
     * @param applicantId 申请人主键
     * @param busNo       业务单号（修订时非空）
     * @return 领域创建命令
     */
    @Override
    public CreateStocktakeCommand toCreateStocktakeCommand(
        CreateStocktakeRequest request,
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
            String typeCode = SupplyType.CABINET_STOCKTAKE.getCode();
            for (StocktakeLineDto line : request.getLines()) {
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
                item.setPractitionerId(request.getPractitionerId());
                item.setRemake(line.getRemake());
                item.setLotNumber(line.getLotNumber());
                item.setTraceNo(line.getTraceNo());
                item.setPrice(line.getPrice());
                item.setTotalPrice(line.getActualAmount());
                item.setProfitLossPrice(line.getProfitLossAmount());
                item.setApplicantId(applicantId);
                item.setApplyTime(null);
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

    /**
     * 将修订请求塌缩为新建形态后委托装配 Create 命令。
     *
     * @param request     修订请求，不得为 null
     * @param operatorId  操作人主键
     * @param applicantId 申请人主键
     * @return 领域创建命令（携带既有 {@code busNo}）
     */
    @Override
    public CreateStocktakeCommand toCreateStocktakeCommand(
        UpdateStocktakeRequest request,
        Long operatorId,
        Long applicantId) {
        if (request == null) {
            throw new IllegalArgumentException("request 不能为空");
        }
        CreateStocktakeRequest synthetic = new CreateStocktakeRequest();
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

    /**
     * 装配创建成功响应。
     *
     * @param busNo 业务单号
     * @return 创建响应
     */
    @Override
    public CreateStocktakeResponse toCreateStocktakeResponse(String busNo) {
        CreateStocktakeResponse res = new CreateStocktakeResponse();
        res.setBusNo(busNo);
        return res;
    }

    /**
     * 装配更新成功响应。
     *
     * @param busNo 业务单号
     * @return 更新响应
     */
    @Override
    public UpdateStocktakeResponse toUpdateStocktakeResponse(String busNo) {
        UpdateStocktakeResponse res = new UpdateStocktakeResponse();
        res.setBusNo(busNo);
        return res;
    }

    /**
     * 将提交审批请求映射为 SubmitApprovalCommand。
     *
     * @param request     提交请求
     * @param operatorId  操作人主键
     * @param applicantId 申请人主键
     * @return 领域提交命令
     */
    @Override
    public SubmitApprovalCommand toSubmitApprovalCommand(
        SubmitStocktakeApprovalRequest request, Long operatorId, Long applicantId) {
        SubmitApprovalCommand command = new SubmitApprovalCommand();
        command.setOperatorId(operatorId);
        SubmitApprovalCommand.SubmitApprovalItemCommand item = new SubmitApprovalCommand.SubmitApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApplicantId(applicantId);
        command.setItem(item);
        return command;
    }

    /**
     * 装配提交审批响应。
     *
     * @param statusAfter 提交后状态枚举
     * @return 提交审批响应
     */
    @Override
    public SubmitStocktakeApprovalResponse toSubmitStocktakeApprovalResponse(SupplyStatus statusAfter) {
        SubmitStocktakeApprovalResponse res = new SubmitStocktakeApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        if (statusAfter != null) {
            res.setStatusEnum(statusAfter.getCode());
        }
        return res;
    }

    /**
     * 将撤回审批请求映射为 WithdrawApprovalCommand。
     *
     * @param request    撤回请求
     * @param operatorId 操作人主键
     * @return 领域撤回命令
     */
    @Override
    public WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawStocktakeApprovalRequest request, Long operatorId) {
        WithdrawApprovalCommand command = new WithdrawApprovalCommand();
        command.setOperatorId(operatorId);
        WithdrawApprovalCommand.WithdrawApprovalItemCommand item = new WithdrawApprovalCommand.WithdrawApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /**
     * 装配撤回审批响应。
     *
     * @param statusAfter 撤回后状态枚举
     * @return 撤回审批响应
     */
    @Override
    public WithdrawStocktakeApprovalResponse toWithdrawStocktakeApprovalResponse(SupplyStatus statusAfter) {
        WithdrawStocktakeApprovalResponse res = new WithdrawStocktakeApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        if (statusAfter != null) {
            res.setStatusEnum(statusAfter.getCode());
        }
        return res;
    }

    /**
     * 将同意请求映射为领域 AgreeRequestCommand。
     *
     * @param request    同意请求
     * @param operatorId 操作人/审批人主键
     * @return 领域同意命令
     */
    @Override
    public AgreeRequestCommand toAgreeRequestCommand(AgreeStocktakeRequest request, Long operatorId) {
        AgreeRequestCommand command = new AgreeRequestCommand();
        command.setOperatorId(operatorId);
        AgreeRequestCommand.AgreeRequestItemCommand item = new AgreeRequestCommand.AgreeRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        command.setItem(item);
        return command;
    }

    /**
     * 将已同意供应请求行装配为盘点发放命令（跳过盈亏量为零的行；若全部为零则 {@code items} 为空列表，由 AppService 跳过落库）。
     *
     * @param lines      已同意明细行
     * @param operatorId 操作人主键
     * @return 盘点发放命令（可能无 items）
     */
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
                item.setTypeEnum(SupplyType.CABINET_STOCKTAKE.getCode());
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

    /**
     * 装配库存增量命令。
     *
     * @param inventoryItemId 库存行主键
     * @param increaseQty     拆零增量
     * @param operatorId      操作人主键
     * @return 增量命令
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
     * 装配库存减量命令。
     *
     * @param inventoryItemId 库存行主键
     * @param decreaseQty     拆零减量
     * @param allowNegative   是否允许负库存
     * @param operatorId      操作人主键
     * @return 减量命令
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
     * 装配同意成功响应。
     *
     * @param busNo       业务单号
     * @param statusEnum  同意后状态持久化码
     * @return 同意响应
     */
    @Override
    public AgreeStocktakeResponse toAgreeStocktakeResponse(String busNo, String statusEnum) {
        AgreeStocktakeResponse res = new AgreeStocktakeResponse();
        res.setBusNo(busNo);
        res.setStatusEnum(statusEnum);
        return res;
    }

    /**
     * 将驳回请求映射为 RejectRequestCommand。
     *
     * @param request    驳回请求
     * @param operatorId 操作人/审批人主键
     * @return 领域驳回命令
     */
    @Override
    public RejectRequestCommand toRejectRequestCommand(RejectStocktakeRequest request, Long operatorId) {
        RejectRequestCommand command = new RejectRequestCommand();
        command.setOperatorId(operatorId);
        RejectRequestCommand.RejectRequestItemCommand item = new RejectRequestCommand.RejectRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        item.setApprovalClock(null);
        String mergedReason = request.getChangeReasonCode();
        if (StringUtils.isNotEmpty(request.getChangeReason())) {
            mergedReason = request.getChangeReason().trim();
        }
        item.setChangeReasonCode(mergedReason);
        command.setItem(item);
        return command;
    }

    /**
     * 装配驳回响应。
     *
     * @param ok 是否成功
     * @return 驳回响应
     */
    @Override
    public RejectStocktakeResponse toRejectStocktakeResponse(boolean ok) {
        RejectStocktakeResponse res = new RejectStocktakeResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /**
     * 将删除请求映射为 RemoveByBusNoCommand。
     *
     * @param request    删除请求
     * @param operatorId 操作人主键
     * @return 领域删除命令
     */
    @Override
    public RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveStocktakeRequest request, Long operatorId) {
        RemoveByBusNoCommand command = new RemoveByBusNoCommand();
        command.setOperatorId(operatorId);
        RemoveByBusNoCommand.RemoveByBusNoItemCommand item = new RemoveByBusNoCommand.RemoveByBusNoItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /**
     * 装配删除响应。
     *
     * @param ok 是否成功
     * @return 删除响应
     */
    @Override
    public RemoveStocktakeResponse toRemoveStocktakeResponse(boolean ok) {
        RemoveStocktakeResponse res = new RemoveStocktakeResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /**
     * 将库存分页映射为选品分页响应。
     *
     * @param page 库存项读模型分页
     * @return 选品分页响应
     */
    @Override
    public PageProductsForStocktakeResponse toPageProductsForStocktakeResponse(Page<InventoryItemModel> page) {
        List<ProductDefinitionItemDto> content = new ArrayList<>();
        for (InventoryItemModel inv : page.getContent()) {
            if (inv == null) {
                continue;
            }
            content.add(this.toProductDefinitionItemDtoFromInventory(inv, null));
        }
        return PagedResultBuilder.build(new PageProductsForStocktakeResponse(), content, page);
    }

    /**
     * 库存读模型与产品展示 DTO 映射为选品行。
     *
     * @param inv 库存项读模型
     * @param pr  产品定义展示 DTO，可为 null
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