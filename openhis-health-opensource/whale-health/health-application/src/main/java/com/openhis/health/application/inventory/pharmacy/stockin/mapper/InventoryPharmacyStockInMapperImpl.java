/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.stockin.mapper;

import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.inventory.pharmacy.stockin.dto.PharmacyStockInDetailLineDto;
import com.openhis.health.application.inventory.pharmacy.stockin.dto.PharmacyStockInLineDto;
import com.openhis.health.application.inventory.pharmacy.stockin.dto.PharmacyStockInListRowDto;
import com.openhis.health.application.inventory.pharmacy.stockin.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.pharmacy.stockin.request.AgreePharmacyStockInRequest;
import com.openhis.health.application.inventory.pharmacy.stockin.request.CreatePharmacyStockInRequest;
import com.openhis.health.application.inventory.pharmacy.stockin.request.RejectPharmacyStockInRequest;
import com.openhis.health.application.inventory.pharmacy.stockin.request.RemovePharmacyStockInRequest;
import com.openhis.health.application.inventory.pharmacy.stockin.request.SubmitPharmacyStockInApprovalRequest;
import com.openhis.health.application.inventory.pharmacy.stockin.request.UpdatePharmacyStockInRequest;
import com.openhis.health.application.inventory.pharmacy.stockin.request.WithdrawPharmacyStockInApprovalRequest;
import com.openhis.health.application.inventory.pharmacy.stockin.response.AgreePharmacyStockInResponse;
import com.openhis.health.application.inventory.pharmacy.stockin.response.CreatePharmacyStockInResponse;
import com.openhis.health.application.inventory.pharmacy.stockin.response.GetPharmacyStockInDetailResponse;
import com.openhis.health.application.inventory.pharmacy.stockin.response.LoadPharmacyStockInFormInitResponse;
import com.openhis.health.application.inventory.pharmacy.stockin.response.PagePharmacyStockInsResponse;
import com.openhis.health.application.inventory.pharmacy.stockin.response.PageProductsForPharmacyStockInResponse;
import com.openhis.health.application.inventory.pharmacy.stockin.response.RejectPharmacyStockInResponse;
import com.openhis.health.application.inventory.pharmacy.stockin.response.RemovePharmacyStockInResponse;
import com.openhis.health.application.inventory.pharmacy.stockin.response.SubmitPharmacyStockInApprovalResponse;
import com.openhis.health.application.inventory.pharmacy.stockin.response.UpdatePharmacyStockInResponse;
import com.openhis.health.application.inventory.pharmacy.stockin.response.WithdrawPharmacyStockInApprovalResponse;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.SupplyCategory;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.health.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 药房入库单装配实现：{@link InventoryPharmacyStockInMapper}。
 *
 * <p>各 {@code @Override} 方法 JavaDoc 与接口保持同义完整说明（B.1），禁止仅以单独一行 {@code @inheritDoc} 占位。</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryPharmacyStockInMapperImpl implements InventoryPharmacyStockInMapper {

    private final LocationQueryService locationQueryService;

    /**
     * 将位置实体列表装配为下拉（IF-PHA-IN-01）。
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
     * 将医务人员实体列表装配为下拉（IF-PHA-IN-01）。
     *
     * @param practitioners 医务人员列表，可为 null
     * @return 下拉项列表，永不为 null
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
            // 人员姓名为空时用 id 兜底，与位置下拉策略一致。
            if (StringUtils.isEmpty(label)) {
                label = String.valueOf(id);
            }
            list.add(new OptionDto(id, String.valueOf(id), label));
        }
        return list;
    }

    /**
     * 装配供应状态筛选项（IF-PHA-IN-01）。
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
     * 装配明细物品表类型两档（IF-PHA-IN-01）。
     *
     * @return 固定两项
     */
    @Override
    public List<OptionDto> toOptionDtoListInboundItemTableTypes() {
        List<OptionDto> list = new ArrayList<>();
        // 两档与库存行 item_table 物理表名对齐，供前端与选品 API 同值回传。
        list.add(new OptionDto(1L, TableNameConstants.MED_MEDICATION_DEFINITION, "药品"));
        list.add(new OptionDto(2L, TableNameConstants.ADM_DEVICE_DEFINITION, "耗材"));
        return list;
    }

    /**
     * 组装药房入库页初始化响应（IF-PHA-IN-01）。
     *
     * @param pharmacyOpts   目的药房
     * @param warehouseOpts  来源药库
     * @param inboundTypes   明细物品表类型项
     * @param statusOpts     单据状态筛选项
     * @param pharmacistOpts 药师
     * @param keeperOpts     库管
     * @return 初始化响应体
     */
    @Override
    public LoadPharmacyStockInFormInitResponse toLoadPharmacyStockInFormInitResponse(
        List<OptionDto> pharmacyOpts,
        List<OptionDto> warehouseOpts,
        List<OptionDto> inboundTypes,
        List<OptionDto> statusOpts,
        List<OptionDto> pharmacistOpts,
        List<OptionDto> keeperOpts) {
        LoadPharmacyStockInFormInitResponse res = new LoadPharmacyStockInFormInitResponse();
        // 初始化响应仅做字段挂接，不含业务分支；各集合由 AppService 预收窄。
        res.setPharmacyLocationOptions(pharmacyOpts);
        res.setWarehouseLocationOptions(warehouseOpts);
        res.setInboundItemTableTypeOptions(inboundTypes);
        res.setStatusOptions(statusOpts);
        res.setPharmacistOptions(pharmacistOpts);
        res.setWarehouseKeeperOptions(keeperOpts);
        return res;
    }

    /**
     * 将当前页业务单号顺序与同批明细行装配为药房入库列表聚合行（IF-PHA-IN-02）。
     *
     * @param busNoPageOrder 分页查询得到的单号顺序
     * @param detailRows     与当前页单号集合对应的全部明细行
     * @return 聚合行列表；来源位置展示名由应用层富化
     * @throws BusinessException 同号多行头字段不一致（{@link ErrorCodeConstants#PHA_IN_102}）
     */
    @Override
    public List<PharmacyStockInListRowDto> toPharmacyStockInListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows) {
        List<PharmacyStockInListRowDto> out = new ArrayList<>();
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
                        ErrorCodeConstants.PHA_IN_102, "同号药房入库单状态头字段不一致: " + busNo);
                }
                // 同号多行必须共享同一来源药库（从哪入），否则违背一张调拨单头语义。
                if (!Objects.equals(head.getSourceLocationId(), line.getSourceLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.PHA_IN_102, "同号药房入库单来源库房头字段不一致: " + busNo);
                }
                // 同号多行必须共享同一目的药房。
                if (!Objects.equals(head.getPurposeLocationId(), line.getPurposeLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.PHA_IN_102, "同号药房入库单目的药房头字段不一致: " + busNo);
                }
            }
            LocalDateTime maxApply = null;
            for (SupplyRequestModel line : lines) {
                if (line == null) {
                    continue;
                }
                if (line.getApplyTime() != null) {
                    // 列表申请时间取同号下最晚申请时刻，表示「单据活跃时间」上界。
                    if (maxApply == null || line.getApplyTime().isAfter(maxApply)) {
                        maxApply = line.getApplyTime();
                    }
                }
            }
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
            PharmacyStockInListRowDto dto = new PharmacyStockInListRowDto();
            dto.setBusNo(busNo);
            dto.setTypeEnum(SupplyType.PHARMACY_STOCK_IN.getCode());
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
            out.add(dto);
        }
        return out;
    }

    /**
     * 装配药房入库分页列表响应（IF-PHA-IN-02；1-based 页码，B.10.11）。
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
    public PagePharmacyStockInsResponse toPagePharmacyStockInsResponse(long mergedTotal, int pageNo, int pageSize, List<PharmacyStockInListRowDto> records) {
        List<PharmacyStockInListRowDto> content = records;
        // records 由上层保证语义；null 时回落空表避免 JSON 序列化歧义。
        if (content == null) {
            content = new ArrayList<>();
        }
        return PagedResultBuilder.build(new PagePharmacyStockInsResponse(), content, mergedTotal, pageNo, pageSize);
    }

    /**
     * 将同号供应请求行装配为药房入库详情响应（IF-PHA-IN-03）。
     *
     * @param lines         同号多行写模型查询结果
     * @param productHints  行 id → 目录提示；可 null
     * @return 详情响应
     */
    @Override
    public GetPharmacyStockInDetailResponse toGetPharmacyStockInDetailResponse(
        List<SupplyRequestModel> lines,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints) {
        GetPharmacyStockInDetailResponse res = new GetPharmacyStockInDetailResponse();
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
        List<PharmacyStockInDetailLineDto> detailLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            PharmacyStockInDetailLineDto dto = this.toPharmacyStockInDetailLineDto(line);
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
     * 将单行供应请求读模型装配为药房入库详情明细 DTO（IF-PHA-IN-03）。
     *
     * @param line 供应请求行
     * @return 明细 DTO；{@code line} 为 null 时返回 null
     */
    @Override
    public PharmacyStockInDetailLineDto toPharmacyStockInDetailLineDto(SupplyRequestModel line) {
        if (line == null) {
            // 调用方循环中可跳过 null 行；此处不抛异常以免装配层吞没上游数据问题。
            return null;
        }
        PharmacyStockInDetailLineDto dto = new PharmacyStockInDetailLineDto();
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
     * 新建药房入库：HTTP 请求装配为领域批量命令（IF-PHA-IN-04）。
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
        CreatePharmacyStockInRequest request,
        Long operatorId,
        Long applicantId,
        LocalDateTime applyTime) {
        // 新建：revisionBusNo 为空，首行 busNo 取请求显式值或交领域生成。
        return this.buildCreateRequisitionCommand(
            request.getLines(),
            request.getSourceLocationId(),
            request.getPurposeLocationId(),
            request.getCategoryEnum(),
            request.getOriginalBusNo(),
            request.getBusNo(),
            null,
            operatorId,
            applicantId,
            applyTime,
            request.getPractitionerId());
    }

    /**
     * 修订药房入库：HTTP 请求装配为领域批量命令（IF-PHA-IN-05）。
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
        UpdatePharmacyStockInRequest request,
        Long operatorId,
        Long applicantId,
        LocalDateTime applyTime) {
        // 修订：显式新建号恒为空，revision 使用当前 bus_no 绑定同聚合。
        return this.buildCreateRequisitionCommand(
            request.getLines(),
            request.getSourceLocationId(),
            request.getPurposeLocationId(),
            request.getCategoryEnum(),
            request.getOriginalBusNo(),
            null,
            request.getBusNo(),
            operatorId,
            applicantId,
            applyTime,
            request.getPractitionerId());
    }

    /**
     * 新建与修订共用：将批号级 HTTP 明细装配为 {@link CreateRequisitionCommand}（固定 {@code PHARMACY_STOCK_IN}）。
     *
     * @param lineDtos            明细行，可 null
     * @param sourceLocationId    来源药库主键
     * @param purposeLocationId   目的药房主键
     * @param categoryEnumRaw     供应类别请求码
     * @param originalBusNoRaw    关联上游单号，可 blank
     * @param explicitBusNoRaw    新建显式单号，可 blank
     * @param revisionBusNo       修订既有单号；与显式新建号二选一
     * @param operatorId          操作人
     * @param applicantId         申请人
     * @param applyTime           申请时刻
     * @return 领域批量命令
     */
    private CreateRequisitionCommand buildCreateRequisitionCommand(
        List<PharmacyStockInLineDto> lineDtos,
        Long sourceLocationId,
        Long purposeLocationId,
        String categoryEnumRaw,
        String originalBusNoRaw,
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
        String typeCode = SupplyType.PHARMACY_STOCK_IN.getCode();
        String original = originalBusNoRaw;
        if (original != null) {
            original = original.trim();
            // 空白关联号视为未填，避免库内存空串与 null 语义分叉。
            if (original.isEmpty()) {
                original = null;
            }
        }
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
        for (PharmacyStockInLineDto line : lineDtos) {
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
            item.setOriginalBusNo(original);
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
                // 与药库出库同意生成的壳单行对齐：制单时刻缺省回落为申请时刻，避免 occurrence_time 空导致下游发放/展示分叉。
                occ = applyTime;
            }
            item.setOccurrenceTime(occ);
            item.setReason(line.getRemake());
            item.setLotNumber(line.getLotNumber());
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
     * 新建成功响应装配（IF-PHA-IN-04）。
     *
     * @param busNo 业务单号
     * @return 响应
     */
    @Override
    public CreatePharmacyStockInResponse toCreatePharmacyStockInResponse(String busNo) {
        CreatePharmacyStockInResponse res = new CreatePharmacyStockInResponse();
        res.setBusNo(busNo);
        return res;
    }

    /**
     * 修订成功响应装配（IF-PHA-IN-05）。
     *
     * @param busNo 业务单号
     * @return 响应
     */
    @Override
    public UpdatePharmacyStockInResponse toUpdatePharmacyStockInResponse(String busNo) {
        UpdatePharmacyStockInResponse res = new UpdatePharmacyStockInResponse();
        res.setBusNo(busNo);
        return res;
    }

    /**
     * 提交审批命令装配（IF-PHA-IN-06）。
     *
     * @param request    提交请求
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    @Override
    public SubmitApprovalCommand toSubmitApprovalCommand(SubmitPharmacyStockInApprovalRequest request, Long operatorId, Long applicantId) {
        SubmitApprovalCommand command = new SubmitApprovalCommand();
        command.setOperatorId(operatorId);
        SubmitApprovalCommand.SubmitApprovalItemCommand item = new SubmitApprovalCommand.SubmitApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApplicantId(applicantId);
        command.setItem(item);
        return command;
    }

    /**
     * 提交审批后的状态出参装配（IF-PHA-IN-06）。
     *
     * @param statusAfter 首行解析后的供应状态枚举，可为 null
     * @return 提交响应
     */
    @Override
    public SubmitPharmacyStockInApprovalResponse toSubmitPharmacyStockInApprovalResponse(SupplyStatus statusAfter) {
        SubmitPharmacyStockInApprovalResponse res = new SubmitPharmacyStockInApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        if (statusAfter != null) {
            // 状态码对外一律持久化 getCode() 字符串（B.10.8.1）。
            res.setStatusEnum(statusAfter.getCode());
        }
        return res;
    }

    /**
     * 撤回审批命令装配（IF-PHA-IN-07）。
     *
     * @param request    撤回请求
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    @Override
    public WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawPharmacyStockInApprovalRequest request, Long operatorId) {
        WithdrawApprovalCommand command = new WithdrawApprovalCommand();
        command.setOperatorId(operatorId);
        WithdrawApprovalCommand.WithdrawApprovalItemCommand item = new WithdrawApprovalCommand.WithdrawApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /**
     * 撤回审批后的状态出参装配（IF-PHA-IN-07）。
     *
     * @param statusAfter 首行解析后的供应状态枚举，可为 null
     * @return 撤回响应
     */
    @Override
    public WithdrawPharmacyStockInApprovalResponse toWithdrawPharmacyStockInApprovalResponse(SupplyStatus statusAfter) {
        WithdrawPharmacyStockInApprovalResponse res = new WithdrawPharmacyStockInApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        if (statusAfter != null) {
            // 状态码对外一律持久化 getCode() 字符串（B.10.8.1）。
            res.setStatusEnum(statusAfter.getCode());
        }
        return res;
    }

    /**
     * 将药房入库同意请求装配为领域 {@link AgreeRequestCommand}（IF-PHA-IN-08）。
     *
     * @param request    同意请求，须含 busNo
     * @param operatorId 当前操作人主键，同时写入 approver_id
     * @return 领域同意命令
     */
    @Override
    public AgreeRequestCommand toAgreeRequestCommand(AgreePharmacyStockInRequest request, Long operatorId) {
        AgreeRequestCommand command = new AgreeRequestCommand();
        command.setOperatorId(operatorId);
        AgreeRequestCommand.AgreeRequestItemCommand item = new AgreeRequestCommand.AgreeRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        command.setItem(item);
        return command;
    }

    /**
     * 将已同意的药房入库明细批量装配为发放命令（IF-PHA-IN-08）。
     *
     * @param lines      同意后的入库行
     * @param operatorId 操作人主键
     * @return 发放创建命令
     */
    @Override
    public CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommandForPharmacyStockIn(List<SupplyRequestModel> lines, Long operatorId) {
        CreateSupplyDeliveryCommand cmd = new CreateSupplyDeliveryCommand();
        cmd.setOperatorId(operatorId);
        List<CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand> items = new ArrayList<>();
        if (lines != null) {
            for (SupplyRequestModel line : lines) {
                // 无持久化 request 行主键则无法挂接发放明细，跳过。
                if (line == null || line.getId() == null) {
                    continue;
                }
                CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand item =
                    new CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand();
                item.setRequestId(line.getId());
                // 发放类型与供应请求 type_enum 对齐，供下游库存与审计识别为药房入库。
                item.setTypeEnum(SupplyType.PHARMACY_STOCK_IN.getCode());
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
     * 同意药房入库后的结构化出参装配（IF-PHA-IN-08）。
     *
     * @param busNo       业务单号
     * @param statusEnum  状态持久化码
     * @return 同意响应
     */
    @Override
    public AgreePharmacyStockInResponse toAgreePharmacyStockInResponse(String busNo, String statusEnum) {
        AgreePharmacyStockInResponse res = new AgreePharmacyStockInResponse();
        res.setBusNo(busNo);
        res.setStatusEnum(statusEnum);
        return res;
    }

    /**
     * 审批驳回命令装配（IF-PHA-IN-09）。
     *
     * @param request    驳回请求
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    @Override
    public RejectRequestCommand toRejectRequestCommand(RejectPharmacyStockInRequest request, Long operatorId) {
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
     * 驳回操作布尔结果出参装配（IF-PHA-IN-09）。
     *
     * @param ok 是否成功
     * @return 驳回响应
     */
    @Override
    public RejectPharmacyStockInResponse toRejectPharmacyStockInResponse(boolean ok) {
        RejectPharmacyStockInResponse res = new RejectPharmacyStockInResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /**
     * 删除命令装配（IF-PHA-IN-10）。
     *
     * @param request    删除请求
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    @Override
    public RemoveByBusNoCommand toRemoveByBusNoCommand(RemovePharmacyStockInRequest request, Long operatorId) {
        RemoveByBusNoCommand command = new RemoveByBusNoCommand();
        command.setOperatorId(operatorId);
        RemoveByBusNoCommand.RemoveByBusNoItemCommand item = new RemoveByBusNoCommand.RemoveByBusNoItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /**
     * 删除操作布尔结果出参装配（IF-PHA-IN-10）。
     *
     * @param ok 是否成功
     * @return 删除响应
     */
    @Override
    public RemovePharmacyStockInResponse toRemovePharmacyStockInResponse(boolean ok) {
        RemovePharmacyStockInResponse res = new RemovePharmacyStockInResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /**
     * 选品分页结果装配（IF-PHA-IN-11；1-based 页码，B.10.11）。
     *
     * <p>分页字段遵循 PagedResult 规范（B.10.11）：content 承载列表数据，totalCount 承载总条数，
     * pageNo/pageSize 由 {@link PagedResultBuilder} 从 Spring Page 推导（B.10.11）。</p>
     *
     * @param page 库存分页
     * @return 选品分页响应
     */
    @Override
    public PageProductsForPharmacyStockInResponse toPageProductsForPharmacyStockInResponse(Page<InventoryItemModel> page) {
        List<ProductDefinitionItemDto> content = new ArrayList<>();
        for (InventoryItemModel inv : page.getContent()) {
            if (inv == null) {
                continue;
            }
            // 选品行先落库存主数据；四槽与采购价由 AppService 富化。
            content.add(this.toProductDefinitionItemDtoFromInventory(inv, null));
        }
        return PagedResultBuilder.build(new PageProductsForPharmacyStockInResponse(), content, page);
    }

    /**
     * 库存行与可选产品读模型装配为药房入库选品行（IF-PHA-IN-11）。
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
            // 与历史进货/请领默认类别对齐，减少前端必传负担。
            return SupplyCategory.STOCK_SUPPLY.getCode();
        }
        SupplyCategory category = EnumUtil.fromCode(SupplyCategory.class, categoryCodeRaw.trim());
        if (category == null) {
            throw new IllegalArgumentException("categoryEnum 非法");
        }
        return category.getCode();
    }
}