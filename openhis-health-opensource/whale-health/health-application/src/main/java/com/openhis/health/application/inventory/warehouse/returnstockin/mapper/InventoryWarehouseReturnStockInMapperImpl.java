/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returnstockin.mapper;

import com.openhis.health.application.inventory.warehouse.returnstockin.dto.ReturnStockInDetailLineDto;
import com.openhis.health.application.inventory.warehouse.returnstockin.dto.ReturnStockInListRowDto;
import com.openhis.health.application.inventory.warehouse.returnstockin.dto.SupplierOptionDto;
import com.openhis.health.application.inventory.warehouse.returnstockin.dto.WarehouseOptionDto;
import com.openhis.health.application.inventory.warehouse.returnstockin.response.GetReturnStockInDetailResponse;
import com.openhis.health.application.inventory.warehouse.returnstockin.response.LoadReturnStockInFormInitResponse;
import com.openhis.health.application.inventory.warehouse.returnstockin.response.PageReturnStockInsResponse;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.SupplierType;
import com.openhis.health.domain.share.enums.SupplyCategory;
import com.openhis.health.domain.share.enums.SupplyRequestType;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 退库入库单读路径装配实现：{@link InventoryWarehouseReturnStockInMapper}。
 *
 * <p>仅覆盖页初始化、按单号聚合列表与详情；写/选品/选单装配已删除（AppService 写接口以 {@code WHS_RSI_006} 拒绝）。</p>
 */
@Component
public class InventoryWarehouseReturnStockInMapperImpl implements InventoryWarehouseReturnStockInMapper {

    /**
     * 将启用位置列表映射为库房/科室/药房下拉选项 DTO。
     *
     * @param locations 启用库位列表，可为 null
     * @return 位置下拉选项；{@code null} 输入返回空列表
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
     * @param suppliers 供应商列表项，可为 null
     * @return 供应商下拉选项；{@code null} 输入返回空列表
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
     * 装配退库入库页初始化响应（选项集合写入各字段）。
     *
     * @param warehouseOptions  药库选项
     * @param departmentOptions 科室选项
     * @param pharmacyOptions   药房选项
     * @param supplierOptions   供应商选项
     * @return 页初始化响应
     */
    @Override
    public LoadReturnStockInFormInitResponse toLoadReturnStockInFormInitResponse(
        List<WarehouseOptionDto> warehouseOptions,
        List<WarehouseOptionDto> departmentOptions,
        List<WarehouseOptionDto> pharmacyOptions,
        List<SupplierOptionDto> supplierOptions) {
        LoadReturnStockInFormInitResponse res = new LoadReturnStockInFormInitResponse();
        res.setWarehouseOptions(warehouseOptions);
        res.setDepartmentOptions(departmentOptions);
        res.setPharmacyOptions(pharmacyOptions);
        res.setSupplierOptions(supplierOptions);
        return res;
    }

    /**
     * 按 busNo 顺序聚合退库入库供应请求明细为列表行 DTO。
     *
     * <p>同号头字段（状态、来源退库单、源/目的位置）不一致时抛 {@code WHS_RSI_102}；金额与申请/审批时间取行聚合。</p>
     *
     * @param busNoPageOrder 分页单号顺序
     * @param detailRows     同页全部明细
     * @return 聚合列表行
     */
    @Override
    public List<ReturnStockInListRowDto> toReturnStockInListAggregated(
        List<String> busNoPageOrder,
        List<SupplyRequestModel> detailRows) {
        List<ReturnStockInListRowDto> out = new ArrayList<>();
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
                        ErrorCodeConstants.WHS_RSI_102, "同号退库入库壳单状态头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getOriginalBusNo(), line.getOriginalBusNo())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_RSI_102, "同号退库入库壳单来源退库单不一致: " + busNo);
                }
                if (!Objects.equals(head.getSourceLocationId(), line.getSourceLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_RSI_102, "同号退库入库壳单源位置头字段不一致: " + busNo);
                }
                if (!Objects.equals(head.getPurposeLocationId(), line.getPurposeLocationId())) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_RSI_102, "同号退库入库壳单目的药库头字段不一致: " + busNo);
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
            ReturnStockInListRowDto dto = new ReturnStockInListRowDto();
            dto.setBusNo(busNo);
            dto.setOriginalBusNo(head.getOriginalBusNo());
            dto.setTypeEnum(this.toApiSortSupplyType(head.getTypeEnum()));
            dto.setStatusEnum(this.toApiSortSupplyStatus(head.getStatusEnum()));
            dto.setCategoryEnum(this.toApiSortSupplyCategory(head.getCategoryEnum()));
            dto.setSourceLocationId(head.getSourceLocationId());
            dto.setPurposeLocationId(head.getPurposeLocationId());
            dto.setSupplierId(head.getSupplierId());
            dto.setApplicantId(head.getApplicantId());
            dto.setApproverId(head.getApproverId());
            dto.setTotalAmount(totalAmount);
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
     * 用聚合列表行构建退库入库单分页响应。
     *
     * @param mergedTotal 去重后总单数
     * @param pageNo      页码
     * @param pageSize    页大小
     * @param records     列表行
     * @return 分页响应
     */
    @Override
    public PageReturnStockInsResponse toPageReturnStockInsAggregatedResponse(
        long mergedTotal,
        int pageNo,
        int pageSize,
        List<ReturnStockInListRowDto> records) {
        List<ReturnStockInListRowDto> content;
        if (records == null) {
            content = new ArrayList<>();
        } else {
            content = records;
        }
        return PagedResultBuilder.build(new PageReturnStockInsResponse(), content, mergedTotal, pageNo, pageSize);
    }

    /**
     * 将供应请求行列表映射为退库入库详情响应（含产品展示与计量带提示）。
     *
     * @param lines         同 busNo 供应请求行
     * @param productByKey  产品读模型映射（compositeKey → 展示 DTO）
     * @param productHints  行 id → 目录编辑提示（unitBands）
     * @return 单头与明细；空列表时返回空 lines
     */
    @Override
    public GetReturnStockInDetailResponse toGetReturnStockInDetailResponse(
        List<SupplyRequestModel> lines,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints) {
        GetReturnStockInDetailResponse res = new GetReturnStockInDetailResponse();
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
        List<ReturnStockInDetailLineDto> detailLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            detailLines.add(this.toDetailLine(line, productByKey, productHints));
        }
        res.setLines(detailLines);
        return res;
    }

    /**
     * 供应请求明细 → 退库入库详情行 DTO（品名 + 单位展示名 + productCatalog）。
     *
     * @param line         供应请求明细
     * @param productByKey 产品复合键 → 展示 DTO
     * @param productHints 行 id → 目录编辑提示
     * @return 详情行 DTO
     */
    private ReturnStockInDetailLineDto toDetailLine(
        SupplyRequestModel line,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints) {
        ReturnStockInDetailLineDto dto = new ReturnStockInDetailLineDto();
        dto.setId(line.getId());
        dto.setItemTable(line.getItemTable());
        dto.setItemId(line.getItemId());
        dto.setItemQuantity(line.getItemQuantity());
        dto.setUnitCode(line.getUnitCode());
        dto.setLotNumber(line.getLotNumber());
        dto.setStartTime(line.getStartTime());
        dto.setEndTime(line.getEndTime());
        dto.setTraceNo(line.getTraceNo());
        dto.setOccurrenceTime(line.getOccurrenceTime());
        dto.setReason(line.getReason());
        dto.setRemake(line.getRemake());
        if (productByKey != null && line.getItemTable() != null && line.getItemId() != null) {
            String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
            ProductDefinitionDisplayDto pr = productByKey.get(key);
            if (pr != null) {
                dto.setProductName(pr.getProductName());
                dto.setUnitCodeDictText(
                    ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                        null, line.getUnitCode(), pr));
            }
        }
        if (productHints != null && line.getId() != null) {
            ProductDefinitionCatalogEditorHintsDto hint = productHints.get(line.getId());
            if (hint != null) {
                dto.setProductCatalog(hint);
            }
        }
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
}
