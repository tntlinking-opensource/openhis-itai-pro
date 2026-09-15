/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.purchaserequest.mapper;

import com.openhis.health.application.inventory.department.purchaserequest.dto.DeptPurchaseRequestDetailLineDto;
import com.openhis.health.application.inventory.department.purchaserequest.dto.DeptPurchaseRequestLineDto;
import com.openhis.health.application.inventory.department.purchaserequest.dto.DeptPurchaseRequestListRowDto;
import com.openhis.health.application.inventory.department.purchaserequest.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.department.purchaserequest.request.AgreeDeptPurchaseRequestRequest;
import com.openhis.health.application.inventory.department.purchaserequest.request.CreateDeptPurchaseRequestRequest;
import com.openhis.health.application.inventory.department.purchaserequest.request.PageProductsForDeptPurchaseRequestRequest;
import com.openhis.health.application.inventory.department.purchaserequest.request.RejectDeptPurchaseRequestRequest;
import com.openhis.health.application.inventory.department.purchaserequest.request.RemoveDeptPurchaseRequestRequest;
import com.openhis.health.application.inventory.department.purchaserequest.request.SubmitDeptPurchaseRequestApprovalRequest;
import com.openhis.health.application.inventory.department.purchaserequest.request.UpdateDeptPurchaseRequestRequest;
import com.openhis.health.application.inventory.department.purchaserequest.request.WithdrawDeptPurchaseRequestApprovalRequest;
import com.openhis.health.application.inventory.department.purchaserequest.response.AgreeDeptPurchaseRequestResponse;
import com.openhis.health.application.inventory.department.purchaserequest.response.CreateDeptPurchaseRequestResponse;
import com.openhis.health.application.inventory.department.purchaserequest.response.GetDeptPurchaseRequestDetailResponse;
import com.openhis.health.application.inventory.department.purchaserequest.response.LoadDeptPurchaseRequestFormInitResponse;
import com.openhis.health.application.inventory.department.purchaserequest.response.PageProductsForDeptPurchaseRequestResponse;
import com.openhis.health.application.inventory.department.purchaserequest.response.PageDeptPurchaseRequestsResponse;
import com.openhis.health.application.inventory.department.purchaserequest.response.RejectDeptPurchaseRequestResponse;
import com.openhis.health.application.inventory.department.purchaserequest.response.RemoveDeptPurchaseRequestResponse;
import com.openhis.health.application.inventory.department.purchaserequest.response.SubmitDeptPurchaseRequestApprovalResponse;
import com.openhis.health.application.inventory.department.purchaserequest.response.UpdateDeptPurchaseRequestResponse;
import com.openhis.health.application.inventory.department.purchaserequest.response.WithdrawDeptPurchaseRequestApprovalResponse;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.SupplyCategory;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateDeptPurchaseRequestCommand;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 科室采购申请单装配实现：{@link InventoryDepartmentPurchaseRequestMapper}。
 *
 * <p>列表/详情/命令/响应装配与选品分页映射；定价写路径由领域 {@code createDeptPurchaseRequest} 处理，
 * 选品进价 enrich 在 AppService 完成。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryDepartmentPurchaseRequestMapperImpl implements InventoryDepartmentPurchaseRequestMapper {

    /** 库位读服务：推导目的位置供应类型。 */
    private final LocationQueryService locationQueryService;

    /**
     * 将启用位置列表映射为 {@link OptionDto} 下拉（B.10.5）。
     *
     * @param locations 启用库位列表
     * @return 下拉选项；{@code null} 输入返回空列表
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
     * 将启用医务人员列表映射为 {@link OptionDto} 下拉（B.10.5）。
     *
     * @param practitioners 医务人员列表
     * @return 人员下拉选项；{@code null} 输入返回空列表
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
     * 装配科室采购申请页初始化响应。
     *
     * @param departmentPharmacyLocationOptions 科室药房选项（含「全部」由 AppService 前置）
     * @param statusOptions                     单据状态选项
     * @param itemTableOpts                     物品来源表选项
     * @param pharmacistOptions                 药师选项
     * @param warehouseKeeperOptions            库管选项
     * @return 页初始化响应
     */
    @Override
    public LoadDeptPurchaseRequestFormInitResponse toLoadDeptPurchaseRequestFormInitResponse(
        List<OptionDto> departmentPharmacyLocationOptions,
        List<OptionDto> statusOptions,
        List<OptionDto> itemTableOpts,
        List<OptionDto> pharmacistOptions,
        List<OptionDto> warehouseKeeperOptions) {
        LoadDeptPurchaseRequestFormInitResponse res = new LoadDeptPurchaseRequestFormInitResponse();
        res.setDepartmentPharmacyLocationOptions(departmentPharmacyLocationOptions);
        res.setStatusOptions(statusOptions);
        res.setPurchaseItemTableTypeOptions(itemTableOpts);
        res.setPharmacistOptions(pharmacistOptions);
        res.setWarehouseKeeperOptions(warehouseKeeperOptions);
        return res;
    }

    /**
     * 装配供应请求状态枚举选项（持久化 code）。
     *
     * @return 按 {@link SupplyStatus#getSortOrder()} 排序的状态下拉
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
     * @return 物品表下拉（药品、耗材固定两项）
     */
    @Override
    public List<OptionDto> toOptionDtoListPurchaseItemTableTypes() {
        List<OptionDto> list = new ArrayList<>();
        // 科室采购申请默认优先耗材（首项）
        list.add(new OptionDto(1L, TableNameConstants.ADM_DEVICE_DEFINITION, "耗材"));
        list.add(new OptionDto(2L, TableNameConstants.MED_MEDICATION_DEFINITION, "药品"));
        return list;
    }

    /**
     * 将选品分页请求转为产品定义读模型查询。
     *
     * @param request 选品分页请求
     * @return 读模型查询对象
     */
    @Override
    public ProductDefinitionPageQuery toProductDefinitionPageQuery(PageProductsForDeptPurchaseRequestRequest request) {
        ProductDefinitionPageQuery query = new ProductDefinitionPageQuery();
        query.setProductTableName(request.getProductTableName());
        query.setCategoryCode(request.getCategoryCode());
        query.setActiveFlag(request.getActiveFlag());
        query.setManufacturer(request.getManufacturer());
        query.setIncludeInventory(request.getIncludeInventory());
        PageQueryBinder.bind(request, query);
        query.setSortField(request.getSortField());
        query.setSortOrder(request.getSortOrder());
        return query;
    }

    /**
     * 按 busNo 顺序聚合供应请求明细为列表行。
     *
     * @param busNoPageOrder 分页单号顺序
     * @param detailRows     同页明细行
     * @return 聚合列表行
     * @throws BusinessException {@link ErrorCodeConstants#DEP_CPR_102} 同号头字段不一致
     */
    @Override
    public List<DeptPurchaseRequestListRowDto> toDeptPurchaseRequestListAggregated(
        List<String> busNoPageOrder,
        List<SupplyRequestModel> detailRows) {
        List<DeptPurchaseRequestListRowDto> out = new ArrayList<>();
        if (busNoPageOrder == null || busNoPageOrder.isEmpty()) {
            return out;
        }
        if (detailRows == null || detailRows.isEmpty()) {
            return out;
        }
        // —— 先按单号分组，便于按分页给出的单号顺序输出且每单只出一行聚合 DTO
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
            // —— 同号多行应共享单据头：状态/目的科室药房不一致时直接失败，避免列表展示「假一致」
            for (SupplyRequestModel line : lines) {
                if (line == null) {
                    continue;
                }
                if (!Objects.equals(head.getStatusEnum(), line.getStatusEnum())) {
                    throw new BusinessException(
                        ErrorCodeConstants.DEP_CPR_102, "同号科室采购申请单状态头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getPurposeLocationId(), line.getPurposeLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.DEP_CPR_102, "同号科室采购申请单目的科室药房头字段不一致: " + busNo);
                }
            }
            BigDecimal totalAmount = BigDecimal.ZERO;
            LocalDateTime maxApply = null;
            LocalDateTime maxOccurrence = null;
            // —— 汇总同号各行金额与最新申请/制单时刻
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
            List<SupplyRequestModel> sortedById = new ArrayList<>(lines);
            sortedById.sort(this::compareSupplyModelIdAscNullsLast);
            // —— 审批时刻取主键升序下第一条非空：稳定且不依赖数据库返回行序
            for (SupplyRequestModel line : sortedById) {
                if (line != null && line.getApprovalTime() != null) {
                    approvalTime = line.getApprovalTime();
                    break;
                }
            }
            DeptPurchaseRequestListRowDto dto = new DeptPurchaseRequestListRowDto();
            dto.setBusNo(busNo);
            dto.setPurposeLocationId(head.getPurposeLocationId());
            dto.setStatusEnum(head.getStatusEnum());
            // —— REJECT 态列表行回填驳回原因（取自头行 reason）
            if (SupplyStatus.REJECT.getCode().equals(head.getStatusEnum())) {
                dto.setRejectReason(head.getReason());
            }
            dto.setApproverId(head.getApproverId());
            dto.setTotalAmount(totalAmount);
            dto.setApprovalTime(approvalTime);
            dto.setApplyTime(maxApply);
            dto.setOccurrenceTime(maxOccurrence);
            out.add(dto);
        }
        return out;
    }

    /**
     * 用聚合列表行构建分页响应（B.10.11）。
     *
     * @param mergedTotal 不重复 busNo 总数
     * @param pageNo      1-based 页码
     * @param pageSize    页大小
     * @param records     当前页聚合行
     * @return 分页响应
     */
    @Override
    public PageDeptPurchaseRequestsResponse toPageDeptPurchaseRequestsAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<DeptPurchaseRequestListRowDto> records) {
        List<DeptPurchaseRequestListRowDto> content;
        if (records == null) {
            content = new ArrayList<>();
        } else {
            content = records;
        }
        return PagedResultBuilder.build(new PageDeptPurchaseRequestsResponse(), content, mergedTotal, pageNo, pageSize);
    }

    /**
     * 将同号供应请求明细与产品读模型装配为详情响应。
     *
     * @param lines        供应请求明细行
     * @param productByKey 产品定义读模型映射
     * @return 详情响应（含 {@code rowVersion} 供修订乐观锁）
     */
    @Override
    public GetDeptPurchaseRequestDetailResponse toGetDeptPurchaseRequestDetailResponse(
        List<SupplyRequestModel> lines,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        GetDeptPurchaseRequestDetailResponse res = new GetDeptPurchaseRequestDetailResponse();
        if (lines == null || lines.isEmpty()) {
            return res;
        }
        SupplyRequestModel head = lines.get(0);
        res.setBusNo(head.getBusNo());
        res.setTypeEnum(head.getTypeEnum());
        res.setStatusEnum(head.getStatusEnum());
        res.setPurposeLocationId(head.getPurposeLocationId());
        res.setPurposeTypeEnum(head.getPurposeTypeEnum());
        res.setCategoryEnum(head.getCategoryEnum());
        res.setPractitionerId(head.getPractitionerId());
        res.setOccurrenceTime(head.getOccurrenceTime());
        res.setRemake(head.getRemake());
        res.setRowVersion(this.resolveDeptPurchaseRequestDocumentRowVersion(lines, head));
        List<DeptPurchaseRequestDetailLineDto> detailLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            detailLines.add(this.toDetailLine(line, productByKey));
        }
        res.setLines(detailLines);
        return res;
    }

    /**
     * 将供应请求明细行与产品读模型装配为详情行 DTO。
     *
     * @param line         供应请求写模型行
     * @param productByKey 产品定义读模型 compositeKey 映射
     * @return 详情行 DTO
     */
    private DeptPurchaseRequestDetailLineDto toDetailLine(
        SupplyRequestModel line,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        DeptPurchaseRequestDetailLineDto dto = new DeptPurchaseRequestDetailLineDto();
        dto.setId(line.getId());
        dto.setItemTable(line.getItemTable());
        dto.setItemId(line.getItemId());
        dto.setItemQuantity(line.getItemQuantity());
        dto.setUnitCode(line.getUnitCode());
        dto.setPrice(line.getPrice());
        dto.setTotalPrice(line.getTotalPrice());
        dto.setRemake(line.getRemake());
        // —— 有读模型命中时补齐产品名称/规格/厂家；无映射则保持 null，由 AppService 二次 enrich
        if (productByKey != null && line.getItemTable() != null && line.getItemId() != null) {
            String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
            ProductDefinitionDisplayDto pr = productByKey.get(key);
            if (pr != null) {
                dto.setProductName(pr.getProductName());
                dto.setSpecification(pr.getTotalVolume());
                dto.setManufacturer(pr.getManufacturer());
            }
        }
        return dto;
    }

    /**
     * 将新建请求装配为领域 {@link CreateDeptPurchaseRequestCommand}。
     *
     * @param request     新建请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    @Override
    public CreateDeptPurchaseRequestCommand toCreateDeptPurchaseRequestCommand(
        CreateDeptPurchaseRequestRequest request,
        Long operatorId,
        Long applicantId) {
        CreateDeptPurchaseRequestCommand command = new CreateDeptPurchaseRequestCommand();
        command.setOperatorId(operatorId);
        List<CreateDeptPurchaseRequestCommand.CreateDeptPurchaseRequestItemCommand> items = new ArrayList<>();
        if (request.getLines() == null) {
            command.setItems(items);
            return command;
        }
        // —— 头级枚举：source_* 与 supplier_id 恒空（DEP-CPR-SDD-R-002）；目的类型由库位推导
        String purposeTypeCode = this.requirePurposeSupplyRequestTypeCodeFromLocation(request.getPurposeLocationId());
        String categoryCode = this.resolveSupplyCategoryCode(request.getCategoryEnum());
        String typeCode = SupplyType.DEPT_PURCHASE_REQUEST.getCode();
        // —— 头级制单时刻 occurrenceTime 写入各行（科室采购为单据级时刻）
        LocalDateTime occurrenceTime = request.getOccurrenceTime();
        for (DeptPurchaseRequestLineDto line : request.getLines()) {
            if (line == null) {
                continue;
            }
            CreateDeptPurchaseRequestCommand.CreateDeptPurchaseRequestItemCommand item =
                new CreateDeptPurchaseRequestCommand.CreateDeptPurchaseRequestItemCommand();
            item.setBusNo(null);
            item.setTypeEnum(typeCode);
            item.setItemTable(line.getItemTable());
            item.setItemId(line.getItemId());
            item.setItemQuantity(line.getItemQuantity());
            item.setUnitCode(line.getUnitCode());
            item.setCategoryEnum(categoryCode);
            item.setPurposeTypeEnum(purposeTypeCode);
            item.setPurposeLocationId(request.getPurposeLocationId());
            item.setOccurrenceTime(occurrenceTime);
            item.setPractitionerId(request.getPractitionerId());
            item.setReason(null);
            item.setRemake(this.resolveLineRemake(line.getRemake(), request.getRemake()));
            item.setPrice(line.getPrice());
            item.setTotalPrice(line.getTotalPrice());
            item.setApplicantId(applicantId);
            item.setApplyTime(null);
            items.add(item);
        }
        command.setItems(items);
        return command;
    }

    /**
     * 将修订请求装配为领域 {@link CreateDeptPurchaseRequestCommand}（同号全量对齐）。
     *
     * @param request     修订请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     * @throws IllegalArgumentException {@code rowVersion} 超出整型范围
     */
    @Override
    public CreateDeptPurchaseRequestCommand toCreateDeptPurchaseRequestCommandForUpdate(
        UpdateDeptPurchaseRequestRequest request,
        Long operatorId,
        Long applicantId) {
        CreateDeptPurchaseRequestCommand command = new CreateDeptPurchaseRequestCommand();
        command.setOperatorId(operatorId);
        Long rowVer = request.getRowVersion();
        if (rowVer != null) {
            // —— rowVersion 须在整型范围内，供领域乐观锁比对
            if (rowVer > Integer.MAX_VALUE || rowVer < Integer.MIN_VALUE) {
                throw new IllegalArgumentException("rowVersion 超出整型范围");
            }
            command.setExpectedRowVersionForRevision(rowVer.intValue());
        }
        List<CreateDeptPurchaseRequestCommand.CreateDeptPurchaseRequestItemCommand> items = new ArrayList<>();
        if (request.getLines() == null) {
            command.setItems(items);
            return command;
        }
        // —— 头级枚举：source_* 与 supplier_id 恒空（DEP-CPR-SDD-R-002）；目的类型由库位推导
        String purposeTypeCode = this.requirePurposeSupplyRequestTypeCodeFromLocation(request.getPurposeLocationId());
        String categoryCode = this.resolveSupplyCategoryCode(request.getCategoryEnum());
        String typeCode = SupplyType.DEPT_PURCHASE_REQUEST.getCode();
        // —— 头级制单时刻 occurrenceTime 写入各行（科室采购为单据级时刻）
        LocalDateTime occurrenceTime = request.getOccurrenceTime();
        for (DeptPurchaseRequestLineDto line : request.getLines()) {
            if (line == null) {
                continue;
            }
            CreateDeptPurchaseRequestCommand.CreateDeptPurchaseRequestItemCommand item =
                new CreateDeptPurchaseRequestCommand.CreateDeptPurchaseRequestItemCommand();
            item.setBusNo(request.getBusNo());
            item.setTypeEnum(typeCode);
            item.setItemTable(line.getItemTable());
            item.setItemId(line.getItemId());
            item.setItemQuantity(line.getItemQuantity());
            item.setUnitCode(line.getUnitCode());
            item.setCategoryEnum(categoryCode);
            item.setPurposeTypeEnum(purposeTypeCode);
            item.setPurposeLocationId(request.getPurposeLocationId());
            item.setOccurrenceTime(occurrenceTime);
            item.setPractitionerId(request.getPractitionerId());
            item.setReason(null);
            item.setRemake(this.resolveLineRemake(line.getRemake(), request.getRemake()));
            item.setPrice(line.getPrice());
            item.setTotalPrice(line.getTotalPrice());
            item.setApplicantId(applicantId);
            item.setApplyTime(null);
            items.add(item);
        }
        command.setItems(items);
        return command;
    }

    /**
     * 装配新建成功响应。
     *
     * @param busNo 领域返回的业务单号
     * @return 含 {@code busNo} 与待审批 {@code statusEnum} code
     */
    @Override
    public CreateDeptPurchaseRequestResponse toCreateDeptPurchaseRequestResponse(String busNo) {
        CreateDeptPurchaseRequestResponse res = new CreateDeptPurchaseRequestResponse();
        res.setBusNo(busNo);
        res.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());
        return res;
    }

    /**
     * 装配修订成功响应。
     *
     * @param busNo 同号修订后的业务单号
     * @return 含 {@code busNo} 与待审批 {@code statusEnum} code
     */
    @Override
    public UpdateDeptPurchaseRequestResponse toUpdateDeptPurchaseRequestResponse(String busNo) {
        UpdateDeptPurchaseRequestResponse res = new UpdateDeptPurchaseRequestResponse();
        res.setBusNo(busNo);
        res.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());
        return res;
    }

    /**
     * 将提交审批请求装配为领域 {@link SubmitApprovalCommand}。
     *
     * @param request     提交请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    @Override
    public SubmitApprovalCommand toSubmitApprovalCommand(
        SubmitDeptPurchaseRequestApprovalRequest request,
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
     * @param status 提交后头行状态，可为 null
     * @return 提交响应
     */
    @Override
    public SubmitDeptPurchaseRequestApprovalResponse toSubmitDeptPurchaseRequestApprovalResponse(SupplyStatus status) {
        SubmitDeptPurchaseRequestApprovalResponse res = new SubmitDeptPurchaseRequestApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        if (status != null) {
            res.setStatusEnum(status.getCode());
        }
        return res;
    }

    /**
     * 将撤回审批请求装配为领域 {@link WithdrawApprovalCommand}。
     *
     * @param request    撤回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    @Override
    public WithdrawApprovalCommand toWithdrawApprovalCommand(
        WithdrawDeptPurchaseRequestApprovalRequest request,
        Long operatorId) {
        WithdrawApprovalCommand command = new WithdrawApprovalCommand();
        command.setOperatorId(operatorId);
        WithdrawApprovalCommand.WithdrawApprovalItemCommand item =
            new WithdrawApprovalCommand.WithdrawApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /**
     * 装配撤回审批成功响应。
     *
     * @param status 撤回后头行状态，可为 null
     * @return 撤回响应
     */
    @Override
    public WithdrawDeptPurchaseRequestApprovalResponse toWithdrawDeptPurchaseRequestApprovalResponse(
        SupplyStatus status) {
        WithdrawDeptPurchaseRequestApprovalResponse res = new WithdrawDeptPurchaseRequestApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        if (status != null) {
            res.setStatusEnum(status.getCode());
        }
        return res;
    }

    /**
     * 将同意请求装配为领域 {@link AgreeRequestCommand}。
     *
     * @param request    同意请求
     * @param operatorId 审批人/操作人
     * @return 领域写命令
     */
    @Override
    public AgreeRequestCommand toAgreeRequestCommand(AgreeDeptPurchaseRequestRequest request, Long operatorId) {
        AgreeRequestCommand command = new AgreeRequestCommand();
        command.setOperatorId(operatorId);
        AgreeRequestCommand.AgreeRequestItemCommand item = new AgreeRequestCommand.AgreeRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        command.setItem(item);
        return command;
    }

    /**
     * 装配同意成功响应。
     *
     * @return 同意响应（{@code success=true}，与订货 agree 同构）
     */
    @Override
    public AgreeDeptPurchaseRequestResponse toAgreeDeptPurchaseRequestResponse() {
        AgreeDeptPurchaseRequestResponse res = new AgreeDeptPurchaseRequestResponse();
        res.setSuccess(Boolean.TRUE);
        return res;
    }

    /**
     * 将驳回请求装配为领域 {@link RejectRequestCommand}。
     *
     * @param request    驳回请求
     * @param operatorId 审批人/操作人
     * @return 领域写命令
     */
    @Override
    public RejectRequestCommand toRejectRequestCommand(RejectDeptPurchaseRequestRequest request, Long operatorId) {
        RejectRequestCommand command = new RejectRequestCommand();
        command.setOperatorId(operatorId);
        RejectRequestCommand.RejectRequestItemCommand item = new RejectRequestCommand.RejectRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        item.setChangeReasonCode(request.getRejectReason());
        command.setItem(item);
        return command;
    }

    /**
     * 装配驳回成功响应。
     *
     * @return 驳回响应
     */
    @Override
    public RejectDeptPurchaseRequestResponse toRejectDeptPurchaseRequestResponse() {
        RejectDeptPurchaseRequestResponse res = new RejectDeptPurchaseRequestResponse();
        res.setSuccess(Boolean.TRUE);
        return res;
    }

    /**
     * 将删除请求装配为领域 {@link RemoveByBusNoCommand}。
     *
     * @param request    删除请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    @Override
    public RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveDeptPurchaseRequestRequest request, Long operatorId) {
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
     * @return 删除响应
     */
    @Override
    public RemoveDeptPurchaseRequestResponse toRemoveDeptPurchaseRequestResponse() {
        RemoveDeptPurchaseRequestResponse res = new RemoveDeptPurchaseRequestResponse();
        res.setSuccess(Boolean.TRUE);
        return res;
    }

    /**
     * 将产品定义读模型分页装配为选品分页响应（B.10.11）。
     *
     * @param page 产品定义读模型分页
     * @return 选品分页响应；进价/unitBands 由 AppService enrich
     */
    @Override
    public PageProductsForDeptPurchaseRequestResponse toPageProductsForDeptPurchaseRequestResponse(
        Page<ProductDefinitionDisplayDto> page) {
        List<ProductDefinitionItemDto> content = new ArrayList<>();
        for (ProductDefinitionDisplayDto pr : page.getContent()) {
            if (pr == null) {
                continue;
            }
            content.add(this.toProductDefinitionItemDto(pr));
        }
        return PagedResultBuilder.build(new PageProductsForDeptPurchaseRequestResponse(), content, page);
    }

    /**
     * 将产品定义读模型装配为选品行 DTO（进价 enrich 在 AppService）。
     *
     * @param pr 产品定义读模型
     * @return 选品行
     */
    private ProductDefinitionItemDto toProductDefinitionItemDto(ProductDefinitionDisplayDto pr) {
        ProductDefinitionItemDto dto = new ProductDefinitionItemDto();
        dto.setProductDefinitionId(pr.getProductDefinitionId());
        dto.setProductTableName(pr.getProductTableName());
        dto.setProductBusNo(pr.getProductBusNo());
        dto.setProductName(pr.getProductName());
        dto.setMerchandiseName(pr.getMerchandiseName());
        dto.setUnitCode(pr.getPurchaseUnitCode());
        dto.setMinUnitCode(pr.getSplitUnitCode());
        dto.setCategoryCode(pr.getCategoryCode());
        dto.setCategoryCodeDictText(pr.getCategoryCodeDictText());
        dto.setPrice(pr.getPrice());
        dto.setManufacturer(pr.getManufacturer());
        dto.setTotalVolume(pr.getTotalVolume());
        dto.setPyStr(pr.getPyStr());
        dto.setWbStr(pr.getWbStr());
        dto.setAvailableQuantity(null);
        return dto;
    }

    /**
     * 行备注：行级为空时回退请求头备注。
     *
     * @param lineRemake   行 remake
     * @param headerRemake 头 remake
     * @return 落库 remake
     */
    private String resolveLineRemake(String lineRemake, String headerRemake) {
        if (StringUtils.isNotEmpty(lineRemake)) {
            return lineRemake;
        }
        return headerRemake;
    }

    /**
     * 科室采购申请头「目的位置类型」：由 {@code purposeLocationId} 推导。
     *
     * @param purposeLocationId 目的科室药房主键
     * @return {@link com.openhis.health.domain.share.enums.SupplyRequestType#getCode()}
     * @throws IllegalArgumentException 当 purposeLocationId 为 null 时
     */
    private String requirePurposeSupplyRequestTypeCodeFromLocation(Long purposeLocationId) {
        if (purposeLocationId == null) {
            throw new IllegalArgumentException("purposeLocationId 不能为空");
        }
        return this.locationQueryService.requireSupplyRequestTypeForLocationId(purposeLocationId).getCode();
    }

    /**
     * 供应类别：空或空白时默认库存供应。
     *
     * @param categoryCodeRaw 请求 categoryEnum
     * @return 持久化 code
     * @throws IllegalArgumentException 非空白但码不在枚举白名单
     */
    private String resolveSupplyCategoryCode(String categoryCodeRaw) {
        if (StringUtils.isEmpty(categoryCodeRaw)) {
            return SupplyCategory.STOCK_SUPPLY.getCode();
        }
        SupplyCategory category = EnumUtil.fromCode(SupplyCategory.class, categoryCodeRaw.trim());
        if (category == null) {
            throw new IllegalArgumentException("categoryEnum 非法");
        }
        return category.getCode();
    }

    /**
     * 供应请求头行排序：按 apply_time 降序，null 排最后。
     *
     * @param left  左操作数
     * @param right 右操作数
     * @return Comparator 约定比较结果
     */
    private int compareSupplyModelApplyTimeDescNullsLast(SupplyRequestModel left, SupplyRequestModel right) {
        LocalDateTime ta = left.getApplyTime();
        LocalDateTime tb = right.getApplyTime();
        if (ta == null && tb == null) {
            return 0;
        }
        if (ta == null) {
            return 1;
        }
        if (tb == null) {
            return -1;
        }
        return tb.compareTo(ta);
    }

    /**
     * 供应请求行 tie-break：按行主键升序，null 排最后。
     *
     * @param left  左操作数
     * @param right 右操作数
     * @return Comparator 约定比较结果
     */
    private int compareSupplyModelIdAscNullsLast(SupplyRequestModel left, SupplyRequestModel right) {
        Long ida = left.getId();
        Long idb = right.getId();
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

    /**
     * 解析文档级 rowVersion：优先头行 version，否则取任一行非空版本，仍空则 0。
     *
     * @param lines 同号全部明细
     * @param head  头行
     * @return 供详情/修订使用的乐观锁版本
     */
    private Integer resolveDeptPurchaseRequestDocumentRowVersion(List<SupplyRequestModel> lines, SupplyRequestModel head) {
        if (head != null && head.getVersion() != null) {
            return head.getVersion();
        }
        if (lines != null) {
            for (SupplyRequestModel line : lines) {
                if (line != null && line.getVersion() != null) {
                    return line.getVersion();
                }
            }
        }
        return Integer.valueOf(0);
    }
}
