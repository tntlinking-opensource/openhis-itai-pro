/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.warehouse.returnfromdownstream.mapper;

import com.openhis.health.application.inventory.warehouse.returnfromdownstream.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.warehouse.returnfromdownstream.dto.ReturnFromDownstreamDetailLineDto;
import com.openhis.health.application.inventory.warehouse.returnfromdownstream.dto.ReturnFromDownstreamLineDto;
import com.openhis.health.application.inventory.warehouse.returnfromdownstream.dto.ReturnFromDownstreamListRowDto;
import com.openhis.health.application.inventory.warehouse.returnfromdownstream.dto.SupplierOptionDto;
import com.openhis.health.application.inventory.warehouse.returnfromdownstream.dto.WarehouseOptionDto;
import com.openhis.health.application.inventory.warehouse.returnfromdownstream.request.*;
import com.openhis.health.application.inventory.warehouse.returnfromdownstream.response.*;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
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
import com.openhis.health.module.common.pagination.PagedResultBuilder;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class InventoryWarehouseReturnFromDownstreamMapperImpl implements InventoryWarehouseReturnFromDownstreamMapper {

    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final LocationQueryService locationQueryService;

    public InventoryWarehouseReturnFromDownstreamMapperImpl(
        ChargeItemDefinitionQueryService chargeItemDefinitionQueryService,
        LocationQueryService locationQueryService) {
        this.chargeItemDefinitionQueryService = chargeItemDefinitionQueryService;
        this.locationQueryService = locationQueryService;
    }

    /** 位置实体列表映射为库房/科室选项 DTO。 */
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

    /** Assemble mapping result for SupplierOptionDtoList. */
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

    /** 装配页初始化响应（选项集合写入各字段）。 */
    @Override
    public LoadReturnFromDownstreamFormInitResponse toLoadReturnFromDownstreamFormInitResponse(
        List<WarehouseOptionDto> warehouseOptions,
        List<WarehouseOptionDto> departmentOptions,
        List<WarehouseOptionDto> pharmacyOptions,
        List<SupplierOptionDto> supplierOptions) {
        LoadReturnFromDownstreamFormInitResponse res = new LoadReturnFromDownstreamFormInitResponse();
        res.setWarehouseOptions(warehouseOptions);
        res.setDepartmentOptions(departmentOptions);
        res.setPharmacyOptions(pharmacyOptions);
        res.setSupplierOptions(supplierOptions);
        return res;
    }

    /** Assemble mapping result for PageReturnFromDownstreamsResponse. */
    @Override
    public PageReturnFromDownstreamsResponse toPageReturnFromDownstreamsResponse(Page<WarehouseDocumentDetailQueryModel> page) {
        List<ReturnFromDownstreamListRowDto> content = new ArrayList<>();
        for (WarehouseDocumentDetailQueryModel row : page.getContent()) {
            if (row == null) {
                continue;
            }
            content.add(this.toReturnFromDownstreamListRowDto(row));
        }
        return PagedResultBuilder.build(new PageReturnFromDownstreamsResponse(), content, page);
    }

    /** Assemble mapping result for PageLinkableDeliveriesResponse. */
    @Override
    public PageLinkableDeliveriesResponse toPageLinkableDeliveriesResponse(Page<WarehouseDocumentDetailQueryModel> page) {
        List<ReturnFromDownstreamListRowDto> content = new ArrayList<>();
        for (WarehouseDocumentDetailQueryModel row : page.getContent()) {
            if (row == null) {
                continue;
            }
            content.add(this.toReturnFromDownstreamListRowDto(row));
        }
        return PagedResultBuilder.build(new PageLinkableDeliveriesResponse(), content, page);
    }

    private ReturnFromDownstreamListRowDto toReturnFromDownstreamListRowDto(WarehouseDocumentDetailQueryModel row) {
        ReturnFromDownstreamListRowDto dto = new ReturnFromDownstreamListRowDto();
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

    /** 将供应请求行列表映射为详情响应（含明细增强字段）。 */
    @Override
    public GetReturnFromDownstreamDetailResponse toGetReturnFromDownstreamDetailResponse(
        List<SupplyRequestModel> lines,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        GetReturnFromDownstreamDetailResponse res = new GetReturnFromDownstreamDetailResponse();
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
        List<ReturnFromDownstreamDetailLineDto> detailLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            detailLines.add(this.toDetailLine(line, productByKey));
        }
        res.setLines(detailLines);
        return res;
    }

    private ReturnFromDownstreamDetailLineDto toDetailLine(
        SupplyRequestModel line,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        ReturnFromDownstreamDetailLineDto dto = new ReturnFromDownstreamDetailLineDto();
        dto.setId(line.getId());
        dto.setItemTable(line.getItemTable());
        dto.setItemId(line.getItemId());
        dto.setItemQuantity(line.getItemQuantity());
        dto.setUnitCode(line.getUnitCode());
        dto.setLotNumber(line.getLotNumber());
        dto.setTraceNo(line.getTraceNo());
        dto.setOccurrenceTime(line.getOccurrenceTime());
        dto.setReason(line.getReason());
        dto.setRemake(line.getRemake());
        if (productByKey != null && line.getItemTable() != null && line.getItemId() != null) {
            String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
            ProductDefinitionDisplayDto pr = productByKey.get(key);
            if (pr != null) {
                dto.setProductName(pr.getProductName());
            }
        }
        return dto;
    }

    /** 将写请求映射为领域 Create 命令（含操作人与申请人）。 */
    @Override
    public CreateReturnCommand toCreateReturnFromDownstreamCommand(
        CreateReturnFromDownstreamRequest request,
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

    /** 将写请求映射为领域 Create 命令（含操作人与申请人）。 */
    @Override
    public CreateReturnCommand toCreateReturnFromDownstreamCommandForUpdate(
        UpdateReturnFromDownstreamRequest request,
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

    private CreateReturnCommand buildCreateReturnCommand(
        String originalBusNo,
        Long sourceLocationId,
        Long purposeLocationId,
        Integer categoryEnumSort,
        Long supplierId,
        List<ReturnFromDownstreamLineDto> lines,
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
        String typeCode = SupplyType.CABINET_RETURN_FROM_DOWNSTREAM.getCode();
        String resolvedOriginal = originalBusNoSnapshot;
        if (StringUtils.isBlank(resolvedOriginal)) {
            resolvedOriginal = originalBusNo;
        }
        for (ReturnFromDownstreamLineDto line : lines) {
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

    /** 装配创建成功响应（写入 busNo 等）。 */
    @Override
    public CreateReturnFromDownstreamResponse toCreateReturnFromDownstreamResponse(String busNo) {
        CreateReturnFromDownstreamResponse res = new CreateReturnFromDownstreamResponse();
        res.setBusNo(busNo);
        res.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getSortOrder().intValue());
        return res;
    }

    /** 装配更新成功响应。 */
    @Override
    public UpdateReturnFromDownstreamResponse toUpdateReturnFromDownstreamResponse(String busNo) {
        UpdateReturnFromDownstreamResponse res = new UpdateReturnFromDownstreamResponse();
        res.setBusNo(busNo);
        res.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getSortOrder().intValue());
        return res;
    }

    /** 将提交审批请求映射为 SubmitApprovalCommand。 */
    @Override
    public SubmitApprovalCommand toSubmitApprovalCommand(
        SubmitReturnFromDownstreamApprovalRequest request,
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

    /** 装配提交审批响应。 */
    @Override
    public SubmitReturnFromDownstreamApprovalResponse toSubmitReturnFromDownstreamApprovalResponse() {
        SubmitReturnFromDownstreamApprovalResponse res = new SubmitReturnFromDownstreamApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        res.setStatusEnum(SupplyStatus.APPROVAL.getSortOrder().intValue());
        return res;
    }

    /** 将撤回审批请求映射为 WithdrawApprovalCommand。 */
    @Override
    public WithdrawApprovalCommand toWithdrawApprovalCommand(
        WithdrawReturnFromDownstreamApprovalRequest request,
        Long operatorId) {
        WithdrawApprovalCommand command = new WithdrawApprovalCommand();
        command.setOperatorId(operatorId);
        WithdrawApprovalCommand.WithdrawApprovalItemCommand item = new WithdrawApprovalCommand.WithdrawApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /** 装配撤回审批响应。 */
    @Override
    public WithdrawReturnFromDownstreamApprovalResponse toWithdrawReturnFromDownstreamApprovalResponse() {
        WithdrawReturnFromDownstreamApprovalResponse res = new WithdrawReturnFromDownstreamApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        res.setStatusEnum(SupplyStatus.WITHDRAW.getSortOrder().intValue());
        return res;
    }

    /** 将同意请求映射为领域 AgreeRequestCommand。 */
    @Override
    public AgreeRequestCommand toAgreeRequestCommand(AgreeReturnFromDownstreamRequest request, Long operatorId) {
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

    /** 将写请求映射为领域 Create 命令（含操作人与申请人）。 将已同意供应请求行装配为发放命令。 */
    @Override
    public CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommandForReturnFromDownstream(
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
                item.setTypeEnum(SupplyType.CABINET_RETURN_FROM_DOWNSTREAM.getCode());
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

    /** 装配同意成功响应。 */
    @Override
    public AgreeReturnFromDownstreamResponse toAgreeReturnFromDownstreamResponse(boolean ok) {
        AgreeReturnFromDownstreamResponse res = new AgreeReturnFromDownstreamResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /** 将驳回请求映射为 RejectRequestCommand。 */
    @Override
    public RejectRequestCommand toRejectRequestCommand(RejectReturnFromDownstreamRequest request, Long operatorId) {
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

    /** 装配驳回响应。 */
    @Override
    public RejectReturnFromDownstreamResponse toRejectReturnFromDownstreamResponse(boolean ok) {
        RejectReturnFromDownstreamResponse res = new RejectReturnFromDownstreamResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /** 将删除请求映射为 RemoveByBusNoCommand。 */
    @Override
    public RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveReturnFromDownstreamRequest request, Long operatorId) {
        RemoveByBusNoCommand command = new RemoveByBusNoCommand();
        command.setOperatorId(operatorId);
        RemoveByBusNoCommand.RemoveByBusNoItemCommand item = new RemoveByBusNoCommand.RemoveByBusNoItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /** 装配删除响应。 */
    @Override
    public RemoveReturnFromDownstreamResponse toRemoveReturnFromDownstreamResponse(boolean ok) {
        RemoveReturnFromDownstreamResponse res = new RemoveReturnFromDownstreamResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /** 将产品/库存分页映射为选品分页响应。 */
    @Override
    public PageProductsForReturnFromDownstreamResponse toPageProductsForReturnFromDownstreamResponse(
        Page<ProductDefinitionDisplayDto> page) {
        List<ProductDefinitionItemDto> content = new ArrayList<>();
        for (ProductDefinitionDisplayDto pr : page.getContent()) {
            if (pr == null) {
                continue;
            }
            content.add(this.toProductDefinitionItemDto(pr));
        }
        return PagedResultBuilder.build(new PageProductsForReturnFromDownstreamResponse(), content, page);
    }

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
        if (StringUtils.isNotBlank(pr.getProductTableName()) && pr.getProductDefinitionId() != null) {
            dto.setPurchasePrice(chargeItemDefinitionQueryService.resolveFirstPurchasePriceValue(
                pr.getProductTableName().trim(), pr.getProductDefinitionId()));
        }
        dto.setManufacturer(pr.getManufacturer());
        dto.setPyStr(pr.getPyStr());
        dto.setWbStr(pr.getWbStr());
        return dto;
    }

    private Integer toApiSortSupplyType(String code) {
        SupplyType t = EnumUtil.fromCode(SupplyType.class, code);
        if (t == null) {
            return null;
        }
        return t.getSortOrder().intValue();
    }

    private Integer toApiSortSupplyStatus(String code) {
        SupplyStatus s = EnumUtil.fromCode(SupplyStatus.class, code);
        if (s == null) {
            return null;
        }
        return s.getSortOrder().intValue();
    }

    private Integer toApiSortSupplyCategory(String code) {
        SupplyCategory c = EnumUtil.fromCode(SupplyCategory.class, code);
        if (c == null) {
            return null;
        }
        return c.getSortOrder().intValue();
    }

    private Integer toApiSortSupplyRequestType(String code) {
        SupplyRequestType t = EnumUtil.fromCode(SupplyRequestType.class, code);
        if (t == null) {
            return null;
        }
        return t.getSortOrder().intValue();
    }

    private String resolveSupplyCategoryCode(Integer categoryEnumSort) {
        if (categoryEnumSort == null) {
            return SupplyCategory.STOCK_SUPPLY.getCode();
        }
        SupplyCategory category = EnumUtil.requireFromSortOrder(SupplyCategory.class, categoryEnumSort.longValue());
        return category.getCode();
    }
}
