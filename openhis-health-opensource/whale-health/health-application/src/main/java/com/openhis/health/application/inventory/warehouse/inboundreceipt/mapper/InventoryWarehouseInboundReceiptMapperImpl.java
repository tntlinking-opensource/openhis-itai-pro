/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper;

import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptDetailLineDto;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptLineDto;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptListRowDto;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.ProductDefinitionItemDto;
import com.openhis.health.application.common.assembler.product.ProductDefinitionCatalogEditorHintsAssembler;
import com.openhis.health.application.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.WarehouseDocumentDetailRowDto;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.request.*;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.response.*;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.application.querysupport.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.SupplierType;
import com.openhis.health.domain.share.enums.SupplyCategory;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.inventoryitem.command.CreateInventoryItemCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.health.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateInboundReceiptCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.application.common.pagination.PageQueryBinder;
import com.openhis.health.application.common.pagination.PagedResultBuilder;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
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
 * 药库进货（入库）单装配实现：{@link InventoryWarehouseInboundReceiptMapper}。
 *
 * 11 选品、12 可关联订货、08 同意后的发放与库存命令。契约、B.10.8.1 / B.10.11 口径以接口为准。</p>
 *
 * <p>各 {@code @Override} 方法须在 JavaDoc 中与接口保持<strong>同义完整说明</strong>（可补充实现细节与体内 {@code //} 呼应），
 * 禁止仅用单独一行 {@code @inheritDoc} 作为方法 JavaDoc 的完整说明占位（B.1）。</p>
 */
@Component
public class InventoryWarehouseInboundReceiptMapperImpl implements InventoryWarehouseInboundReceiptMapper {

    private final LocationQueryService locationQueryService;

    public InventoryWarehouseInboundReceiptMapperImpl(LocationQueryService locationQueryService) {
        this.locationQueryService = locationQueryService;
    }

    @Override
    public List<OptionDto> toOptionDtoListFromLocations(List<Location> locations) {
        List<OptionDto> list = new ArrayList<>();
        if (locations == null) {
            return list;
        }
        // —— 位置主数据可能缺名称：下拉 label 回退为 id 字符串，避免前端渲染空白项。
        for (Location location : locations) {
            if (location == null || location.getId() == null) {
                continue;
            }
            Long id = location.getId();
            String label = location.getName();
            if (StringUtils.isBlank(label)) {
                label = String.valueOf(id);
            }
            list.add(new OptionDto(id, String.valueOf(id), label));
        }
        return list;
    }

    @Override
    public List<OptionDto> toOptionDtoListFromSuppliers(List<SupplierListItemQueryModel> suppliers) {
        List<OptionDto> list = new ArrayList<>();
        if (suppliers == null) {
            return list;
        }
        for (SupplierListItemQueryModel row : suppliers) {
            if (row == null || row.getId() == null) {
                continue;
            }
            Long id = row.getId();
            SupplierType st = EnumUtil.fromCode(SupplierType.class, row.getTypeEnum());
            String typeLabel;
            if (st != null) {
                typeLabel = st.getDisplay();
            } else {
                typeLabel = row.getTypeEnum();
            }
            String baseName = row.getName();
            if (StringUtils.isBlank(baseName)) {
                baseName = String.valueOf(id);
            }
            String label = baseName;
            if (StringUtils.isNotBlank(typeLabel)) {
                label = baseName + "（" + typeLabel + "）";
            }
            list.add(new OptionDto(id, String.valueOf(id), label));
        }
        return list;
    }

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

    @Override
    public List<OptionDto> toOptionDtoListInboundItemTableTypes() {
        List<OptionDto> list = new ArrayList<>();
        list.add(new OptionDto(1L, TableNameConstants.MED_MEDICATION_DEFINITION, "药品"));
        list.add(new OptionDto(2L, TableNameConstants.ADM_DEVICE_DEFINITION, "耗材"));
        return list;
    }

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
            if (StringUtils.isBlank(label)) {
                label = String.valueOf(id);
            }
            list.add(new OptionDto(id, String.valueOf(id), label));
        }
        return list;
    }

    @Override
    public LoadInboundReceiptFormInitResponse toLoadInboundReceiptFormInitResponse(
        List<OptionDto> pharmacyLocationOptions,
        List<OptionDto> warehouseLocationOptions,
        List<OptionDto> supplierOptions,
        List<OptionDto> pharmacistOptions,
        List<OptionDto> warehouseKeeperOptions,
        List<OptionDto> inboundItemTableTypeOptions,
        List<OptionDto> statusOptions) {
        LoadInboundReceiptFormInitResponse res = new LoadInboundReceiptFormInitResponse();
        res.setPharmacyLocationOptions(pharmacyLocationOptions);
        res.setWarehouseLocationOptions(warehouseLocationOptions);
        res.setSupplierOptions(supplierOptions);
        res.setPharmacistOptions(pharmacistOptions);
        res.setWarehouseKeeperOptions(warehouseKeeperOptions);
        res.setInboundItemTableTypeOptions(inboundItemTableTypeOptions);
        res.setStatusOptions(statusOptions);
        return res;
    }

    @Override
    public List<InboundReceiptListRowDto> toInboundReceiptListAggregated(
        List<String> busNoPageOrder,
        List<SupplyRequestModel> detailRows) {
        List<InboundReceiptListRowDto> out = new ArrayList<>();
        if (busNoPageOrder == null || busNoPageOrder.isEmpty()) {
            return out;
        }
        if (detailRows == null || detailRows.isEmpty()) {
            return out;
        }
        // —— 先按单号分组，便于按分页给出的单号顺序输出且每单只出一行聚合 DTO。
        LinkedHashMap<String, List<SupplyRequestModel>> byBusNo = new LinkedHashMap<>();
        for (SupplyRequestModel row : detailRows) {
            if (row == null || StringUtils.isBlank(row.getBusNo())) {
                continue;
            }
            String bn = row.getBusNo().trim();
            byBusNo.computeIfAbsent(bn, k -> new ArrayList<>()).add(row);
        }
        for (String busNoRaw : busNoPageOrder) {
            if (StringUtils.isBlank(busNoRaw)) {
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
            // —— 同号多行应共享单据头：供应商与状态不一致时直接失败，避免列表展示「假一致」。
            for (SupplyRequestModel line : lines) {
                if (line == null) {
                    continue;
                }
                if (!Objects.equals(head.getSupplierId(), line.getSupplierId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_IN_102, "同号进货单供应商头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getStatusEnum(), line.getStatusEnum())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_IN_102, "同号进货单状态头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getPurposeLocationId(), line.getPurposeLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_IN_102, "同号进货单目的库房头字段不一致: " + busNo);
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
            List<SupplyRequestModel> sortedById = new ArrayList<>(lines);
            sortedById.sort(this::compareSupplyModelIdAscNullsLast);
            // —— 审批时刻取主键升序下第一条非空：稳定且不依赖数据库返回行序。
            for (SupplyRequestModel line : sortedById) {
                if (line != null && line.getApprovalTime() != null) {
                    approvalTime = line.getApprovalTime();
                    break;
                }
            }
            InboundReceiptListRowDto dto = new InboundReceiptListRowDto();
            dto.setBusNo(busNo);
            dto.setPurposeLocationId(head.getPurposeLocationId());
            dto.setStatusEnum(head.getStatusEnum());
            dto.setApproverId(head.getApproverId());
            dto.setTotalAmount(totalAmount);
            dto.setApprovalTime(approvalTime);
            dto.setApplyTime(maxApply);
            dto.setOccurrenceTime(maxOccurrence);
            out.add(dto);
        }
        return out;
    }

    @Override
    public PageInboundReceiptsResponse toPageInboundReceiptsAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<InboundReceiptListRowDto> records) {
        // —— 防御性拷贝：调用方若传 null，仍返回可序列化的空列表，避免前端 NPE。
        List<InboundReceiptListRowDto> content;
        if (records == null) {
            content = new ArrayList<>();
        } else {
            content = records;
        }
        // —— B.10.11：使用 PagedResultBuilder 统一构建分页响应。——
        return PagedResultBuilder.build(new PageInboundReceiptsResponse(), content, mergedTotal, pageNo, pageSize);
    }

    /**
     *
     * <p>使用 {@link PagedResult} 统一构建分页响应，{@code content} 为订货相关明细行数据。</p>
     *
     * @param page 库房单据读模型分页
     * @return 订货单候选分页响应；行内枚举语义为持久化 code（B.10.8.1）
     */
    @Override
    public PageLinkablePurchaseOrdersResponse toPageLinkablePurchaseOrdersResponse(Page<WarehouseDocumentDetailQueryModel> page) {
        List<WarehouseDocumentDetailRowDto> content = new ArrayList<>();
        for (WarehouseDocumentDetailQueryModel row : page.getContent()) {
            content.add(this.toWarehouseDocumentDetailRowDto(row));
        }
        return PagedResultBuilder.build(
            new PageLinkablePurchaseOrdersResponse(),
            content,
            page);
    }

    /**
     * 比较两条供应申请读模型上的申请时刻：空值排后，非空按时间降序，用于同号多行下选取「最新申请」头行。
     *
     * @param left  左操作数，不应为 {@code null}
     * @param right 右操作数，不应为 {@code null}
     * @return 与 {@link java.util.Comparator#compare} 约定的比较结果符号
     */
    private int compareSupplyModelApplyTimeDescNullsLast(SupplyRequestModel left, SupplyRequestModel right) {
        LocalDateTime ta = left.getApplyTime();
        LocalDateTime tb = right.getApplyTime();
        if (ta == null && tb == null) {
            // —— 双空：视为同一时刻，稳定合并到同一排序桶。
            return 0;
        }
        if (ta == null) {
            // —— 左空右非空：左排后（nulls last 语义在降序比较器中的体现）。
            return 1;
        }
        if (tb == null) {
            return -1;
        }
        return tb.compareTo(ta);
    }

    /**
     * 按行主键升序比较两条读模型；空主键排后，用于「首条非空审批时间」等稳定口径。
     *
     * @param left  左操作数，不应为 {@code null}
     * @param right 右操作数，不应为 {@code null}
     * @return 与 {@link java.util.Comparator#compare} 约定的比较结果符号
     */
    private int compareSupplyModelIdAscNullsLast(SupplyRequestModel left, SupplyRequestModel right) {
        Long ida = left.getId();
        Long idb = right.getId();
        if (ida == null && idb == null) {
            return 0;
        }
        if (ida == null) {
            // —— 无主键行排后：审批时间扫描时跳过脏占位行。
            return 1;
        }
        if (idb == null) {
            return -1;
        }
        return ida.compareTo(idb);
    }

    private WarehouseDocumentDetailRowDto toWarehouseDocumentDetailRowDto(WarehouseDocumentDetailQueryModel row) {
        WarehouseDocumentDetailRowDto dto = new WarehouseDocumentDetailRowDto();
        dto.setId(row.getId());
        dto.setBusNo(row.getBusNo());
        dto.setOriginalBusNo(row.getOriginalBusNo());
        dto.setTypeEnum(row.getTypeEnum());
        dto.setStatusEnum(row.getStatusEnum());
        dto.setCategoryEnum(row.getCategoryEnum());
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

    @Override
    public GetInboundReceiptDetailResponse toGetInboundReceiptDetailResponse(
        List<SupplyRequestModel> lines,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        GetInboundReceiptDetailResponse res = new GetInboundReceiptDetailResponse();
        if (lines == null || lines.isEmpty()) {
            return res;
        }
        SupplyRequestModel head = lines.get(0);
        res.setBusNo(head.getBusNo());
        res.setOriginalBusNo(head.getOriginalBusNo());
        res.setTypeEnum(head.getTypeEnum());
        res.setStatusEnum(head.getStatusEnum());
        res.setSupplierId(head.getSupplierId());
        res.setSourceLocationId(head.getSourceLocationId());
        res.setPurposeLocationId(head.getPurposeLocationId());
        res.setSourceTypeEnum(head.getSourceTypeEnum());
        res.setPurposeTypeEnum(head.getPurposeTypeEnum());
        res.setCategoryEnum(head.getCategoryEnum());
        res.setRowVersion(this.resolveInboundReceiptDocumentRowVersion(lines, head));
        List<InboundReceiptDetailLineDto> detailLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            detailLines.add(this.toInboundReceiptDetailLine(line, productByKey));
        }
        res.setLines(detailLines);
        return res;
    }

    private InboundReceiptDetailLineDto toInboundReceiptDetailLine(
        SupplyRequestModel line,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        InboundReceiptDetailLineDto dto = new InboundReceiptDetailLineDto();
        dto.setId(line.getId());
        dto.setItemTable(line.getItemTable());
        dto.setItemId(line.getItemId());
        dto.setItemQuantity(line.getItemQuantity());
        dto.setUnitCode(line.getUnitCode());
        dto.setLotNumber(line.getLotNumber());
        dto.setTraceNo(line.getTraceNo());
        dto.setPrice(line.getPrice());
        dto.setTotalPrice(line.getTotalPrice());
        dto.setOccurrenceTime(line.getOccurrenceTime());
        dto.setStartTime(line.getStartTime());
        dto.setEndTime(line.getEndTime());
        dto.setRemake(line.getRemake());
        if (productByKey != null && line.getItemTable() != null && line.getItemId() != null) {
            // —— 有读模型命中时补齐展示列：无映射则保持 null，由前端降级为「—」或触发重新同步主数据。
            String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
            ProductDefinitionDisplayDto pr = productByKey.get(key);
            if (pr != null) {
                dto.setProductName(pr.getProductName());
                dto.setSpecification(pr.getTotalVolume());
                dto.setManufacturer(pr.getManufacturer());
                dto.setUnitCodeDictText(this.resolveUnitCodeDictText(line.getUnitCode(), pr));
                dto.setProductCatalog(ProductDefinitionCatalogEditorHintsAssembler.fromDisplayDto(pr));
            }
        }
        return dto;
    }

    /**
     * 按行 {@code unit_code} 与读模型各业务单位列对齐，返回对应字典展示名。
     *
     * @param unitCode 明细行持久化单位码
     * @param pr       产品读模型
     * @return 展示名或 {@code null}
     */
    private String resolveUnitCodeDictText(String unitCode, ProductDefinitionDisplayDto pr) {
        return com.openhis.health.application.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler
            .resolveUnitDisplayName(null, unitCode, pr);
    }

    @Override
    public CreateInboundReceiptCommand toCreateInboundReceiptCommand(
        CreateInboundReceiptRequest request,
        Long operatorId,
        Long applicantId) {
        CreateInboundReceiptCommand command = new CreateInboundReceiptCommand();
        command.setOperatorId(operatorId);
        List<CreateInboundReceiptCommand.CreateInboundReceiptItemCommand> items = new ArrayList<>();
        if (request.getLines() != null) {
            // —— 头级枚举/类型码在循环外解析一次：所有明细行共享同一供应类别、位置类型与进货类型（减少重复 EnumUtil 调用）。
            // —— 头级位置类型：由库位 id 推导 SupplyRequestType.code（B.10.8.1），请求体不传类型枚举。——
            String sourceTypeCode = this.resolveSourceTypeEnumForInboundHeader(request.getSourceLocationId());
            String purposeTypeCode = this.resolvePurposeTypeEnumForInboundHeader(request.getPurposeLocationId());
            String categoryCode = this.resolveSupplyCategoryCode(request.getCategoryEnum());
            String typeCode = SupplyType.CABINET_INBOUND_RECEIPT.getCode();
            String original = request.getOriginalBusNo();
            if (original != null) {
                original = original.trim();
                if (original.isEmpty()) {
                    // —— 仅空白字符视为「未引用订货」：落库 original_bus_no 为空，与前端清空输入框语义一致。
                    original = null;
                }
            }
            for (InboundReceiptLineDto line : request.getLines()) {
                if (line == null) {
                    continue;
                }
                CreateInboundReceiptCommand.CreateInboundReceiptItemCommand item =
                    new CreateInboundReceiptCommand.CreateInboundReceiptItemCommand();
                // —— 新建首包：业务单号由领域批量取号写入，装配阶段 item 级 busNo 先保持 null（与修订路径区分）。
                item.setBusNo(null);
                item.setOriginalBusNo(original);
                item.setTypeEnum(typeCode);
                item.setItemTable(line.getItemTable());
                item.setItemId(line.getItemId());
                item.setItemQuantity(line.getItemQuantity());
                item.setUnitCode(line.getUnitCode());
                item.setCategoryEnum(categoryCode);
                item.setSourceTypeEnum(sourceTypeCode);
                item.setSourceLocationId(request.getSourceLocationId());
                item.setPurposeTypeEnum(purposeTypeCode);
                item.setPurposeLocationId(request.getPurposeLocationId());
                item.setSupplierId(request.getSupplierId());
                item.setLotNumber(line.getLotNumber());
                item.setTraceNo(line.getTraceNo());
                item.setPrice(line.getPrice());
                item.setTotalPrice(line.getTotalPrice());
                item.setOccurrenceTime(line.getOccurrenceTime());
                item.setStartTime(line.getStartTime());
                item.setEndTime(line.getEndTime());
                item.setReason(null);
                item.setRemake(line.getRemake());
                item.setApplicantId(applicantId);
                item.setPractitionerId(request.getPractitionerId());
                item.setApplyTime(null);
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

    @Override
    public CreateInboundReceiptCommand toCreateInboundReceiptCommandForUpdate(
        UpdateInboundReceiptRequest request,
        Long operatorId,
        Long applicantId,
        String originalBusNoSnapshot) {
        CreateInboundReceiptCommand command = new CreateInboundReceiptCommand();
        command.setOperatorId(operatorId);
        Long rowVer = request.getRowVersion();
        if (rowVer != null) {
            if (rowVer > Integer.MAX_VALUE || rowVer < Integer.MIN_VALUE) {
                throw new IllegalArgumentException("rowVersion 超出整型范围");
            }
            // —— 领域修订命令携带期望版本：与 JPA @Version 对齐，用于乐观锁冲突检测。
            command.setExpectedRowVersionForRevision(rowVer.intValue());
        }
        List<CreateInboundReceiptCommand.CreateInboundReceiptItemCommand> items = new ArrayList<>();
        if (request.getLines() != null) {
            // —— 修订路径头字段解析：源位置类型可空；目的类型可与新建一致由库位推导（B.10.8）。
            String sourceTypeCode = this.resolveSourceTypeEnumForInboundHeader(request.getSourceLocationId());
            String purposeTypeCode = this.resolvePurposeTypeEnumForInboundHeader(request.getPurposeLocationId());
            String categoryCode = this.resolveSupplyCategoryCode(request.getCategoryEnum());
            String typeCode = SupplyType.CABINET_INBOUND_RECEIPT.getCode();
            String originalRef = originalBusNoSnapshot;
            if (originalRef != null) {
                originalRef = originalRef.trim();
                if (originalRef.isEmpty()) {
                    // —— 与新建路径一致：全空白视作未引用订货，避免向领域写入空字符串。
                    originalRef = null;
                }
            }
            for (InboundReceiptLineDto line : request.getLines()) {
                if (line == null) {
                    continue;
                }
                CreateInboundReceiptCommand.CreateInboundReceiptItemCommand item =
                    new CreateInboundReceiptCommand.CreateInboundReceiptItemCommand();
                item.setBusNo(request.getBusNo());
                item.setOriginalBusNo(originalRef);
                // —— 修订仍走「同号全量替换」写路径：每行须携带业务单号，领域据此覆盖旧明细。
                item.setTypeEnum(typeCode);
                item.setItemTable(line.getItemTable());
                item.setItemId(line.getItemId());
                item.setItemQuantity(line.getItemQuantity());
                item.setUnitCode(line.getUnitCode());
                item.setCategoryEnum(categoryCode);
                item.setSourceTypeEnum(sourceTypeCode);
                item.setSourceLocationId(request.getSourceLocationId());
                item.setPurposeTypeEnum(purposeTypeCode);
                item.setPurposeLocationId(request.getPurposeLocationId());
                item.setSupplierId(request.getSupplierId());
                item.setLotNumber(line.getLotNumber());
                item.setTraceNo(line.getTraceNo());
                item.setPrice(line.getPrice());
                item.setTotalPrice(line.getTotalPrice());
                item.setOccurrenceTime(line.getOccurrenceTime());
                item.setStartTime(line.getStartTime());
                item.setEndTime(line.getEndTime());
                item.setReason(null);
                item.setRemake(line.getRemake());
                item.setApplicantId(applicantId);
                item.setPractitionerId(request.getPractitionerId());
                item.setApplyTime(null);
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

    @Override
    public CreateInboundReceiptResponse toCreateInboundReceiptResponse(String busNo) {
        CreateInboundReceiptResponse res = new CreateInboundReceiptResponse();
        res.setBusNo(busNo);
        res.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());
        return res;
    }

    @Override
    public UpdateInboundReceiptResponse toUpdateInboundReceiptResponse(String busNo) {
        UpdateInboundReceiptResponse res = new UpdateInboundReceiptResponse();
        res.setBusNo(busNo);
        res.setStatusEnum(SupplyStatus.PENDING_APPROVAL.getCode());
        return res;
    }

    @Override
    public SubmitApprovalCommand toSubmitApprovalCommand(SubmitInboundReceiptApprovalRequest request, Long operatorId, Long applicantId) {
        SubmitApprovalCommand command = new SubmitApprovalCommand();
        command.setOperatorId(operatorId);
        SubmitApprovalCommand.SubmitApprovalItemCommand item = new SubmitApprovalCommand.SubmitApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApplicantId(applicantId);
        command.setItem(item);
        return command;
    }

    @Override
    public SubmitInboundReceiptApprovalResponse toSubmitInboundReceiptApprovalResponse(SupplyStatus statusAfter) {
        SubmitInboundReceiptApprovalResponse res = new SubmitInboundReceiptApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        // —— 若回读首行状态失败则不下发 statusEnum，前端应以「重新拉详情」为真源，避免展示陈旧枚举。
        if (statusAfter != null) {
            res.setStatusEnum(statusAfter.getCode());
        }
        return res;
    }

    @Override
    public WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawInboundReceiptApprovalRequest request, Long operatorId) {
        WithdrawApprovalCommand command = new WithdrawApprovalCommand();
        command.setOperatorId(operatorId);
        WithdrawApprovalCommand.WithdrawApprovalItemCommand item = new WithdrawApprovalCommand.WithdrawApprovalItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    @Override
    public WithdrawInboundReceiptApprovalResponse toWithdrawInboundReceiptApprovalResponse(SupplyStatus statusAfter) {
        WithdrawInboundReceiptApprovalResponse res = new WithdrawInboundReceiptApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        // —— 与提交审批响应一致：状态枚举缺失时不强行填默认值。
        if (statusAfter != null) {
            res.setStatusEnum(statusAfter.getCode());
        }
        return res;
    }

    @Override
    public AgreeRequestCommand toAgreeRequestCommand(AgreeInboundReceiptRequest request, Long operatorId) {
        AgreeRequestCommand command = new AgreeRequestCommand();
        command.setOperatorId(operatorId);
        AgreeRequestCommand.AgreeRequestItemCommand item = new AgreeRequestCommand.AgreeRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        command.setItem(item);
        return command;
    }

    @Override
    public AgreeInboundReceiptResponse toAgreeInboundReceiptResponse(boolean ok) {
        AgreeInboundReceiptResponse res = new AgreeInboundReceiptResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    @Override
    public CreateSupplyDeliveryCommand toCreateSupplyDeliveryCommandForInbound(List<SupplyRequestModel> lines, Long operatorId) {
        CreateSupplyDeliveryCommand cmd = new CreateSupplyDeliveryCommand();
        cmd.setOperatorId(operatorId);
        List<CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand> items = new ArrayList<>();
        if (lines != null) {
            // —— 每条发放行绑定供应请求行 id：无持久化主键的行跳过，避免发放落库后无法回溯来源请求。
            for (SupplyRequestModel line : lines) {
                if (line == null || line.getId() == null) {
                    continue;
                }
                CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand item =
                    new CreateSupplyDeliveryCommand.CreateSupplyDeliveryItemCommand();
                item.setRequestId(line.getId());
                item.setTypeEnum(SupplyType.CABINET_INBOUND_RECEIPT.getCode());
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

    @Override
    public CreateInventoryItemCommand toCreateInventoryItemCommand(
        SupplyRequestModel line,
        Long purposeLocationId,
        String resolvedProductName,
        String resolvedLotNumber,
        BigDecimal quantity,
        String unitCode,
        Long operatorId,
        ProductDefinitionDisplayDto productDefinition) {
        return this.toCreateInventoryItemCommand(
            line,
            purposeLocationId,
            resolvedProductName,
            resolvedLotNumber,
            quantity,
            unitCode,
            operatorId,
            productDefinition,
            null);
    }

    @Override
    public CreateInventoryItemCommand toCreateInventoryItemCommand(
        SupplyRequestModel line,
        Long purposeLocationId,
        String resolvedProductName,
        String resolvedLotNumber,
        BigDecimal quantity,
        String unitCode,
        Long operatorId,
        ProductDefinitionDisplayDto productDefinition,
        Long inventoryItemSupplierId) {
        CreateInventoryItemCommand command = new CreateInventoryItemCommand();
        command.setOperatorId(operatorId);
        CreateInventoryItemCommand.CreateInventoryItemItemCommand item =
            new CreateInventoryItemCommand.CreateInventoryItemItemCommand();
        item.setItemTable(line.getItemTable());
        item.setItemId(line.getItemId());
        item.setName(resolvedProductName);
        Long supplierId = line.getSupplierId();
        if (inventoryItemSupplierId != null) {
            supplierId = inventoryItemSupplierId;
        }
        item.setSupplierId(supplierId);
        item.setUnitCode(unitCode);
        item.setQuantity(quantity);
        item.setLotNumber(resolvedLotNumber);
        item.setLocationId(purposeLocationId);
        item.setLocationStoreId(null);
        // —— 效期/生产日期：与供应行 start_time / end_time 对齐，缺省保持 null。——
        item.setProductionDate(line.getStartTime());
        item.setExpirationDate(line.getEndTime());
        // —— 分类与检索码：从已批量加载的产品定义读模型冗余到库存项，便于列表检索与展示。——
        if (productDefinition != null) {
            if (StringUtils.isNotBlank(productDefinition.getCategoryCode())) {
                item.setCategoryCode(productDefinition.getCategoryCode().trim());
            }
            if (StringUtils.isNotBlank(productDefinition.getPyStr())) {
                item.setPyStr(productDefinition.getPyStr().trim());
            }
            if (StringUtils.isNotBlank(productDefinition.getWbStr())) {
                item.setWbStr(productDefinition.getWbStr().trim());
            }
        }
        command.setItem(item);
        return command;
    }

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

    @Override
    public RejectRequestCommand toRejectRequestCommand(RejectInboundReceiptRequest request, Long operatorId) {
        RejectRequestCommand command = new RejectRequestCommand();
        command.setOperatorId(operatorId);
        RejectRequestCommand.RejectRequestItemCommand item = new RejectRequestCommand.RejectRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        item.setChangeReasonCode(request.getRejectReason());
        command.setItem(item);
        return command;
    }

    @Override
    public RejectInboundReceiptResponse toRejectInboundReceiptResponse(boolean ok) {
        RejectInboundReceiptResponse res = new RejectInboundReceiptResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    @Override
    public RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveInboundReceiptRequest request, Long operatorId) {
        RemoveByBusNoCommand command = new RemoveByBusNoCommand();
        command.setOperatorId(operatorId);
        RemoveByBusNoCommand.RemoveByBusNoItemCommand item = new RemoveByBusNoCommand.RemoveByBusNoItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    @Override
    public RemoveInboundReceiptResponse toRemoveInboundReceiptResponse(boolean ok) {
        RemoveInboundReceiptResponse res = new RemoveInboundReceiptResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    @Override
    public ProductDefinitionPageQuery toProductDefinitionPageQuery(PageProductsForInboundReceiptRequest request) {
        ProductDefinitionPageQuery query = new ProductDefinitionPageQuery();
        // —— 以下字段原样透传至读模型分页查询；关键字模糊、机构收窄等在 RMQueryService 内按 B.2.1 / B.10.11 处理。
        query.setProductTableName(request.getProductTableName());
        query.setCategoryCode(request.getCategoryCode());
        query.setActiveFlag(request.getActiveFlag());
        query.setSupplierId(request.getSupplierId());
        query.setManufacturer(request.getManufacturer());
        query.setIncludeInventory(request.getIncludeInventory());
        PageQueryBinder.bind(request, query);
        query.setSortField(request.getSortField());
        query.setSortOrder(request.getSortOrder());
        return query;
    }

    /**
     *
     * <p>使用 {@link PagedResult} 统一构建分页响应，{@code content} 为选品行数据。</p>
     *
     * @param page 产品定义读模型分页
     * @return 选品分页响应
     */
    @Override
    public PageProductsForInboundReceiptResponse toPageProductsForInboundReceiptResponse(Page<ProductDefinitionDisplayDto> page) {
        List<ProductDefinitionItemDto> content = new ArrayList<>();
        for (ProductDefinitionDisplayDto pr : page.getContent()) {
            content.add(this.toProductDefinitionItemDto(pr));
        }
        return PagedResultBuilder.build(
            new PageProductsForInboundReceiptResponse(),
            content,
            page);
    }

    /**
     * {@link com.openhis.health.application.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler#buildInboundSelectionBands}；选品列表 {@code purchasePrice}（实例维 {@code PURCHASE}）在应用层二次写入；{@code availableQuantity} 不装配（进货选品不关联库存）。
     *
     * @param pr 读模型
     * @return DTO
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
        // —— 选品列表 purchasePrice 由应用层解析 PURCHASE；此处不预填读模型 purchase_price。——
        dto.setPurchasePrice(null);
        dto.setManufacturer(pr.getManufacturer());
        dto.setTotalVolume(pr.getTotalVolume());
        dto.setPyStr(pr.getPyStr());
        dto.setWbStr(pr.getWbStr());
        dto.setUnitBands(ProductDefinitionCatalogUnitBandsAssembler.buildInboundSelectionBands(pr));
        dto.setAvailableQuantity(null);
        return dto;
    }

    /**
     * 进货单头「源位置类型」：仅由 {@code sourceLocationId} 推导；无来源库位时为 {@code null}。
     *
     * @param sourceLocationId 请求 {@code sourceLocationId}，可 null
     * @return 持久化 code；无库位时为 {@code null}
     */
    private String resolveSourceTypeEnumForInboundHeader(Long sourceLocationId) {
        if (sourceLocationId == null) {
            return null;
        }
        return this.locationQueryService.requireSupplyRequestTypeForLocationId(sourceLocationId).getCode();
    }

    /**
     * 进货单头「目的位置类型」：仅由 {@code purposeLocationId} 推导。
     *
     * @param purposeLocationId 请求 {@code purposeLocationId}，必填
     * @return 持久化 code
     */
    private String resolvePurposeTypeEnumForInboundHeader(Long purposeLocationId) {
        if (purposeLocationId == null) {
            throw new IllegalArgumentException("purposeLocationId 不能为空");
        }
        return this.locationQueryService.requireSupplyRequestTypeForLocationId(purposeLocationId).getCode();
    }

    /**
     * 供应类别：空或空白时默认库存供应；否则按 {@link SupplyCategory#getCode()} 解析（B.10.8）。
     *
     * @param categoryCodeRaw 请求传入的类别码，可为 null
     * @return 持久化 code
     * @throws IllegalArgumentException 非空白但码不在枚举白名单
     */
    private String resolveSupplyCategoryCode(String categoryCodeRaw) {
        if (StringUtils.isBlank(categoryCodeRaw)) {
            return SupplyCategory.STOCK_SUPPLY.getCode();
        }
        SupplyCategory category = EnumUtil.fromCode(SupplyCategory.class, categoryCodeRaw.trim());
        if (category == null) {
            throw new IllegalArgumentException("categoryEnum 非法");
        }
        return category.getCode();
    }

    private Integer resolveInboundReceiptDocumentRowVersion(List<SupplyRequestModel> lines, SupplyRequestModel head) {
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
