/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.transferin.mapper;

import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.application.inventory.warehouse.transferin.dto.TransferInDetailLineDto;
import com.zxplat.application.inventory.warehouse.transferin.dto.TransferInListRowDto;
import com.zxplat.application.inventory.warehouse.transferin.dto.ProductDefinitionItemDto;
import com.zxplat.application.inventory.warehouse.transferin.response.GetTransferInDetailResponse;
import com.zxplat.application.inventory.warehouse.transferin.response.LoadTransferInFormInitResponse;
import com.zxplat.application.inventory.warehouse.transferin.response.PageTransferInsResponse;
import com.zxplat.application.inventory.warehouse.transferin.response.PageProductsForTransferInResponse;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.SupplyStatus;
import com.zxplat.domain.share.enums.SupplyType;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
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
 * 药库调入单装配实现：{@link InventoryWarehouseTransferInMapper}。
 *
 * <p>各 {@code @Override} 方法 JavaDoc 与接口保持同义完整说明（B.1），禁止仅以单独一行 {@code @inheritDoc} 占位。</p>
 */
@Component
public class InventoryWarehouseTransferInMapperImpl implements InventoryWarehouseTransferInMapper {

    /**
     * 将位置实体列表装配为下拉（IF-WHS-TI-01）。
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
     * 将医务人员实体列表装配为下拉（IF-WHS-TI-01）。
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
     * 装配供应状态筛选项（IF-WHS-TI-01）。
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
     * 装配明细物品表类型两档（IF-WHS-TI-01）。
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
     * 组装药库调入页初始化响应（IF-WHS-TI-01）。
     *
     * @param pharmacyOpts   目的药库
     * @param warehouseOpts  来源药库
     * @param inboundTypes   明细物品表类型项
     * @param statusOpts     单据状态筛选项
     * @param pharmacistOpts 药师
     * @param keeperOpts     库管
     * @return 初始化响应体
     */
    @Override
    public LoadTransferInFormInitResponse toLoadTransferInFormInitResponse(
        List<OptionDto> pharmacyOpts,
        List<OptionDto> warehouseOpts,
        List<OptionDto> inboundTypes,
        List<OptionDto> statusOpts,
        List<OptionDto> pharmacistOpts,
        List<OptionDto> keeperOpts) {
        LoadTransferInFormInitResponse res = new LoadTransferInFormInitResponse();
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
     * 将当前页业务单号顺序与同批明细行装配为药库调入列表聚合行（IF-WHS-TI-02）。
     *
     * @param busNoPageOrder 分页查询得到的单号顺序
     * @param detailRows     与当前页单号集合对应的全部明细行
     * @return 聚合行列表；来源位置展示名由应用层富化
     * @throws BusinessException 同号多行头字段不一致（{@link ErrorCodeConstants#WHS_TI_102}）
     */
    @Override
    public List<TransferInListRowDto> toTransferInListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows) {
        List<TransferInListRowDto> out = new ArrayList<>();
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
                        ErrorCodeConstants.WHS_TI_102, "同号药库调入单状态头字段不一致: " + busNo);
                }
                // 同号多行必须共享同一来源药库（从哪入），否则违背一张调拨单头语义。
                if (!Objects.equals(head.getSourceLocationId(), line.getSourceLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_TI_102, "同号药库调入单来源库房头字段不一致: " + busNo);
                }
                // 同号多行必须共享同一目的药库。
                if (!Objects.equals(head.getPurposeLocationId(), line.getPurposeLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_TI_102, "同号药库调入单目的药库头字段不一致: " + busNo);
                }
            }
            LocalDateTime maxApply = null;
            // —— 列表金额与出库/进货列表同构：同号各行 total_price 之和。——
            BigDecimal totalAmount = BigDecimal.ZERO;
            for (SupplyRequestModel line : lines) {
                if (line == null) {
                    continue;
                }
                if (line.getTotalPrice() != null) {
                    totalAmount = totalAmount.add(line.getTotalPrice());
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
            TransferInListRowDto dto = new TransferInListRowDto();
            dto.setBusNo(busNo);
            dto.setTypeEnum(SupplyType.CABINET_TRANSFER_IN.getCode());
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
     * 装配药库调入分页列表响应（IF-WHS-TI-02；1-based 页码，B.10.11）。
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
    public PageTransferInsResponse toPageTransferInsResponse(long mergedTotal, int pageNo, int pageSize, List<TransferInListRowDto> records) {
        List<TransferInListRowDto> content = records;
        // records 由上层保证语义；null 时回落空表避免 JSON 序列化歧义。
        if (content == null) {
            content = new ArrayList<>();
        }
        return PagedResultBuilder.build(new PageTransferInsResponse(), content, mergedTotal, pageNo, pageSize);
    }

    /**
     * 将同号供应请求行装配为药库调入详情响应（IF-WHS-TI-03）。
     *
     * @param lines         同号多行写模型查询结果
     * @param productHints  行 id → 目录提示；可 null
     * @return 详情响应
     */
    @Override
    public GetTransferInDetailResponse toGetTransferInDetailResponse(
        List<SupplyRequestModel> lines,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints) {
        GetTransferInDetailResponse res = new GetTransferInDetailResponse();
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
        res.setPractitionerId(head.getPractitionerId());
        List<TransferInDetailLineDto> detailLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            TransferInDetailLineDto dto = this.toTransferInDetailLineDto(line);
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
     * 将单行供应请求读模型装配为药库调入详情明细 DTO（IF-WHS-TI-03）。
     *
     * @param line 供应请求行
     * @return 明细 DTO；{@code line} 为 null 时返回 null
     */
    @Override
    public TransferInDetailLineDto toTransferInDetailLineDto(SupplyRequestModel line) {
        if (line == null) {
            // 调用方循环中可跳过 null 行；此处不抛异常以免装配层吞没上游数据问题。
            return null;
        }
        TransferInDetailLineDto dto = new TransferInDetailLineDto();
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
     * 选品分页结果装配（IF-WHS-TI-11；1-based 页码，B.10.11）。
     *
     * <p>分页字段遵循 PagedResult 规范（B.10.11）：content 承载列表数据，totalCount 承载总条数，
     * pageNo/pageSize 由 {@link PagedResultBuilder} 从 Spring Page 推导（B.10.11）。</p>
     *
     * @param page 库存分页
     * @return 选品分页响应
     */
    @Override
    public PageProductsForTransferInResponse toPageProductsForTransferInResponse(Page<InventoryItemModel> page) {
        List<ProductDefinitionItemDto> content = new ArrayList<>();
        for (InventoryItemModel inv : page.getContent()) {
            if (inv == null) {
                continue;
            }
            // 选品行先落库存主数据；四槽与采购价由 AppService 富化。
            content.add(this.toProductDefinitionItemDtoFromInventory(inv, null));
        }
        return PagedResultBuilder.build(new PageProductsForTransferInResponse(), content, page);
    }

    /**
     * 库存行与可选产品读模型装配为药库调入选品行（IF-WHS-TI-11）。
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
}