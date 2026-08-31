/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.assembler;

import com.openhis.health.application.shared.product.ClinicalCatalogInventoryPageBatch;
import com.openhis.health.application.shared.product.ClinicalCatalogInventoryPageBatchLoader;
import com.openhis.health.application.shared.product.ClinicalCatalogPriceResolver;
import com.openhis.health.application.shared.product.ClinicalCatalogUnitPriceQuery;
import com.openhis.health.application.shared.product.DoctorStationAdviceInventoryUnitQuantityHelper;
import com.openhis.health.application.shared.product.ProductTableNameHelper;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceCatalogRowDto;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceInventoryRowDto;
import com.openhis.health.application.outpatient.doctorstation.advice.request.PageDoctorStationAdviceCatalogRequest;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.application.shared.product.MedicationPackageSplitQuantityDisplayAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IF-DOR-11：医嘱开立目录分页装配（Asm：产品定义读模型 + 取药模板库位 ∩ 库存 + 同就诊发放预减 + 定价表选中单价）。
 * <p>复合业务键与读模型服务一致，使用 {@link GeneralSharedConstants#PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR}，避免与表名/编号中常见字符碰撞。</p>
 * <p><b>计价价型分流</b>：诊疗（{@link TableNameConstants#WKF_ACTIVITY_DEFINITION}）仅 {@link PriceType#SALE}；
 * 药品/耗材仅 {@link PriceType#LOT_NUMBER_PRICE}，且目录库存明细 {@link DoctorStationAdviceInventoryRowDto} 上单价按
 * {@code lot_no} 与库存 {@link DoctorStationAdviceInventoryRowDto#getLotNumber()} 对齐；汇总行的 {@link DoctorStationAdviceCatalogRowDto#getUnitPrice()}
 * 与首条可用库存批号一致。</p>
 */
@Component
@RequiredArgsConstructor
public class DoctorStationAdviceCatalogAssembler {

    /** 收费项定义（定价行）：当前页批量取价与 fallback 主键加载。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /** 临床目录统一单价解析（诊疗 SALE / 药耗批号售价）。 */
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;

    /** 当前页药耗库存/库位/预占批量预取（消 N+1）。 */
    private final ClinicalCatalogInventoryPageBatchLoader clinicalCatalogInventoryPageBatchLoader;

    /** 默认执行科室展示名（诊疗 org_id → 机构名称）。 */
    private final OrganizationQueryService organizationQueryService;

    /**
     * 装配目录分页结果（读模型分页已由调用方执行一次）。
     *
     * <p><b>规模（B.2）</b>：定价批量查询与库存/预占计算均按<strong>当前页</strong>内容收敛；
     * 若调用方传入超大 {@code pageSize}，单次装配 I/O 与内存会线性放大，应由接口层限制页大小。</p>
     *
     * @param request  请求
     * @param page      产品定义分页结果
     * @param snapshot 门诊就诊快照（患者/科室门控）
     * @return 当前页行
     */
    public List<DoctorStationAdviceCatalogRowDto> assemblePageContent(
        PageDoctorStationAdviceCatalogRequest request,
        Page<ProductDefinitionDisplayDto> page,
        OutpatientEncounterContextQueryModel snapshot
    ) {
        // Gate：请求患者必须与门诊快照一致，防止越权用他人就诊上下文套目录数据
        if (!request.getPatientId().equals(snapshot.getPatientId())) {
            return List.of();
        }
        List<ProductDefinitionDisplayDto> content = page.getContent();
        if (content.isEmpty()) {
            return List.of();
        }

        // Pre：当前页定价行一次拉齐，避免循环内逐条打定价查询
        Map<String, List<ChargeItemDefinition>> priceByKey = clinicalCatalogPriceResolver.loadPriceLinesGrouped(content);
        Long orgId = snapshot.getOrganizationId();
        if (request.getOrganizationUnitId() != null) {
            // 请求显式指定组织单元时覆盖快照机构，用于多院区模板解析
            orgId = request.getOrganizationUnitId();
        }

        // Pre：仅按定价行是否已可解析收集 fallback 主键，禁止调用 resolveCatalogUnitPrice（会触发模板+库存 N+1）
        LinkedHashSet<Long> fallbackChargeDefIds = new LinkedHashSet<>();
        for (ProductDefinitionDisplayDto pre : content) {
            if (pre.getChargeItemDefinitionId() == null) {
                continue;
            }
            String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                pre.getProductTableName(), pre.getProductDefinitionId());
            List<ChargeItemDefinition> preLines = priceByKey.getOrDefault(compositeKey, List.of());
            if (!clinicalCatalogPriceResolver.hasResolvableCatalogPriceFromLines(pre.getProductTableName(), preLines)) {
                fallbackChargeDefIds.add(pre.getChargeItemDefinitionId());
            }
        }
        Map<Long, ChargeItemDefinition> fallbackPriceByDefId =
            chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(fallbackChargeDefIds);

        // Pre：当前页药耗候选库位 + 库存 + 预占一次批量预取（机构模板只查一次）
        ClinicalCatalogInventoryPageBatch inventoryBatch =
            clinicalCatalogInventoryPageBatchLoader.preload(content, orgId, request.getPharmacyLocationId());

        List<DoctorStationAdviceCatalogRowDto> rows = new ArrayList<>();
        for (ProductDefinitionDisplayDto productRow : content) {
            DoctorStationAdviceCatalogRowDto dto = baseRow(productRow);
            String productCompositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                productRow.getProductTableName(), productRow.getProductDefinitionId());

            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productRow.getProductTableName())
                || TableNameConstants.ADM_DEVICE_DEFINITION.equals(productRow.getProductTableName())) {
                // 药品/耗材：使用预取库存 ∩ 候选库位，并扣减发放预占，得到「可开量」
                List<InventoryCatalogAvailabilityRowModel> physical =
                    inventoryBatch.getPhysicalRows(productCompositeKey);
                Map<String, BigDecimal> occ = inventoryBatch.getOccupancy(productCompositeKey);
                // 库位级预占按批次遍历顺序消耗一次，避免同库多批重复扣整库占用。
                Map<String, BigDecimal> remainingOcc =
                    DoctorStationAdviceInventoryUnitQuantityHelper.copyOccupancyRemaining(occ);
                List<DoctorStationAdviceInventoryRowDto> invDtos = new ArrayList<>();
                BigDecimal sumNetSplit = BigDecimal.ZERO;
                int order = 0;
                for (InventoryCatalogAvailabilityRowModel row : physical) {
                    BigDecimal net = DoctorStationAdviceInventoryUnitQuantityHelper.consumeLocationOccupancyForLot(
                        remainingOcc, row.getItemTable(), row.getItemId(), row.getLocationId(), row.getQuantity());
                    if (net.compareTo(BigDecimal.ZERO) <= 0) {
                        // 本批被预占消化完，不进入可开明细
                        continue;
                    }
                    DoctorStationAdviceInventoryRowDto inv = new DoctorStationAdviceInventoryRowDto();
                    inv.setItemTable(row.getItemTable());
                    inv.setItemId(row.getItemId());
                    inv.setCategoryCode(productRow.getCategoryCode());
                    inv.setInventoryId(row.getId());
                    // 库存明细 quantity 仍为拆零单位，供 FIFO/批号对齐；目录汇总 availableQuantity 在 unitBands 就绪后换算展示单位
                    inv.setQuantity(net);
                    inv.setUnitCode(productRow.getSplitUnitCode());
                    inv.setUnitName(productRow.getSplitUnitCodeDictText());
                    inv.setLotNumber(row.getLotNumber());
                    inv.setExpirationDate(row.getExpirationDate());
                    inv.setCreatedAt(row.getCreatedAt());
                    inv.setLocationId(row.getLocationId());
                    inv.setLocationStoreId(row.getLocationId());
                    inv.setDisplayOrder(order++);
                    invDtos.add(inv);
                    sumNetSplit = sumNetSplit.add(net);
                }
                dto.setInventoryList(invDtos);
                // 暂存拆零加总；availableQuantity 在定价与 unitBands 装配后按展示单位回填
                dto.setAvailableQuantity(sumNetSplit);
                // IF-DOR-11 读侧：模板 match 默认发药库位，供前端回传 IF-DOR-14 dispenseLocationId
                Long defaultDispenseLocationId = inventoryBatch.getDefaultDispenseLocationId(productCompositeKey);
                if (defaultDispenseLocationId != null) {
                    dto.setDefaultDispenseLocationId(defaultDispenseLocationId);
                }
            }

            // Asm：单价来自批量定价结果；药耗有库存时按「库存批号 ↔ 定价 lot_no」逐行回填，目录行单价与首条库存批号对齐
            List<ChargeItemDefinition> lines = priceByKey.getOrDefault(productCompositeKey, List.of());
            boolean headerPriceFromInventoryLots = false;
            ChargeItemDefinition catalogPriceLine = null;
            List<DoctorStationAdviceInventoryRowDto> inventoryRows = dto.getInventoryList();
            if (ProductTableNameHelper.isMedicationOrDevice(productRow.getProductTableName())
                && inventoryRows != null && !inventoryRows.isEmpty()) {
                Map<String, ChargeItemDefinition> lotPriceIndex = clinicalCatalogPriceResolver.indexLotNumberPriceRows(lines);
                for (DoctorStationAdviceInventoryRowDto invRow : inventoryRows) {
                    ChargeItemDefinition rowPriceLine = clinicalCatalogPriceResolver.resolveLotNumberPriceLineForLot(
                        lotPriceIndex, invRow.getLotNumber());
                    if (rowPriceLine != null) {
                        invRow.setPrice(rowPriceLine.getPriceValue());
                        invRow.setChargeItemDefinitionId(rowPriceLine.getId());
                    }
                }
                DoctorStationAdviceInventoryRowDto firstInv = inventoryRows.get(0);
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
                ChargeItemDefinition selected;
                if (ProductTableNameHelper.isMedicationOrDevice(productRow.getProductTableName())) {
                    // 药耗无库存或批号未命中价：有 FIFO 批号则按批号对价；否则取参考零售价 SALE
                    String leadingLot = null;
                    if (inventoryRows != null && !inventoryRows.isEmpty()) {
                        leadingLot = inventoryRows.get(0).getLotNumber();
                    }
                    if (StringUtils.isNotEmpty(leadingLot)) {
                        Map<String, ChargeItemDefinition> lotPriceIndex =
                            clinicalCatalogPriceResolver.indexLotNumberPriceRows(lines);
                        selected = clinicalCatalogPriceResolver.resolveLotNumberPriceLineForLot(
                            lotPriceIndex, leadingLot);
                        if (selected == null) {
                            // 批号对不上价：展示参考零售价，不用 priority 在批号价里择优
                            selected = clinicalCatalogPriceResolver.selectReferenceSalePriceLine(lines);
                        }
                    } else {
                        selected = clinicalCatalogPriceResolver.selectReferenceSalePriceLine(lines);
                    }
                } else {
                    // 诊疗：仅 SALE 择优，无库存 IO
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
                    selected = clinicalCatalogPriceResolver.resolveCatalogUnitPrice(priceCmd);
                }
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
                    // 药耗兜底：允许批号售价或参考零售价 SALE
                    if (fallback != null && ProductTableNameHelper.isMedicationOrDevice(productRow.getProductTableName())
                        && !PriceType.LOT_NUMBER_PRICE.getCode().equals(fallback.getPriceTypeCode())
                        && !PriceType.SALE.getCode().equals(fallback.getPriceTypeCode())) {
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
            // Asm：库存列展示对齐库存明细（满包整数+拆零余量）；数值 availableQuantity 仍换算包装单位供对比
            if (dto.getAvailableQuantity() != null) {
                // 此时 availableQuantity 仍为拆零净量加总，先写展示文案再换算包装数值
                dto.setAvailableQuantityText(
                    MedicationPackageSplitQuantityDisplayAssembler.buildDisplayQuantityText(
                        dto.getAvailableQuantity(), productRow));
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
    private void enrichDefaultPerformOrgNames(List<DoctorStationAdviceCatalogRowDto> rows) {
        Set<Long> orgIds = new LinkedHashSet<>();
        for (DoctorStationAdviceCatalogRowDto row : rows) {
            if (row.getDefaultPerformOrgId() != null) {
                orgIds.add(row.getDefaultPerformOrgId());
            }
        }
        if (orgIds.isEmpty()) {
            return;
        }
        Map<Long, String> orgNameById = organizationQueryService.mapNamesByIds(orgIds);
        for (DoctorStationAdviceCatalogRowDto row : rows) {
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
    private DoctorStationAdviceCatalogRowDto baseRow(ProductDefinitionDisplayDto productRow) {
        DoctorStationAdviceCatalogRowDto dto = new DoctorStationAdviceCatalogRowDto();
        dto.setProductDefinitionId(productRow.getProductDefinitionId());
        dto.setProductTableName(productRow.getProductTableName());
        dto.setBusNo(productRow.getProductBusNo());
        dto.setName(productRow.getProductName());
        // 商品名：药品主档 merchandise_name；非药品为空
        dto.setMerchandiseName(productRow.getMerchandiseName());
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
        // 中药特殊煎法：目录维护值，选品时默认带入方明细
        dto.setDosageInstruction(productRow.getDosageInstruction());
        dto.setDosageInstructionDictText(productRow.getDosageInstructionDictText());
        dto.setDoseFormCode(productRow.getDoseFormCode());
        dto.setDoseFormCodeDictText(productRow.getDoseFormCodeDictText());
        // 药品目录默认/最小/最大单次剂量（剂量单位），供开立页默认带出
        dto.setDose(productRow.getDose());
        dto.setMinUnit(productRow.getMinUnit());
        dto.setMaxUnit(productRow.getMaxUnit());
        dto.setRestrictedFlag(productRow.getRestrictedFlag());
        dto.setRestrictedScope(productRow.getRestrictedScope());
        dto.setRestrictedEnum(productRow.getRestrictedEnum());
        dto.setRestrictedEnumDictText(productRow.getRestrictedEnumDictText());
        dto.setPartAttributeEnum(productRow.getPartAttributeEnum());
        dto.setPartAttributeEnumDictText(productRow.getPartAttributeEnumDictText());
        dto.setThoPartAttributeEnum(productRow.getThoPartAttributeEnum());
        dto.setThoPartAttributeEnumDictText(productRow.getThoPartAttributeEnumDictText());
        dto.setTotalVolume(productRow.getTotalVolume());
        dto.setManufacturer(productRow.getManufacturer());
        dto.setSkinTestFlag(productRow.getSkinTestFlag());
        dto.setInjectFlag(productRow.getInjectFlag());
        if (StringUtils.isNotEmpty(productRow.getActivityTypeCode())) {
            dto.setActivityTypeCode(productRow.getActivityTypeCode());
        }
        dto.setDefaultPerformOrgId(productRow.getLocationId());
        dto.setDefaultPerformOrgName(productRow.getLocationName());
        // 医保等级：读模型已回填 chrgitmLvText，开立页单价旁展示
        dto.setChrgitmLv(productRow.getChrgitmLv());
        dto.setChrgitmLvText(productRow.getChrgitmLvText());
        return dto;
    }
}
