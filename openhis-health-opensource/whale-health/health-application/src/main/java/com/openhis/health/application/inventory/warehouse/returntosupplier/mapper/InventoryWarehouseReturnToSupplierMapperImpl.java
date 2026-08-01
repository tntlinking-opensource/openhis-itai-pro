/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returntosupplier.mapper;
import com.openhis.health.application.inventory.warehouse.returntosupplier.dto.LinkableGoodsReceiptListRowDto;
import com.openhis.health.application.inventory.warehouse.returntosupplier.dto.ReturnableInboundLineDto;
import com.openhis.health.application.inventory.warehouse.returntosupplier.dto.ReturnToSupplierDetailLineDto;
import com.openhis.health.application.inventory.warehouse.returntosupplier.dto.ReturnToSupplierLineDto;
import com.openhis.health.application.inventory.warehouse.returntosupplier.dto.ReturnToSupplierListRowDto;
import com.openhis.health.application.inventory.warehouse.returntosupplier.dto.SupplierOptionDto;
import com.openhis.health.application.inventory.warehouse.returntosupplier.dto.WarehouseOptionDto;
import com.openhis.health.application.inventory.warehouse.returntosupplier.request.*;
import com.openhis.health.application.inventory.warehouse.returntosupplier.response.*;
import com.openhis.health.application.inventory.warehouse.returntosupplier.support.ReturnToSupplierInboundLineHelper;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
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
import com.openhis.health.domain.workflow.supplyrequest.command.CreateReturnToSupplierCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogEditorHintsAssembler;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 药库退货单应用层 Mapper 实现（AS-INV-WHS-005）：HTTP DTO 与领域命令/读模型之间的字段搬运。
 *
 * <p>不写业务门禁；枚举对外 sortOrder 与持久化 code 的转换在本类完成。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryWarehouseReturnToSupplierMapperImpl implements InventoryWarehouseReturnToSupplierMapper {

    private final LocationQueryService locationQueryService;

    /**
     * 将启用药库位置列表映射为下拉选项 DTO。
     *
     * @param locations 启用库位列表
     * @return 药库下拉选项
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
     * 将供应商列表项映射为下拉选项 DTO。
     *
     * @param suppliers 启用供应商列表项
     * @return 供应商下拉选项
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
     * 装配退供应商页初始化响应。
     *
     * @param warehouseOptions 药库选项
     * @param supplierOptions  供应商选项
     * @return 页初始化响应
     */
    @Override
    public LoadReturnToSupplierFormInitResponse toLoadReturnToSupplierFormInitResponse(
        List<WarehouseOptionDto> warehouseOptions,
        List<SupplierOptionDto> supplierOptions) {
        LoadReturnToSupplierFormInitResponse res = new LoadReturnToSupplierFormInitResponse();
        res.setWarehouseOptions(warehouseOptions);
        res.setSupplierOptions(supplierOptions);
        return res;
    }

    /**
     * 库房单据读模型 → 退供应商列表行 DTO（枚举转 API sortOrder）。
     *
     * @param row 库房单据读模型
     * @return 列表行 DTO
     */
    private ReturnToSupplierListRowDto toReturnToSupplierListRowDto(WarehouseDocumentDetailQueryModel row) {
        ReturnToSupplierListRowDto dto = new ReturnToSupplierListRowDto();
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
     * 将同号供应请求明细与产品读模型装配为退供应商详情响应。
     *
     * @param lines        供应请求明细行
     * @param productByKey 产品定义读模型映射
     * @return 详情响应
     */
    @Override
    public GetReturnToSupplierDetailResponse toGetReturnToSupplierDetailResponse(
        List<SupplyRequestModel> lines,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        GetReturnToSupplierDetailResponse res = new GetReturnToSupplierDetailResponse();
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
        res.setInvoiceNo(head.getInvoiceNo());
        List<ReturnToSupplierDetailLineDto> detailLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            detailLines.add(this.toDetailLine(line, productByKey));
        }
        res.setLines(detailLines);
        return res;
    }

    /**
     * 供应请求明细 → 退供应商详情行 DTO（按产品键回填品名）。
     *
     * @param line         供应请求明细
     * @param productByKey 产品复合键 → 展示 DTO
     * @return 详情行 DTO
     */
    private ReturnToSupplierDetailLineDto toDetailLine(
        SupplyRequestModel line,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        ReturnToSupplierDetailLineDto dto = new ReturnToSupplierDetailLineDto();
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
                // —— 品名 + 单位展示名 + 计量带目录，对齐进货详情 IF-WHS-IN-03 富化口径。——
                dto.setProductName(pr.getProductName());
                dto.setUnitCodeDictText(
                    com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler
                        .resolveUnitDisplayName(null, line.getUnitCode(), pr));
                dto.setProductCatalog(ProductDefinitionCatalogEditorHintsAssembler.fromDisplayDto(pr));
            }
        }
        return dto;
    }

    /**
     * 将新建退供应商请求装配为领域 CreateReturnToSupplierCommand。
     *
     * @param request               新建请求
     * @param operatorId            操作人
     * @param applicantId           制单人员
     * @param originalBusNoSnapshot 关联上游进货单号快照
     * @return 领域写命令
     */
    @Override
    public CreateReturnToSupplierCommand toCreateReturnToSupplierCommand(
        CreateReturnToSupplierRequest request,
        Long operatorId,
        Long applicantId,
        String originalBusNoSnapshot) {
        return this.buildCreateReturnToSupplierCommand(
            request.getOriginalBusNo(),
            request.getSourceLocationId(),
            request.getPurposeLocationId(),
            request.getCategoryEnum(),
            request.getSupplierId(),
            request.getPractitionerId(),
            request.getInvoiceNo(),
            request.getLines(),
            null,
            operatorId,
            applicantId,
            originalBusNoSnapshot);
    }

    /**
     * 将修订退供应商请求装配为领域 CreateReturnToSupplierCommand（同号全量对齐）。
     *
     * @param request               修订请求
     * @param operatorId            操作人
     * @param applicantId           制单人员
     * @param originalBusNoSnapshot 关联上游进货单号快照
     * @return 领域写命令
     */
    @Override
    public CreateReturnToSupplierCommand toCreateReturnToSupplierCommandForUpdate(
        UpdateReturnToSupplierRequest request,
        Long operatorId,
        Long applicantId,
        String originalBusNoSnapshot) {
        return this.buildCreateReturnToSupplierCommand(
            originalBusNoSnapshot,
            request.getSourceLocationId(),
            request.getPurposeLocationId(),
            request.getCategoryEnum(),
            request.getSupplierId(),
            request.getPractitionerId(),
            request.getInvoiceNo(),
            request.getLines(),
            request.getBusNo(),
            operatorId,
            applicantId,
            originalBusNoSnapshot);
    }

    /**
     * 新建/修订退供应商请求公共写命令装配（解析库位类型与供应类别）。
     *
     * @param originalBusNo           请求体关联进货单号
     * @param sourceLocationId        源药库库位
     * @param purposeLocationId       目的库位（供应商侧占位）
     * @param categoryEnumSort        前端供应类别 sort
     * @param supplierId              供应商
     * @param practitionerId          经办人
     * @param invoiceNo               采购发票号（可空）
     * @param lines                   明细行
     * @param busNo                   修订时业务单号；新建为 null
     * @param operatorId              操作人
     * @param applicantId             制单人
     * @param originalBusNoSnapshot   关联单号快照（优先于 originalBusNo）
     * @return 领域 CreateReturnToSupplierCommand
     */
    private CreateReturnToSupplierCommand buildCreateReturnToSupplierCommand(
        String originalBusNo,
        Long sourceLocationId,
        Long purposeLocationId,
        Integer categoryEnumSort,
        Long supplierId,
        Long practitionerId,
        String invoiceNo,
        List<ReturnToSupplierLineDto> lines,
        String busNo,
        Long operatorId,
        Long applicantId,
        String originalBusNoSnapshot) {
        CreateReturnToSupplierCommand command = new CreateReturnToSupplierCommand();
        command.setOperatorId(operatorId);
        List<CreateReturnToSupplierCommand.CreateReturnToSupplierItemCommand> items = new ArrayList<>();
        if (lines == null) {
            command.setItems(items);
            return command;
        }
        String sourceTypeCode = this.locationQueryService.requireSupplyRequestTypeForLocationId(sourceLocationId).getCode();
        String purposeTypeCode = this.locationQueryService.requireSupplyRequestTypeForLocationId(purposeLocationId).getCode();
        String categoryCode = this.resolveSupplyCategoryCode(categoryEnumSort);
        String typeCode = SupplyType.CABINET_RETURN_TO_SUPPLIER.getCode();
        String resolvedOriginal = originalBusNoSnapshot;
        if (StringUtils.isEmpty(resolvedOriginal)) {
            // 新建路径使用请求体 originalBusNo
            resolvedOriginal = originalBusNo;
        }
        for (ReturnToSupplierLineDto line : lines) {
            if (line == null) {
                continue;
            }
            CreateReturnToSupplierCommand.CreateReturnToSupplierItemCommand item =
                new CreateReturnToSupplierCommand.CreateReturnToSupplierItemCommand();
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
            item.setPractitionerId(practitionerId);
            item.setOccurrenceTime(line.getOccurrenceTime());
            item.setApplicantId(applicantId);
            item.setApplyTime(null);
            item.setReason(line.getReason());
            item.setLotNumber(line.getLotNumber());
            item.setTraceNo(line.getTraceNo());
            // 单头发票号写入每一行；空时由 3504 上报从关联进货单回落
            item.setInvoiceNo(invoiceNo);
            items.add(item);
        }
        command.setItems(items);
        return command;
    }

    /**
     * 装配新建退供应商成功响应。
     *
     * @param busNo 新建业务单号
     * @return 新建响应
     */
    @Override
    public CreateReturnToSupplierResponse toCreateReturnToSupplierResponse(String busNo) {
        CreateReturnToSupplierResponse res = new CreateReturnToSupplierResponse();
        res.setBusNo(busNo);
        res.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getSortOrder().intValue());
        return res;
    }

    /**
     * 装配修订退供应商成功响应。
     *
     * @param busNo 业务单号
     * @return 修订响应
     */
    @Override
    public UpdateReturnToSupplierResponse toUpdateReturnToSupplierResponse(String busNo) {
        UpdateReturnToSupplierResponse res = new UpdateReturnToSupplierResponse();
        res.setBusNo(busNo);
        res.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getSortOrder().intValue());
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
        SubmitReturnToSupplierApprovalRequest request,
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
     * 装配提交审批成功响应。
     *
     * @return 提交响应
     */
    @Override
    public SubmitReturnToSupplierApprovalResponse toSubmitReturnToSupplierApprovalResponse() {
        SubmitReturnToSupplierApprovalResponse res = new SubmitReturnToSupplierApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        res.setStatusEnum(SupplyStatus.APPROVAL.getSortOrder().intValue());
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
    public WithdrawApprovalCommand toWithdrawApprovalCommand(
        WithdrawReturnToSupplierApprovalRequest request,
        Long operatorId) {
        WithdrawApprovalCommand command = new WithdrawApprovalCommand();
        command.setOperatorId(operatorId);
        WithdrawApprovalCommand.WithdrawApprovalItemCommand item = new WithdrawApprovalCommand.WithdrawApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /**
     * 装配撤回审批成功响应。
     *
     * @return 撤回响应
     */
    @Override
    public WithdrawReturnToSupplierApprovalResponse toWithdrawReturnToSupplierApprovalResponse() {
        WithdrawReturnToSupplierApprovalResponse res = new WithdrawReturnToSupplierApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        res.setStatusEnum(SupplyStatus.WITHDRAW.getSortOrder().intValue());
        return res;
    }

    /**
     * 将同意请求装配为领域 AgreeRequestCommand。
     *
     * @param request    同意请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    @Override
    public AgreeRequestCommand toAgreeRequestCommand(AgreeReturnToSupplierRequest request, Long operatorId) {
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
     * 将已同意明细行装配为退供应商发放命令。
     *
     * @param lines      已同意的供应请求明细行
     * @param operatorId 操作人
     * @return 发放命令
     */
    @Override
    public CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommandForReturnToSupplier(
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
                item.setTypeEnum(SupplyType.CABINET_RETURN_TO_SUPPLIER.getCode());
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
     * 装配同意退供应商成功响应。
     *
     * @param ok 是否同意成功
     * @return 同意响应
     */
    @Override
    public AgreeReturnToSupplierResponse toAgreeReturnToSupplierResponse(boolean ok) {
        AgreeReturnToSupplierResponse res = new AgreeReturnToSupplierResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /**
     * 将驳回请求装配为领域 RejectRequestCommand。
     *
     * @param request    驳回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    @Override
    public RejectRequestCommand toRejectRequestCommand(RejectReturnToSupplierRequest request, Long operatorId) {
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
     * 装配驳回退供应商成功响应。
     *
     * @param ok 是否驳回成功
     * @return 驳回响应
     */
    @Override
    public RejectReturnToSupplierResponse toRejectReturnToSupplierResponse(boolean ok) {
        RejectReturnToSupplierResponse res = new RejectReturnToSupplierResponse();
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
    public RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveReturnToSupplierRequest request, Long operatorId) {
        RemoveByBusNoCommand command = new RemoveByBusNoCommand();
        command.setOperatorId(operatorId);
        RemoveByBusNoCommand.RemoveByBusNoItemCommand item = new RemoveByBusNoCommand.RemoveByBusNoItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /**
     * 装配删除退供应商成功响应。
     *
     * @param ok 是否删除成功
     * @return 删除响应
     */
    @Override
    public RemoveReturnToSupplierResponse toRemoveReturnToSupplierResponse(boolean ok) {
        RemoveReturnToSupplierResponse res = new RemoveReturnToSupplierResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
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

    /**
     * 按 busNo 顺序聚合退货供应请求明细为列表行 DTO。
     *
     * @param busNoPageOrder 分页单号顺序
     * @param detailRows     同页全部明细
     * @return 聚合列表行
     */
    @Override
    public List<ReturnToSupplierListRowDto> toReturnToSupplierListAggregated(
        List<String> busNoPageOrder,
        List<SupplyRequestModel> detailRows) {
        List<ReturnToSupplierListRowDto> out = new ArrayList<>();
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
                        ErrorCodeConstants.WHS_RT_102, "同号退货单状态头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getOriginalBusNo(), line.getOriginalBusNo())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_RT_102, "同号退货单来源进货单不一致: " + busNo);
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
            ReturnToSupplierListRowDto dto = new ReturnToSupplierListRowDto();
            dto.setBusNo(busNo);
            dto.setOriginalBusNo(head.getOriginalBusNo());
            dto.setTypeEnum(this.toApiSortSupplyType(head.getTypeEnum()));
            dto.setStatusEnum(this.toApiSortSupplyStatus(head.getStatusEnum()));
            dto.setCategoryEnum(this.toApiSortSupplyCategory(head.getCategoryEnum()));
            dto.setSourceLocationId(head.getSourceLocationId());
            dto.setPurposeLocationId(head.getPurposeLocationId());
            dto.setSupplierId(head.getSupplierId());
            // —— 制单人/审批人主键取头行；展示名由 AppService 批量富化。——
            dto.setApplicantId(head.getApplicantId());
            dto.setApproverId(head.getApproverId());
            // —— 双写 totalPrice/totalAmount，兼容侧栏卡片字段兼容读取。——
            dto.setTotalPrice(totalAmount);
            dto.setTotalAmount(totalAmount);
            // —— 制单时间：优先申请时刻，缺省回落业务发生时刻（与进货关联选单同构）。——
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
     * 用聚合列表行构建退货单分页响应。
     *
     * @param mergedTotal 去重后总单数
     * @param pageNo      页码
     * @param pageSize    页大小
     * @param records     列表行
     * @return 分页响应
     */
    @Override
    public PageReturnToSuppliersResponse toPageReturnToSuppliersAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<ReturnToSupplierListRowDto> records) {
        List<ReturnToSupplierListRowDto> content;
        if (records == null) {
            content = new ArrayList<>();
        } else {
            content = records;
        }
        return PagedResultBuilder.build(new PageReturnToSuppliersResponse(), content, mergedTotal, pageNo, pageSize);
    }

    /**
     * 用可关联进货单聚合行构建分页响应。
     *
     * @param mergedTotal 去重后总单数
     * @param pageNo      页码
     * @param pageSize    页大小
     * @param records     列表行
     * @return 分页响应
     */
    @Override
    public PageLinkableGoodsReceiptsResponse toPageLinkableGoodsReceiptsAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<LinkableGoodsReceiptListRowDto> records) {
        List<LinkableGoodsReceiptListRowDto> content;
        if (records == null) {
            content = new ArrayList<>();
        } else {
            content = records;
        }
        return PagedResultBuilder.build(new PageLinkableGoodsReceiptsResponse(), content, mergedTotal, pageNo, pageSize);
    }

    /**
     * 将已同意进货单与可退余量映射为退货预填响应。
     *
     * <p>行上 {@code traceNo} 置空；由 AppService 调用 {@code ReturnToSupplierRemainingTracePrefiller}
     * 按当前院内剩余码覆盖（BR-WHS-RT-008），禁止在此回放进货历史码串。</p>
     *
     * @param inboundLines    进货明细
     * @param returnableByKey   行键 → 可退余量
     * @param returnedByKey     行键 → 已退占用量
     * @param availableByKey    行键 → 源库可用量
     * @param productByKey      产品读模型
     * @return 预填响应
     */
    @Override
    public GetLinkableGoodsReceiptDetailResponse toGetLinkableGoodsReceiptDetailResponse(
        List<SupplyRequestModel> inboundLines,
        Map<String, BigDecimal> returnableByKey,
        Map<String, BigDecimal> returnedByKey,
        Map<String, BigDecimal> availableByKey,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        GetLinkableGoodsReceiptDetailResponse res = new GetLinkableGoodsReceiptDetailResponse();
        if (inboundLines == null || inboundLines.isEmpty()) {
            res.setLines(new ArrayList<>());
            return res;
        }
        SupplyRequestModel head = inboundLines.get(0);
        String busNo = head.getBusNo();
        res.setOriginalBusNo(busNo);
        res.setGoodsReceiptBusNo(busNo);
        Long purposeLocationId = head.getPurposeLocationId();
        res.setPurposeLocationId(purposeLocationId);
        res.setSourceLocationId(purposeLocationId);
        res.setSupplierId(head.getSupplierId());
        res.setCategoryEnum(head.getCategoryEnum());
        res.setPractitionerId(head.getPractitionerId());
        Map<String, BigDecimal> safeReturnable = returnableByKey;
        if (safeReturnable == null) {
            safeReturnable = new LinkedHashMap<>();
        }
        Map<String, BigDecimal> safeReturned = returnedByKey;
        if (safeReturned == null) {
            safeReturned = new LinkedHashMap<>();
        }
        Map<String, BigDecimal> safeAvailable = availableByKey;
        if (safeAvailable == null) {
            safeAvailable = new LinkedHashMap<>();
        }
        Map<String, ProductDefinitionDisplayDto> safeProducts = productByKey;
        if (safeProducts == null) {
            safeProducts = new LinkedHashMap<>();
        }
        Map<String, BigDecimal> inboundByKey = ReturnToSupplierInboundLineHelper.sumSplitQuantitiesByLineKey(inboundLines);
        List<ReturnableInboundLineDto> lines = new ArrayList<>();
        for (SupplyRequestModel line : inboundLines) {
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            String key = ReturnToSupplierInboundLineHelper.buildInboundLineKey(
                line.getItemTable(), line.getItemId(), line.getLotNumber());
            if (key.isEmpty()) {
                continue;
            }
            ReturnableInboundLineDto dto = new ReturnableInboundLineDto();
            dto.setItemTable(line.getItemTable());
            dto.setItemId(line.getItemId());
            dto.setInboundQuantity(inboundByKey.getOrDefault(key, line.getItemQuantity()));
            dto.setReturnedQuantity(safeReturned.getOrDefault(key, BigDecimal.ZERO));
            dto.setReturnableQuantity(safeReturnable.getOrDefault(key, BigDecimal.ZERO));
            dto.setSourceAvailableQuantity(safeAvailable.getOrDefault(key, BigDecimal.ZERO));
            dto.setUnitCode(line.getUnitCode());
            dto.setLotNumber(line.getLotNumber());
            // 追溯码不由进货历史串回放；由 AppService 调 RemainingTracePrefiller 覆盖（BR-WHS-RT-008）
            dto.setTraceNo(null);
            dto.setPrice(line.getPrice());
            dto.setTotalPrice(line.getTotalPrice());
            dto.setOccurrenceTime(line.getOccurrenceTime());
            dto.setRemake(line.getRemake());
            String productKey = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
            ProductDefinitionDisplayDto product = safeProducts.get(productKey);
            if (product != null) {
                // —— 产品展示富化：行价仍取源进货落库价，不回落当前目录价。——
                dto.setProductName(product.getProductName());
                dto.setSpecification(product.getTotalVolume());
                dto.setManufacturer(product.getManufacturer());
                dto.setUnitCodeDictText(
                    com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler
                        .resolveUnitDisplayName(null, line.getUnitCode(), product));
                dto.setProductCatalog(ProductDefinitionCatalogEditorHintsAssembler.fromDisplayDto(product));
            }
            lines.add(dto);
        }
        res.setLines(lines);
        return res;
    }
}
