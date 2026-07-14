/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper;

import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptDetailLineDto;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptLineDto;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptListRowDto;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.ProductDefinitionItemDto;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogEditorHintsAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.WarehouseDocumentDetailRowDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderListRowDto;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.request.*;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.response.*;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.application.shared.product.ProductDefinitionPageQuery;
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
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
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
import java.util.HashMap;
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
@RequiredArgsConstructor
public class InventoryWarehouseInboundReceiptMapperImpl implements InventoryWarehouseInboundReceiptMapper {

    private final LocationQueryService locationQueryService;

    /**
     * 将启用药库/药房位置列表映射为下拉选项。
     *
     * @param locations 启用库位列表
     * @return 位置下拉选项
     */
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
            if (StringUtils.isEmpty(label)) {
                label = String.valueOf(id);
            }
            list.add(new OptionDto(id, String.valueOf(id), label));
        }
        return list;
    }

    /**
     * 将启用供应商列表映射为下拉选项。
     *
     * @param suppliers 供应商列表项
     * @return 供应商下拉选项
     */
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
            if (StringUtils.isEmpty(baseName)) {
                baseName = String.valueOf(id);
            }
            String label = baseName;
            if (StringUtils.isNotEmpty(typeLabel)) {
                label = baseName + "（" + typeLabel + "）";
            }
            list.add(new OptionDto(id, String.valueOf(id), label));
        }
        return list;
    }

    /**
     * 装配供应单据状态下拉选项。
     *
     * @return 单据状态选项
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
     * 装配入库项目类型两档固定下拉选项。
     *
     * @return 药品/耗材类型选项
     */
    @Override
    public List<OptionDto> toOptionDtoListInboundItemTableTypes() {
        List<OptionDto> list = new ArrayList<>();
        list.add(new OptionDto(1L, TableNameConstants.MED_MEDICATION_DEFINITION, "药品"));
        list.add(new OptionDto(2L, TableNameConstants.ADM_DEVICE_DEFINITION, "耗材"));
        return list;
    }

    /**
     * 将启用人员列表映射为下拉选项。
     *
     * @param practitioners 人员列表
     * @return 人员下拉选项
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
     * 装配进货页初始化响应。
     *
     * @param pharmacyLocationOptions     药房位置选项
     * @param warehouseLocationOptions    药库位置选项
     * @param supplierOptions             供应商选项
     * @param pharmacistOptions           药师选项
     * @param warehouseKeeperOptions      库管选项
     * @param inboundItemTableTypeOptions 入库项目类型选项
     * @param statusOptions               状态选项
     * @return 页初始化响应
     */
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

    /**
     * 供应商/人员展示名由 {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.service.InventoryWarehouseInboundReceiptAppService}
     * 在返回前批量富化（B.2 分层）。
     *
     * @param busNoPageOrder 当前页单号顺序（与领域 {@code GROUP BY bus_no} 分页一致）
     * @param detailRows     当前页涉及的全部明细行
     * @return 聚合行列表
     * @throws com.openhis.whale.core.application.exception.BusinessException 同号多行在供应商或状态等「头字段」上不一致，
     *                                   装配层直接失败（常见错误码 {@link com.openhis.health.domain.share.constants.ErrorCodeConstants#WHS_IN_102}）
     */
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
            // 驳回态：把供应行 reason 带到列表 rejectReason，供侧栏卡片展示
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
     * 装配进货列表分页响应（pageNo 为 1-based）。
     *
     * @param mergedTotal 去重后的总单数
     * @param pageNo      页码（1-based）
     * @param pageSize    页大小
     * @param records     当前页聚合行
     * @return 分页响应
     */
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
     * 将订货单聚合列表行映射为可关联选单响应行（一行一单）。
     *
     * @param aggregatedRows 订货单聚合行
     * @param detailLines    同页供应请求明细
     * @return 可关联订货单列表行
     */
    @Override
    public List<WarehouseDocumentDetailRowDto> toLinkablePurchaseOrderRows(
        List<PurchaseOrderListRowDto> aggregatedRows,
        List<SupplyRequestModel> detailLines) {
        List<WarehouseDocumentDetailRowDto> out = new ArrayList<>();
        if (aggregatedRows == null || aggregatedRows.isEmpty()) {
            return out;
        }
        Map<String, SupplyRequestModel> headByBusNo = this.buildHeadLineByBusNo(detailLines);
        for (PurchaseOrderListRowDto row : aggregatedRows) {
            if (row == null || StringUtils.isEmpty(row.getBusNo())) {
                continue;
            }
            String busNo = row.getBusNo().trim();
            WarehouseDocumentDetailRowDto dto = new WarehouseDocumentDetailRowDto();
            dto.setBusNo(busNo);
            dto.setPurposeLocationId(row.getPurposeLocationId());
            dto.setApplyTime(row.getApplyTime());
            dto.setApprovalTime(row.getApprovalTime());
            dto.setTypeEnum(SupplyType.CABINET_PURCHASE_ORDER.getCode());
            dto.setStatusEnum(row.getStatusEnum());
            dto.setSupplierName(row.getSupplierName());
            dto.setCreatorName(row.getCreatorName());
            dto.setPurposeLocationName(row.getPurposeLocationName());
            dto.setTotalAmount(row.getTotalAmount());
            SupplyRequestModel head = headByBusNo.get(busNo);
            if (head != null) {
                dto.setSupplierId(head.getSupplierId());
                dto.setApplicantId(head.getApplicantId());
            }
            out.add(dto);
        }
        return out;
    }

    /**
     * 用按 {@code busNo} 去重后的聚合行构建可关联订货单分页响应（B.10.11）。
     *
     * @param mergedTotal 不重复 busNo 总数
     * @param pageNo      1-based 页码
     * @param pageSize    页大小
     * @param records     当前页聚合行
     * @return 可关联订货单分页响应
     */
    @Override
    public PageLinkablePurchaseOrdersResponse toPageLinkablePurchaseOrdersAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<WarehouseDocumentDetailRowDto> records) {
        List<WarehouseDocumentDetailRowDto> content;
        if (records == null) {
            content = new ArrayList<>();
        } else {
            content = records;
        }
        return PagedResultBuilder.build(
            new PageLinkablePurchaseOrdersResponse(),
            content,
            mergedTotal,
            pageNo,
            pageSize);
    }

    /**
     * 按 busNo 选取表头行（申请时刻最新者优先），供列表供应商 id 回填。
     *
     * @param detailLines 同页供应请求明细
     * @return busNo → 表头行
     */
    private Map<String, SupplyRequestModel> buildHeadLineByBusNo(List<SupplyRequestModel> detailLines) {
        Map<String, SupplyRequestModel> best = new HashMap<>();
        if (detailLines == null) {
            return best;
        }
        for (SupplyRequestModel line : detailLines) {
            if (line == null || StringUtils.isEmpty(line.getBusNo())) {
                continue;
            }
            String bn = line.getBusNo().trim();
            SupplyRequestModel cur = best.get(bn);
            if (cur == null) {
                best.put(bn, line);
                continue;
            }
            LocalDateTime ta = line.getApplyTime();
            LocalDateTime tb = cur.getApplyTime();
            // —— 同单多行时取申请时刻较新者作表头，避免供应商/经手人错行。——
            if (ta != null && (tb == null || ta.isAfter(tb))) {
                best.put(bn, line);
            }
        }
        return best;
    }

    /**
     * 将已同意订货单写模型行映射为进货新建预填响应（DM §6.1.2 拷贝语义：批号/追溯码留空）。
     *
     * @param purchaseOrderLines 订货单供应请求行，非空
     * @param productByKey       产品读模型（itemTable+itemId 复合键；可空 map）
     * @return 进货表单预填 DTO
     */
    @Override
    public GetLinkablePurchaseOrderDetailResponse toGetLinkablePurchaseOrderDetailResponse(
        List<SupplyRequestModel> purchaseOrderLines,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        GetLinkablePurchaseOrderDetailResponse res = new GetLinkablePurchaseOrderDetailResponse();
        if (purchaseOrderLines == null || purchaseOrderLines.isEmpty()) {
            return res;
        }
        SupplyRequestModel head = purchaseOrderLines.get(0);
        String busNo = head.getBusNo();
        res.setOriginalBusNo(busNo);
        res.setPurchaseOrderBusNo(busNo);
        Long purposeLocationId = head.getPurposeLocationId();
        res.setPurposeLocationId(purposeLocationId);
        // —— 药库进货默认源/目的同为订货目的库，用户可在保存前调整。——
        res.setSourceLocationId(purposeLocationId);
        res.setSupplierId(head.getSupplierId());
        res.setCategoryEnum(head.getCategoryEnum());
        res.setPractitionerId(head.getPractitionerId());
        Map<String, ProductDefinitionDisplayDto> safeProducts = productByKey;
        if (safeProducts == null) {
            safeProducts = new LinkedHashMap<>();
        }
        List<InboundReceiptDetailLineDto> lines = new ArrayList<>();
        for (SupplyRequestModel line : purchaseOrderLines) {
            if (line == null) {
                continue;
            }
            lines.add(this.toInboundReceiptDetailLine(line, safeProducts));
        }
        res.setLines(lines);
        return res;
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

    /**
     * 库房单据明细读模型 → 工作站行 DTO（头/行共用字段平铺，不含产品 enrich）。
     *
     * @param row 领域明细查询行
     * @return 前端明细行 DTO
     */
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
        dto.setApplicantId(row.getApplicantId());
        return dto;
    }

    /**
     * 将同号供应请求明细与产品读模型装配为进货详情响应（
     * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.response.GetInboundReceiptDetailResponse}）。
     *
     * <p>头字段取自 {@code lines} 的首行（与领域同号多行共享头语义）；{@link com.openhis.health.application.inventory.warehouse.inboundreceipt.response.GetInboundReceiptDetailResponse#getRowVersion()}
     * 由 {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapperImpl}
     * 内 {@code resolveInboundReceiptDocumentRowVersion} 解析。明细行通过 {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptDetailLineDto}
     * 下发；产品展示字段仅在 {@code productByKey} 命中复合键时填充。</p>
     *
     * @param lines         同号多行写模型查询结果；{@code null} 或空时返回空壳响应（各字段默认 null/空列表）
     * @param productByKey  产品复合键（{@code itemTable + itemId}，与 {@link com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil#buildCompositeKey} 一致）到展示 DTO 的映射；
     *                      可 {@code null}，则不下发 {@code productName}/{@code specification}/{@code manufacturer}
     * @return 详情响应；头/行上 {@code *Enum} 类字段均为持久化 {@code getCode()} 字符串（B.10.8.1）
     */
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

    /**
     * 单条供应请求行 → 进货详情明细 DTO；产品展示字段按复合键命中 {@code productByKey}。
     *
     * @param line         供应请求写模型行
     * @param productByKey 产品复合键 → 展示 DTO；可 null
     * @return 详情明细行
     */
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
        return com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler
            .resolveUnitDisplayName(null, unitCode, pr);
    }

    /**
     * 将新建进货请求装配为领域 CreateInboundReceiptCommand。
     *
     * <p>供应头 {@code source_type_enum}/{@code purpose_type_enum} 由装配层按 {@code sourceLocationId}/{@code purposeLocationId} 与
     * {@code adm_location.form_enum} 推导为 {@link com.openhis.health.domain.share.enums.SupplyRequestType#getCode()}，请求体可不传；
     * {@code categoryEnum} 为 {@link com.openhis.health.domain.share.enums.SupplyCategory#getCode()} 字符串，做 {@code trim} 与
     * {@link EnumUtil} 校验或默认解析。</p>
     *
     * @param request     创建请求（{@code lines} 由应用层先行非空校验）
     * @param operatorId  操作人主键
     * @param applicantId 申请人主键（进货场景通常与操作人一致）
     * @return 领域创建命令；各 item 的 {@code typeEnum} 固定为药库进货类型 code；草稿阶段各 item 的 {@code applyTime} 固定为 null（申请时间在提交审批时由域层写入）
     * @throws IllegalArgumentException 显式非法枚举、或 {@code categoryEnum} 非空但非法（经 {@link EnumUtil}）；位置类型无法由库位推导时
     */
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

    /**
     * 将修订进货请求装配为领域 CreateInboundReceiptCommand（同号全量对齐）。
     *
     * <p>枚举字段解析规则同 {@link #toCreateInboundReceiptCommand}；另将 {@link UpdateInboundReceiptRequest#getRowVersion()}
     * 写入命令期望版本（须在 {@code int} 范围内）。</p>
     *
     * @param request               修订请求（含乐观锁版本）
     * @param operatorId            操作人主键
     * @param applicantId           申请人主键
     * @param originalBusNoSnapshot 修订前首行携带的来源订货单号快照
     * @return 领域创建命令（修订语义与新建共用写路径）；草稿阶段各 item 的 {@code applyTime} 为 null
     * @throws IllegalArgumentException code 非法或 {@code rowVersion} 超出 {@code int} 可表示范围
     */
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

    /**
     * 装配新建进货成功响应。
     *
     * @param busNo 新建业务单号
     * @return 新建响应
     */
    @Override
    public CreateInboundReceiptResponse toCreateInboundReceiptResponse(String busNo) {
        CreateInboundReceiptResponse res = new CreateInboundReceiptResponse();
        res.setBusNo(busNo);
        return res;
    }

    /**
     * 装配修订进货成功响应。
     *
     * @param busNo 业务单号
     * @return 修订响应
     */
    @Override
    public UpdateInboundReceiptResponse toUpdateInboundReceiptResponse(String busNo) {
        UpdateInboundReceiptResponse res = new UpdateInboundReceiptResponse();
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
    public SubmitApprovalCommand toSubmitApprovalCommand(SubmitInboundReceiptApprovalRequest request, Long operatorId, Long applicantId) {
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
    public SubmitInboundReceiptApprovalResponse toSubmitInboundReceiptApprovalResponse(SupplyStatus statusAfter) {
        SubmitInboundReceiptApprovalResponse res = new SubmitInboundReceiptApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        // —— 若回读首行状态失败则不下发 statusEnum，前端应以「重新拉详情」为真源，避免展示陈旧枚举。
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
    public WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawInboundReceiptApprovalRequest request, Long operatorId) {
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
    public WithdrawInboundReceiptApprovalResponse toWithdrawInboundReceiptApprovalResponse(SupplyStatus statusAfter) {
        WithdrawInboundReceiptApprovalResponse res = new WithdrawInboundReceiptApprovalResponse();
        res.setSuccess(Boolean.TRUE);
        // —— 与提交审批响应一致：状态枚举缺失时不强行填默认值。
        if (statusAfter != null) {
            res.setStatusEnum(statusAfter.getCode());
        }
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
    public AgreeRequestCommand toAgreeRequestCommand(AgreeInboundReceiptRequest request, Long operatorId) {
        AgreeRequestCommand command = new AgreeRequestCommand();
        command.setOperatorId(operatorId);
        AgreeRequestCommand.AgreeRequestItemCommand item = new AgreeRequestCommand.AgreeRequestItemCommand();
        item.setBusNo(request.getBusNo());
        item.setApproverId(operatorId);
        command.setItem(item);
        return command;
    }

    /**
     * 装配同意进货成功响应。
     *
     * @param ok 是否同意成功
     * @return 同意响应
     */
    @Override
    public AgreeInboundReceiptResponse toAgreeInboundReceiptResponse(boolean ok) {
        AgreeInboundReceiptResponse res = new AgreeInboundReceiptResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /**
     * 将已同意明细行装配为进货发放命令。
     *
     * @param lines      已同意的供应请求明细行
     * @param operatorId 操作人
     * @return 发放命令
     */
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

    /**
     * 将同意行与产品读模型装配为新建库存项命令。
     *
     * @param line               供应请求明细行
     * @param purposeLocationId  目的库位
     * @param resolvedProductName 解析后的产品名称
     * @param resolvedLotNumber  解析后的批号
     * @param quantity           数量
     * @param unitCode           单位 code
     * @param operatorId         操作人
     * @param productDefinition  产品读模型
     * @return 新建库存项命令
     */
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

    /**
     * 同单参数 {@link #toCreateInventoryItemCommand(SupplyRequestModel, Long, String, String, BigDecimal, String, Long, ProductDefinitionDisplayDto)}，
     * 显式指定新建库存项 {@code supplier_id}（出库同意链目的库首次建账从源库行承袭时使用；{@code null} 时回落为行上供应商）。
     *
     * @param line                    供应请求明细行
     * @param purposeLocationId       目的库位
     * @param resolvedProductName     解析后的产品名称
     * @param resolvedLotNumber       解析后的批号
     * @param quantity                数量
     * @param unitCode                单位 code
     * @param operatorId              操作人
     * @param productDefinition       产品读模型
     * @param inventoryItemSupplierId 新建库存项供应商主键；{@code null} 时回落为行上供应商
     * @return 新建库存项命令
     */
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
            if (StringUtils.isNotEmpty(productDefinition.getCategoryCode())) {
                item.setCategoryCode(productDefinition.getCategoryCode().trim());
            }
            if (StringUtils.isNotEmpty(productDefinition.getPyStr())) {
                item.setPyStr(productDefinition.getPyStr().trim());
            }
            if (StringUtils.isNotEmpty(productDefinition.getWbStr())) {
                item.setWbStr(productDefinition.getWbStr().trim());
            }
        }
        command.setItem(item);
        return command;
    }

    /**
     * 装配增库存命令（目的库已有库存项时使用）。
     *
     * @param inventoryItemId 库存项主键
     * @param increaseQty     增加数量
     * @param operatorId      操作人
     * @return 增库存命令
     */
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

    /**
     * 将驳回请求装配为领域 RejectRequestCommand。
     *
     * @param request    驳回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
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

    /**
     * 装配驳回进货成功响应。
     *
     * @param ok 是否驳回成功
     * @return 驳回响应
     */
    @Override
    public RejectInboundReceiptResponse toRejectInboundReceiptResponse(boolean ok) {
        RejectInboundReceiptResponse res = new RejectInboundReceiptResponse();
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
    public RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveInboundReceiptRequest request, Long operatorId) {
        RemoveByBusNoCommand command = new RemoveByBusNoCommand();
        command.setOperatorId(operatorId);
        RemoveByBusNoCommand.RemoveByBusNoItemCommand item = new RemoveByBusNoCommand.RemoveByBusNoItemCommand();
        item.setBusNo(request.getBusNo());
        command.setItem(item);
        return command;
    }

    /**
     * 装配删除进货成功响应。
     *
     * @param ok 是否删除成功
     * @return 删除响应
     */
    @Override
    public RemoveInboundReceiptResponse toRemoveInboundReceiptResponse(boolean ok) {
        RemoveInboundReceiptResponse res = new RemoveInboundReceiptResponse();
        res.setSuccess(Boolean.valueOf(ok));
        return res;
    }

    /**
     * 将选品分页请求装配为产品定义分页查询。
     *
     * @param request 选品分页请求
     * @return 领域分页查询
     */
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
     * {@link com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler#buildInboundSelectionBands}；选品列表 {@code purchasePrice}（实例维 {@code PURCHASE}）在应用层二次写入；{@code availableQuantity} 不装配（进货选品不关联库存）。
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
     * 进货单乐观锁 rowVersion：首行 version 优先，否则扫描同号任一行，均无则 0。
     *
     * @param lines 同 busNo 多行
     * @param head  头行（通常 lines[0]）
     * @return 前端提交用的 rowVersion
     */
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