/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.assembler;

import com.openhis.health.application.shared.product.ClinicalCatalogDispenseLocationResolver;
import com.openhis.health.application.shared.product.ClinicalCatalogPriceResolver;
import com.openhis.health.application.shared.product.ClinicalCatalogUnitPriceQuery;
import com.openhis.health.application.shared.product.DoctorStationAdviceInventoryUnitQuantityHelper;
import com.openhis.health.application.shared.product.ProductTableNameHelper;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceCatalogRowDto;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceInventoryRowDto;
import com.openhis.health.application.inpatient.doctorstation.advice.request.PageInpatientDoctorStationAdviceCatalogRequest;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseOrderCatalogOccupancyModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.devicedispense.queries.model.DeviceDispenseOrderCatalogOccupancyModel;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IF-IP-DOR-11：医嘱开立目录分页装配（Asm：产品定义读模型 + 取药模板库位 ∩ 库存 + 同就诊发放预减 + 定价表选中单价）。
 * <p>复合业务键与读模型服务一致，使用 {@link GeneralSharedConstants#PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR}，避免与表名/编号中常见字符碰撞。</p>
 * <p><b>计价价型分流</b>：诊疗（{@link TableNameConstants#WKF_ACTIVITY_DEFINITION}）仅 {@link PriceType#SALE}；
 * 药品/耗材仅 {@link PriceType#LOT_NUMBER_PRICE}，且目录库存明细 {@link InpatientDoctorStationAdviceInventoryRowDto} 上单价按
 * {@code lot_no} 与库存 {@link InpatientDoctorStationAdviceInventoryRowDto#getLotNumber()} 对齐；汇总行的 {@link InpatientDoctorStationAdviceCatalogRowDto#getUnitPrice()}
 * 与首条可用库存批号一致。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientDoctorStationAdviceCatalogAssembler {

    /** 机构下配送/发药库位模板：解析候选库位 ID 列表。 */
    /** 取药科室模板库位解析。 */
    private final ClinicalCatalogDispenseLocationResolver clinicalCatalogDispenseLocationResolver;
    /** 库存可用量：按表名+物料/定义+库位批量查询目录可开量。 */
    private final InventoryItemQueryService inventoryItemQueryService;
    /** 同就诊药品发放预占：目录可开量扣减。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    /** 同就诊耗材发放预占：目录可开量扣减。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;
    /** 收费项定义（定价行）：当前页批量取价。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /** 临床目录统一单价解析（诊疗 SALE / 药耗批号售价）。 */
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;
    /** 默认执行科室展示名（诊疗 org_id → 机构名称）。 */
    private final OrganizationQueryService organizationQueryService;

    /**
     * IF-IP-TCM-35：装配饮片目录分页（仅定价与单位，不含库存预占/预减）。
     *
     * @param request  请求
     * @param page     产品定义分页结果
     * @param snapshot 住院就诊快照
     * @return 当前页行（inventoryList 为空、availableQuantity 为 null）
     */
    public List<InpatientDoctorStationAdviceCatalogRowDto> assemblePageContentWithoutInventoryGate(
        PageInpatientDoctorStationAdviceCatalogRequest request,
        Page<ProductDefinitionDisplayDto> page,
        InpatientEncounterContextQueryModel snapshot
    ) {
        if (!request.getPatientId().equals(snapshot.getPatientId())) {
            return List.of();
        }
        List<ProductDefinitionDisplayDto> content = page.getContent();
        if (content.isEmpty()) {
            return List.of();
        }
        Map<String, List<ChargeItemDefinition>> priceByKey = clinicalCatalogPriceResolver.loadPriceLinesGrouped(content);
        Long orgId = snapshot.getOrganizationId();
        if (request.getOrganizationUnitId() != null) {
            orgId = request.getOrganizationUnitId();
        }
        LinkedHashSet<Long> fallbackChargeDefIds = new LinkedHashSet<>();
        for (ProductDefinitionDisplayDto pre : content) {
            String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                pre.getProductTableName(), pre.getProductDefinitionId());
            List<ChargeItemDefinition> preLines = priceByKey.getOrDefault(compositeKey, List.of());
            ClinicalCatalogUnitPriceQuery preCmd = ClinicalCatalogUnitPriceQuery.builder()
                .productTableName(pre.getProductTableName())
                .productDefinitionId(pre.getProductDefinitionId())
                .categoryCode(pre.getCategoryCode())
                .methodCode(pre.getMethodCode())
                .organizationId(orgId)
                .pharmacyLocationId(request.getPharmacyLocationId())
                .encounterId(request.getEncounterId())
                .priceLines(preLines)
                .productRowForOccupancy(pre)
                .build();
            if (clinicalCatalogPriceResolver.resolveCatalogUnitPrice(preCmd) == null
                && pre.getChargeItemDefinitionId() != null) {
                fallbackChargeDefIds.add(pre.getChargeItemDefinitionId());
            }
        }
        Map<Long, ChargeItemDefinition> fallbackPriceByDefId = chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(fallbackChargeDefIds);
        List<InpatientDoctorStationAdviceCatalogRowDto> rows = new ArrayList<>();
        for (ProductDefinitionDisplayDto productRow : content) {
            InpatientDoctorStationAdviceCatalogRowDto dto = baseRow(productRow);
            dto.setInventoryList(List.of());
            dto.setAvailableQuantity(null);
            Long defaultDispenseLocationId = resolveDefaultDispenseLocationId(orgId, productRow);
            if (defaultDispenseLocationId != null) {
                dto.setDefaultDispenseLocationId(defaultDispenseLocationId);
            }
            String productCompositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                productRow.getProductTableName(), productRow.getProductDefinitionId());
            List<ChargeItemDefinition> lines = priceByKey.getOrDefault(productCompositeKey, List.of());
            ClinicalCatalogUnitPriceQuery priceCmd = ClinicalCatalogUnitPriceQuery.builder()
                .productTableName(productRow.getProductTableName())
                .productDefinitionId(productRow.getProductDefinitionId())
                .categoryCode(productRow.getCategoryCode())
                .methodCode(productRow.getMethodCode())
                .organizationId(orgId)
                .pharmacyLocationId(request.getPharmacyLocationId())
                .encounterId(request.getEncounterId())
                .priceLines(lines)
                .productRowForOccupancy(productRow)
                .build();
            ChargeItemDefinition selected = clinicalCatalogPriceResolver.resolveCatalogUnitPrice(priceCmd);
            ChargeItemDefinition catalogPriceLine = selected;
            if (selected != null) {
                dto.setChargeItemDefinitionId(selected.getId());
                dto.setUnitPrice(selected.getPriceValue());
            } else {
                ChargeItemDefinition fallback = null;
                if (productRow.getChargeItemDefinitionId() != null) {
                    fallback = fallbackPriceByDefId.get(productRow.getChargeItemDefinitionId());
                }
                if (fallback != null && ProductTableNameHelper.isActivity(productRow.getProductTableName())
                    && !PriceType.SALE.getCode().equals(fallback.getPriceTypeCode())) {
                    fallback = null;
                }
                if (fallback != null && ProductTableNameHelper.isMedicationOrDevice(productRow.getProductTableName())
                    && !PriceType.LOT_NUMBER_PRICE.getCode().equals(fallback.getPriceTypeCode())) {
                    fallback = null;
                }
                if (fallback != null) {
                    dto.setChargeItemDefinitionId(fallback.getId());
                    dto.setUnitPrice(fallback.getPriceValue());
                    catalogPriceLine = fallback;
                } else if (productRow.getChargeItemDefinitionId() != null) {
                    dto.setChargeItemDefinitionId(productRow.getChargeItemDefinitionId());
                }
            }
            String catalogPriceTypeCode = null;
            if (catalogPriceLine != null) {
                catalogPriceTypeCode = catalogPriceLine.getPriceTypeCode();
            }
            String bandPricingUnitCode = ProductDefinitionCatalogUnitBandsAssembler.resolveDoctorStationBandPricingUnitCode(
                productRow, catalogPriceTypeCode);
            dto.setUnitBands(ProductDefinitionCatalogUnitBandsAssembler.buildDoctorStationBands(
                productRow, bandPricingUnitCode, dto.getUnitPrice()));
            rows.add(dto);
        }
        enrichDefaultPerformOrgNames(rows);
        return rows;
    }

    /**
     * 装配目录分页结果（读模型分页已由调用方执行一次）。
     *
     * <p><b>规模（B.2）</b>：定价批量查询与库存/预占计算均按<strong>当前页</strong>内容收敛；
     * 若调用方传入超大 {@code pageSize}，单次装配 I/O 与内存会线性放大，应由接口层限制页大小。</p>
     *
     * @param request  请求
     * @param page      产品定义分页结果
     * @param snapshot 住院就诊快照（患者/科室门控）
     * @return 当前页行
     */
    public List<InpatientDoctorStationAdviceCatalogRowDto> assemblePageContent(
        PageInpatientDoctorStationAdviceCatalogRequest request,
        Page<ProductDefinitionDisplayDto> page,
        InpatientEncounterContextQueryModel snapshot
    ) {
        // 门控：请求患者必须与住院快照一致，防止越权用他人就诊上下文套目录数据
        if (!request.getPatientId().equals(snapshot.getPatientId())) {
            return List.of();
        }
        List<ProductDefinitionDisplayDto> content = page.getContent();
        if (content.isEmpty()) {
            return List.of();
        }

        // Pre：当前页所有产品行的定价行一次性拉齐，避免循环内逐条打定价查询（A.9.1 批量 I/O 精神在应用层的对齐）
        Map<String, List<ChargeItemDefinition>> priceByKey = clinicalCatalogPriceResolver.loadPriceLinesGrouped(content);
        Long orgId = snapshot.getOrganizationId();
        if (request.getOrganizationUnitId() != null) {
            orgId = request.getOrganizationUnitId();
        }

        LinkedHashSet<Long> fallbackChargeDefIds = new LinkedHashSet<>();
        for (ProductDefinitionDisplayDto pre : content) {
            String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                pre.getProductTableName(), pre.getProductDefinitionId());
            List<ChargeItemDefinition> preLines = priceByKey.getOrDefault(compositeKey, List.of());
            ClinicalCatalogUnitPriceQuery preCmd = ClinicalCatalogUnitPriceQuery.builder()
                .productTableName(pre.getProductTableName())
                .productDefinitionId(pre.getProductDefinitionId())
                .categoryCode(pre.getCategoryCode())
                .methodCode(pre.getMethodCode())
                .organizationId(orgId)
                .pharmacyLocationId(request.getPharmacyLocationId())
                .encounterId(request.getEncounterId())
                .priceLines(preLines)
                .productRowForOccupancy(pre)
                .build();
            if (clinicalCatalogPriceResolver.resolveCatalogUnitPrice(preCmd) == null
                && pre.getChargeItemDefinitionId() != null) {
                fallbackChargeDefIds.add(pre.getChargeItemDefinitionId());
            }
        }
        Map<Long, ChargeItemDefinition> fallbackPriceByDefId = chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(fallbackChargeDefIds);
        List<InpatientDoctorStationAdviceCatalogRowDto> rows = new ArrayList<>();
        for (ProductDefinitionDisplayDto productRow : content) {
            InpatientDoctorStationAdviceCatalogRowDto dto = baseRow(productRow);

            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productRow.getProductTableName())
                || TableNameConstants.ADM_DEVICE_DEFINITION.equals(productRow.getProductTableName())) {
                // 药品/耗材：需要库存 ∩ 模板库位，并扣减候选库房全局草稿/待发占用，得到「可开量」
                List<Long> candidateLocs = resolveCandidateLocations(orgId, productRow, request.getPharmacyLocationId());
                List<Long> itemIds = List.of(productRow.getProductDefinitionId());
                List<InventoryCatalogAvailabilityRowModel> physical = inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                    productRow.getProductTableName(), itemIds, candidateLocs);
                Map<String, BigDecimal> occ = loadOccupancyMap(productRow, candidateLocs);
                List<InpatientDoctorStationAdviceInventoryRowDto> invDtos = new ArrayList<>();
                BigDecimal sumNetSplit = BigDecimal.ZERO;
                int order = 0;
                for (InventoryCatalogAvailabilityRowModel row : physical) {
                    String k = ProductTableNameHelper.tripleCompositeKey(row.getItemTable(), row.getItemId(), row.getLocationId());
                    BigDecimal net = row.getQuantity();
                    if (net == null) {
                        net = BigDecimal.ZERO;
                    }
                    net = net.subtract(occ.getOrDefault(k, BigDecimal.ZERO));
                    if (net.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    InpatientDoctorStationAdviceInventoryRowDto inv = new InpatientDoctorStationAdviceInventoryRowDto();
                    inv.setItemTable(row.getItemTable());
                    inv.setItemId(row.getItemId());
                    inv.setCategoryCode(productRow.getCategoryCode());
                    inv.setInventoryId(row.getId());
                    // 库存明细 quantity 仍为拆零单位；目录汇总 availableQuantity 在 unitBands 就绪后换算展示单位
                    inv.setQuantity(net);
                    inv.setUnitCode(productRow.getSplitUnitCode());
                    inv.setUnitName(productRow.getSplitUnitCodeDictText());
                    inv.setLotNumber(row.getLotNumber());
                    inv.setLocationId(row.getLocationId());
                    inv.setLocationStoreId(row.getLocationId());
                    inv.setDisplayOrder(order++);
                    invDtos.add(inv);
                    sumNetSplit = sumNetSplit.add(net);
                }
                dto.setInventoryList(invDtos);
                dto.setAvailableQuantity(sumNetSplit);
                // IF-IP-DOR-11 读侧：默认发药库位（目录展示；住院写路径不使用）
                Long defaultDispenseLocationId = resolveDefaultDispenseLocationId(orgId, productRow);
                if (defaultDispenseLocationId != null) {
                    dto.setDefaultDispenseLocationId(defaultDispenseLocationId);
                }
            }

            // Asm：单价来自批量定价结果；药耗有库存时按「库存批号 ↔ 定价 lot_no」逐行回填，目录行单价与首条库存批号对齐
            String productCompositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                productRow.getProductTableName(), productRow.getProductDefinitionId());
            List<ChargeItemDefinition> lines = priceByKey.getOrDefault(productCompositeKey, List.of());
            boolean headerPriceFromInventoryLots = false;
            ChargeItemDefinition catalogPriceLine = null;
            List<InpatientDoctorStationAdviceInventoryRowDto> inventoryRows = dto.getInventoryList();
            if (ProductTableNameHelper.isMedicationOrDevice(productRow.getProductTableName())
                && inventoryRows != null && !inventoryRows.isEmpty()) {
                Map<String, ChargeItemDefinition> lotPriceIndex = clinicalCatalogPriceResolver.indexLotNumberPriceRows(lines);
                for (InpatientDoctorStationAdviceInventoryRowDto invRow : inventoryRows) {
                    ChargeItemDefinition rowPriceLine = clinicalCatalogPriceResolver.resolveLotNumberPriceLineForLot(
                        lotPriceIndex, invRow.getLotNumber());
                    if (rowPriceLine != null) {
                        invRow.setPrice(rowPriceLine.getPriceValue());
                        invRow.setChargeItemDefinitionId(rowPriceLine.getId());
                    }
                }
                InpatientDoctorStationAdviceInventoryRowDto firstInv = inventoryRows.get(0);
                ChargeItemDefinition headerLine = clinicalCatalogPriceResolver.resolveLotNumberPriceLineForLot(
                    lotPriceIndex, firstInv.getLotNumber());
                if (headerLine != null) {
                    dto.setChargeItemDefinitionId(headerLine.getId());
                    dto.setUnitPrice(headerLine.getPriceValue());
                    catalogPriceLine = headerLine;
                    headerPriceFromInventoryLots = true;
                }
            }
            if (!headerPriceFromInventoryLots) {
                ClinicalCatalogUnitPriceQuery priceCmd = ClinicalCatalogUnitPriceQuery.builder()
                    .productTableName(productRow.getProductTableName())
                    .productDefinitionId(productRow.getProductDefinitionId())
                    .categoryCode(productRow.getCategoryCode())
                    .methodCode(productRow.getMethodCode())
                    .organizationId(orgId)
                    .pharmacyLocationId(request.getPharmacyLocationId())
                    .encounterId(request.getEncounterId())
                    .priceLines(lines)
                    .productRowForOccupancy(productRow)
                    .build();
                ChargeItemDefinition selected = clinicalCatalogPriceResolver.resolveCatalogUnitPrice(priceCmd);
                if (selected != null) {
                    dto.setChargeItemDefinitionId(selected.getId());
                    dto.setUnitPrice(selected.getPriceValue());
                    catalogPriceLine = selected;
                } else {
                    ChargeItemDefinition fallback = null;
                    if (productRow.getChargeItemDefinitionId() != null) {
                        fallback = fallbackPriceByDefId.get(productRow.getChargeItemDefinitionId());
                    }
                    if (fallback != null && ProductTableNameHelper.isActivity(productRow.getProductTableName())
                        && !PriceType.SALE.getCode().equals(fallback.getPriceTypeCode())) {
                        fallback = null;
                    }
                    if (fallback != null && ProductTableNameHelper.isMedicationOrDevice(productRow.getProductTableName())
                        && !PriceType.LOT_NUMBER_PRICE.getCode().equals(fallback.getPriceTypeCode())) {
                        fallback = null;
                    }
                    if (fallback != null) {
                        dto.setChargeItemDefinitionId(fallback.getId());
                        dto.setUnitPrice(fallback.getPriceValue());
                        catalogPriceLine = fallback;
                    } else if (productRow.getChargeItemDefinitionId() != null) {
                        dto.setChargeItemDefinitionId(productRow.getChargeItemDefinitionId());
                    }
                }
            }
            String catalogPriceTypeCode = null;
            if (catalogPriceLine != null) {
                catalogPriceTypeCode = catalogPriceLine.getPriceTypeCode();
            }
            String bandPricingUnitCode = ProductDefinitionCatalogUnitBandsAssembler.resolveDoctorStationBandPricingUnitCode(
                productRow, catalogPriceTypeCode);
            dto.setUnitBands(ProductDefinitionCatalogUnitBandsAssembler.buildDoctorStationBands(
                productRow, bandPricingUnitCode, dto.getUnitPrice()));
            // 目录库存列：拆零净量换算为 PACKAGE 槽（或主计价单位）后再下发，与 unitCode / unitPrice 配套
            if (dto.getAvailableQuantity() != null) {
                dto.setAvailableQuantity(ProductDefinitionCatalogUnitBandsAssembler.convertDoctorStationCatalogAvailableQuantity(
                    productRow, dto.getUnitBands(), dto.getAvailableQuantity()));
            }
            rows.add(dto);
        }
        enrichDefaultPerformOrgNames(rows);
        return rows;
    }

    /**
     * 批量回填默认执行科室展示名（{@code defaultPerformOrgId} 为机构主键，非库位）。
     */
    private void enrichDefaultPerformOrgNames(List<InpatientDoctorStationAdviceCatalogRowDto> rows) {
        Set<Long> orgIds = new LinkedHashSet<>();
        for (InpatientDoctorStationAdviceCatalogRowDto row : rows) {
            if (row.getDefaultPerformOrgId() != null) {
                orgIds.add(row.getDefaultPerformOrgId());
            }
        }
        if (orgIds.isEmpty()) {
            return;
        }
        Map<Long, String> orgNameById = organizationQueryService.mapNamesByIds(orgIds);
        for (InpatientDoctorStationAdviceCatalogRowDto row : rows) {
            if (row.getDefaultPerformOrgId() == null) {
                continue;
            }
            String name = orgNameById.get(row.getDefaultPerformOrgId());
            if (StringUtils.isNotEmpty(name)) {
                row.setDefaultPerformOrgName(name);
            }
        }
    }

    /**
     * 从产品读模型装配目录行「与库存、定价无关」的基础展示字段（类型、名称、类别、五类单位槽、药品默认频次用法等）。
     *
     * @param productRow 当前页一条产品定义读模型
     * @return 已填充基础字段的 DTO，不含库存与定价单价
     */
    private InpatientDoctorStationAdviceCatalogRowDto baseRow(ProductDefinitionDisplayDto productRow) {
        InpatientDoctorStationAdviceCatalogRowDto dto = new InpatientDoctorStationAdviceCatalogRowDto();
        dto.setProductDefinitionId(productRow.getProductDefinitionId());
        dto.setProductTableName(productRow.getProductTableName());
        dto.setBusNo(productRow.getProductBusNo());
        dto.setName(productRow.getProductName());
        dto.setPyCode(productRow.getPyStr());
        dto.setWbCode(productRow.getWbStr());
        dto.setCategoryCode(productRow.getCategoryCode());
        dto.setCategoryCodeDictText(productRow.getCategoryCodeDictText());
        // 计价主单位：仅包装单位（package_unit_code），禁止跨槽回落
        String primaryUnitCode = ProductDefinitionCatalogUnitBandsAssembler.resolveCatalogPrimaryUnitCode(productRow);
        dto.setUnitCode(primaryUnitCode);
        dto.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveCatalogPrimaryUnitName(productRow));
        dto.setUnitBands(ProductDefinitionCatalogUnitBandsAssembler.buildDoctorStationBands(productRow, primaryUnitCode, null));
        dto.setRateCode(productRow.getRateCode());
        dto.setRateCodeDictText(productRow.getRateCodeDictText());
        dto.setMethodCode(productRow.getMethodCode());
        dto.setMethodCodeDictText(productRow.getMethodCodeDictText());
        dto.setDoseFormCode(productRow.getDoseFormCode());
        dto.setDoseFormCodeDictText(productRow.getDoseFormCodeDictText());
        dto.setRestrictedFlag(productRow.getRestrictedFlag());
        dto.setRestrictedScope(productRow.getRestrictedScope());
        dto.setRestrictedEnum(productRow.getRestrictedEnum());
        dto.setRestrictedEnumDictText(productRow.getRestrictedEnumDictText());
        dto.setPartAttributeEnum(productRow.getPartAttributeEnum());
        dto.setPartAttributeEnumDictText(productRow.getPartAttributeEnumDictText());
        dto.setThoPartAttributeEnum(productRow.getThoPartAttributeEnum());
        dto.setThoPartAttributeEnumDictText(productRow.getThoPartAttributeEnumDictText());
        dto.setTotalVolume(productRow.getTotalVolume());
        dto.setSkinTestFlag(productRow.getSkinTestFlag());
        dto.setInjectFlag(productRow.getInjectFlag());
        if (StringUtils.isNotEmpty(productRow.getActivityTypeCode())) {
            dto.setActivityTypeCode(productRow.getActivityTypeCode());
        }
        dto.setDefaultPerformOrgId(productRow.getLocationId());
        dto.setDefaultPerformOrgName(productRow.getLocationName());
        return dto;
    }

    /**
     * 解析发药/耗材候选库位：按开立科室 + 物品表 + 目录 {@code categoryCode}（药品/耗材类别）+ 具体项 + 用法
     * 过滤模板适用行（calcScore≥0）；若请求携带药房库位且命中候选，则收窄为该单点库位。
     *
     * @param orgId               机构或科室所属机构，用于模板查询
     * @param productRow          当前产品行（表名、categoryCode、定义 id、默认用法参与模板匹配）
     * @param pharmacyLocationId 可选；前端指定「在某药房开」时传入
     * @return 候选库位 ID 列表；机构缺失时返回空列表表示不做库存展示
     */
    private List<Long> resolveCandidateLocations(Long orgId, ProductDefinitionDisplayDto productRow, Long pharmacyLocationId) {
        return clinicalCatalogDispenseLocationResolver.listApplicableCandidateLocationIds(
            orgId, productRow, pharmacyLocationId);
    }

    /**
     * IF-IP-DOR-11 读侧：按取药科室模板解析默认发药/发材库位。
     *
     * @param orgId      开立机构
     * @param productRow 产品读模型
     * @return 默认库位 id；目录读侧展示用
     */
    private Long resolveDefaultDispenseLocationId(Long orgId, ProductDefinitionDisplayDto productRow) {
        return clinicalCatalogDispenseLocationResolver.matchDefaultDispenseLocationId(orgId, productRow);
    }

    /**
     * 构建候选库位下已占用数量映射（拆零单位），键为 {@link ProductTableNameHelper#tripleCompositeKey}；不限就诊。
     *
     * @param productRow     当前目录产品（区分药品/耗材预占查询分支及单位换算）
     * @param candidateLocs  模板解析出的库位候选；空则直接返回空映射
     * @return 复合键 → 拆零单位预占数量；无发放时为空映射
     */
    private Map<String, BigDecimal> loadOccupancyMap(ProductDefinitionDisplayDto productRow, List<Long> candidateLocs) {
        Map<String, BigDecimal> acc = new LinkedHashMap<>();
        if (candidateLocs == null || candidateLocs.isEmpty()) {
            return new LinkedHashMap<>();
        }
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productRow.getProductTableName())) {
            List<MedicationDispenseOrderCatalogOccupancyModel> occRows = medicationDispenseQueryService.listMedicationDispensesForOrderCatalogOccupancy(
                candidateLocs, List.of(productRow.getProductDefinitionId()));
            return DoctorStationAdviceInventoryUnitQuantityHelper.mergeMedicationOccupancyInSplitUnits(
                occRows, productRow, productRow.getProductTableName());
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(productRow.getProductTableName())) {
            List<DeviceDispenseOrderCatalogOccupancyModel> occRows = deviceDispenseQueryService.listDeviceDispensesForOrderCatalogOccupancy(
                candidateLocs, List.of(productRow.getProductDefinitionId()));
            return DoctorStationAdviceInventoryUnitQuantityHelper.mergeDeviceOccupancyInSplitUnits(
                occRows, productRow, productRow.getProductTableName());
        }
        return new LinkedHashMap<>();
    }
}
