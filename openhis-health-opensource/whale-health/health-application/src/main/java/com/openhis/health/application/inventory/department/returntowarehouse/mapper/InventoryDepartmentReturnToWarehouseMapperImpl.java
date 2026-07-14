/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.returntowarehouse.mapper;

import com.openhis.health.application.inventory.department.returntowarehouse.dto.LinkableStockInListRowDto;
import com.openhis.health.application.inventory.department.returntowarehouse.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.department.returntowarehouse.dto.ReturnableStockInLineDto;
import com.openhis.health.application.inventory.department.returntowarehouse.dto.ReturnToWarehouseDetailLineDto;
import com.openhis.health.application.inventory.department.returntowarehouse.dto.ReturnToWarehouseLineDto;
import com.openhis.health.application.inventory.department.returntowarehouse.dto.ReturnToWarehouseListRowDto;
import com.openhis.health.application.inventory.department.returntowarehouse.dto.SupplierOptionDto;
import com.openhis.health.application.inventory.department.returntowarehouse.dto.WarehouseOptionDto;
import com.openhis.health.application.inventory.department.returntowarehouse.request.*;
import com.openhis.health.application.inventory.department.returntowarehouse.response.*;
import com.openhis.health.application.inventory.department.returntowarehouse.support.ReturnToWarehouseStockInLineHelper;
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
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 科室退药库装配实现：{@link InventoryDepartmentReturnToWarehouseMapper}。仅做字段搬运与枚举 code 归一；业务流程判断在应用编排层。
 */
@Component
@RequiredArgsConstructor
public class InventoryDepartmentReturnToWarehouseMapperImpl implements InventoryDepartmentReturnToWarehouseMapper {

    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final LocationQueryService locationQueryService;

    /**
     * 将启用位置列表映射为下拉选项 DTO。
     *
     * @param locations 启用库位列表
     * @return 位置下拉选项
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
     * @param suppliers 供应商列表项
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
     * 装配科室退药库页初始化响应。
     *
     * @param departmentPharmacyLocationOptions  源科室药房位置选项
     * @param warehouseLocationOptions 目的药库选项
     * @param supplierOptions          供应商选项
     * @return 页初始化响应
     */
    @Override
    public LoadReturnToWarehouseFormInitResponse toLoadReturnToWarehouseFormInitResponse(
        List<WarehouseOptionDto> departmentPharmacyLocationOptions,
        List<WarehouseOptionDto> warehouseLocationOptions,
        List<SupplierOptionDto> supplierOptions) {
        LoadReturnToWarehouseFormInitResponse res = new LoadReturnToWarehouseFormInitResponse();
        res.setDepartmentPharmacyLocationOptions(departmentPharmacyLocationOptions);
        res.setWarehouseLocationOptions(warehouseLocationOptions);
        res.setSupplierOptions(supplierOptions);
        return res;
    }

    /**
     * 将库房单据读模型分页装配为退药库列表响应。
     *
     * @param page 库房单据读模型分页
     * @return 列表分页响应
     */
    @Override
    public PageReturnToWarehousesResponse toPageReturnToWarehousesResponse(Page<WarehouseDocumentDetailQueryModel> page) {
        List<ReturnToWarehouseListRowDto> content = new ArrayList<>();
        for (WarehouseDocumentDetailQueryModel row : page.getContent()) {
            if (row == null) {
                continue;
            }
            content.add(this.toReturnToWarehouseListRowDto(row));
        }
        return PagedResultBuilder.build(new PageReturnToWarehousesResponse(), content, page);
    }

    /**
     * 库房单据读模型 → 科室退药库列表行 DTO（枚举转 API sortOrder）。
     *
     * @param row 库房单据读模型
     * @return 列表行 DTO
     */
    private ReturnToWarehouseListRowDto toReturnToWarehouseListRowDto(WarehouseDocumentDetailQueryModel row) {
        ReturnToWarehouseListRowDto dto = new ReturnToWarehouseListRowDto();
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
     * 将同号供应请求明细与产品读模型装配为退药库详情响应。
     *
     * @param lines        供应请求明细行
     * @param productByKey 产品定义读模型映射
     * @return 详情响应
     */
    @Override
    public GetReturnToWarehouseDetailResponse toGetReturnToWarehouseDetailResponse(
        List<SupplyRequestModel> lines,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        GetReturnToWarehouseDetailResponse res = new GetReturnToWarehouseDetailResponse();
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
        List<ReturnToWarehouseDetailLineDto> detailLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            detailLines.add(this.toDetailLine(line, productByKey));
        }
        res.setLines(detailLines);
        return res;
    }

    /**
     * 供应请求明细 → 退药库详情行 DTO（按产品键回填品名）。
     *
     * @param line         供应请求明细
     * @param productByKey 产品复合键 → 展示 DTO
     * @return 详情行 DTO
     */
    private ReturnToWarehouseDetailLineDto toDetailLine(
        SupplyRequestModel line,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        ReturnToWarehouseDetailLineDto dto = new ReturnToWarehouseDetailLineDto();
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

    /**
     * 将新建退药库请求装配为领域 CreateReturnCommand。
     *
     * @param request               新建请求
     * @param operatorId            操作人
     * @param applicantId           制单人员
     * @param originalBusNoSnapshot 关联科室入库单号快照
     * @return 领域写命令
     */
    @Override
    public CreateReturnCommand toCreateReturnToWarehouseCommand(
        CreateReturnToWarehouseRequest request,
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
     * 将修订退药库请求装配为领域 CreateReturnCommand（同号全量对齐）。
     *
     * @param request               修订请求
     * @param operatorId            操作人
     * @param applicantId           制单人员
     * @param originalBusNoSnapshot 关联科室入库单号快照
     * @return 领域写命令
     */
    @Override
    public CreateReturnCommand toCreateReturnToWarehouseCommandForUpdate(
        UpdateReturnToWarehouseRequest request,
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
     * 新建/修订退药库请求公共写命令装配（解析库位类型与供应类别）。
     *
     * @param originalBusNo           请求体关联入库单号
     * @param sourceLocationId        源科室药房库位
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
        List<ReturnToWarehouseLineDto> lines,
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
        String typeCode = SupplyType.DEPT_RETURN.getCode();
        String resolvedOriginal = originalBusNoSnapshot;
        if (StringUtils.isEmpty(resolvedOriginal)) {
            resolvedOriginal = originalBusNo;
        }
        for (ReturnToWarehouseLineDto line : lines) {
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
     * 装配新建退药库成功响应。
     *
     * @param busNo 新建业务单号
     * @return 新建响应
     */
    @Override
    public CreateReturnToWarehouseResponse toCreateReturnToWarehouseResponse(String busNo) {
        CreateReturnToWarehouseResponse res = new CreateReturnToWarehouseResponse();
        res.setBusNo(busNo);
        res.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getSortOrder().intValue());
        return res;
    }

    /**
     * 装配修订退药库成功响应。
     *
     * @param busNo 业务单号
     * @return 修订响应
     */
    @Override
    public UpdateReturnToWarehouseResponse toUpdateReturnToWarehouseResponse(String busNo) {
        UpdateReturnToWarehouseResponse res = new UpdateReturnToWarehouseResponse();
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
        SubmitReturnToWarehouseApprovalRequest request,
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
    public SubmitReturnToWarehouseApprovalResponse toSubmitReturnToWarehouseApprovalResponse() {
        SubmitReturnToWarehouseApprovalResponse res = new SubmitReturnToWarehouseApprovalResponse();
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
        WithdrawReturnToWarehouseApprovalRequest request,
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
    public WithdrawReturnToWarehouseApprovalResponse toWithdrawReturnToWarehouseApprovalResponse() {
        WithdrawReturnToWarehouseApprovalResponse res = new WithdrawReturnToWarehouseApprovalResponse();
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
    public AgreeRequestCommand toAgreeRequestCommand(AgreeReturnToWarehouseRequest request, Long operatorId) {
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
     * 将已同意明细行装配为退药库发放命令。
     *
     * @param lines      已同意的供应请求明细行
     * @param operatorId 操作人
     * @return 发放命令
     */
    @Override
    public CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommandForReturnToWarehouse(
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
                item.setTypeEnum(SupplyType.DEPT_RETURN.getCode());
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
     * 装配同意科室退库成功响应（含系统生成的退库入库壳单号）。
     *
     * @param deptReturnBusNo         科室退库单号
     * @param statusEnum                  同意后的状态码
     * @param generatedReturnInboundBusNo 退库入库壳单号
     * @return 同意响应
     */
    @Override
    public AgreeReturnToWarehouseResponse toAgreeReturnToWarehouseResponse(
        String deptReturnBusNo,
        String statusEnum,
        String generatedReturnInboundBusNo) {
        AgreeReturnToWarehouseResponse res = new AgreeReturnToWarehouseResponse();
        res.setBusNo(deptReturnBusNo);
        res.setStatusEnum(statusEnum);
        res.setGeneratedReturnInboundBusNo(generatedReturnInboundBusNo);
        res.setSuccess(Boolean.TRUE);
        return res;
    }

    /**
     * 将已同意科室退库明细装配为退库入库壳单写命令。
     *
     * @param agreedReturnLines   已同意科室退库明细
     * @param deptReturnBusNo 科室退库单号
     * @param operatorId          操作人
     * @param applicantId         制单人
     * @return 领域写命令
     */
    @Override
    public CreateReturnCommand toCreateReturnCommandForShellCounterpartyReturnInbound(
        List<SupplyRequestModel> agreedReturnLines,
        String deptReturnBusNo,
        Long operatorId,
        Long applicantId) {
        CreateReturnCommand command = new CreateReturnCommand();
        command.setOperatorId(operatorId);
        command.setBootstrapFinalStatusCode(SupplyStatus.AGREE.getCode());
        List<CreateReturnCommand.CreateReturnItemCommand> items = new ArrayList<>();
        String prBusNo = "";
        if (deptReturnBusNo != null) {
            prBusNo = deptReturnBusNo.trim();
        }
        LocalDateTime shellApplyTime = LocalDateTime.now();
        if (agreedReturnLines != null) {
            for (SupplyRequestModel line : agreedReturnLines) {
                if (line == null) {
                    continue;
                }
                CreateReturnCommand.CreateReturnItemCommand item = new CreateReturnCommand.CreateReturnItemCommand();
                item.setBusNo(null);
                item.setOriginalBusNo(prBusNo);
                item.setTypeEnum(SupplyType.CABINET_RETURN_STOCK_IN.getCode());
                item.setItemTable(line.getItemTable());
                item.setItemId(line.getItemId());
                item.setItemQuantity(line.getItemQuantity());
                item.setUnitCode(line.getUnitCode());
                item.setCategoryEnum(line.getCategoryEnum());
                item.setSourceTypeEnum(line.getSourceTypeEnum());
                item.setSourceLocationId(line.getSourceLocationId());
                item.setPurposeTypeEnum(line.getPurposeTypeEnum());
                item.setPurposeLocationId(line.getPurposeLocationId());
                item.setSupplierId(line.getSupplierId());
                item.setLotNumber(line.getLotNumber());
                item.setTraceNo(line.getTraceNo());
                item.setPrice(line.getPrice());
                item.setTotalPrice(line.getTotalPrice());
                item.setOccurrenceTime(line.getOccurrenceTime());
                item.setApplicantId(applicantId);
                item.setApplyTime(shellApplyTime);
                item.setReason(null);
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

    /**
     * 用可关联入库单聚合行构建分页响应。
     *
     * @param mergedTotal 去重后总单数
     * @param pageNo      页码
     * @param pageSize    页大小
     * @param records     列表行
     * @return 分页响应
     */
    @Override
    public PageLinkableReturnSourceDocumentsResponse toPageLinkableStockInsAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<LinkableStockInListRowDto> records) {
        List<LinkableStockInListRowDto> content;
        if (records == null) {
            content = new ArrayList<>();
        } else {
            content = records;
        }
        return PagedResultBuilder.build(new PageLinkableReturnSourceDocumentsResponse(), content, mergedTotal, pageNo, pageSize);
    }

    /**
     * 将已同意入库单与可退余量映射为退库预填响应。
     *
     * @param stockInLines    入库明细
     * @param returnableByKey 行键 → 可退余量
     * @param returnedByKey   行键 → 已退占用量
     * @param availableByKey  行键 → 源科室药房可用量
     * @param productByKey    产品读模型
     * @return 预填响应
     */
    @Override
    public GetLinkableStockInDetailResponse toGetLinkableStockInDetailResponse(
        List<SupplyRequestModel> stockInLines,
        Map<String, BigDecimal> returnableByKey,
        Map<String, BigDecimal> returnedByKey,
        Map<String, BigDecimal> availableByKey,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        GetLinkableStockInDetailResponse res = new GetLinkableStockInDetailResponse();
        if (stockInLines == null || stockInLines.isEmpty()) {
            res.setLines(new ArrayList<>());
            return res;
        }
        SupplyRequestModel head = stockInLines.get(0);
        String busNo = head.getBusNo();
        res.setOriginalBusNo(busNo);
        res.setStockInBusNo(busNo);
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
        Map<String, BigDecimal> stockInByKey = ReturnToWarehouseStockInLineHelper.sumSplitQuantitiesByLineKey(stockInLines);
        List<ReturnableStockInLineDto> lines = new ArrayList<>();
        for (SupplyRequestModel line : stockInLines) {
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            String key = ReturnToWarehouseStockInLineHelper.buildStockInLineKey(
                line.getItemTable(), line.getItemId(), line.getLotNumber());
            if (key.isEmpty()) {
                continue;
            }
            ReturnableStockInLineDto dto = new ReturnableStockInLineDto();
            dto.setItemTable(line.getItemTable());
            dto.setItemId(line.getItemId());
            dto.setStockInQuantity(stockInByKey.getOrDefault(key, line.getItemQuantity()));
            dto.setReturnedQuantity(safeReturned.getOrDefault(key, BigDecimal.ZERO));
            dto.setReturnableQuantity(safeReturnable.getOrDefault(key, BigDecimal.ZERO));
            dto.setSourceAvailableQuantity(safeAvailable.getOrDefault(key, BigDecimal.ZERO));
            dto.setUnitCode(line.getUnitCode());
            dto.setLotNumber(line.getLotNumber());
            dto.setTraceNo(line.getTraceNo());
            dto.setPrice(line.getPrice());
            dto.setTotalPrice(line.getTotalPrice());
            dto.setOccurrenceTime(line.getOccurrenceTime());
            dto.setRemake(line.getRemake());
            String productKey = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
            ProductDefinitionDisplayDto product = safeProducts.get(productKey);
            if (product != null) {
                // —— 产品展示富化：行价仍取源入库落库价。——
                dto.setProductName(product.getProductName());
                dto.setSpecification(product.getTotalVolume());
                dto.setManufacturer(product.getManufacturer());
                dto.setUnitCodeDictText(
                    com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler
                        .resolveUnitDisplayName(null, line.getUnitCode(), product));
            }
            lines.add(dto);
        }
        res.setLines(lines);
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
    public RejectRequestCommand toRejectRequestCommand(RejectReturnToWarehouseRequest request, Long operatorId) {
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
     * 装配驳回退药库成功响应。
     *
     * @param ok 是否驳回成功
     * @return 驳回响应
     */
    @Override
    public RejectReturnToWarehouseResponse toRejectReturnToWarehouseResponse(boolean ok) {
        RejectReturnToWarehouseResponse res = new RejectReturnToWarehouseResponse();
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
    public RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveReturnToWarehouseRequest request, Long operatorId) {
        RemoveByBusNoCommand command = new RemoveByBusNoCommand();
        command.setOperatorId(operatorId);
        RemoveByBusNoCommand.RemoveByBusNoItemCommand item = new RemoveByBusNoCommand.RemoveByBusNoItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /**
     * 装配删除退药库成功响应。
     *
     * @param ok 是否删除成功
     * @return 删除响应
     */
    @Override
    public RemoveReturnToWarehouseResponse toRemoveReturnToWarehouseResponse(boolean ok) {
        RemoveReturnToWarehouseResponse res = new RemoveReturnToWarehouseResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /**
     * 将产品定义读模型分页装配为退药库选品分页响应（本场景已废弃自由选品）。
     *
     * @param page 产品定义读模型分页
     * @return 选品分页响应
     */
    @Override
    public PageProductsForReturnToWarehouseResponse toPageProductsForReturnToWarehouseResponse(
        Page<ProductDefinitionDisplayDto> page) {
        List<ProductDefinitionItemDto> content = new ArrayList<>();
        for (ProductDefinitionDisplayDto pr : page.getContent()) {
            if (pr == null) {
                continue;
            }
            content.add(this.toProductDefinitionItemDto(pr));
        }
        return PagedResultBuilder.build(new PageProductsForReturnToWarehouseResponse(), content, page);
    }

    /**
     * 产品定义读模型 → 退药库选品 DTO（含首条采购价 enrich）。
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
        if (StringUtils.isNotEmpty(pr.getProductTableName()) && pr.getProductDefinitionId() != null) {
            dto.setPurchasePrice(chargeItemDefinitionQueryService.resolveFirstPurchasePriceValue(
                pr.getProductTableName().trim(), pr.getProductDefinitionId()));
        }
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
