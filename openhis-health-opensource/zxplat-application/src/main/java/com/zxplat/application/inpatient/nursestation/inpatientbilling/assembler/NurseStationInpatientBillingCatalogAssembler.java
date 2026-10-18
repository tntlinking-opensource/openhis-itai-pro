/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.assembler;

import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingCatalogRowDto;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingPackageChildDto;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingWriteItemDto;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.request.PageNurseStationPricingCatalogRequest;
import com.zxplat.application.shared.product.ClinicalCatalogDispenseLocationResolver;
import com.zxplat.application.shared.product.ClinicalCatalogPriceResolver;
import com.zxplat.application.shared.product.ClinicalCatalogUnitPriceQuery;
import com.zxplat.application.shared.product.MedicationPackageSplitQuantityDisplayAssembler;
import com.zxplat.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.zxplat.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.zxplat.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.zxplat.domain.share.constants.InpatientNurseStationConstants;
import com.zxplat.domain.share.constants.ProductDefinitionCatalogUnitBandConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * IF-IP-PRC-01：护士划价选品目录装配（BR-IP-PRC-017）。
 *
 * <p>耗材由 AppService 按<strong>住院科室取药模板</strong>耗材房库存分页后调用
 * {@link #assembleDeviceCatalogRowsFromInventory}（数量可为 0；无库存记录不出现在分页里）。
 * 诊疗仍走产品定义分页 + {@link #assembleCatalogRows}。
 * 单价以包装锚 LOT_NUMBER_PRICE 写入 unitBands，行头默认展示拆零槽价。
 * 套餐子项仅读侧 {@code packageChildren} 展示（**BR-IP-PRC-011 不写路径展开**）。
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
     * 解析住院科室取药模板中的耗材候选库位（不按登录人岗位管辖）。
     *
     * @param organizationId     住院科室
     * @param pharmacyLocationId 可选指定库房
     * @return 候选库位 id；无模板时为空列表
     */
    public List<Long> listAdmissionDepartmentDevicePharmacyLocationIds(
        Long organizationId,
        Long pharmacyLocationId
    ) {
        return clinicalCatalogDispenseLocationResolver.listDeviceTemplateDefLocationIds(
            organizationId, pharmacyLocationId);
    }

    /**
     * 装配诊疗选品当前页行（产品定义分页；耗材请走 {@link #assembleDeviceCatalogRowsFromInventory}）。
     *
     * @param request 分页与机构/库房筛选
     * @param page    产品定义分页
     * @return 诊疗目录行
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
            if (productRow == null) {
                continue;
            }
            // 耗材改由库存分页驱动；本路径仅装配诊疗，避免目录总量与库存行脱节
            if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(productRow.getProductTableName())) {
                continue;
            }
            String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                productRow.getProductTableName(), productRow.getProductDefinitionId());
            List<ChargeItemDefinition> lines = priceByKey.getOrDefault(compositeKey, List.of());
            rows.add(this.assembleActivityCatalogRow(
                request, organizationId, productRow, lines, childrenByActivityId));
        }
        return rows;
    }

    /**
     * 按库存批号页装配耗材划价目录行（一行库存 = 一行目录；含数量 0）。
     *
     * @param request       目录请求（机构/库房）
     * @param inventoryRows 当前页库存项（已限定住院科室取药模板耗材房）
     * @return 耗材目录行
     */
    public List<NurseStationPricingCatalogRowDto> assembleDeviceCatalogRowsFromInventory(
        PageNurseStationPricingCatalogRequest request,
        List<InventoryItemModel> inventoryRows
    ) {
        if (request == null || inventoryRows == null || inventoryRows.isEmpty()) {
            return List.of();
        }
        Long organizationId = request.getOrganizationUnitId();
        // Pre：批量加载本页库存对应耗材产品定义
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        LinkedHashSet<String> seenKeys = new LinkedHashSet<>();
        for (InventoryItemModel inv : inventoryRows) {
            if (inv == null || inv.getItemId() == null || StringUtils.isEmpty(inv.getItemTable())) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(inv.getItemTable().trim(), inv.getItemId());
            if (!seenKeys.add(key)) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(inv.getItemTable().trim());
            lookup.setProductDefinitionId(inv.getItemId());
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> productByKey =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        List<ProductDefinitionDisplayDto> products = new ArrayList<>(productByKey.values());
        Map<String, List<ChargeItemDefinition>> priceByKey =
            clinicalCatalogPriceResolver.loadPriceLinesGrouped(products);
        List<NurseStationPricingCatalogRowDto> rows = new ArrayList<>();
        for (InventoryItemModel inv : inventoryRows) {
            if (inv == null || inv.getItemId() == null) {
                continue;
            }
            String tableName = inv.getItemTable();
            if (StringUtils.isEmpty(tableName)) {
                tableName = TableNameConstants.ADM_DEVICE_DEFINITION;
            } else {
                tableName = tableName.trim();
            }
            String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(tableName, inv.getItemId());
            ProductDefinitionDisplayDto productRow = productByKey.get(compositeKey);
            // 无有效产品定义：跳过（停用/非划价品不应出现）
            if (productRow == null) {
                continue;
            }
            List<ChargeItemDefinition> lines = priceByKey.getOrDefault(compositeKey, List.of());
            BigDecimal net = inv.getQuantity();
            if (net == null) {
                net = BigDecimal.ZERO;
            }
            // 负数量展示为 0，仍保留该库存行
            if (net.compareTo(BigDecimal.ZERO) < 0) {
                net = BigDecimal.ZERO;
            }
            NurseStationPricingCatalogRowDto dto = this.baseRow(productRow);
            dto.setDeviceDefId(productRow.getProductDefinitionId());
            dto.setInventoryItemId(inv.getId());
            dto.setLotNumber(inv.getLotNumber());
            dto.setRemainingQuantity(net);
            dto.setExpirationDate(inv.getExpirationDate());
            dto.setAvailableQuantityText(
                MedicationPackageSplitQuantityDisplayAssembler.buildDisplayQuantityText(net, productRow));
            if (inv.getLocationId() != null) {
                dto.setDefaultDispenseLocationId(inv.getLocationId());
            } else {
                dto.setDefaultDispenseLocationId(
                    this.resolveCatalogDispenseLocationId(request, organizationId, productRow));
            }
            Map<String, ChargeItemDefinition> lotPriceIndex =
                clinicalCatalogPriceResolver.indexLotNumberPriceRows(lines);
            ChargeItemDefinition lotPrice = clinicalCatalogPriceResolver.resolveLotNumberPriceLineForLot(
                lotPriceIndex, inv.getLotNumber());
            if (lotPrice == null) {
                lotPrice = clinicalCatalogPriceResolver.selectReferenceSalePriceLine(lines);
            }
            if (lotPrice == null) {
                lotPrice = clinicalCatalogPriceResolver.selectBestLotNumberPriceLine(lines);
            }
            this.applySelectedPriceAndBands(dto, productRow, lotPrice, true);
            rows.add(dto);
        }
        return rows;
    }

    /**
     * 装配诊疗目录行（无批号/库存维度）。
     *
     * @param request                目录请求
     * @param organizationId         机构
     * @param productRow             产品读模型
     * @param lines                  价目行
     * @param childrenByActivityId   套餐子项索引
     * @return 诊疗目录行
     */
    private NurseStationPricingCatalogRowDto assembleActivityCatalogRow(
        PageNurseStationPricingCatalogRequest request,
        Long organizationId,
        ProductDefinitionDisplayDto productRow,
        List<ChargeItemDefinition> lines,
        Map<Long, List<ActivityDefinitionChildQueryModel>> childrenByActivityId
    ) {
        NurseStationPricingCatalogRowDto dto = this.baseRow(productRow);
        dto.setActivityId(productRow.getProductDefinitionId());
        List<ActivityDefinitionChildQueryModel> childModels =
            childrenByActivityId.get(productRow.getProductDefinitionId());
        if (childModels != null && !childModels.isEmpty()) {
            dto.setPackageChildren(this.mapPackageChildren(childModels));
        }
        String gateKind = this.resolveDurationGateKind(productRow.getProductName());
        dto.setDurationGateKind(gateKind);
        dto.setOxygenOrEcgFlag(gateKind != null);
        ClinicalCatalogUnitPriceQuery priceCmd = ClinicalCatalogUnitPriceQuery.builder()
            .productTableName(productRow.getProductTableName())
            .productDefinitionId(productRow.getProductDefinitionId())
            .categoryCode(productRow.getCategoryCode())
            .organizationId(organizationId)
            .pharmacyLocationId(request.getPharmacyLocationId())
            .priceLines(lines)
            .productRowForOccupancy(productRow)
            .build();
        ChargeItemDefinition selected = clinicalCatalogPriceResolver.resolveCatalogUnitPrice(priceCmd);
        this.applySelectedPriceAndBands(dto, productRow, selected, false);
        return dto;
    }

    /**
     * 将命中价目写入 chargeItemDefinitionId / unitBands，并按是否默认拆零回填行头 unitCode/unitPrice。
     *
     * <p>包装锚 {@code price_value} 经 {@link ProductDefinitionCatalogUnitBandsAssembler#buildDoctorStationBands}
     * 线性写入各槽；禁止先除拆零再乘回（BR-MON-PKG-001 / BR-ORD-016）。</p>
     *
     * @param dto              目录行
     * @param productRow       产品读模型
     * @param selected         命中定价行；可空
     * @param preferSplitDefault 耗材 true：行头默认拆零槽；诊疗 false：保持包装主单位
     */
    private void applySelectedPriceAndBands(
        NurseStationPricingCatalogRowDto dto,
        ProductDefinitionDisplayDto productRow,
        ChargeItemDefinition selected,
        boolean preferSplitDefault
    ) {
        if (selected != null) {
            dto.setChargeItemDefinitionId(selected.getId());
            dto.setUnitPrice(selected.getPriceValue());
        } else if (productRow.getChargeItemDefinitionId() != null) {
            // 无价目命中时回落读模型上的默认 chargeItemDefinitionId（仅展示）
            dto.setChargeItemDefinitionId(productRow.getChargeItemDefinitionId());
        }
        String selectedPriceTypeCode = null;
        if (selected != null) {
            selectedPriceTypeCode = selected.getPriceTypeCode();
        }
        String bandPricingUnitCode = ProductDefinitionCatalogUnitBandsAssembler
            .resolveDoctorStationBandPricingUnitCode(productRow, selectedPriceTypeCode);
        List<ProductDefinitionCatalogUnitBandDto> bands =
            ProductDefinitionCatalogUnitBandsAssembler.buildDoctorStationBands(
                productRow, bandPricingUnitCode, dto.getUnitPrice());
        dto.setUnitBands(bands);
        if (!preferSplitDefault) {
            // 诊疗：行头保持包装主单位 + 包装锚单价
            return;
        }
        // 耗材：默认按拆零单位划价；行头单价取 SPLIT 槽（已由包装锚线性换算，禁止二次÷ratio）
        ProductDefinitionCatalogUnitBandDto splitBand = this.findBandByKind(
            bands, ProductDefinitionCatalogUnitBandConstants.BAND_SPLIT);
        if (splitBand != null && StringUtils.isNotEmpty(splitBand.getUnitCode())) {
            dto.setUnitCode(splitBand.getUnitCode());
            dto.setUnitName(splitBand.getUnitName());
            // 仅当槽上已写出有效单价时才覆盖行头，避免 null/0 冲掉包装锚价
            if (splitBand.getUnitPrice() != null
                && splitBand.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
                dto.setUnitPrice(splitBand.getUnitPrice());
            }
        }
    }

    /**
     * 按 bandKind 查找单位槽。
     *
     * @param bands    单位槽列表
     * @param bandKind 槽语义
     * @return 命中槽；未命中 null
     */
    private ProductDefinitionCatalogUnitBandDto findBandByKind(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        String bandKind
    ) {
        if (bands == null || bands.isEmpty() || StringUtils.isEmpty(bandKind)) {
            return null;
        }
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null) {
                continue;
            }
            if (bandKind.equals(band.getBandKind())) {
                return band;
            }
        }
        return null;
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
        // 规格：产品定义 total_volume（诊疗/耗材共用）
        dto.setTotalVolume(productRow.getTotalVolume());
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
     * （对齐 BR-IP-PRC-007 / 滚费 {@code resolveActivityOrderUnitCode}）。</p>
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
