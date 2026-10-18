/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.deptissuereturn.mapper;

import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.application.inventory.department.deptissuereturn.dto.DeptIssueReturnDetailLineDto;
import com.zxplat.application.inventory.department.deptissuereturn.dto.DeptIssueReturnLineDto;
import com.zxplat.application.inventory.department.deptissuereturn.dto.DeptIssueReturnListRowDto;
import com.zxplat.application.inventory.department.deptissuereturn.dto.LinkableIssueListRowDto;
import com.zxplat.application.inventory.department.deptissuereturn.dto.LinkableIssueReturnLineDto;
import com.zxplat.application.inventory.department.deptissuereturn.request.AgreeDeptIssueReturnRequest;
import com.zxplat.application.inventory.department.deptissuereturn.request.CreateDeptIssueReturnRequest;
import com.zxplat.application.inventory.department.deptissuereturn.request.RejectDeptIssueReturnRequest;
import com.zxplat.application.inventory.department.deptissuereturn.request.RemoveDeptIssueReturnRequest;
import com.zxplat.application.inventory.department.deptissuereturn.request.SubmitDeptIssueReturnApprovalRequest;
import com.zxplat.application.inventory.department.deptissuereturn.request.UpdateDeptIssueReturnRequest;
import com.zxplat.application.inventory.department.deptissuereturn.request.WithdrawDeptIssueReturnApprovalRequest;
import com.zxplat.application.inventory.department.deptissuereturn.response.AgreeDeptIssueReturnResponse;
import com.zxplat.application.inventory.department.deptissuereturn.response.CreateDeptIssueReturnResponse;
import com.zxplat.application.inventory.department.deptissuereturn.response.GetDeptIssueReturnDetailResponse;
import com.zxplat.application.inventory.department.deptissuereturn.response.GetLinkableIssueDetailResponse;
import com.zxplat.application.inventory.department.deptissuereturn.response.LoadDeptIssueReturnFormInitResponse;
import com.zxplat.application.inventory.department.deptissuereturn.response.PageDeptIssueReturnsResponse;
import com.zxplat.application.inventory.department.deptissuereturn.response.PageLinkableIssueDocumentsResponse;
import com.zxplat.application.inventory.department.deptissuereturn.response.RejectDeptIssueReturnResponse;
import com.zxplat.application.inventory.department.deptissuereturn.response.RemoveDeptIssueReturnResponse;
import com.zxplat.application.inventory.department.deptissuereturn.response.SubmitDeptIssueReturnApprovalResponse;
import com.zxplat.application.inventory.department.deptissuereturn.response.UpdateDeptIssueReturnResponse;
import com.zxplat.application.inventory.department.deptissuereturn.response.WithdrawDeptIssueReturnApprovalResponse;
import com.zxplat.application.inventory.department.deptissuereturn.support.DeptIssueReturnLineHelper;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.domain.share.enums.SupplyCategory;
import com.zxplat.domain.share.enums.SupplyStatus;
import com.zxplat.domain.share.enums.SupplyType;
import com.zxplat.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.zxplat.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand;
import com.zxplat.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.CreateDeptIssueReturnCommand;
import com.zxplat.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.zxplat.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.zxplat.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.zxplat.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
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
 * 科室退领单装配实现：{@link InventoryDepartmentDeptIssueReturnMapper}。
 *
 * <p>仅做字段搬运与枚举 code 归一；业务流程判断在
 * {@link com.zxplat.application.inventory.department.deptissuereturn.service.InventoryDepartmentDeptIssueReturnAppService}。
 * 各 {@code @Override} 方法 JavaDoc 与接口契约一致（B.1，禁止单独 {@code @inheritDoc} 占位）。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryDepartmentDeptIssueReturnMapperImpl implements InventoryDepartmentDeptIssueReturnMapper {

    /** 位置读服务：推导 {@code purposeTypeEnum} 与展示 enrich。 */
    private final LocationQueryService locationQueryService;

    /** 组织读服务：enrich 上级科室展示名。 */
    private final OrganizationQueryService organizationQueryService;

    /**
     * 将位置实体列表装配为科室退领页药房下拉。
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
            OptionDto opt = new OptionDto(id, String.valueOf(id), label);
            if (StringUtils.isNotEmpty(p.getPyStr())) {
                opt.setPyStr(p.getPyStr().trim());
            }
            if (StringUtils.isNotEmpty(p.getWbStr())) {
                opt.setWbStr(p.getWbStr().trim());
            }
            list.add(opt);
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
     * 装配科室退领页初始化响应（选项集合写入各字段）。
     *
     * @param departmentPharmacies 科室药房下拉
     * @param statusOpts             状态下拉
     * @param pharmacistOpts         药师下拉
     * @return 页初始化响应
     */
    @Override
    public LoadDeptIssueReturnFormInitResponse toLoadDeptIssueReturnFormInitResponse(
        List<OptionDto> departmentPharmacies,
        List<OptionDto> statusOpts,
        List<OptionDto> pharmacistOpts) {
        LoadDeptIssueReturnFormInitResponse res = new LoadDeptIssueReturnFormInitResponse();
        res.setDepartmentPharmacies(departmentPharmacies);
        res.setSupplyStatuses(statusOpts);
        res.setPharmacists(pharmacistOpts);
        return res;
    }

    /**
     * 按 busNo 顺序聚合供应请求明细为科室退领列表行 DTO。
     *
     * @param busNoPageOrder 当前页单号顺序
     * @param detailRows     当前页涉及的全部明细行
     * @return 聚合后的列表行
     */
    @Override
    public List<DeptIssueReturnListRowDto> toDeptIssueReturnListAggregated(
        List<String> busNoPageOrder,
        List<SupplyRequestModel> detailRows) {
        List<DeptIssueReturnListRowDto> out = new ArrayList<>();
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
                        ErrorCodeConstants.DEP_TL_102, "同号退领单状态头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getPurposeLocationId(), line.getPurposeLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.DEP_TL_102, "同号退领单药房头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getOriginalBusNo(), line.getOriginalBusNo())) {
                    throw new BusinessException(
                        ErrorCodeConstants.DEP_TL_102, "同号退领单源领用单号头字段不一致: " + busNo);
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
            DeptIssueReturnListRowDto dto = new DeptIssueReturnListRowDto();
            dto.setBusNo(busNo);
            dto.setOriginalBusNo(head.getOriginalBusNo());
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
            // 领用科室：优先落库 consumingOrganizationId，历史单回落药房上级科室
            Long orgId = this.resolveConsumingOrganizationId(head);
            if (orgId != null) {
                dto.setConsumingOrganizationId(String.valueOf(orgId));
            }
            // —— 侧栏「总金额」：明细行 totalPrice 求和（BR-MON-001 计算刻度加总）。——
            BigDecimal totalAmount = BigDecimal.ZERO;
            for (SupplyRequestModel line : lines) {
                if (line == null || line.getTotalPrice() == null) {
                    continue;
                }
                totalAmount = totalAmount.add(MonetaryAmountSupport.scaleCalc(line.getTotalPrice()));
            }
            dto.setTotalAmount(totalAmount);
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
    public PageDeptIssueReturnsResponse toPageDeptIssueReturnsAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<DeptIssueReturnListRowDto> records) {
        List<DeptIssueReturnListRowDto> content = new ArrayList<>();
        if (records != null) {
            content = records;
        }
        return PagedResultBuilder.build(new PageDeptIssueReturnsResponse(), content, mergedTotal, pageNo, pageSize);
    }

    /**
     * 将供应请求行列表映射为科室退领详情响应（含明细与目录提示）。
     *
     * @param lines        供应请求明细行
     * @param productHints 行 id 到目录编辑提示的映射
     * @return 详情响应
     */
    @Override
    public GetDeptIssueReturnDetailResponse toGetDeptIssueReturnDetailResponse(
        List<SupplyRequestModel> lines,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints) {
        GetDeptIssueReturnDetailResponse res = new GetDeptIssueReturnDetailResponse();
        if (lines == null || lines.isEmpty()) {
            return res;
        }
        List<SupplyRequestModel> sortedByApply = new ArrayList<>(lines);
        sortedByApply.sort(this::compareSupplyModelApplyTimeDescNullsLast);
        SupplyRequestModel head = sortedByApply.get(0);
        res.setBusNo(head.getBusNo());
        res.setOriginalBusNo(head.getOriginalBusNo());
        res.setStatusEnum(head.getStatusEnum());
        res.setPurposeLocationId(head.getPurposeLocationId());
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
        // 领用科室：优先落库 consumingOrganizationId，历史单回落药房上级科室
        Long orgId = this.resolveConsumingOrganizationId(head);
        if (orgId != null) {
            res.setConsumingOrganizationId(String.valueOf(orgId));
        }
        List<DeptIssueReturnDetailLineDto> detailLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            DeptIssueReturnDetailLineDto dto = this.toDeptIssueReturnDetailLineDto(line);
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
     * 单条供应请求行映射为科室退领详情明细 DTO。
     *
     * @param line 供应请求明细行
     * @return 详情行 DTO；line 为 null 时返回 null
     */
    @Override
    public DeptIssueReturnDetailLineDto toDeptIssueReturnDetailLineDto(SupplyRequestModel line) {
        if (line == null) {
            return null;
        }
        DeptIssueReturnDetailLineDto dto = new DeptIssueReturnDetailLineDto();
        dto.setItemTable(line.getItemTable());
        dto.setItemId(line.getItemId());
        dto.setLotNumber(line.getLotNumber());
        dto.setTraceNo(line.getTraceNo());
        dto.setItemQuantity(line.getItemQuantity());
        dto.setUnitCode(line.getUnitCode());
        dto.setPrice(line.getPrice());
        dto.setTotalPrice(line.getTotalPrice());
        // 效期/行备注：供前端「有效期」「备注」列回显
        dto.setEndTime(line.getEndTime());
        dto.setRemake(line.getRemake());
        dto.setItemName(null);
        dto.setSpec(null);
        dto.setManufacturer(null);
        dto.setUnitName(null);
        dto.setProductCatalog(null);
        return dto;
    }

    /**
     * 将新建科室退领请求装配为领域 CreateDeptIssueReturnCommand。
     *
     * @param request                   新建请求
     * @param operatorId                操作人
     * @param applicantId               制单人员
     * @param busNo                     预分配业务单号；新建时为 null
     * @param consumingOrganizationId   领用科室（自源领用单继承）
     * @return 领域写命令
     */
    @Override
    public CreateDeptIssueReturnCommand toCreateDeptIssueReturnCommand(
        CreateDeptIssueReturnRequest request,
        Long operatorId,
        Long applicantId,
        String busNo,
        Long consumingOrganizationId) {
        if (request == null) {
            throw new IllegalArgumentException("request 不能为空");
        }
        boolean revisionMode = false;
        if (busNo != null && !busNo.isEmpty()) {
            revisionMode = true;
        }
        return this.buildCreateDeptIssueReturnCommand(
            request.getOriginalBusNo(),
            request.getPurposeLocationId(),
            request.getLines(),
            busNo,
            revisionMode,
            operatorId,
            applicantId,
            consumingOrganizationId);
    }

    /**
     * 将修订科室退领请求装配为领域 CreateDeptIssueReturnCommand（首行 busNo 表示同号全量对齐）。
     *
     * @param request                   修订请求
     * @param operatorId                操作人
     * @param applicantId               制单人员
     * @param originalBusNoSnapshot     源领用单号快照
     * @param consumingOrganizationId   领用科室（自源领用单继承）
     * @return 领域写命令
     */
    @Override
    public CreateDeptIssueReturnCommand toCreateDeptIssueReturnCommand(
        UpdateDeptIssueReturnRequest request,
        Long operatorId,
        Long applicantId,
        String originalBusNoSnapshot,
        Long consumingOrganizationId) {
        if (request == null) {
            throw new IllegalArgumentException("request 不能为空");
        }
        String revisionBusNo = null;
        if (request.getBusNo() != null) {
            revisionBusNo = request.getBusNo().trim();
        }
        if (revisionBusNo != null && revisionBusNo.isEmpty()) {
            revisionBusNo = null;
        }
        return this.buildCreateDeptIssueReturnCommand(
            originalBusNoSnapshot,
            request.getPurposeLocationId(),
            request.getLines(),
            revisionBusNo,
            true,
            operatorId,
            applicantId,
            consumingOrganizationId);
    }

    /**
     * 装配新建科室退领成功响应。
     *
     * @param busNo 新建业务单号
     * @return 新建响应
     */
    @Override
    public CreateDeptIssueReturnResponse toCreateDeptIssueReturnResponse(String busNo) {
        CreateDeptIssueReturnResponse res = new CreateDeptIssueReturnResponse();
        res.setBusNo(busNo);
        return res;
    }

    /**
     * 装配修订科室退领成功响应。
     *
     * @param busNo 业务单号
     * @return 修订响应
     */
    @Override
    public UpdateDeptIssueReturnResponse toUpdateDeptIssueReturnResponse(String busNo) {
        UpdateDeptIssueReturnResponse res = new UpdateDeptIssueReturnResponse();
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
        SubmitDeptIssueReturnApprovalRequest request,
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
     * 装配提交审批成功响应（含提交后展示状态）。
     *
     * @param statusAfter 提交后供应状态
     * @return 提交响应
     */
    @Override
    public SubmitDeptIssueReturnApprovalResponse toSubmitDeptIssueReturnApprovalResponse(SupplyStatus statusAfter) {
        SubmitDeptIssueReturnApprovalResponse res = new SubmitDeptIssueReturnApprovalResponse();
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
    public WithdrawApprovalCommand toWithdrawApprovalCommand(
        WithdrawDeptIssueReturnApprovalRequest request,
        Long operatorId) {
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
    public WithdrawDeptIssueReturnApprovalResponse toWithdrawDeptIssueReturnApprovalResponse(SupplyStatus statusAfter) {
        WithdrawDeptIssueReturnApprovalResponse res = new WithdrawDeptIssueReturnApprovalResponse();
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
    public AgreeRequestCommand toAgreeRequestCommand(AgreeDeptIssueReturnRequest request, Long operatorId) {
        AgreeRequestCommand command = new AgreeRequestCommand();
        command.setOperatorId(operatorId);
        AgreeRequestCommand.AgreeRequestItemCommand item = new AgreeRequestCommand.AgreeRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        command.setItem(item);
        return command;
    }

    /**
     * 将已同意明细行装配为科室退领发放命令。
     *
     * @param lines      已同意的供应请求明细行
     * @param operatorId 操作人
     * @return 发放命令
     */
    @Override
    public CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommand(
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
                BigDecimal returnQty = line.getItemQuantity();
                if (returnQty == null || returnQty.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand item =
                    new CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand();
                item.setRequestId(line.getId());
                item.setTypeEnum(SupplyType.DEPT_ISSUE_RETURN.getCode());
                item.setItemTable(line.getItemTable());
                item.setItemId(line.getItemId());
                item.setQuantity(returnQty);
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
     * 装配科室退领增库存命令。
     *
     * @param inventoryItemId 库存项主键
     * @param increaseQty     增加数量（拆零，正数）
     * @param operatorId      操作人
     * @return 增库存命令
     */
    @Override
    public IncreaseQuantityCommand toIncreaseQuantityCommand(
        Long inventoryItemId,
        BigDecimal increaseQty,
        Long operatorId) {
        IncreaseQuantityCommand command = new IncreaseQuantityCommand();
        command.setOperatorId(operatorId);
        IncreaseQuantityCommand.IncreaseQuantityItemCommand item = new IncreaseQuantityCommand.IncreaseQuantityItemCommand();
        item.setId(inventoryItemId);
        item.setIncreaseQty(increaseQty);
        command.setItem(item);
        return command;
    }

    /**
     * 装配同意科室退领成功响应。
     *
     * @param busNo      业务单号
     * @param statusEnum 同意后状态 code
     * @return 同意响应
     */
    @Override
    public AgreeDeptIssueReturnResponse toAgreeDeptIssueReturnResponse(String busNo, String statusEnum) {
        AgreeDeptIssueReturnResponse res = new AgreeDeptIssueReturnResponse();
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
    public RejectRequestCommand toRejectRequestCommand(RejectDeptIssueReturnRequest request, Long operatorId) {
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
     * 装配驳回科室退领成功响应。
     *
     * @param ok 是否驳回成功
     * @return 驳回响应
     */
    @Override
    public RejectDeptIssueReturnResponse toRejectDeptIssueReturnResponse(boolean ok) {
        RejectDeptIssueReturnResponse res = new RejectDeptIssueReturnResponse();
        res.setOk(Boolean.valueOf(ok));
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
    public RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveDeptIssueReturnRequest request, Long operatorId) {
        RemoveByBusNoCommand command = new RemoveByBusNoCommand();
        command.setOperatorId(operatorId);
        RemoveByBusNoCommand.RemoveByBusNoItemCommand item = new RemoveByBusNoCommand.RemoveByBusNoItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /**
     * 装配删除科室退领成功响应。
     *
     * @param ok 是否删除成功
     * @return 删除响应
     */
    @Override
    public RemoveDeptIssueReturnResponse toRemoveDeptIssueReturnResponse(boolean ok) {
        RemoveDeptIssueReturnResponse res = new RemoveDeptIssueReturnResponse();
        res.setOk(Boolean.valueOf(ok));
        return res;
    }

    /**
     * 将源领用明细与可退行快照装配为拉取预填响应。
     *
     * @param issueLines      已同意源领用明细
     * @param returnableLines 可退行快照
     * @param productByKey    产品读模型（itemTable+itemId 复合键；可空 map）
     * @return 拉取预填响应
     */
    @Override
    public GetLinkableIssueDetailResponse toGetLinkableIssueDetailResponse(
        List<SupplyRequestModel> issueLines,
        List<DeptIssueReturnLineHelper.ReturnableLineSnapshot> returnableLines,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        GetLinkableIssueDetailResponse res = new GetLinkableIssueDetailResponse();
        if (issueLines == null || issueLines.isEmpty()) {
            res.setLines(new ArrayList<>());
            return res;
        }
        List<SupplyRequestModel> sortedByApply = new ArrayList<>(issueLines);
        sortedByApply.sort(this::compareSupplyModelApplyTimeDescNullsLast);
        SupplyRequestModel head = sortedByApply.get(0);
        res.setOriginalBusNo(head.getBusNo());
        Long purposeLocationId = head.getPurposeLocationId();
        if (purposeLocationId != null) {
            res.setPurposeLocationId(String.valueOf(purposeLocationId));
            this.locationQueryService.findOptionalById(purposeLocationId).ifPresent(location -> {
                res.setPurposeLocationName(location.getName());
            });
        }
        // 领用科室：优先落库 consumingOrganizationId，历史单回落药房上级科室
        Long orgId = this.resolveConsumingOrganizationId(head);
        if (orgId != null) {
            res.setConsumingOrganizationId(String.valueOf(orgId));
            Set<Long> orgIds = new HashSet<>();
            orgIds.add(orgId);
            Map<Long, String> orgNameById = this.organizationQueryService.findOrganizationNamesByIds(orgIds);
            res.setConsumingOrganizationName(orgNameById.get(orgId));
        }
        List<LinkableIssueReturnLineDto> lineDtos = new ArrayList<>();
        Map<String, ProductDefinitionDisplayDto> safeProducts = productByKey;
        if (safeProducts == null) {
            safeProducts = new LinkedHashMap<>();
        }
        if (returnableLines != null) {
            for (DeptIssueReturnLineHelper.ReturnableLineSnapshot snapshot : returnableLines) {
                ProductDefinitionDisplayDto product = null;
                if (snapshot != null
                    && StringUtils.isNotEmpty(snapshot.getItemTable())
                    && snapshot.getItemId() != null) {
                    String key = ProductDefinitionKeyUtil.buildCompositeKey(
                        snapshot.getItemTable(), snapshot.getItemId());
                    product = safeProducts.get(key);
                }
                LinkableIssueReturnLineDto dto = this.toLinkableIssueReturnLineDto(snapshot, product);
                if (dto != null) {
                    lineDtos.add(dto);
                }
            }
        }
        res.setLines(lineDtos);
        return res;
    }

    /**
     * 将可退行快照装配为可退领用行 DTO。
     *
     * @param snapshot 可退行快照
     * @param product  产品读模型（可 null；用于名称/规格/单位展示）
     * @return 可退领用行 DTO；snapshot 为 null 时返回 null
     */
    @Override
    public LinkableIssueReturnLineDto toLinkableIssueReturnLineDto(
        DeptIssueReturnLineHelper.ReturnableLineSnapshot snapshot,
        ProductDefinitionDisplayDto product) {
        if (snapshot == null) {
            return null;
        }
        LinkableIssueReturnLineDto dto = new LinkableIssueReturnLineDto();
        dto.setItemTable(snapshot.getItemTable());
        dto.setItemId(snapshot.getItemId());
        dto.setLotNumber(snapshot.getLotNumber());
        dto.setTraceNo(snapshot.getTraceNo());
        dto.setIssueQuantity(snapshot.getIssueQuantity());
        dto.setReturnableQuantity(snapshot.getReturnableQuantity());
        dto.setUnitCode(snapshot.getUnitCode());
        dto.setPrice(snapshot.getPrice());
        // —— 可退余量 × 单价 → 金额初值（BR-MON-001）；详情保存后仍以落库 totalPrice 为准。——
        if (snapshot.getPrice() != null && snapshot.getReturnableQuantity() != null) {
            dto.setTotalPrice(
                MonetaryAmountSupport.lineTotal(snapshot.getPrice(), snapshot.getReturnableQuantity()));
        } else {
            dto.setTotalPrice(null);
        }
        dto.setEndTime(snapshot.getEndTime());
        dto.setRemake(snapshot.getRemake());
        // —— 产品展示富化：行价仍取源领用落库价，不回落当前目录价。——
        if (product != null) {
            dto.setProductName(product.getProductName());
            dto.setSpecification(product.getTotalVolume());
            dto.setManufacturer(product.getManufacturer());
            dto.setUnitCodeDictText(
                com.zxplat.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler
                    .resolveUnitDisplayName(null, snapshot.getUnitCode(), product));
        }
        return dto;
    }

    /**
     * 用可关联领用单聚合行构建分页响应。
     *
     * @param mergedTotal 去重后总单数
     * @param pageNo      页码（1-based）
     * @param pageSize    页大小
     * @param records     列表行
     * @return 分页响应
     */
    @Override
    public PageLinkableIssueDocumentsResponse toPageLinkableIssueDocumentsAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<LinkableIssueListRowDto> records) {
        List<LinkableIssueListRowDto> content = new ArrayList<>();
        if (records != null) {
            content = records;
        }
        return PagedResultBuilder.build(new PageLinkableIssueDocumentsResponse(), content, mergedTotal, pageNo, pageSize);
    }

    /**
     * 将写模型请求明细装配为领域 CreateDeptIssueReturnCommand。
     *
     * @param originalBusNo     源领用单号
     * @param purposeLocationId 科室药房位置主键
     * @param lines             写模型明细
     * @param revisionBusNo     修订业务单号（仅首行写入）；新建时为 null
     * @param revisionMode              是否修订模式（首行写入 revisionBusNo）
     * @param operatorId                操作人
     * @param applicantId               制单人员
     * @param consumingOrganizationId   领用科室（自源领用单继承）
     * @return 领域写命令
     */
    private CreateDeptIssueReturnCommand buildCreateDeptIssueReturnCommand(
        String originalBusNo,
        Long purposeLocationId,
        List<DeptIssueReturnLineDto> lines,
        String revisionBusNo,
        boolean revisionMode,
        Long operatorId,
        Long applicantId,
        Long consumingOrganizationId) {
        CreateDeptIssueReturnCommand command = new CreateDeptIssueReturnCommand();
        command.setOperatorId(operatorId);
        List<CreateDeptIssueReturnCommand.CreateDeptIssueReturnItemCommand> items = new ArrayList<>();
        if (lines == null) {
            command.setItems(items);
            return command;
        }
        String purposeTypeCode =
            this.locationQueryService.requireSupplyRequestTypeForLocationId(purposeLocationId).getCode();
        String categoryCode = SupplyCategory.STOCK_SUPPLY.getCode();
        String typeCode = SupplyType.DEPT_ISSUE_RETURN.getCode();
        String resolvedOriginalBusNo = originalBusNo;
        if (resolvedOriginalBusNo != null) {
            resolvedOriginalBusNo = resolvedOriginalBusNo.trim();
        }
        if (resolvedOriginalBusNo != null && resolvedOriginalBusNo.isEmpty()) {
            resolvedOriginalBusNo = null;
        }
        LocalDateTime applyTime = LocalDateTime.now();
        boolean firstLine = true;
        for (DeptIssueReturnLineDto line : lines) {
            if (line == null) {
                continue;
            }
            CreateDeptIssueReturnCommand.CreateDeptIssueReturnItemCommand item =
                new CreateDeptIssueReturnCommand.CreateDeptIssueReturnItemCommand();
            if (revisionMode && firstLine) {
                item.setBusNo(revisionBusNo);
            } else {
                item.setBusNo(null);
            }
            firstLine = false;
            item.setOriginalBusNo(resolvedOriginalBusNo);
            item.setTypeEnum(typeCode);
            item.setItemTable(line.getItemTable());
            item.setItemId(line.getItemId());
            item.setItemQuantity(line.getItemQuantity());
            item.setUnitCode(line.getUnitCode());
            item.setCategoryEnum(categoryCode);
            item.setPurposeTypeEnum(purposeTypeCode);
            item.setPurposeLocationId(purposeLocationId);
            // 领用科室：自源领用单继承，禁止客户端覆盖
            item.setConsumingOrganizationId(consumingOrganizationId);
            item.setLotNumber(line.getLotNumber());
            item.setTraceNo(line.getTraceNo());
            item.setPrice(line.getPrice());
            item.setTotalPrice(this.resolveLineTotalPrice(line));
            item.setEndTime(line.getEndTime());
            item.setRemake(line.getRemake());
            item.setApplicantId(applicantId);
            item.setApplyTime(applyTime);
            item.setOccurrenceTime(null);
            items.add(item);
        }
        command.setItems(items);
        return command;
    }

    /**
     * 按行单价与数量计算行总价（BR-MON-001 计算刻度）；缺单价或数量时返回 null。
     *
     * @param line 写模型明细行
     * @return 行总价；无法计算时 null
     */
    private BigDecimal resolveLineTotalPrice(DeptIssueReturnLineDto line) {
        if (line == null || line.getPrice() == null || line.getItemQuantity() == null) {
            return null;
        }
        return MonetaryAmountSupport.lineTotal(line.getPrice(), line.getItemQuantity());
    }

    /**
     * 解析领用科室 organization 主键：优先落库 {@code consumingOrganizationId}，历史单回落科室药房上级科室。
     *
     * @param head 供应请求表头行
     * @return 领用科室主键；无法解析时 null
     */
    private Long resolveConsumingOrganizationId(SupplyRequestModel head) {
        if (head == null) {
            return null;
        }
        // 新单：直接用落库领用科室
        if (head.getConsumingOrganizationId() != null) {
            return head.getConsumingOrganizationId();
        }
        // 历史单：回落科室药房 Location.organization_id
        if (head.getPurposeLocationId() != null) {
            return this.resolveOrganizationIdForLocation(head.getPurposeLocationId());
        }
        return null;
    }

    /**
     * 由科室药房库位主键解析上级科室 organization_id（历史单回落）。
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