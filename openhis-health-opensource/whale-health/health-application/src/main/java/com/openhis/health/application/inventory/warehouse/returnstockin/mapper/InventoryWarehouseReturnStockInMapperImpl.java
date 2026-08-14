/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returnstockin.mapper;

import com.openhis.health.application.inventory.warehouse.returnstockin.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.warehouse.returnstockin.dto.ReturnStockInDetailLineDto;
import com.openhis.health.application.inventory.warehouse.returnstockin.dto.ReturnStockInLineDto;
import com.openhis.health.application.inventory.warehouse.returnstockin.dto.ReturnStockInListRowDto;
import com.openhis.health.application.inventory.warehouse.returnstockin.dto.SupplierOptionDto;
import com.openhis.health.application.inventory.warehouse.returnstockin.dto.WarehouseOptionDto;
import com.openhis.health.application.inventory.warehouse.returnstockin.request.*;
import com.openhis.health.application.inventory.warehouse.returnstockin.response.*;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.SupplierType;
import com.openhis.health.domain.share.enums.SupplyCategory;
import com.openhis.health.domain.share.enums.SupplyRequestType;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateReturnCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 退库入库单装配实现：{@link InventoryWarehouseReturnStockInMapper}。
 *
 * <p>各 {@code @Override} 方法 JavaDoc 与接口契约一致（B.1）；写路径 HTTP 契约保留但应用层已拒绝手工落账。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryWarehouseReturnStockInMapperImpl implements InventoryWarehouseReturnStockInMapper {

    private final LocationQueryService locationQueryService;

    /**
     * 将启用位置列表映射为库房/科室/药房下拉选项 DTO。
     *
     * @param locations 启用库位列表，可为 null
     * @return 位置下拉选项；{@code null} 输入返回空列表
     */
    @Override
    public List<WarehouseOptionDto> toWarehouseOptionDtoList(List<Location> locations) {
        List<WarehouseOptionDto> list = new ArrayList<>();
        if (locations == null) {
            return list;
        }
        for (Location location : locations) {
            if (location == null) {
                continue;
            }
            WarehouseOptionDto dto = new WarehouseOptionDto();
            dto.setLocationId(location.getId());
            dto.setDisplayName(location.getName());
            dto.setActive(Boolean.TRUE);
            list.add(dto);
        }
        return list;
    }

    /**
     * 将启用供应商列表映射为下拉选项 DTO。
     *
     * @param suppliers 供应商列表项，可为 null
     * @return 供应商下拉选项；{@code null} 输入返回空列表
     */
    @Override
    public List<SupplierOptionDto> toSupplierOptionDtoList(List<SupplierListItemQueryModel> suppliers) {
        List<SupplierOptionDto> list = new ArrayList<>();
        if (suppliers == null) {
            return list;
        }
        for (SupplierListItemQueryModel row : suppliers) {
            if (row == null) {
                continue;
            }
            SupplierOptionDto dto = new SupplierOptionDto();
            dto.setSupplierId(row.getId());
            dto.setSupplierName(row.getName());
            SupplierType st = EnumUtil.fromCode(SupplierType.class, row.getTypeEnum());
            if (st != null) {
                dto.setSupplierType(st.getDisplay());
            } else {
                dto.setSupplierType(row.getTypeEnum());
            }
            list.add(dto);
        }
        return list;
    }

    /**
     * 装配退库入库页初始化响应（选项集合写入各字段）。
     *
     * @param warehouseOptions  药库选项
     * @param departmentOptions 科室选项
     * @param pharmacyOptions   药房选项
     * @param supplierOptions   供应商选项
     * @return 页初始化响应
     */
    @Override
    public LoadReturnStockInFormInitResponse toLoadReturnStockInFormInitResponse(
        List<WarehouseOptionDto> warehouseOptions,
        List<WarehouseOptionDto> departmentOptions,
        List<WarehouseOptionDto> pharmacyOptions,
        List<SupplierOptionDto> supplierOptions) {
        LoadReturnStockInFormInitResponse res = new LoadReturnStockInFormInitResponse();
        res.setWarehouseOptions(warehouseOptions);
        res.setDepartmentOptions(departmentOptions);
        res.setPharmacyOptions(pharmacyOptions);
        res.setSupplierOptions(supplierOptions);
        return res;
    }

    /**
     * 将库房单据读模型分页装配为退库入库单列表响应。
     *
     * @param page 库房单据读模型分页
     * @return 列表分页响应
     */
    @Override
    public PageReturnStockInsResponse toPageReturnStockInsResponse(Page<WarehouseDocumentDetailQueryModel> page) {
        List<ReturnStockInListRowDto> content = new ArrayList<>();
        for (WarehouseDocumentDetailQueryModel row : page.getContent()) {
            if (row == null) {
                continue;
            }
            content.add(this.toReturnStockInListRowDto(row));
        }
        return PagedResultBuilder.build(new PageReturnStockInsResponse(), content, page);
    }

    /**
     * 按 busNo 顺序聚合退库入库供应请求明细为列表行 DTO。
     *
     * @param busNoPageOrder 分页单号顺序
     * @param detailRows     同页全部明细
     * @return 聚合列表行
     */
    @Override
    public List<ReturnStockInListRowDto> toReturnStockInListAggregated(
        List<String> busNoPageOrder,
        List<SupplyRequestModel> detailRows) {
        List<ReturnStockInListRowDto> out = new ArrayList<>();
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
            SupplyRequestModel head = lines.get(0);
            for (SupplyRequestModel line : lines) {
                if (line == null) {
                    continue;
                }
                if (!Objects.equals(head.getStatusEnum(), line.getStatusEnum())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_RSI_102, "同号退库入库壳单状态头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getOriginalBusNo(), line.getOriginalBusNo())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_RSI_102, "同号退库入库壳单来源退库单不一致: " + busNo);
                }
                if (!Objects.equals(head.getSourceLocationId(), line.getSourceLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_RSI_102, "同号退库入库壳单源位置头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getPurposeLocationId(), line.getPurposeLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_RSI_102, "同号退库入库壳单目的药库头字段不一致: " + busNo);
                }
            }
            BigDecimal totalAmount = BigDecimal.ZERO;
            LocalDateTime maxApply = null;
            LocalDateTime maxOccurrence = null;
            for (SupplyRequestModel line : lines) {
                if (line == null) {
                    continue;
                }
                if (line.getTotalPrice() != null) {
                    totalAmount = totalAmount.add(line.getTotalPrice());
                }
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
            }
            LocalDateTime approvalTime = null;
            for (SupplyRequestModel line : lines) {
                if (line != null && line.getApprovalTime() != null) {
                    approvalTime = line.getApprovalTime();
                    break;
                }
            }
            ReturnStockInListRowDto dto = new ReturnStockInListRowDto();
            dto.setBusNo(busNo);
            dto.setOriginalBusNo(head.getOriginalBusNo());
            dto.setTypeEnum(this.toApiSortSupplyType(head.getTypeEnum()));
            dto.setStatusEnum(this.toApiSortSupplyStatus(head.getStatusEnum()));
            dto.setCategoryEnum(this.toApiSortSupplyCategory(head.getCategoryEnum()));
            dto.setSourceLocationId(head.getSourceLocationId());
            dto.setPurposeLocationId(head.getPurposeLocationId());
            dto.setSupplierId(head.getSupplierId());
            dto.setApplicantId(head.getApplicantId());
            dto.setApproverId(head.getApproverId());
            dto.setTotalPrice(totalAmount);
            dto.setTotalAmount(totalAmount);
            if (maxApply != null) {
                dto.setApplyTime(maxApply);
            } else {
                dto.setApplyTime(maxOccurrence);
            }
            dto.setApprovalTime(approvalTime);
            out.add(dto);
        }
        return out;
    }

    /**
     * 用聚合列表行构建退库入库单分页响应。
     *
     * @param mergedTotal 去重后总单数
     * @param pageNo      页码
     * @param pageSize    页大小
     * @param records     列表行
     * @return 分页响应
     */
    @Override
    public PageReturnStockInsResponse toPageReturnStockInsAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<ReturnStockInListRowDto> records) {
        List<ReturnStockInListRowDto> content;
        if (records == null) {
            content = new ArrayList<>();
        } else {
            content = records;
        }
        return PagedResultBuilder.build(new PageReturnStockInsResponse(), content, mergedTotal, pageNo, pageSize);
    }

    /**
     * 将可关联出库/发放单读模型分页装配为选单响应。
     *
     * @param page 可关联单据分页
     * @return 可关联单据分页响应
     */
    @Override
    public PageLinkableDeliveriesResponse toPageLinkableDeliveriesResponse(Page<WarehouseDocumentDetailQueryModel> page) {
        List<ReturnStockInListRowDto> content = new ArrayList<>();
        for (WarehouseDocumentDetailQueryModel row : page.getContent()) {
            if (row == null) {
                continue;
            }
            content.add(this.toReturnStockInListRowDto(row));
        }
        return PagedResultBuilder.build(new PageLinkableDeliveriesResponse(), content, page);
    }

    /**
     * 库房单据读模型 → 退库入库列表行 DTO（枚举转 API sortOrder）。
     *
     * @param row 库房单据读模型
     * @return 列表行 DTO
     */
    private ReturnStockInListRowDto toReturnStockInListRowDto(WarehouseDocumentDetailQueryModel row) {
        ReturnStockInListRowDto dto = new ReturnStockInListRowDto();
        dto.setId(row.getId());
        dto.setBusNo(row.getBusNo());
        dto.setOriginalBusNo(row.getOriginalBusNo());
        dto.setTypeEnum(this.toApiSortSupplyType(row.getTypeEnum()));
        dto.setStatusEnum(this.toApiSortSupplyStatus(row.getStatusEnum()));
        dto.setCategoryEnum(this.toApiSortSupplyCategory(row.getCategoryEnum()));
        dto.setSourceLocationId(row.getSourceLocationId());
        dto.setPurposeLocationId(row.getPurposeLocationId());
        dto.setSupplierId(row.getSupplierId());
        dto.setItemId(row.getItemId());
        dto.setItemTable(row.getItemTable());
        dto.setItemQuantity(row.getItemQuantity());
        dto.setUnitCode(row.getUnitCode());
        dto.setLotNumber(row.getLotNumber());
        dto.setTraceNo(row.getTraceNo());
        dto.setPrice(row.getPrice());
        dto.setTotalPrice(row.getTotalPrice());
        dto.setApplyTime(row.getApplyTime());
        dto.setApprovalTime(row.getApprovalTime());
        return dto;
    }

    /**
     * 将供应请求行列表映射为退库入库详情响应（含产品展示与计量带提示）。
     *
     * @param lines         同 busNo 供应请求行
     * @param productByKey  产品读模型映射（compositeKey → 展示 DTO）
     * @param productHints  行 id → 目录编辑提示（unitBands）
     * @return 单头与明细；空列表时返回空 lines
     */
    @Override
    public GetReturnStockInDetailResponse toGetReturnStockInDetailResponse(
        List<SupplyRequestModel> lines,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints) {
        GetReturnStockInDetailResponse res = new GetReturnStockInDetailResponse();
        if (lines == null || lines.isEmpty()) {
            res.setLines(new ArrayList<>());
            return res;
        }
        SupplyRequestModel head = lines.get(0);
        res.setBusNo(head.getBusNo());
        res.setOriginalBusNo(head.getOriginalBusNo());
        res.setTypeEnum(this.toApiSortSupplyType(head.getTypeEnum()));
        res.setStatusEnum(this.toApiSortSupplyStatus(head.getStatusEnum()));
        res.setSupplierId(head.getSupplierId());
        res.setSourceLocationId(head.getSourceLocationId());
        res.setPurposeLocationId(head.getPurposeLocationId());
        res.setSourceTypeEnum(this.toApiSortSupplyRequestType(head.getSourceTypeEnum()));
        res.setPurposeTypeEnum(this.toApiSortSupplyRequestType(head.getPurposeTypeEnum()));
        res.setCategoryEnum(this.toApiSortSupplyCategory(head.getCategoryEnum()));
        List<ReturnStockInDetailLineDto> detailLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            detailLines.add(this.toDetailLine(line, productByKey, productHints));
        }
        res.setLines(detailLines);
        return res;
    }

    /**
     * 供应请求明细 → 退库入库详情行 DTO（品名 + 单位展示名 + productCatalog）。
     *
     * @param line         供应请求明细
     * @param productByKey 产品复合键 → 展示 DTO
     * @param productHints 行 id → 目录编辑提示
     * @return 详情行 DTO
     */
    private ReturnStockInDetailLineDto toDetailLine(
        SupplyRequestModel line,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints) {
        ReturnStockInDetailLineDto dto = new ReturnStockInDetailLineDto();
        dto.setId(line.getId());
        dto.setItemTable(line.getItemTable());
        dto.setItemId(line.getItemId());
        dto.setItemQuantity(line.getItemQuantity());
        dto.setUnitCode(line.getUnitCode());
        dto.setLotNumber(line.getLotNumber());
        dto.setStartTime(line.getStartTime());
        dto.setEndTime(line.getEndTime());
        dto.setTraceNo(line.getTraceNo());
        dto.setOccurrenceTime(line.getOccurrenceTime());
        dto.setReason(line.getReason());
        dto.setRemake(line.getRemake());
        if (productByKey != null && line.getItemTable() != null && line.getItemId() != null) {
            String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
            ProductDefinitionDisplayDto pr = productByKey.get(key);
            if (pr != null) {
                dto.setProductName(pr.getProductName());
                dto.setUnitCodeDictText(
                    ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                        null, line.getUnitCode(), pr));
            }
        }
        if (productHints != null && line.getId() != null) {
            ProductDefinitionCatalogEditorHintsDto hint = productHints.get(line.getId());
            if (hint != null) {
                dto.setProductCatalog(hint);
            }
        }
        return dto;
    }

    /**
     * 将创建请求映射为领域 {@link CreateReturnCommand}（含操作人与申请人）。
     *
     * @param request                 创建请求体
     * @param operatorId              操作人主键
     * @param applicantId             申请人主键
     * @param originalBusNoSnapshot   关联来源单号快照（可空）
     * @return 领域创建退库命令
     */
    @Override
    public CreateReturnCommand toCreateReturnStockInCommand(
        CreateReturnStockInRequest request,
        Long operatorId,
        Long applicantId,
        String originalBusNoSnapshot) {
        return this.buildCreateReturnCommand(
            request.getOriginalBusNo(),
            request.getSourceLocationId(),
            request.getPurposeLocationId(),
            request.getCategoryEnum(),
            request.getSupplierId(),
            request.getLines(),
            null,
            operatorId,
            applicantId,
            originalBusNoSnapshot);
    }

    /**
     * 将更新请求映射为领域 {@link CreateReturnCommand}（含操作人与申请人，保留 busNo）。
     *
     * @param request                 更新请求体
     * @param operatorId              操作人主键
     * @param applicantId             申请人主键
     * @param originalBusNoSnapshot   关联来源单号快照
     * @return 领域创建/覆盖退库命令
     */
    @Override
    public CreateReturnCommand toCreateReturnStockInCommandForUpdate(
        UpdateReturnStockInRequest request,
        Long operatorId,
        Long applicantId,
        String originalBusNoSnapshot) {
        return this.buildCreateReturnCommand(
            originalBusNoSnapshot,
            request.getSourceLocationId(),
            request.getPurposeLocationId(),
            request.getCategoryEnum(),
            request.getSupplierId(),
            request.getLines(),
            request.getBusNo(),
            operatorId,
            applicantId,
            originalBusNoSnapshot);
    }

    /**
     * 新建/修订退库入库请求公共写命令装配（解析库位类型与供应类别）。
     *
     * @param originalBusNo           请求体关联来源单号
     * @param sourceLocationId        源库位
     * @param purposeLocationId       目的药库库位
     * @param categoryEnumSort        前端供应类别 sort
     * @param supplierId              供应商
     * @param lines                   明细行
     * @param busNo                   修订时业务单号；新建为 null
     * @param operatorId              操作人
     * @param applicantId             制单人
     * @param originalBusNoSnapshot   关联单号快照（优先于 originalBusNo）
     * @return 领域 CreateReturnCommand
     */
    private CreateReturnCommand buildCreateReturnCommand(
        String originalBusNo,
        Long sourceLocationId,
        Long purposeLocationId,
        Integer categoryEnumSort,
        Long supplierId,
        List<ReturnStockInLineDto> lines,
        String busNo,
        Long operatorId,
        Long applicantId,
        String originalBusNoSnapshot) {
        CreateReturnCommand command = new CreateReturnCommand();
        command.setOperatorId(operatorId);
        List<CreateReturnCommand.CreateReturnItemCommand> items = new ArrayList<>();
        if (lines == null) {
            command.setItems(items);
            return command;
        }
        String sourceTypeCode = this.locationQueryService.requireSupplyRequestTypeForLocationId(sourceLocationId).getCode();
        String purposeTypeCode = this.locationQueryService.requireSupplyRequestTypeForLocationId(purposeLocationId).getCode();
        String categoryCode = this.resolveSupplyCategoryCode(categoryEnumSort);
        String typeCode = SupplyType.CABINET_RETURN_STOCK_IN.getCode();
        String resolvedOriginal = originalBusNoSnapshot;
        if (StringUtils.isEmpty(resolvedOriginal)) {
            resolvedOriginal = originalBusNo;
        }
        for (ReturnStockInLineDto line : lines) {
            if (line == null) {
                continue;
            }
            CreateReturnCommand.CreateReturnItemCommand item =
                new CreateReturnCommand.CreateReturnItemCommand();
            item.setBusNo(busNo);
            if (resolvedOriginal != null) {
                item.setOriginalBusNo(resolvedOriginal.trim());
            } else {
                item.setOriginalBusNo(null);
            }
            item.setTypeEnum(typeCode);
            item.setItemTable(line.getItemTable());
            item.setItemId(line.getItemId());
            item.setItemQuantity(line.getItemQuantity());
            item.setUnitCode(line.getUnitCode());
            item.setCategoryEnum(categoryCode);
            item.setSourceTypeEnum(sourceTypeCode);
            item.setSourceLocationId(sourceLocationId);
            item.setPurposeTypeEnum(purposeTypeCode);
            item.setPurposeLocationId(purposeLocationId);
            item.setSupplierId(supplierId);
            item.setOccurrenceTime(line.getOccurrenceTime());
            item.setApplicantId(applicantId);
            item.setApplyTime(null);
            item.setReason(line.getReason());
            item.setLotNumber(line.getLotNumber());
            item.setTraceNo(line.getTraceNo());
            items.add(item);
        }
        command.setItems(items);
        return command;
    }

    /**
     * 装配创建成功响应（写入 busNo 与待审批状态）。
     *
     * @param busNo 新建业务单号
     * @return 创建响应
     */
    @Override
    public CreateReturnStockInResponse toCreateReturnStockInResponse(String busNo) {
        CreateReturnStockInResponse res = new CreateReturnStockInResponse();
        res.setBusNo(busNo);
        res.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getSortOrder().intValue());
        return res;
    }

    /**
     * 装配更新成功响应（写入 busNo 与待审批状态）。
     *
     * @param busNo 业务单号
     * @return 更新响应
     */
    @Override
    public UpdateReturnStockInResponse toUpdateReturnStockInResponse(String busNo) {
        UpdateReturnStockInResponse res = new UpdateReturnStockInResponse();
        res.setBusNo(busNo);
        res.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getSortOrder().intValue());
        return res;
    }

    /**
     * 将提交审批请求映射为 {@link SubmitApprovalCommand}。
     *
     * @param request     提交审批请求
     * @param operatorId  操作人主键
     * @param applicantId 申请人主键
     * @return 领域提交审批命令
     */
    @Override
    public SubmitApprovalCommand toSubmitApprovalCommand(
        SubmitReturnStockInApprovalRequest request,
        Long operatorId,
        Long applicantId) {
        SubmitApprovalCommand command = new SubmitApprovalCommand();
        command.setOperatorId(operatorId);
        SubmitApprovalCommand.SubmitApprovalItemCommand item = new SubmitApprovalCommand.SubmitApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApplicantId(applicantId);
        command.setItem(item);
        return command;
    }

    /**
     * 装配提交审批成功响应（状态置为审批中）。
     *
     * @return 提交审批响应
     */
    @Override
    public SubmitReturnStockInApprovalResponse toSubmitReturnStockInApprovalResponse() {
        SubmitReturnStockInApprovalResponse res = new SubmitReturnStockInApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        res.setStatusEnum(SupplyStatus.APPROVAL.getSortOrder().intValue());
        return res;
    }

    /**
     * 将撤回审批请求映射为 {@link WithdrawApprovalCommand}。
     *
     * @param request    撤回审批请求
     * @param operatorId 操作人主键
     * @return 领域撤回审批命令
     */
    @Override
    public WithdrawApprovalCommand toWithdrawApprovalCommand(
        WithdrawReturnStockInApprovalRequest request,
        Long operatorId) {
        WithdrawApprovalCommand command = new WithdrawApprovalCommand();
        command.setOperatorId(operatorId);
        WithdrawApprovalCommand.WithdrawApprovalItemCommand item = new WithdrawApprovalCommand.WithdrawApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /**
     * 装配撤回审批成功响应（状态置为已撤回）。
     *
     * @return 撤回审批响应
     */
    @Override
    public WithdrawReturnStockInApprovalResponse toWithdrawReturnStockInApprovalResponse() {
        WithdrawReturnStockInApprovalResponse res = new WithdrawReturnStockInApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        res.setStatusEnum(SupplyStatus.WITHDRAW.getSortOrder().intValue());
        return res;
    }

    /**
     * 将同意请求映射为领域 {@link AgreeRequestCommand}。
     *
     * @param request    同意请求
     * @param operatorId 操作人主键
     * @return 领域同意命令
     */
    @Override
    public AgreeRequestCommand toAgreeRequestCommand(AgreeReturnStockInRequest request, Long operatorId) {
        AgreeRequestCommand command = new AgreeRequestCommand();
        command.setOperatorId(operatorId);
        AgreeRequestCommand.AgreeRequestItemCommand item = new AgreeRequestCommand.AgreeRequestItemCommand();
        item.setBusNo(request.getBusNo());
        Long approverId = request.getApproverId();
        if (approverId == null) {
            approverId = operatorId;
        }
        item.setApproverId(approverId);
        item.setApprovalClock(request.getApprovalClock());
        command.setItem(item);
        return command;
    }

    /**
     * 将已同意供应请求行装配为退库入库发放命令（库存已在下游 agree 落账，本路径为契约占位）。
     *
     * @param lines      已同意供应请求行
     * @param operatorId 操作人
     * @return 发放写命令
     */
    @Override
    public CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommandForReturnStockIn(
        List<SupplyRequestModel> lines,
        Long operatorId) {
        CreateSupplyDeliveryCommand cmd = new CreateSupplyDeliveryCommand();
        cmd.setOperatorId(operatorId);
        List<CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand> items = new ArrayList<>();
        if (lines != null) {
            for (SupplyRequestModel line : lines) {
                if (line == null || line.getId() == null) {
                    continue;
                }
                CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand item =
                    new CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand();
                item.setRequestId(line.getId());
                item.setTypeEnum(SupplyType.CABINET_RETURN_STOCK_IN.getCode());
                item.setItemTable(line.getItemTable());
                item.setItemId(line.getItemId());
                item.setQuantity(line.getItemQuantity());
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
     * 装配同意成功响应。
     *
     * @param ok 领域 agree 是否成功
     * @return 同意响应
     */
    @Override
    public AgreeReturnStockInResponse toAgreeReturnStockInResponse(boolean ok) {
        AgreeReturnStockInResponse res = new AgreeReturnStockInResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /**
     * 将驳回请求映射为 {@link RejectRequestCommand}。
     *
     * @param request    驳回请求
     * @param operatorId 操作人主键
     * @return 领域驳回命令
     */
    @Override
    public RejectRequestCommand toRejectRequestCommand(RejectReturnStockInRequest request, Long operatorId) {
        RejectRequestCommand command = new RejectRequestCommand();
        command.setOperatorId(operatorId);
        RejectRequestCommand.RejectRequestItemCommand item = new RejectRequestCommand.RejectRequestItemCommand();
        item.setBusNo(request.getBusNo());
        Long approverId = request.getApproverId();
        if (approverId == null) {
            approverId = operatorId;
        }
        item.setApproverId(approverId);
        item.setApprovalClock(request.getApprovalClock());
        item.setChangeReasonCode(request.getRejectReason());
        command.setItem(item);
        return command;
    }

    /**
     * 装配驳回成功响应。
     *
     * @param ok 领域 reject 是否成功
     * @return 驳回响应
     */
    @Override
    public RejectReturnStockInResponse toRejectReturnStockInResponse(boolean ok) {
        RejectReturnStockInResponse res = new RejectReturnStockInResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /**
     * 将删除请求映射为 {@link RemoveByBusNoCommand}。
     *
     * @param request    删除请求
     * @param operatorId 操作人主键
     * @return 领域按单号删除命令
     */
    @Override
    public RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveReturnStockInRequest request, Long operatorId) {
        RemoveByBusNoCommand command = new RemoveByBusNoCommand();
        command.setOperatorId(operatorId);
        RemoveByBusNoCommand.RemoveByBusNoItemCommand item = new RemoveByBusNoCommand.RemoveByBusNoItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /**
     * 装配删除成功响应。
     *
     * @param ok 领域 remove 是否成功
     * @return 删除响应
     */
    @Override
    public RemoveReturnStockInResponse toRemoveReturnStockInResponse(boolean ok) {
        RemoveReturnStockInResponse res = new RemoveReturnStockInResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /**
     * 将产品定义分页映射为退库入库选品分页响应（含采购价 enrich）。
     *
     * @param page 产品定义展示分页
     * @return 选品分页响应
     */
    @Override
    public PageProductsForReturnStockInResponse toPageProductsForReturnStockInResponse(
        Page<ProductDefinitionDisplayDto> page) {
        List<ProductDefinitionItemDto> content = new ArrayList<>();
        for (ProductDefinitionDisplayDto pr : page.getContent()) {
            if (pr == null) {
                continue;
            }
            content.add(this.toProductDefinitionItemDto(pr));
        }
        return PagedResultBuilder.build(new PageProductsForReturnStockInResponse(), content, page);
    }

    /**
     * 产品定义读模型 → 退库入库选品 DTO（含首条采购价 enrich）。
     *
     * @param pr 产品定义展示 DTO
     * @return 选品 DTO
     */
    private ProductDefinitionItemDto toProductDefinitionItemDto(ProductDefinitionDisplayDto pr) {
        ProductDefinitionItemDto dto = new ProductDefinitionItemDto();
        dto.setProductDefinitionId(pr.getProductDefinitionId());
        dto.setProductTableName(pr.getProductTableName());
        dto.setProductName(pr.getProductName());
        dto.setMerchandiseName(pr.getMerchandiseName());
        dto.setUnitCode(pr.getPurchaseUnitCode());
        dto.setMinUnitCode(pr.getSplitUnitCode());
        dto.setCategoryCode(pr.getCategoryCode());
        dto.setPrice(pr.getPrice());
        // —— 退库入库选品已禁手工；不灌目录 PURCHASE（流转金额只认批号/源单落库价）。——
        dto.setPurchasePrice(null);
        dto.setManufacturer(pr.getManufacturer());
        dto.setPyStr(pr.getPyStr());
        dto.setWbStr(pr.getWbStr());
        return dto;
    }

    /**
     * 供应类型持久化 code → API sortOrder。
     *
     * @param code 持久化 code
     * @return sortOrder；无法解析则 null
     */
    private Integer toApiSortSupplyType(String code) {
        SupplyType t = EnumUtil.fromCode(SupplyType.class, code);
        if (t == null) {
            return null;
        }
        return t.getSortOrder().intValue();
    }

    /**
     * 供应状态持久化 code → API sortOrder。
     *
     * @param code 持久化 code
     * @return sortOrder；无法解析则 null
     */
    private Integer toApiSortSupplyStatus(String code) {
        SupplyStatus s = EnumUtil.fromCode(SupplyStatus.class, code);
        if (s == null) {
            return null;
        }
        return s.getSortOrder().intValue();
    }

    /**
     * 供应类别持久化 code → API sortOrder。
     *
     * @param code 持久化 code
     * @return sortOrder；无法解析则 null
     */
    private Integer toApiSortSupplyCategory(String code) {
        SupplyCategory c = EnumUtil.fromCode(SupplyCategory.class, code);
        if (c == null) {
            return null;
        }
        return c.getSortOrder().intValue();
    }

    /**
     * 供应请求位置类型持久化 code → API sortOrder。
     *
     * @param code 持久化 code
     * @return sortOrder；无法解析则 null
     */
    private Integer toApiSortSupplyRequestType(String code) {
        SupplyRequestType t = EnumUtil.fromCode(SupplyRequestType.class, code);
        if (t == null) {
            return null;
        }
        return t.getSortOrder().intValue();
    }

    /**
     * 前端供应类别 sort → 持久化 category code；缺省为常备供应。
     *
     * @param categoryEnumSort 前端 sort；可空
     * @return 持久化 category code
     */
    private String resolveSupplyCategoryCode(Integer categoryEnumSort) {
        if (categoryEnumSort == null) {
            return SupplyCategory.STOCK_SUPPLY.getCode();
        }
        SupplyCategory category = EnumUtil.requireFromSortOrder(SupplyCategory.class, categoryEnumSort.longValue());
        return category.getCode();
    }
}
