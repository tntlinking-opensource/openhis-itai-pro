/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.assembler;

import com.openhis.health.application.shared.product.ClinicalCatalogDispenseLocationResolver;
import com.openhis.health.application.shared.product.ClinicalCatalogPriceResolver;
import com.openhis.health.application.shared.product.ClinicalCatalogUnitPriceQuery;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingCatalogRowDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingPackageChildDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingWriteItemDto;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.request.PageNurseStationPricingCatalogRequest;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * IF-IP-PRC-01：护士划价选品目录装配（BR-IP-PRC-017；不做库存 Gate）。
 *
 * <p>耗材行读侧解析默认发放库房 + FIFO 批号售价；套餐子项仅读侧 {@code packageChildren} 展示（**BR-IP-PRC-011 不写路径展开**）。
 * 皮试等场景按子诊疗 id 择价复用 {@link #applyChildActivityPricing}。</p>
 */
@Component
@RequiredArgsConstructor
public class NurseStationInpatientBillingCatalogAssembler {

    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;
    private final ClinicalCatalogDispenseLocationResolver clinicalCatalogDispenseLocationResolver;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 装配划价选品当前页行。
     *
     * @param request 分页与机构/库房筛选
     * @param page    产品定义分页
     * @return 目录行
     */
    public List<NurseStationPricingCatalogRowDto> assembleCatalogRows(
        PageNurseStationPricingCatalogRequest request,
        Page<ProductDefinitionDisplayDto> page) {
        if (request == null || page == null) {
            return List.of();
        }
        List<ProductDefinitionDisplayDto> content = page.getContent();
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        Long organizationId = request.getOrganizationUnitId();
        // Pre：当前页产品批量取价目行、诊疗套餐子项（避免行内 N+1）
        Map<String, List<ChargeItemDefinition>> priceByKey =
            clinicalCatalogPriceResolver.loadPriceLinesGrouped(content);
        Map<Long, List<ActivityDefinitionChildQueryModel>> childrenByActivityId =
            this.loadPackageChildrenForPage(content);
        List<NurseStationPricingCatalogRowDto> rows = new ArrayList<>();
        for (ProductDefinitionDisplayDto productRow : content) {
            NurseStationPricingCatalogRowDto dto = this.baseRow(productRow);
            String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                productRow.getProductTableName(), productRow.getProductDefinitionId());
            List<ChargeItemDefinition> lines = priceByKey.getOrDefault(compositeKey, List.of());
            // 耗材读侧：解析默认发放库房（请求 pharmacyLocationId 优先，否则模板 match）
            Long clientResolvedDispenseLocationId = this.resolveCatalogDispenseLocationId(
                request, organizationId, productRow);
            if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(productRow.getProductTableName())
                && clientResolvedDispenseLocationId != null) {
                dto.setDefaultDispenseLocationId(clientResolvedDispenseLocationId);
            }
            // FIFO 批号售价：透传已解析库位，与 IF-DOR-11 读侧同构
            ClinicalCatalogUnitPriceQuery priceCmd = ClinicalCatalogUnitPriceQuery.builder()
                .productTableName(productRow.getProductTableName())
                .productDefinitionId(productRow.getProductDefinitionId())
                .categoryCode(productRow.getCategoryCode())
                .organizationId(organizationId)
                .pharmacyLocationId(request.getPharmacyLocationId())
                .clientResolvedDispenseLocationId(clientResolvedDispenseLocationId)
                .priceLines(lines)
                .productRowForOccupancy(productRow)
                .build();
            ChargeItemDefinition selected = clinicalCatalogPriceResolver.resolveCatalogUnitPrice(priceCmd);
            if (selected != null) {
                dto.setChargeItemDefinitionId(selected.getId());
                dto.setUnitPrice(selected.getPriceValue());
            } else if (productRow.getChargeItemDefinitionId() != null) {
                // 无价目命中时回落读模型上的默认 chargeItemDefinitionId（仅展示）
                dto.setChargeItemDefinitionId(productRow.getChargeItemDefinitionId());
            }
            if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(productRow.getProductTableName())) {
                dto.setActivityId(productRow.getProductDefinitionId());
                List<ActivityDefinitionChildQueryModel> childModels =
                    childrenByActivityId.get(productRow.getProductDefinitionId());
                if (childModels != null && !childModels.isEmpty()) {
                    dto.setPackageChildren(this.mapPackageChildren(childModels));
                }
                String gateKind = this.resolveDurationGateKind(productRow.getProductName());
                dto.setDurationGateKind(gateKind);
                dto.setOxygenOrEcgFlag(gateKind != null);
            } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(productRow.getProductTableName())) {
                // 耗材：写 IF-IP-PRC-02/04 时须将 defaultDispenseLocationId 回传 performLocation
                dto.setDeviceDefId(productRow.getProductDefinitionId());
            }
            String selectedPriceTypeCode = null;
            if (selected != null) {
                selectedPriceTypeCode = selected.getPriceTypeCode();
            }
            dto.setUnitBands(ProductDefinitionCatalogUnitBandsAssembler.buildDoctorStationBands(
                productRow,
                ProductDefinitionCatalogUnitBandsAssembler.resolveDoctorStationBandPricingUnitCode(
                    productRow, selectedPriceTypeCode),
                dto.getUnitPrice()));
            rows.add(dto);
        }
        return rows;
    }

    /**
     * 耗材目录行默认发放库房：请求 {@code pharmacyLocationId} 优先，否则按机构 + 模板 match。
     *
     * @param request          目录请求
     * @param organizationId   机构单元
     * @param productRow       产品读模型
     * @return 发材库位；诊疗行或非耗材时为 null
     */
    private Long resolveCatalogDispenseLocationId(
        PageNurseStationPricingCatalogRequest request,
        Long organizationId,
        ProductDefinitionDisplayDto productRow
    ) {
        if (productRow == null
            || !TableNameConstants.ADM_DEVICE_DEFINITION.equals(productRow.getProductTableName())) {
            // 诊疗行不参与发材库位解析
            return null;
        }
        if (request != null && request.getPharmacyLocationId() != null) {
            // 用户显式收窄库房时优先采用（读侧筛选，非写路径透传）
            return request.getPharmacyLocationId();
        }
        if (organizationId == null) {
            return null;
        }
        return clinicalCatalogDispenseLocationResolver.matchDefaultDispenseLocationId(
            organizationId, productRow);
    }

    /**
     * 由产品读模型投影目录行基础展示字段（不含价目与库位 enrich）。
     *
     * @param productRow 产品定义读模型
     * @return 未填单价/库位的目录行壳
     */
    private NurseStationPricingCatalogRowDto baseRow(ProductDefinitionDisplayDto productRow) {
        NurseStationPricingCatalogRowDto dto = new NurseStationPricingCatalogRowDto();
        dto.setProductDefinitionId(productRow.getProductDefinitionId());
        dto.setProductTableName(productRow.getProductTableName());
        dto.setBusNo(productRow.getProductBusNo());
        dto.setName(productRow.getProductName());
        dto.setPyCode(productRow.getPyStr());
        dto.setWbCode(productRow.getWbStr());
        dto.setCategoryCode(productRow.getCategoryCode());
        dto.setCategoryCodeDictText(productRow.getCategoryCodeDictText());
        dto.setUnitCode(ProductDefinitionCatalogUnitBandsAssembler.resolveCatalogPrimaryUnitCode(productRow));
        dto.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveCatalogPrimaryUnitName(productRow));
        return dto;
    }

    /**
     * 按子诊疗 id 解析 SALE 定价并回填写行（皮试划价等；**非**套餐写路径子项展开）。
     *
     * <p>计价单位取子诊疗目录包装单位（{@code packageUnitCode}），**禁止**沿用父皮试药 {@code unitCode}
     *（对齐 BR-IP-PRC-007 / 滚费 {@code resolveActivityOrderUnitCode}）。</p>
     *
     * @param childRow        待写入行（父就诊/账户/数量等已由调用方填充）
     * @param childActivityId 子诊疗活动定义 id
     */
    public void applyChildActivityPricing(
        NurseStationPricingWriteItemDto childRow,
        Long childActivityId) {
        if (childRow == null || childActivityId == null) {
            return;
        }
        // Pre：子诊疗当前 ACTIVE 价目（BR-CHGDEF-QRY-004）
        ChargeItemDefinitionQueryCommand cmd = new ChargeItemDefinitionQueryCommand();
        cmd.setInstanceTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        cmd.setInstanceIds(List.of(childActivityId));
        List<ChargeItemDefinition> lines = chargeItemDefinitionQueryService.findPriceLinesByProjects(cmd);
        ClinicalCatalogUnitPriceQuery priceCmd = ClinicalCatalogUnitPriceQuery.builder()
            .productTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION)
            .productDefinitionId(childActivityId)
            .priceLines(lines)
            .build();
        ChargeItemDefinition selected = clinicalCatalogPriceResolver.resolveCatalogUnitPrice(priceCmd);
        // 命中价目时覆盖单价与 chargeItemDefinitionId，供后续 priceByNurseStation
        if (selected != null) {
            childRow.setChargeItemDefinitionId(selected.getId());
            childRow.setUnitPrice(selected.getPriceValue());
            if (childRow.getQuantity() != null) {
                childRow.setTotalPrice(selected.getPriceValue().multiply(childRow.getQuantity()));
            }
        }
        childRow.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        childRow.setProductDefinitionId(childActivityId);
        childRow.setActivityId(childActivityId);
        // Pre：覆盖为单位为子诊疗包装单位，避免皮试写行误带药品单位
        String activityUnitCode = this.resolveActivityCatalogUnitCode(childActivityId);
        if (StringUtils.isNotEmpty(activityUnitCode)) {
            childRow.setUnitCode(activityUnitCode);
        }
    }

    /**
     * 解析诊疗活动目录主计价单位（包装单位 code）。
     *
     * @param activityId 活动定义主键
     * @return 单位 code；未维护时 null
     */
    private String resolveActivityCatalogUnitCode(Long activityId) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        lookup.setProductDefinitionId(activityId);
        Map<String, ProductDefinitionDisplayDto> loaded =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(List.of(lookup));
        ProductDefinitionDisplayDto product = loaded.get(ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId));
        return ProductDefinitionCatalogUnitBandsAssembler.resolveCatalogPrimaryUnitCode(product);
    }

    /**
     * 批量加载当前页诊疗行的套餐子项（一次 IN 查询，避免逐行查子表）。
     *
     * @param content 当前页产品读模型
     * @return activityId → 子项列表
     */
    private Map<Long, List<ActivityDefinitionChildQueryModel>> loadPackageChildrenForPage(
        List<ProductDefinitionDisplayDto> content) {
        LinkedHashSet<Long> activityIds = new LinkedHashSet<>();
        for (ProductDefinitionDisplayDto row : content) {
            if (row != null && TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(row.getProductTableName())
                && row.getProductDefinitionId() != null) {
                activityIds.add(row.getProductDefinitionId());
            }
        }
        if (activityIds.isEmpty()) {
            return Map.of();
        }
        return activityDefinitionQueryService.listChildrenByActivityIds(new ArrayList<>(activityIds));
    }

    /**
     * 将领域子项读模型映射为划价目录套餐子 DTO（写路径服务端再展开，前端仅展示）。
     *
     * @param childModels 活动定义子表读模型
     * @return 套餐子项列表
     */
    private List<NurseStationPricingPackageChildDto> mapPackageChildren(
        List<ActivityDefinitionChildQueryModel> childModels) {
        List<NurseStationPricingPackageChildDto> list = new ArrayList<>();
        for (ActivityDefinitionChildQueryModel child : childModels) {
            if (child == null || child.getChildrenId() == null) {
                continue;
            }
            NurseStationPricingPackageChildDto dto = new NurseStationPricingPackageChildDto();
            dto.setChildrenId(child.getChildrenId());
            dto.setChildrenNum(child.getChildrenNum());
            dto.setChildrenName(child.getChildrenName());
            list.add(dto);
        }
        return list;
    }

    /**
     * 按产品名称关键字识别吸氧/心电计时 Gate 类型（BR-IP-PRC-010；与写路径 Gate 口径一致）。
     *
     * @param productName 诊疗展示名
     * @return {@link InpatientNurseStationConstants#DURATION_GATE_KIND_OXYGEN} /
     *         {@link InpatientNurseStationConstants#DURATION_GATE_KIND_ECG}，非计时类为 null
     */
    public String resolveDurationGateKind(String productName) {
        String name = StringUtils.defaultString(productName);
        if (name.contains("吸氧") || name.contains("氧气")) {
            return InpatientNurseStationConstants.DURATION_GATE_KIND_OXYGEN;
        }
        if (name.contains("心电")) {
            return InpatientNurseStationConstants.DURATION_GATE_KIND_ECG;
        }
        return null;
    }
}
