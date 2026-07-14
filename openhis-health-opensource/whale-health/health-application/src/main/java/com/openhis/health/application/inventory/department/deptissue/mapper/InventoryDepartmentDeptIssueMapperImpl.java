/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissue.mapper;

import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.inventory.department.deptissue.dto.DeptIssueDetailLineDto;
import com.openhis.health.application.inventory.department.deptissue.dto.DeptIssueLineDto;
import com.openhis.health.application.inventory.department.deptissue.dto.DeptIssueListRowDto;
import com.openhis.health.application.inventory.department.deptissue.dto.DeptPharmacyOptionDto;
import com.openhis.health.application.inventory.department.deptissue.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.department.deptissue.request.AgreeDeptIssueRequest;
import com.openhis.health.application.inventory.department.deptissue.request.CreateDeptIssueRequest;
import com.openhis.health.application.inventory.department.deptissue.request.RejectDeptIssueRequest;
import com.openhis.health.application.inventory.department.deptissue.request.RemoveDeptIssueRequest;
import com.openhis.health.application.inventory.department.deptissue.request.SubmitDeptIssueApprovalRequest;
import com.openhis.health.application.inventory.department.deptissue.request.UpdateDeptIssueRequest;
import com.openhis.health.application.inventory.department.deptissue.request.WithdrawDeptIssueApprovalRequest;
import com.openhis.health.application.inventory.department.deptissue.response.AgreeDeptIssueResponse;
import com.openhis.health.application.inventory.department.deptissue.response.CreateDeptIssueResponse;
import com.openhis.health.application.inventory.department.deptissue.response.GetDeptIssueDetailResponse;
import com.openhis.health.application.inventory.department.deptissue.response.LoadDeptIssueFormInitResponse;
import com.openhis.health.application.inventory.department.deptissue.response.PageDeptIssuesResponse;
import com.openhis.health.application.inventory.department.deptissue.response.PageProductsForDeptIssueResponse;
import com.openhis.health.application.inventory.department.deptissue.response.RejectDeptIssueResponse;
import com.openhis.health.application.inventory.department.deptissue.response.RemoveDeptIssueResponse;
import com.openhis.health.application.inventory.department.deptissue.response.SubmitDeptIssueApprovalResponse;
import com.openhis.health.application.inventory.department.deptissue.response.UpdateDeptIssueResponse;
import com.openhis.health.application.inventory.department.deptissue.response.WithdrawDeptIssueApprovalResponse;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.SupplyCategory;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.workflow.inventoryitem.command.DecreaseQuantityCommand;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.health.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateDeptIssueCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 科室领用单装配实现：{@link InventoryDepartmentDeptIssueMapper}。
 *
 * <p>仅做字段搬运与枚举 code 归一；业务流程判断在 {@link com.openhis.health.application.inventory.department.deptissue.service.InventoryDepartmentDeptIssueAppService}。
 * 各 {@code @Override} 方法 JavaDoc 与接口契约一致（B.1，禁止单独 {@code @inheritDoc} 占位）。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryDepartmentDeptIssueMapperImpl implements InventoryDepartmentDeptIssueMapper {

    /** 位置读服务：推导 {@code purposeTypeEnum} 与药房门控。 */
    private final LocationQueryService locationQueryService;

    /** 组织读服务：enrich 上级科室展示名。 */
    private final OrganizationQueryService organizationQueryService;

    /**
     * 将位置实体列表装配为科室领用页药房下拉（含上级科室只读字段）。
     *
     * @param locations 位置实体列表，可为 null
     * @return 下拉项列表，永不为 null
     */
    @Override
    public List<DeptPharmacyOptionDto> toDeptPharmacyOptionDtoListFromLocations(List<Location> locations) {
        List<DeptPharmacyOptionDto> list = new ArrayList<>();
        if (locations == null) {
            return list;
        }
        Set<Long> orgIds = new HashSet<>();
        for (Location location : locations) {
            if (location != null && location.getOrganizationId() != null) {
                orgIds.add(location.getOrganizationId());
            }
        }
        Map<Long, String> orgNameById = this.organizationQueryService.findOrganizationNamesByIds(orgIds);
        for (Location location : locations) {
            if (location == null || location.getId() == null) {
                continue;
            }
            Long id = location.getId();
            String label = location.getName();
            if (StringUtils.isEmpty(label)) {
                label = String.valueOf(id);
            }
            DeptPharmacyOptionDto dto = new DeptPharmacyOptionDto();
            dto.setValue(String.valueOf(id));
            dto.setLabel(label);
            Long orgId = location.getOrganizationId();
            if (orgId != null) {
                dto.setOrganizationId(String.valueOf(orgId));
                dto.setOrganizationName(orgNameById.get(orgId));
            }
            list.add(dto);
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
    public List<OptionDto> toOptionDtoListProductTableTypes() {
        List<OptionDto> list = new ArrayList<>();
        list.add(new OptionDto(1L, TableNameConstants.MED_MEDICATION_DEFINITION, "药品"));
        list.add(new OptionDto(2L, TableNameConstants.ADM_DEVICE_DEFINITION, "耗材"));
        return list;
    }

    /**
     * 装配科室领用页初始化响应（选项集合写入各字段）。
     *
     * @param pharmacyOpts   科室药房下拉
     * @param productTables  物品表类型下拉
     * @param statusOpts     状态下拉
     * @param pharmacistOpts 药师下拉
     * @return 页初始化响应
     */
    @Override
    public LoadDeptIssueFormInitResponse toLoadDeptIssueFormInitResponse(
        List<DeptPharmacyOptionDto> pharmacyOpts,
        List<OptionDto> productTables,
        List<OptionDto> statusOpts,
        List<OptionDto> pharmacistOpts) {
        LoadDeptIssueFormInitResponse res = new LoadDeptIssueFormInitResponse();
        res.setDepartmentPharmacyOptions(pharmacyOpts);
        res.setProductTableOptions(productTables);
        res.setStatusOptions(statusOpts);
        res.setPharmacistOptions(pharmacistOpts);
        return res;
    }

    /**
     * 按 busNo 顺序聚合供应请求明细为列表行 DTO。
     *
     * @param busNoPageOrder 当前页单号顺序
     * @param detailRows     当前页涉及的全部明细行
     * @return 聚合后的列表行
     */
    @Override
    public List<DeptIssueListRowDto> toDeptIssueListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows) {
        List<DeptIssueListRowDto> out = new ArrayList<>();
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
                        ErrorCodeConstants.DEP_IS_102, "同号领用单状态头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getPurposeLocationId(), line.getPurposeLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.DEP_IS_102, "同号领用单药房头字段不一致: " + busNo);
                }
            }
            LocalDateTime maxApply = null;
            for (SupplyRequestModel line : lines) {
                if (line == null) {
                    continue;
                }
                if (line.getApplyTime() != null) {
                    if (maxApply == null || line.getApplyTime().isAfter(maxApply)) {
                        maxApply = line.getApplyTime();
                    }
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
            DeptIssueListRowDto dto = new DeptIssueListRowDto();
            dto.setBusNo(busNo);
            dto.setStatusEnum(head.getStatusEnum());
            // 驳回态：把供应行 reason 带到列表 rejectReason，供侧栏卡片展示
            if (SupplyStatus.REJECT.getCode().equals(head.getStatusEnum())) {
                dto.setRejectReason(head.getReason());
            }
            if (head.getPurposeLocationId() != null) {
                dto.setPurposeLocationId(String.valueOf(head.getPurposeLocationId()));
            }
            dto.setApplicantId(head.getApplicantId());
            dto.setApproverId(head.getApproverId());
            dto.setApplyTime(maxApply);
            dto.setApprovalTime(approvalTime);
            if (head.getPurposeLocationId() != null) {
                Long orgId = this.resolveOrganizationIdForLocation(head.getPurposeLocationId());
                if (orgId != null) {
                    dto.setConsumingOrganizationId(String.valueOf(orgId));
                }
            }
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
    public PageDeptIssuesResponse toPageDeptIssuesAggregatedResponse(
        long mergedTotal, int pageNo, int pageSize, List<DeptIssueListRowDto> records) {
        List<DeptIssueListRowDto> content = new ArrayList<>();
        if (records != null) {
            content = records;
        }
        return PagedResultBuilder.build(new PageDeptIssuesResponse(), content, mergedTotal, pageNo, pageSize);
    }

    /**
     * 将供应请求行列表映射为详情响应（含明细与目录提示）。
     *
     * @param lines        供应请求明细行
     * @param productHints 行 id 到目录编辑提示的映射
     * @return 详情响应
     */
    @Override
    public GetDeptIssueDetailResponse toGetDeptIssueDetailResponse(
        List<SupplyRequestModel> lines,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints) {
        GetDeptIssueDetailResponse res = new GetDeptIssueDetailResponse();
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
        res.setReason(head.getReason());
        res.setRemake(head.getRemake());
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
        if (head.getPurposeLocationId() != null) {
            Long orgId = this.resolveOrganizationIdForLocation(head.getPurposeLocationId());
            if (orgId != null) {
                res.setConsumingOrganizationId(String.valueOf(orgId));
            }
        }
        List<DeptIssueDetailLineDto> detailLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            DeptIssueDetailLineDto dto = this.toDeptIssueDetailLineDto(line);
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
    public DeptIssueDetailLineDto toDeptIssueDetailLineDto(SupplyRequestModel line) {
        if (line == null) {
            return null;
        }
        DeptIssueDetailLineDto dto = new DeptIssueDetailLineDto();
        dto.setItemTable(line.getItemTable());
        dto.setItemId(line.getItemId());
        dto.setLotNumber(line.getLotNumber());
        dto.setTraceNo(line.getTraceNo());
        dto.setItemQuantity(line.getItemQuantity());
        dto.setUnitCode(line.getUnitCode());
        dto.setPrice(line.getPrice());
        dto.setTotalPrice(line.getTotalPrice());
        dto.setItemName(null);
        dto.setSpec(null);
        dto.setManufacturer(null);
        dto.setUnitName(null);
        dto.setProductCatalog(null);
        return dto;
    }

    /**
     * 将新建科室领用请求装配为领域 CreateDeptIssueCommand。
     *
     * @param request     新建请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @param busNo       预分配业务单号；新建时为 null
     * @return 领域写命令
     */
    @Override
    public CreateDeptIssueCommand toCreateDeptIssueCommand(
        CreateDeptIssueRequest request,
        Long operatorId,
        Long applicantId,
        String busNo) {
        CreateDeptIssueCommand command = new CreateDeptIssueCommand();
        command.setOperatorId(operatorId);
        List<CreateDeptIssueCommand.CreateDeptIssueItemCommand> items = new ArrayList<>();
        if (request.getLines() != null) {
            String purposeTypeCode =
                this.locationQueryService.requireSupplyRequestTypeForLocationId(request.getPurposeLocationId()).getCode();
            String categoryCode = SupplyCategory.STOCK_SUPPLY.getCode();
            String typeCode = SupplyType.DEPT_ISSUE.getCode();
            for (DeptIssueLineDto line : request.getLines()) {
                if (line == null) {
                    continue;
                }
                CreateDeptIssueCommand.CreateDeptIssueItemCommand item = new CreateDeptIssueCommand.CreateDeptIssueItemCommand();
                item.setBusNo(busNo);
                item.setTypeEnum(typeCode);
                item.setItemTable(line.getItemTable());
                item.setItemId(line.getItemId());
                item.setItemQuantity(line.getItemQuantity());
                item.setUnitCode(line.getUnitCode());
                item.setCategoryEnum(categoryCode);
                item.setPurposeTypeEnum(purposeTypeCode);
                item.setPurposeLocationId(request.getPurposeLocationId());
                item.setPractitionerId(request.getPractitionerId());
                item.setReason(request.getReason());
                item.setRemake(request.getRemake());
                item.setLotNumber(line.getLotNumber());
                item.setTraceNo(line.getTraceNo());
                item.setPrice(line.getPrice());
                item.setTotalPrice(this.resolveLineTotalPrice(line));
                item.setApplicantId(applicantId);
                item.setApplyTime(null);
                item.setOccurrenceTime(null);
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

    /**
     * 将修订科室领用请求装配为领域 CreateDeptIssueCommand（同号全量对齐）。
     *
     * @param request     修订请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    @Override
    public CreateDeptIssueCommand toCreateDeptIssueCommand(
        UpdateDeptIssueRequest request,
        Long operatorId,
        Long applicantId) {
        if (request == null) {
            throw new IllegalArgumentException("request 不能为空");
        }
        CreateDeptIssueRequest synthetic = new CreateDeptIssueRequest();
        synthetic.setPurposeLocationId(request.getPurposeLocationId());
        synthetic.setPractitionerId(request.getPractitionerId());
        synthetic.setReason(request.getReason());
        synthetic.setRemake(request.getRemake());
        synthetic.setLines(request.getLines());
        String busNo = null;
        if (request.getBusNo() != null) {
            busNo = request.getBusNo().trim();
        }
        if (busNo != null && busNo.isEmpty()) {
            busNo = null;
        }
        return this.toCreateDeptIssueCommand(synthetic, operatorId, applicantId, busNo);
    }

    /**
     * 装配新建科室领用成功响应。
     *
     * @param busNo 新建业务单号
     * @return 新建响应
     */
    @Override
    public CreateDeptIssueResponse toCreateDeptIssueResponse(String busNo) {
        CreateDeptIssueResponse res = new CreateDeptIssueResponse();
        res.setBusNo(busNo);
        return res;
    }

    /**
     * 装配修订科室领用成功响应。
     *
     * @param busNo 业务单号
     * @return 修订响应
     */
    @Override
    public UpdateDeptIssueResponse toUpdateDeptIssueResponse(String busNo) {
        UpdateDeptIssueResponse res = new UpdateDeptIssueResponse();
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
        SubmitDeptIssueApprovalRequest request, Long operatorId, Long applicantId) {
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
    public SubmitDeptIssueApprovalResponse toSubmitDeptIssueApprovalResponse(SupplyStatus statusAfter) {
        SubmitDeptIssueApprovalResponse res = new SubmitDeptIssueApprovalResponse();
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
    public WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawDeptIssueApprovalRequest request, Long operatorId) {
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
    public WithdrawDeptIssueApprovalResponse toWithdrawDeptIssueApprovalResponse(SupplyStatus statusAfter) {
        WithdrawDeptIssueApprovalResponse res = new WithdrawDeptIssueApprovalResponse();
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
    public AgreeRequestCommand toAgreeRequestCommand(AgreeDeptIssueRequest request, Long operatorId) {
        AgreeRequestCommand command = new AgreeRequestCommand();
        command.setOperatorId(operatorId);
        AgreeRequestCommand.AgreeRequestItemCommand item = new AgreeRequestCommand.AgreeRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        command.setItem(item);
        return command;
    }

    /**
     * 将已同意明细行装配为科室领用发放命令。
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
                BigDecimal issueQty = line.getItemQuantity();
                if (issueQty == null || issueQty.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand item =
                    new CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand();
                item.setRequestId(line.getId());
                item.setTypeEnum(SupplyType.DEPT_ISSUE.getCode());
                item.setItemTable(line.getItemTable());
                item.setItemId(line.getItemId());
                item.setQuantity(issueQty);
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
     * 装配科室领用扣减库存命令。
     *
     * @param inventoryItemId 库存项主键
     * @param decreaseQty     减少数量（拆零，正数）
     * @param allowNegative   是否允许负库存
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
     * 装配同意科室领用成功响应。
     *
     * @param busNo      业务单号
     * @param statusEnum 同意后状态 code
     * @return 同意响应
     */
    @Override
    public AgreeDeptIssueResponse toAgreeDeptIssueResponse(String busNo, String statusEnum) {
        AgreeDeptIssueResponse res = new AgreeDeptIssueResponse();
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
    public RejectRequestCommand toRejectRequestCommand(RejectDeptIssueRequest request, Long operatorId) {
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

    /**
     * 装配驳回科室领用成功响应。
     *
     * @param ok 是否驳回成功
     * @return 驳回响应
     */
    @Override
    public RejectDeptIssueResponse toRejectDeptIssueResponse(boolean ok) {
        RejectDeptIssueResponse res = new RejectDeptIssueResponse();
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
    public RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveDeptIssueRequest request, Long operatorId) {
        RemoveByBusNoCommand command = new RemoveByBusNoCommand();
        command.setOperatorId(operatorId);
        RemoveByBusNoCommand.RemoveByBusNoItemCommand item = new RemoveByBusNoCommand.RemoveByBusNoItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /**
     * 装配删除科室领用成功响应。
     *
     * @param ok 是否删除成功
     * @return 删除响应
     */
    @Override
    public RemoveDeptIssueResponse toRemoveDeptIssueResponse(boolean ok) {
        RemoveDeptIssueResponse res = new RemoveDeptIssueResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /**
     * 将现库库存分页装配为科室领用选品分页响应（不含价带 enrich，由 AppService 补全）。
     *
     * @param page 库存读模型分页
     * @return 选品分页响应
     */
    @Override
    public PageProductsForDeptIssueResponse toPageProductsForDeptIssueResponse(Page<InventoryItemModel> page) {
        List<ProductDefinitionItemDto> content = new ArrayList<>();
        for (InventoryItemModel inv : page.getContent()) {
            if (inv == null) {
                continue;
            }
            content.add(this.toProductDefinitionItemDtoFromInventory(inv, null));
        }
        return PagedResultBuilder.build(new PageProductsForDeptIssueResponse(), content, page);
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
     * 按行单价与数量计算行总价；缺单价或数量时返回 null。
     *
     * @param line 写模型明细行
     * @return 行总价；无法计算时 null
     */
    private BigDecimal resolveLineTotalPrice(DeptIssueLineDto line) {
        if (line == null || line.getPrice() == null || line.getItemQuantity() == null) {
            return null;
        }
        return line.getPrice().multiply(line.getItemQuantity());
    }

    /**
     * 由科室药房库位主键解析上级科室 organization_id。
     *
     * @param locationId 科室药房库位主键
     * @return 上级科室主键；库位不存在或未配置时 null
     */
    private Long resolveOrganizationIdForLocation(Long locationId) {
        if (locationId == null) {
            return null;
        }
        return this.locationQueryService.findOptionalById(locationId)
            .map(Location::getOrganizationId)
            .orElse(null);
    }

    /**
     * 供应请求行按申请时间降序比较（null 申请时间排后）。
     *
     * @param a 比较左行
     * @param b 比较右行
     * @return Comparator 结果
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
     * 供应请求行按持久化主键升序比较（null id 排后）。
     *
     * @param a 比较左行
     * @param b 比较右行
     * @return Comparator 结果
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