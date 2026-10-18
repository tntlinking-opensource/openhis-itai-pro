/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.documentdetail.support;

import com.zxplat.application.inventory.documentdetail.dto.DocumentDetailRowDto;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.custom.supplier.service.SupplierQueryService;
import com.zxplat.domain.share.enums.SupplyStatus;
import com.zxplat.domain.share.enums.SupplyType;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel;
import com.zxplat.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单据详情行 enrich：头字段摊平、主数据展示名、盘点/损益拆零源字段（不读现库存 enrich 账面）。
 */
@Component
@RequiredArgsConstructor
public class DocumentDetailEnricher {

    /** 库位 init 下拉与行 enrich。 */
    private final LocationQueryService locationQueryService;
    /** 领用科室 Organization 展示名 enrich。 */
    private final OrganizationQueryService organizationQueryService;
    /** 供应商展示名 enrich。 */
    private final SupplierQueryService supplierQueryService;
    /** 制单/审批/经手人展示名 enrich。 */
    private final PractitionerQueryService practitionerQueryService;
    /** 产品定义批量加载（展示 + 单位槽）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 批量预加载 enrich 上下文（库位/供应商/人员/产品）。
     *
     * @param models 领域读模型行
     * @return 上下文
     */
    public DocumentDetailEnrichmentContext buildContext(List<WarehouseDocumentDetailQueryModel> models) {
        DocumentDetailEnrichmentContext context = new DocumentDetailEnrichmentContext();
        if (models == null || models.isEmpty()) {
            context.setProductByKey(new HashMap<>());
            return context;
        }
        Set<Long> locationIds = new HashSet<>();
        Set<Long> supplierIds = new HashSet<>();
        Set<Long> practitionerIds = new HashSet<>();
        Set<Long> consumingOrganizationIds = new HashSet<>();
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (WarehouseDocumentDetailQueryModel model : models) {
            if (model == null) {
                continue;
            }
            if (model.getSourceLocationId() != null) {
                locationIds.add(model.getSourceLocationId());
            }
            if (model.getPurposeLocationId() != null) {
                locationIds.add(model.getPurposeLocationId());
            }
            // 落库领用科室优先收集；历史空列后续用药房上级科室回落
            if (model.getConsumingOrganizationId() != null) {
                consumingOrganizationIds.add(model.getConsumingOrganizationId());
            }
            if (model.getSupplierId() != null) {
                supplierIds.add(model.getSupplierId());
            }
            if (model.getApplicantId() != null) {
                practitionerIds.add(model.getApplicantId());
            }
            if (model.getApproverId() != null) {
                practitionerIds.add(model.getApproverId());
            }
            if (model.getPractitionerId() != null) {
                practitionerIds.add(model.getPractitionerId());
            }
            if (StringUtils.isNotEmpty(model.getItemTable()) && model.getItemId() != null) {
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(model.getItemTable().trim());
                lookup.setProductDefinitionId(model.getItemId());
                lookups.add(lookup);
            }
        }
        Map<Long, String> locationNameById = new HashMap<>();
        Map<Long, Long> organizationIdByLocationId = new HashMap<>();
        for (Long locationId : locationIds) {
            this.locationQueryService.findOptionalById(locationId).ifPresent(loc -> {
                if (loc.getName() != null) {
                    locationNameById.put(locationId, loc.getName().trim());
                }
                // 历史领用单列空时，用科室药房上级科室回落领用科室
                if (loc.getOrganizationId() != null) {
                    organizationIdByLocationId.put(locationId, loc.getOrganizationId());
                    consumingOrganizationIds.add(loc.getOrganizationId());
                }
            });
        }
        Map<Long, String> supplierNameById = new HashMap<>();
        for (Long supplierId : supplierIds) {
            this.supplierQueryService.findSupplierNameIfPresent(supplierId)
                .ifPresent(name -> supplierNameById.put(supplierId, name.trim()));
        }
        Map<Long, String> practitionerNameById = new HashMap<>();
        if (!practitionerIds.isEmpty()) {
            List<Practitioner> practitioners = this.practitionerQueryService.findAllByIds(practitionerIds);
            for (Practitioner practitioner : practitioners) {
                if (practitioner == null || practitioner.getId() == null || practitioner.getName() == null) {
                    continue;
                }
                practitionerNameById.put(practitioner.getId(), practitioner.getName().trim());
            }
        }
        Map<Long, String> organizationNameById = this.organizationQueryService.mapNamesByIds(consumingOrganizationIds);
        Map<String, ProductDefinitionDisplayDto> productByKey = new HashMap<>();
        if (!lookups.isEmpty()) {
            productByKey = this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        }
        context.setLocationNameById(locationNameById);
        context.setOrganizationIdByLocationId(organizationIdByLocationId);
        context.setOrganizationNameById(organizationNameById);
        context.setSupplierNameById(supplierNameById);
        context.setPractitionerNameById(practitionerNameById);
        context.setProductByKey(productByKey);
        return context;
    }

    /**
     * 将读模型行映射为 DTO 并摊平头字段（数量为拆零源，换算由 {@link DocumentDetailQuantityConversionHelper} 完成）。
     *
     * @param models  读模型
     * @param context enrich 上下文
     * @return 行 DTO 列表
     */
    public List<DocumentDetailRowDto> toRows(
        List<WarehouseDocumentDetailQueryModel> models,
        DocumentDetailEnrichmentContext context
    ) {
        List<DocumentDetailRowDto> rows = new ArrayList<>();
        if (models == null || models.isEmpty()) {
            return rows;
        }
        Map<Long, String> locationNameById = context.getLocationNameById();
        Map<Long, Long> organizationIdByLocationId = context.getOrganizationIdByLocationId();
        Map<Long, String> organizationNameById = context.getOrganizationNameById();
        Map<Long, String> supplierNameById = context.getSupplierNameById();
        Map<Long, String> practitionerNameById = context.getPractitionerNameById();
        Map<String, ProductDefinitionDisplayDto> productByKey = context.getProductByKey();
        for (WarehouseDocumentDetailQueryModel model : models) {
            if (model == null) {
                continue;
            }
            DocumentDetailRowDto row = new DocumentDetailRowDto();
            row.setBusNo(model.getBusNo());
            row.setOriginalBusNo(model.getOriginalBusNo());
            row.setTypeEnum(model.getTypeEnum());
            row.setTypeEnumText(this.resolveTypeEnumText(model.getTypeEnum()));
            // 回填真实状态（期初可含审核中，禁止写死 AGREE）
            row.setStatusEnum(model.getStatusEnum());
            row.setStatusEnumText(this.resolveStatusEnumText(model.getStatusEnum()));
            row.setSourceLocationId(model.getSourceLocationId());
            if (model.getSourceLocationId() != null && locationNameById != null) {
                row.setSourceLocationName(locationNameById.get(model.getSourceLocationId()));
            }
            row.setPurposeLocationId(model.getPurposeLocationId());
            if (model.getPurposeLocationId() != null && locationNameById != null) {
                row.setPurposeLocationName(locationNameById.get(model.getPurposeLocationId()));
            }
            // 领用/退领：优先落库 consumingOrganizationId；历史空列回落药房上级科室
            Long consumingOrganizationId = model.getConsumingOrganizationId();
            if (consumingOrganizationId == null
                && model.getPurposeLocationId() != null
                && organizationIdByLocationId != null
                && this.isDeptIssueFamily(model.getTypeEnum())) {
                consumingOrganizationId = organizationIdByLocationId.get(model.getPurposeLocationId());
            }
            row.setConsumingOrganizationId(consumingOrganizationId);
            if (consumingOrganizationId != null && organizationNameById != null) {
                row.setConsumingOrganizationName(organizationNameById.get(consumingOrganizationId));
            }
            row.setSupplierId(model.getSupplierId());
            if (model.getSupplierId() != null && supplierNameById != null) {
                row.setSupplierName(supplierNameById.get(model.getSupplierId()));
            }
            row.setApplicantId(model.getApplicantId());
            if (model.getApplicantId() != null && practitionerNameById != null) {
                row.setApplicantName(practitionerNameById.get(model.getApplicantId()));
            }
            row.setApproverId(model.getApproverId());
            if (model.getApproverId() != null && practitionerNameById != null) {
                row.setApproverName(practitionerNameById.get(model.getApproverId()));
            }
            row.setPractitionerId(model.getPractitionerId());
            if (model.getPractitionerId() != null && practitionerNameById != null) {
                row.setPractitionerName(practitionerNameById.get(model.getPractitionerId()));
            }
            row.setApplyTime(model.getApplyTime());
            row.setApprovalTime(model.getApprovalTime());
            row.setOccurrenceTime(model.getOccurrenceTime());
            row.setLineId(model.getId());
            row.setItemTable(model.getItemTable());
            row.setItemId(model.getItemId());
            row.setLotNumber(model.getLotNumber());
            row.setTraceNo(model.getTraceNo());
            row.setStartTime(model.getStartTime());
            row.setEndTime(model.getEndTime());
            row.setUnitCode(model.getUnitCode());
            row.setPrice(model.getPrice());
            row.setTotalPrice(model.getTotalPrice());
            row.setRemake(model.getRemake());
            row.setSplitItemQuantity(model.getItemQuantity());
            String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(model.getItemTable(), model.getItemId());
            ProductDefinitionDisplayDto product = null;
            if (productByKey != null && compositeKey != null) {
                product = productByKey.get(compositeKey);
            }
            if (product != null) {
                row.setProductName(product.getProductName());
                row.setProductBusNo(product.getProductBusNo());
                row.setSpecification(product.getTotalVolume());
                row.setManufacturer(product.getManufacturer());
                List<ProductDefinitionCatalogUnitBandDto> bands =
                    ProductDefinitionCatalogUnitBandsAssembler.buildInboundSelectionBands(product);
                row.setUnitCodeDictText(this.resolveUnitCodeDictText(bands, model.getUnitCode()));
            }
            if (SupplyType.isStocktakeTypeCode(model.getTypeEnum())
                || SupplyType.isGainLossTypeCode(model.getTypeEnum())) {
                row.setSplitTotalQuantity(model.getTotalQuantity());
                row.setGainLossAmount(model.getProfitLossPrice());
                row.setActualAmount(model.getTotalPrice());
                if (SupplyType.isStocktakeTypeCode(model.getTypeEnum())) {
                    row.setSplitBookQuantity(this.deriveStocktakeBookSplitQuantity(
                        model.getTotalQuantity(), model.getItemQuantity()));
                }
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 盘点盘前拆零账面 = total_quantity − item_quantity（同意时冻结口径）。
     *
     * @param totalQuantity 实盘拆零
     * @param itemQuantity  盈亏拆零
     * @return 账面拆零；任一为 null 时按已有值推导
     */
    private BigDecimal deriveStocktakeBookSplitQuantity(BigDecimal totalQuantity, BigDecimal itemQuantity) {
        if (totalQuantity == null) {
            return null;
        }
        if (itemQuantity == null) {
            return totalQuantity;
        }
        return totalQuantity.subtract(itemQuantity);
    }

    /**
     * 解析类型展示名。
     *
     * @param typeEnum 持久化码
     * @return 展示名或原码
     */
    private String resolveTypeEnumText(String typeEnum) {
        if (StringUtils.isEmpty(typeEnum)) {
            return null;
        }
        for (SupplyType supplyType : SupplyType.values()) {
            if (supplyType.getCode().equals(typeEnum.trim())) {
                return supplyType.getDisplay();
            }
        }
        return typeEnum.trim();
    }

    /**
     * 解析状态展示名（期初可含审核中，须按落库码回显）。
     *
     * @param statusEnum 持久化码
     * @return 展示名或原码
     */
    private String resolveStatusEnumText(String statusEnum) {
        if (StringUtils.isEmpty(statusEnum)) {
            return null;
        }
        String trimmed = statusEnum.trim();
        for (SupplyStatus supplyStatus : SupplyStatus.values()) {
            if (supplyStatus.getCode().equals(trimmed)) {
                return supplyStatus.getDisplay();
            }
        }
        return trimmed;
    }

    /**
     * 是否科室领用族（领用/退领）——仅此类才回落药房上级科室为领用科室。
     *
     * @param typeEnum 供应类型码
     * @return true=领用或退领
     */
    private boolean isDeptIssueFamily(String typeEnum) {
        if (StringUtils.isEmpty(typeEnum)) {
            return false;
        }
        String trimmed = typeEnum.trim();
        if (SupplyType.DEPT_ISSUE.getCode().equals(trimmed)) {
            return true;
        }
        return SupplyType.DEPT_ISSUE_RETURN.getCode().equals(trimmed);
    }

    /**
     * 从单位槽解析计量单位展示名。
     *
     * @param bands    单位槽
     * @param unitCode 单位码
     * @return 展示名或 null
     */
    private String resolveUnitCodeDictText(List<ProductDefinitionCatalogUnitBandDto> bands, String unitCode) {
        if (bands == null || StringUtils.isEmpty(unitCode)) {
            return null;
        }
        String trimmed = unitCode.trim();
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null || band.getUnitCode() == null) {
                continue;
            }
            if (trimmed.equals(band.getUnitCode().trim())) {
                return band.getUnitName();
            }
        }
        return null;
    }

    /**
     * enrich 预加载结果容器。
     */
    @Getter
    public static class DocumentDetailEnrichmentContext {

        private Map<Long, String> locationNameById = new HashMap<>();
        private Map<Long, Long> organizationIdByLocationId = new HashMap<>();
        private Map<Long, String> organizationNameById = new HashMap<>();
        private Map<Long, String> supplierNameById = new HashMap<>();
        private Map<Long, String> practitionerNameById = new HashMap<>();
        private Map<String, ProductDefinitionDisplayDto> productByKey = new HashMap<>();

        /**
         * @param locationNameById 库位 id → 名称
         */
        public void setLocationNameById(Map<Long, String> locationNameById) {
            this.locationNameById = locationNameById;
        }

        /**
         * @param organizationIdByLocationId 库位 id → 上级 Organization id（历史领用科室回落）
         */
        public void setOrganizationIdByLocationId(Map<Long, Long> organizationIdByLocationId) {
            this.organizationIdByLocationId = organizationIdByLocationId;
        }

        /**
         * @param organizationNameById Organization id → 名称
         */
        public void setOrganizationNameById(Map<Long, String> organizationNameById) {
            this.organizationNameById = organizationNameById;
        }

        /**
         * @param supplierNameById 供应商 id → 名称
         */
        public void setSupplierNameById(Map<Long, String> supplierNameById) {
            this.supplierNameById = supplierNameById;
        }

        /**
         * @param practitionerNameById 人员 id → 名称
         */
        public void setPractitionerNameById(Map<Long, String> practitionerNameById) {
            this.practitionerNameById = practitionerNameById;
        }

        /**
         * @param productByKey 产品 compositeKey → 展示 DTO
         */
        public void setProductByKey(Map<String, ProductDefinitionDisplayDto> productByKey) {
            this.productByKey = productByKey;
        }
    }
}
