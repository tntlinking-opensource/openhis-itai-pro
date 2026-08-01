/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.requisition.mapper;

import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.inventory.pharmacy.requisition.dto.PharmacyRequisitionDetailLineDto;
import com.openhis.health.application.inventory.pharmacy.requisition.dto.PharmacyRequisitionLineDto;
import com.openhis.health.application.inventory.pharmacy.requisition.dto.PharmacyRequisitionListRowDto;
import com.openhis.health.application.inventory.pharmacy.requisition.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.pharmacy.requisition.request.AgreePharmacyRequisitionRequest;
import com.openhis.health.application.inventory.pharmacy.requisition.request.CreatePharmacyRequisitionRequest;
import com.openhis.health.application.inventory.pharmacy.requisition.request.RejectPharmacyRequisitionRequest;
import com.openhis.health.application.inventory.pharmacy.requisition.request.RemovePharmacyRequisitionRequest;
import com.openhis.health.application.inventory.pharmacy.requisition.request.SubmitPharmacyRequisitionApprovalRequest;
import com.openhis.health.application.inventory.pharmacy.requisition.request.UpdatePharmacyRequisitionRequest;
import com.openhis.health.application.inventory.pharmacy.requisition.request.WithdrawPharmacyRequisitionApprovalRequest;
import com.openhis.health.application.inventory.pharmacy.requisition.response.AgreePharmacyRequisitionResponse;
import com.openhis.health.application.inventory.pharmacy.requisition.response.CreatePharmacyRequisitionResponse;
import com.openhis.health.application.inventory.pharmacy.requisition.response.GetPharmacyRequisitionDetailResponse;
import com.openhis.health.application.inventory.pharmacy.requisition.response.LoadPharmacyRequisitionFormInitResponse;
import com.openhis.health.application.inventory.pharmacy.requisition.response.PagePharmacyRequisitionsResponse;
import com.openhis.health.application.inventory.pharmacy.requisition.response.PageProductsForPharmacyRequisitionResponse;
import com.openhis.health.application.inventory.pharmacy.requisition.response.RejectPharmacyRequisitionResponse;
import com.openhis.health.application.inventory.pharmacy.requisition.response.RemovePharmacyRequisitionResponse;
import com.openhis.health.application.inventory.pharmacy.requisition.response.SubmitPharmacyRequisitionApprovalResponse;
import com.openhis.health.application.inventory.pharmacy.requisition.response.UpdatePharmacyRequisitionResponse;
import com.openhis.health.application.inventory.pharmacy.requisition.response.WithdrawPharmacyRequisitionApprovalResponse;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.SupplyCategory;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.health.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateRequisitionCommand;
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
 * 药房请领单装配实现：{@link InventoryPharmacyRequisitionMapper}。
 *
 * <p>各 {@code @Override} 方法 JavaDoc 与接口保持同义完整说明（B.1），禁止仅以单独一行 {@code @inheritDoc} 占位。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryPharmacyRequisitionMapperImpl implements InventoryPharmacyRequisitionMapper {

    private final LocationQueryService locationQueryService;

    /**
     * 将位置实体列表装配为下拉（IF-PHA-QL-01）。
     *
     * @param locations 位置实体列表，可为 null
     * @return 下拉项列表，永不为 null
     */
    @Override
    public List<OptionDto> toOptionDtoListFromLocations(List<Location> locations) {
        List<OptionDto> list = new ArrayList<>();
        // 无位置数据时返回空下拉，避免前端判 null。
        if (locations == null) {
            return list;
        }
        for (Location location : locations) {
            // 跳过无主键行，避免 OptionDto 无法稳定绑定。
            if (location == null || location.getId() == null) {
                continue;
            }
            Long id = location.getId();
            String label = location.getName();
            // 名称为空时用主键兜底展示，避免空白项误导操作者。
            if (StringUtils.isEmpty(label)) {
                label = String.valueOf(id);
            }
            list.add(new OptionDto(id, String.valueOf(id), label));
        }
        return list;
    }

    /**
     * 装配供应状态筛选项（IF-PHA-QL-01）。
     *
     * @return 按 {@link SupplyStatus#getSortOrder()} 升序的选项列表
     */
    @Override
    public List<OptionDto> toOptionDtoListSupplyStatuses() {
        List<SupplyStatus> ordered = new ArrayList<>();
        for (SupplyStatus st : SupplyStatus.values()) {
            ordered.add(st);
        }
        // 按产品约定排序权重展示，便于操作者按流程顺序理解状态。
        ordered.sort(Comparator.comparing(SupplyStatus::getSortOrder));
        List<OptionDto> list = new ArrayList<>();
        for (SupplyStatus st : ordered) {
            list.add(new OptionDto(st.getSortOrder(), st.getCode(), st.getDisplay()));
        }
        return list;
    }

    /**
     * 组装药房请领页初始化响应（IF-PHA-QL-01）。
     *
     * @param pharmacyOpts  目的药房
     * @param warehouseOpts 来源药库
     * @param statusOpts    单据状态筛选项
     * @return 初始化响应体
     */
    @Override
    public LoadPharmacyRequisitionFormInitResponse toLoadPharmacyRequisitionFormInitResponse(
        List<OptionDto> pharmacyOpts,
        List<OptionDto> warehouseOpts,
        List<OptionDto> statusOpts) {
        LoadPharmacyRequisitionFormInitResponse res = new LoadPharmacyRequisitionFormInitResponse();
        res.setPharmacyLocationOptions(pharmacyOpts);
        res.setWarehouseLocationOptions(warehouseOpts);
        res.setStatusOptions(statusOpts);
        return res;
    }

    /**
     * 将当前页业务单号顺序与同批明细行装配为药房请领列表聚合行（IF-PHA-QL-02）。
     *
     * @param busNoPageOrder 分页查询得到的单号顺序
     * @param detailRows     与当前页单号集合对应的全部明细行
     * @return 聚合行列表；来源位置展示名由应用层富化
     * @throws BusinessException 同号多行头字段不一致（{@link ErrorCodeConstants#PHA_QL_102}）
     */
    @Override
    public List<PharmacyRequisitionListRowDto> toPharmacyRequisitionListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows) {
        List<PharmacyRequisitionListRowDto> out = new ArrayList<>();
        // 分页键为空：无聚合输出（与领域去重分页一致）。
        if (busNoPageOrder == null || busNoPageOrder.isEmpty()) {
            return out;
        }
        // 明细为空：当前页无行可装配。
        if (detailRows == null || detailRows.isEmpty()) {
            return out;
        }
        LinkedHashMap<String, List<SupplyRequestModel>> byBusNo = new LinkedHashMap<>();
        for (SupplyRequestModel row : detailRows) {
            if (row == null || StringUtils.isEmpty(row.getBusNo())) {
                continue;
            }
            String bn = row.getBusNo().trim();
            // 同一 bus_no 多明细行归桶，供后续头字段一致性校验与聚合。
            byBusNo.computeIfAbsent(bn, k -> new ArrayList<>()).add(row);
        }
        for (String busNoRaw : busNoPageOrder) {
            if (StringUtils.isEmpty(busNoRaw)) {
                continue;
            }
            String busNo = busNoRaw.trim();
            List<SupplyRequestModel> lines = byBusNo.get(busNo);
            // 分页键在明细图中缺失：跳过（不应出现，出现则视为数据缺口而非抛错）。
            if (lines == null || lines.isEmpty()) {
                continue;
            }
            List<SupplyRequestModel> sortedByApply = new ArrayList<>(lines);
            sortedByApply.sort(this::compareSupplyModelApplyTimeDescNullsLast);
            // 取「最近申请」行为代表头，与列表展示制单语义一致。
            SupplyRequestModel head = sortedByApply.get(0);
            for (SupplyRequestModel line : lines) {
                if (line == null) {
                    continue;
                }
                // 同号多行必须共享同一审批态，否则列表一行无法表达。
                if (!Objects.equals(head.getStatusEnum(), line.getStatusEnum())) {
                    throw new BusinessException(
                        ErrorCodeConstants.PHA_QL_102, "同号药房请领单状态头字段不一致: " + busNo);
                }
                // 同号多行必须共享同一来源药库（从哪入），否则违背一张调拨单头语义。
                if (!Objects.equals(head.getSourceLocationId(), line.getSourceLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.PHA_QL_102, "同号药房请领单来源库房头字段不一致: " + busNo);
                }
                // 同号多行必须共享同一目的药房。
                if (!Objects.equals(head.getPurposeLocationId(), line.getPurposeLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.PHA_QL_102, "同号药房请领单目的药房头字段不一致: " + busNo);
                }
            }
            LocalDateTime maxApply = null;
            List<BigDecimal> lineTotals = new ArrayList<>();
            for (SupplyRequestModel line : lines) {
                if (line == null) {
                    continue;
                }
                if (line.getTotalPrice() != null) {
                    lineTotals.add(line.getTotalPrice());
                }
                if (line.getApplyTime() != null) {
                    // 列表申请时间取同号下最晚申请时刻，表示「单据活跃时间」上界。
                    if (maxApply == null || line.getApplyTime().isAfter(maxApply)) {
                        maxApply = line.getApplyTime();
                    }
                }
            }
            BigDecimal totalAmount = MonetaryAmountSupport.sumThenSettle(lineTotals);
            LocalDateTime approvalTime = null;
            List<SupplyRequestModel> sortedById = new ArrayList<>(lines);
            sortedById.sort(this::compareSupplyModelIdAscNullsLast);
            for (SupplyRequestModel line : sortedById) {
                // 审批时间取「最早有审批时刻的行」，避免多行重复审批时间抖动。
                if (line != null && line.getApprovalTime() != null) {
                    approvalTime = line.getApprovalTime();
                    break;
                }
            }
            PharmacyRequisitionListRowDto dto = new PharmacyRequisitionListRowDto();
            dto.setBusNo(busNo);
            dto.setTypeEnum(SupplyType.PHARMACY_REQUISITION.getCode());
            dto.setStatusEnum(head.getStatusEnum());
            // 驳回态：把供应行 reason 带到列表 rejectReason，供侧栏卡片展示
            if (SupplyStatus.REJECT.getCode().equals(head.getStatusEnum())) {
                dto.setRejectReason(head.getReason());
            }
            dto.setSourceLocationId(head.getSourceLocationId());
            dto.setPurposeLocationId(head.getPurposeLocationId());
            dto.setApproverId(head.getApproverId());
            dto.setApplicantId(head.getApplicantId());
            dto.setOriginalBusNo(head.getOriginalBusNo());
            dto.setApprovalTime(approvalTime);
            dto.setApplyTime(maxApply);
            dto.setTotalAmount(totalAmount);
            out.add(dto);
        }
        return out;
    }

    /**
     * 装配药房请领分页列表响应（IF-PHA-QL-02；1-based 页码，B.10.11）。
     *
     * <p>分页字段遵循 PagedResult 规范（B.10.11）：content 承载列表数据，totalCount 承载总条数，
     * pageNo/pageSize 为 1-based 自然数。</p>
     *
     * @param mergedTotal 去重后的单据总条数
     * @param pageNo      当前页码（1-based）
     * @param pageSize    每页条数
     * @param records     当前页聚合行
     * @return 分页响应
     */
    @Override
    public PagePharmacyRequisitionsResponse toPagePharmacyRequisitionsResponse(long mergedTotal, int pageNo, int pageSize, List<PharmacyRequisitionListRowDto> records) {
        List<PharmacyRequisitionListRowDto> content = records;
        // records 由上层保证语义；null 时回落空表避免 JSON 序列化歧义。
        if (content == null) {
            content = new ArrayList<>();
        }
        return PagedResultBuilder.build(new PagePharmacyRequisitionsResponse(), content, mergedTotal, pageNo, pageSize);
    }

    /**
     * 将同号供应请求行装配为药房请领详情响应（IF-PHA-QL-03）。
     *
     * @param lines         同号多行写模型查询结果
     * @param productHints  行 id → 目录提示；可 null
     * @return 详情响应
     */
    @Override
    public GetPharmacyRequisitionDetailResponse toGetPharmacyRequisitionDetailResponse(
        List<SupplyRequestModel> lines,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints) {
        GetPharmacyRequisitionDetailResponse res = new GetPharmacyRequisitionDetailResponse();
        if (lines == null || lines.isEmpty()) {
            return res;
        }
        List<SupplyRequestModel> sortedByApply = new ArrayList<>(lines);
        sortedByApply.sort(this::compareSupplyModelApplyTimeDescNullsLast);
        // 详情头字段取「最近申请」行，与列表聚合头策略一致。
        SupplyRequestModel head = sortedByApply.get(0);
        res.setBusNo(head.getBusNo());
        res.setOriginalBusNo(head.getOriginalBusNo());
        res.setTypeEnum(head.getTypeEnum());
        res.setStatusEnum(head.getStatusEnum());
        res.setSourceLocationId(head.getSourceLocationId());
        res.setPurposeLocationId(head.getPurposeLocationId());
        res.setSourceTypeEnum(head.getSourceTypeEnum());
        res.setPurposeTypeEnum(head.getPurposeTypeEnum());
        res.setCategoryEnum(head.getCategoryEnum());
        List<PharmacyRequisitionDetailLineDto> detailLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            PharmacyRequisitionDetailLineDto dto = this.toPharmacyRequisitionDetailLineDto(line);
            // 目录提示按行 id 对齐；无提示时仍返回批号级明细主体。
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
     * 将单行供应请求读模型装配为药房请领详情明细 DTO（IF-PHA-QL-03）。
     *
     * @param line 供应请求行
     * @return 明细 DTO；{@code line} 为 null 时返回 null
     */
    @Override
    public PharmacyRequisitionDetailLineDto toPharmacyRequisitionDetailLineDto(SupplyRequestModel line) {
        if (line == null) {
            // 调用方循环中可跳过 null 行；此处不抛异常以免装配层吞没上游数据问题。
            return null;
        }
        PharmacyRequisitionDetailLineDto dto = new PharmacyRequisitionDetailLineDto();
        dto.setId(line.getId());
        dto.setItemTable(line.getItemTable());
        dto.setItemId(line.getItemId());
        dto.setItemQuantity(line.getItemQuantity());
        dto.setUnitCode(line.getUnitCode());
        dto.setLotNumber(line.getLotNumber());
        dto.setStartTime(line.getStartTime());
        dto.setEndTime(line.getEndTime());
        dto.setTraceNo(line.getTraceNo());
        dto.setRemake(line.getRemake());
        dto.setPrice(line.getPrice());
        dto.setTotalPrice(line.getTotalPrice());
        dto.setOccurrenceTime(line.getOccurrenceTime());
        // 展示类字段由应用层后续富化或保持空；此处不猜测产品主数据以免与读模型分叉。
        dto.setProductName(null);
        dto.setSpecification(null);
        dto.setManufacturer(null);
        dto.setUnitCodeDictText(null);
        dto.setProductCatalog(null);
        return dto;
    }

    /**
     * 新建药房请领：HTTP 请求装配为领域批量命令（IF-PHA-QL-04）。
     *
     * @param request     创建请求
     * @param operatorId  操作人主键
     * @param applicantId 申请人主键
     * @param applyTime   申请时间
     * @return 领域创建命令
     * @throws IllegalArgumentException 位置类型/类别 code 非法
     */
    @Override
    public CreateRequisitionCommand toCreateRequisitionCommand(
        CreatePharmacyRequisitionRequest request,
        Long operatorId,
        Long applicantId,
        LocalDateTime applyTime) {
        // 新建：revisionBusNo 为空，首行 busNo 取请求显式值或交领域生成。
        return this.buildCreateRequisitionCommand(
            request.getLines(),
            request.getSourceLocationId(),
            request.getPurposeLocationId(),
            request.getCategoryEnum(),
            request.getBusNo(),
            null,
            operatorId,
            applicantId,
            applyTime,
            request.getPractitionerId());
    }

    /**
     * 修订药房请领：HTTP 请求装配为领域批量命令（IF-PHA-QL-05）。
     *
     * @param request     修订请求
     * @param operatorId  操作人主键
     * @param applicantId 申请人主键
     * @param applyTime   申请时间
     * @return 领域创建命令
     * @throws IllegalArgumentException 位置类型/类别 code 非法
     */
    @Override
    public CreateRequisitionCommand toCreateRequisitionCommand(
        UpdatePharmacyRequisitionRequest request,
        Long operatorId,
        Long applicantId,
        LocalDateTime applyTime) {
        // 修订：显式新建号恒为空，revision 使用当前 bus_no 绑定同聚合。
        return this.buildCreateRequisitionCommand(
            request.getLines(),
            request.getSourceLocationId(),
            request.getPurposeLocationId(),
            request.getCategoryEnum(),
            null,
            request.getBusNo(),
            operatorId,
            applicantId,
            applyTime,
            request.getPractitionerId());
    }

    /**
     * 新建与修订共用：将批号级 HTTP 明细装配为 {@link CreateRequisitionCommand}（固定 {@code PHARMACY_REQUISITION}）。
     *
     * @param lineDtos          明细行，可 null
     * @param sourceLocationId  来源药库主键
     * @param purposeLocationId 目的药房主键
     * @param categoryEnumRaw   供应类别请求码
     * @param explicitBusNoRaw  新建显式单号，可 blank
     * @param revisionBusNo     修订既有单号；与显式新建号二选一
     * @param operatorId        操作人
     * @param applicantId       申请人
     * @param applyTime         申请时刻
     * @param practitionerId    经手人
     * @return 领域批量命令
     */
    private CreateRequisitionCommand buildCreateRequisitionCommand(
        List<PharmacyRequisitionLineDto> lineDtos,
        Long sourceLocationId,
        Long purposeLocationId,
        String categoryEnumRaw,
        String explicitBusNoRaw,
        String revisionBusNo,
        Long operatorId,
        Long applicantId,
        LocalDateTime applyTime,
        Long practitionerId) {
        CreateRequisitionCommand command = new CreateRequisitionCommand();
        command.setOperatorId(operatorId);
        List<CreateRequisitionCommand.CreateRequisitionItemCommand> items = new ArrayList<>();
        // 明细缺失：返回空项命令，由领域门控拒绝空单（装配层不抛业务异常）。
        if (lineDtos == null) {
            command.setItems(items);
            return command;
        }
        if (sourceLocationId == null) {
            throw new IllegalArgumentException("sourceLocationId 不能为空");
        }
        if (purposeLocationId == null) {
            throw new IllegalArgumentException("purposeLocationId 不能为空");
        }
        String sourceTypeCode = this.locationQueryService.requireSupplyRequestTypeForLocationId(sourceLocationId).getCode();
        String purposeTypeCode = this.locationQueryService.requireSupplyRequestTypeForLocationId(purposeLocationId).getCode();
        String categoryCode = this.resolveSupplyCategoryCode(categoryEnumRaw);
        String typeCode = SupplyType.PHARMACY_REQUISITION.getCode();
        String explicitBus = explicitBusNoRaw;
        if (explicitBus != null) {
            explicitBus = explicitBus.trim();
            if (explicitBus.isEmpty()) {
                explicitBus = null;
            }
        }
        String revisionTrim = null;
        if (revisionBusNo != null) {
            revisionTrim = revisionBusNo.trim();
        }
        for (PharmacyRequisitionLineDto line : lineDtos) {
            if (line == null) {
                continue;
            }
            CreateRequisitionCommand.CreateRequisitionItemCommand item = new CreateRequisitionCommand.CreateRequisitionItemCommand();
            // 修订路径强制使用既有 bus_no；新建路径允许显式号或交由领域按日生成。
            if (StringUtils.isNotEmpty(revisionTrim)) {
                item.setBusNo(revisionTrim);
            } else {
                item.setBusNo(explicitBus);
            }
            item.setOriginalBusNo(null);
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
            LocalDateTime occ = line.getOccurrenceTime();
            if (occ == null) {
                // 明细未填效期时回落为申请时刻，避免 occurrence_time 空导致展示与下游对账分叉。
                occ = applyTime;
            }
            item.setOccurrenceTime(occ);
            item.setReason(line.getRemake());
            item.setLotNumber(line.getLotNumber());
            item.setStartTime(line.getStartTime());
            item.setEndTime(line.getEndTime());
            item.setTraceNo(line.getTraceNo());
            item.setPrice(line.getPrice());
            item.setTotalPrice(line.getTotalPrice());
            item.setApplicantId(applicantId);
            item.setApplyTime(applyTime);
            item.setPractitionerId(practitionerId);
            items.add(item);
        }
        command.setItems(items);
        return command;
    }

    /**
     * 新建成功响应装配（IF-PHA-QL-04）。
     *
     * @param busNo 业务单号
     * @return 响应
     */
    @Override
    public CreatePharmacyRequisitionResponse toCreatePharmacyRequisitionResponse(String busNo) {
        CreatePharmacyRequisitionResponse res = new CreatePharmacyRequisitionResponse();
        res.setBusNo(busNo);
        return res;
    }

    /**
     * 修订成功响应装配（IF-PHA-QL-05）。
     *
     * @param busNo 业务单号
     * @return 响应
     */
    @Override
    public UpdatePharmacyRequisitionResponse toUpdatePharmacyRequisitionResponse(String busNo) {
        UpdatePharmacyRequisitionResponse res = new UpdatePharmacyRequisitionResponse();
        res.setBusNo(busNo);
        return res;
    }

    /**
     * 提交审批命令装配（IF-PHA-QL-06）。
     *
     * @param request    提交请求
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    @Override
    public SubmitApprovalCommand toSubmitApprovalCommand(SubmitPharmacyRequisitionApprovalRequest request, Long operatorId, Long applicantId) {
        SubmitApprovalCommand command = new SubmitApprovalCommand();
        command.setOperatorId(operatorId);
        SubmitApprovalCommand.SubmitApprovalItemCommand item = new SubmitApprovalCommand.SubmitApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApplicantId(applicantId);
        command.setItem(item);
        return command;
    }

    /**
     * 提交审批后的状态出参装配（IF-PHA-QL-06）。
     *
     * @param statusAfter 首行解析后的供应状态枚举，可为 null
     * @return 提交响应
     */
    @Override
    public SubmitPharmacyRequisitionApprovalResponse toSubmitPharmacyRequisitionApprovalResponse(SupplyStatus statusAfter) {
        SubmitPharmacyRequisitionApprovalResponse res = new SubmitPharmacyRequisitionApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        if (statusAfter != null) {
            // 状态码对外一律持久化 getCode() 字符串（B.10.8.1）。
            res.setStatusEnum(statusAfter.getCode());
        }
        return res;
    }

    /**
     * 撤回审批命令装配（IF-PHA-QL-07）。
     *
     * @param request    撤回请求
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    @Override
    public WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawPharmacyRequisitionApprovalRequest request, Long operatorId) {
        WithdrawApprovalCommand command = new WithdrawApprovalCommand();
        command.setOperatorId(operatorId);
        WithdrawApprovalCommand.WithdrawApprovalItemCommand item = new WithdrawApprovalCommand.WithdrawApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /**
     * 撤回审批后的状态出参装配（IF-PHA-QL-07）。
     *
     * @param statusAfter 首行解析后的供应状态枚举，可为 null
     * @return 撤回响应
     */
    @Override
    public WithdrawPharmacyRequisitionApprovalResponse toWithdrawPharmacyRequisitionApprovalResponse(SupplyStatus statusAfter) {
        WithdrawPharmacyRequisitionApprovalResponse res = new WithdrawPharmacyRequisitionApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        if (statusAfter != null) {
            // 状态码对外一律持久化 getCode() 字符串（B.10.8.1）。
            res.setStatusEnum(statusAfter.getCode());
        }
        return res;
    }

    /**
     * 同意命令装配（IF-PHA-QL-08）。
     *
     * @param request    同意请求
     * @param operatorId 操作人主键（审批人可空时回落值）
     * @return 领域命令
     */
    @Override
    public AgreeRequestCommand toAgreeRequestCommand(AgreePharmacyRequisitionRequest request, Long operatorId) {
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
     * 同意药房请领后的结构化出参装配（IF-PHA-QL-08）。
     *
     * @param busNo       业务单号
     * @param statusEnum  状态持久化码
     * @return 同意响应
     */
    @Override
    public AgreePharmacyRequisitionResponse toAgreePharmacyRequisitionResponse(String busNo, String statusEnum) {
        AgreePharmacyRequisitionResponse res = new AgreePharmacyRequisitionResponse();
        res.setBusNo(busNo);
        res.setStatusEnum(statusEnum);
        return res;
    }

    /**
     * 审批驳回命令装配（IF-PHA-QL-09）。
     *
     * @param request    驳回请求
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    @Override
    public RejectRequestCommand toRejectRequestCommand(RejectPharmacyRequisitionRequest request, Long operatorId) {
        RejectRequestCommand command = new RejectRequestCommand();
        command.setOperatorId(operatorId);
        RejectRequestCommand.RejectRequestItemCommand item = new RejectRequestCommand.RejectRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        item.setApprovalClock(null);
        String mergedReason = null;
        if (StringUtils.isNotEmpty(request.getChangeReason())) {
            mergedReason = request.getChangeReason().trim();
        } else if (StringUtils.isNotEmpty(request.getChangeReasonCode())) {
            mergedReason = request.getChangeReasonCode().trim();
        }
        item.setChangeReasonCode(mergedReason);
        command.setItem(item);
        return command;
    }

    /**
     * 驳回操作布尔结果出参装配（IF-PHA-QL-09）。
     *
     * @param ok 是否成功
     * @return 驳回响应
     */
    @Override
    public RejectPharmacyRequisitionResponse toRejectPharmacyRequisitionResponse(boolean ok) {
        RejectPharmacyRequisitionResponse res = new RejectPharmacyRequisitionResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /**
     * 删除命令装配（IF-PHA-QL-10）。
     *
     * @param request    删除请求
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    @Override
    public RemoveByBusNoCommand toRemoveByBusNoCommand(RemovePharmacyRequisitionRequest request, Long operatorId) {
        RemoveByBusNoCommand command = new RemoveByBusNoCommand();
        command.setOperatorId(operatorId);
        RemoveByBusNoCommand.RemoveByBusNoItemCommand item = new RemoveByBusNoCommand.RemoveByBusNoItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /**
     * 删除操作布尔结果出参装配（IF-PHA-QL-10）。
     *
     * @param ok 是否成功
     * @return 删除响应
     */
    @Override
    public RemovePharmacyRequisitionResponse toRemovePharmacyRequisitionResponse(boolean ok) {
        RemovePharmacyRequisitionResponse res = new RemovePharmacyRequisitionResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /**
     * 选品分页结果装配（IF-PHA-QL-11；1-based 页码，B.10.11）。
     *
     * <p>分页字段遵循 PagedResult 规范（B.10.11）：content 承载列表数据，totalCount 承载总条数，
     * pageNo/pageSize 由 {@link PagedResultBuilder} 从 Spring Page 推导（B.10.11）。</p>
     *
     * @param page 库存分页
     * @return 选品分页响应
     */
    @Override
    public PageProductsForPharmacyRequisitionResponse toPageProductsForPharmacyRequisitionResponse(Page<InventoryItemModel> page) {
        List<ProductDefinitionItemDto> content = new ArrayList<>();
        for (InventoryItemModel inv : page.getContent()) {
            if (inv == null) {
                continue;
            }
            // 选品行先落库存主数据；四槽与采购价由 AppService 富化。
            content.add(this.toProductDefinitionItemDtoFromInventory(inv, null));
        }
        return PagedResultBuilder.build(new PageProductsForPharmacyRequisitionResponse(), content, page);
    }

    /**
     * 库存行与可选产品读模型装配为药房请领选品行（IF-PHA-QL-11）。
     *
     * @param inv 库存项读模型
     * @param pr  产品定义读模型，可 null
     * @return 选品 DTO
     */
    @Override
    public ProductDefinitionItemDto toProductDefinitionItemDtoFromInventory(InventoryItemModel inv, ProductDefinitionDisplayDto pr) {
        ProductDefinitionItemDto dto = new ProductDefinitionItemDto();
        dto.setInventoryItemId(inv.getId());
        dto.setProductTableName(inv.getItemTable());
        dto.setProductDefinitionId(inv.getItemId());
        dto.setAvailableQuantity(inv.getQuantity());
        dto.setLotNumber(inv.getLotNumber());
        dto.setExpirationDate(inv.getExpirationDate());
        if (pr != null) {
            // 读模型在场时补齐编号与展示维度，避免选品弹窗仅见库存主键。
            dto.setProductBusNo(pr.getProductBusNo());
            dto.setProductName(pr.getProductName());
            dto.setSpecification(pr.getTotalVolume());
            dto.setManufacturer(pr.getManufacturer());
        } else {
            // 无读模型：保持空展示字段，由上层决定是否二次批量加载。
            dto.setProductName(null);
            dto.setSpecification(null);
            dto.setManufacturer(null);
        }
        dto.setUnitBands(null);
        dto.setPurchaseUnitPrice(null);
        return dto;
    }

    /**
     * 供应请求行按申请时间降序比较器；申请时间为 null 的排后（列表/详情头选取）。
     *
     * @param left  左侧行
     * @param right 右侧行
     * @return 比较结果
     */
    private int compareSupplyModelApplyTimeDescNullsLast(SupplyRequestModel left, SupplyRequestModel right) {
        LocalDateTime ta = left.getApplyTime();
        LocalDateTime tb = right.getApplyTime();
        if (ta == null && tb == null) {
            return 0;
        }
        // null 申请时间视为「更旧」，排序时沉底，避免抢占头行。
        if (ta == null) {
            return 1;
        }
        if (tb == null) {
            return -1;
        }
        return tb.compareTo(ta);
    }

    /**
     * 供应请求行按主键升序比较器；主键为 null 的排后（审批时间选取时稳定次序）。
     *
     * @param left  左侧行
     * @param right 右侧行
     * @return 比较结果
     */
    private int compareSupplyModelIdAscNullsLast(SupplyRequestModel left, SupplyRequestModel right) {
        Long ida = left.getId();
        Long idb = right.getId();
        if (ida == null && idb == null) {
            return 0;
        }
        // 无主键行排序沉底，保证稳定选取「最早有 id」的审批时间候选。
        if (ida == null) {
            return 1;
        }
        if (idb == null) {
            return -1;
        }
        return ida.compareTo(idb);
    }

    /**
     * 解析供应类别请求码；未传时回落默认「库存供应」语义。
     *
     * @param categoryCodeRaw 请求 categoryEnum，可 blank
     * @return 持久化类别码
     * @throws IllegalArgumentException 码不在枚举白名单
     */
    private String resolveSupplyCategoryCode(String categoryCodeRaw) {
        if (StringUtils.isEmpty(categoryCodeRaw)) {
            // 与药房入库/药库出库选品共用默认类别，减少前端必传负担。
            return SupplyCategory.STOCK_SUPPLY.getCode();
        }
        SupplyCategory category = EnumUtil.fromCode(SupplyCategory.class, categoryCodeRaw.trim());
        if (category == null) {
            throw new IllegalArgumentException("categoryEnum 非法");
        }
        return category.getCode();
    }
}