/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.advice.assembler;

import com.zxplat.application.shared.product.ClinicalCatalogInventoryPageBatch;
import com.zxplat.application.shared.product.ClinicalCatalogInventoryPageBatchLoader;
import com.zxplat.application.shared.product.ClinicalCatalogPriceResolver;
import com.zxplat.application.shared.product.ClinicalCatalogUnitPriceQuery;
import com.zxplat.application.shared.product.DoctorStationAdviceInventoryUnitQuantityHelper;
import com.zxplat.application.shared.product.ProductTableNameHelper;
import com.zxplat.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceCatalogRowDto;
import com.zxplat.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceInventoryRowDto;
import com.zxplat.application.inpatient.doctorstation.advice.request.PageInpatientDoctorStationAdviceCatalogRequest;
import com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.zxplat.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.share.enums.PriceType;
import com.zxplat.application.shared.product.MedicationPackageSplitQuantityDisplayAssembler;
import com.zxplat.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.zxplat.application.shared.advice.AdviceExtensionJsonParser;
import com.zxplat.application.shared.advice.AdviceExtensionJsonParser.AdvicePackageChildItemDto;
import com.zxplat.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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

    /** 收费项定义（定价行）：当前页批量取价与 fallback 主键加载。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /** 临床目录统一单价解析（诊疗 SALE / 药耗批号售价）。 */
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;

    /** 当前页药耗库存/库位/预占批量预取（消 N+1）。 */
    private final ClinicalCatalogInventoryPageBatchLoader clinicalCatalogInventoryPageBatchLoader;

    /** 默认执行科室展示名（诊疗 org_id → 机构名称）。 */
    private final OrganizationQueryService organizationQueryService;

    /** 诊疗活动定义只读：目录行挂载带子项目录（选品列表不展示，签发打印用）。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

    /**
     * IF-IP-TCM-35：装配饮片目录分页（不下发库存明细/可开量；单价仍按 FIFO 首条可用批号匹配批号售价）。
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
        // Gate：请求患者必须与住院快照一致
        if (!request.getPatientId().equals(snapshot.getPatientId())) {
            return List.of();
        }
        List<ProductDefinitionDisplayDto> content = page.getContent();
        if (content.isEmpty()) {
            return List.of();
        }
        // Pre：定价行批量加载；fallback 仅按定价行是否可解析收集，禁止预扫 resolveCatalogUnitPrice
        Map<String, List<ChargeItemDefinition>> priceByKey = clinicalCatalogPriceResolver.loadPriceLinesGrouped(content);
        Long orgId = snapshot.getOrganizationId();
        if (request.getOrganizationUnitId() != null) {
            orgId = request.getOrganizationUnitId();
        }
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
        // Pre：批量预取候选库位 + 库存 + 预占（取价仍走 FIFO 首批号；本路径不下发 inventoryList）
        ClinicalCatalogInventoryPageBatch inventoryBatch =
            clinicalCatalogInventoryPageBatchLoader.preload(content, orgId, request.getPharmacyLocationId());
        List<InpatientDoctorStationAdviceCatalogRowDto> rows = new ArrayList<>();
        for (ProductDefinitionDisplayDto productRow : content) {
            InpatientDoctorStationAdviceCatalogRowDto dto = baseRow(productRow);
            // 饮片目录：不展示库存明细与可开量，但默认发药点仍回填
            dto.setInventoryList(List.of());
            dto.setAvailableQuantity(null);
            String productCompositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                productRow.getProductTableName(), productRow.getProductDefinitionId());
            Long defaultDispenseLocationId = inventoryBatch.getDefaultDispenseLocationId(productCompositeKey);
            if (defaultDispenseLocationId != null) {
                dto.setDefaultDispenseLocationId(defaultDispenseLocationId);
            }
            List<ChargeItemDefinition> lines = priceByKey.getOrDefault(productCompositeKey, List.of());
            // Asm：药耗单价 = FIFO 首条净可用库存批号 ↔ LOT_NUMBER_PRICE；禁止用 priority 代替批号匹配
            ChargeItemDefinition selected = resolveMedicationOrDeviceCatalogPriceByFifoLot(
                productRow, lines, inventoryBatch, productCompositeKey, orgId, request);
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
        this.attachPackageChildren(rows);
        return rows;
    }

    /**
     * 药耗目录单价：优先用预取库存中 FIFO 首条净可用批号匹配 {@link PriceType#LOT_NUMBER_PRICE}；
     * 无可用库存（或批号对不上价）时取 {@link PriceType#SALE} 参考零售价展示。
     *
     * @param productRow           产品读模型
     * @param lines                已批量加载的定价行
     * @param inventoryBatch       当前页库存预取
     * @param productCompositeKey  产品复合键
     * @param orgId                开立机构
     * @param request              目录请求（药房/就诊上下文）
     * @return 命中的定价行；无命中为 null
     */
    private ChargeItemDefinition resolveMedicationOrDeviceCatalogPriceByFifoLot(
        ProductDefinitionDisplayDto productRow,
        List<ChargeItemDefinition> lines,
        ClinicalCatalogInventoryPageBatch inventoryBatch,
        String productCompositeKey,
        Long orgId,
        PageInpatientDoctorStationAdviceCatalogRequest request
    ) {
        if (!ProductTableNameHelper.isMedicationOrDevice(productRow.getProductTableName())) {
            // 诊疗：仅 SALE，无 FIFO
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
            return clinicalCatalogPriceResolver.resolveCatalogUnitPrice(priceCmd);
        }
        // 药耗：FIFO 首条净可用批号 → LOT_NUMBER_PRICE；对不上或无库存 → SALE 参考价
        String leadingLot = findLeadingNetAvailableLotNumber(inventoryBatch, productCompositeKey);
        if (StringUtils.isNotEmpty(leadingLot)) {
            Map<String, ChargeItemDefinition> lotPriceIndex =
                clinicalCatalogPriceResolver.indexLotNumberPriceRows(lines);
            ChargeItemDefinition lotMatched =
                clinicalCatalogPriceResolver.resolveLotNumberPriceLineForLot(lotPriceIndex, leadingLot);
            if (lotMatched != null) {
                return lotMatched;
            }
        }
        return clinicalCatalogPriceResolver.selectReferenceSalePriceLine(lines);
    }

    /**
     * 从预取库存中取 FIFO 首条净可用批号（扣发放预占后 quantity&gt;0；行序与库存读侧效期/创建时间 FIFO 一致）。
     *
     * @param inventoryBatch      当前页预取
     * @param productCompositeKey 产品复合键
     * @return 批号；无可用行为 null
     */
    private String findLeadingNetAvailableLotNumber(
        ClinicalCatalogInventoryPageBatch inventoryBatch,
        String productCompositeKey
    ) {
        List<InventoryCatalogAvailabilityRowModel> physical = inventoryBatch.getPhysicalRows(productCompositeKey);
        Map<String, BigDecimal> occupancy = inventoryBatch.getOccupancy(productCompositeKey);
        Map<String, BigDecimal> remainingOcc =
            DoctorStationAdviceInventoryUnitQuantityHelper.copyOccupancyRemaining(occupancy);
        for (InventoryCatalogAvailabilityRowModel row : physical) {
            if (row == null) {
                // 预取行可能含空槽，跳过以免空指针中断首批解析
                continue;
            }
            BigDecimal net = DoctorStationAdviceInventoryUnitQuantityHelper.consumeLocationOccupancyForLot(
                remainingOcc, row.getItemTable(), row.getItemId(), row.getLocationId(), row.getQuantity());
            // 扣完本批能消化的预占后仍有量，即为 FIFO 首条净可用批。
            if (net.compareTo(BigDecimal.ZERO) > 0) {
                return row.getLotNumber();
            }
        }
        return null;
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
        // Gate：请求患者必须与住院快照一致，防止越权用他人就诊上下文套目录数据
        if (!request.getPatientId().equals(snapshot.getPatientId())) {
            return List.of();
        }
        List<ProductDefinitionDisplayDto> content = page.getContent();
        if (content.isEmpty()) {
            return List.of();
        }

        // Pre：当前页定价行一次拉齐
        Map<String, List<ChargeItemDefinition>> priceByKey = clinicalCatalogPriceResolver.loadPriceLinesGrouped(content);
        Long orgId = snapshot.getOrganizationId();
        if (request.getOrganizationUnitId() != null) {
            orgId = request.getOrganizationUnitId();
        }

        // Pre：仅按定价行是否已可解析收集 fallback，禁止 resolveCatalogUnitPrice 触发模板+库存 N+1
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

        // Pre：当前页药耗候选库位 + 库存 + 预占一次批量预取
        ClinicalCatalogInventoryPageBatch inventoryBatch =
            clinicalCatalogInventoryPageBatchLoader.preload(content, orgId, request.getPharmacyLocationId());

        List<InpatientDoctorStationAdviceCatalogRowDto> rows = new ArrayList<>();
        for (ProductDefinitionDisplayDto productRow : content) {
            InpatientDoctorStationAdviceCatalogRowDto dto = baseRow(productRow);
            String productCompositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                productRow.getProductTableName(), productRow.getProductDefinitionId());

            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(productRow.getProductTableName())
                || TableNameConstants.ADM_DEVICE_DEFINITION.equals(productRow.getProductTableName())) {
                // 药品/耗材：使用预取库存 ∩ 候选库位，并扣减发放预占
                List<InventoryCatalogAvailabilityRowModel> physical =
                    inventoryBatch.getPhysicalRows(productCompositeKey);
                Map<String, BigDecimal> occ = inventoryBatch.getOccupancy(productCompositeKey);
                // 库位级预占按批次遍历顺序消耗一次，避免同库多批重复扣整库占用。
                Map<String, BigDecimal> remainingOcc =
                    DoctorStationAdviceInventoryUnitQuantityHelper.copyOccupancyRemaining(occ);
                List<InpatientDoctorStationAdviceInventoryRowDto> invDtos = new ArrayList<>();
                BigDecimal sumNetSplit = BigDecimal.ZERO;
                int order = 0;
                for (InventoryCatalogAvailabilityRowModel row : physical) {
                    BigDecimal net = DoctorStationAdviceInventoryUnitQuantityHelper.consumeLocationOccupancyForLot(
                        remainingOcc, row.getItemTable(), row.getItemId(), row.getLocationId(), row.getQuantity());
                    if (net.compareTo(BigDecimal.ZERO) <= 0) {
                        // 本批被预占消化完，不进入可开明细
                        continue;
                    }
                    InpatientDoctorStationAdviceInventoryRowDto inv = new InpatientDoctorStationAdviceInventoryRowDto();
                    inv.setItemTable(row.getItemTable());
                    inv.setItemId(row.getItemId());
                    inv.setCategoryCode(productRow.getCategoryCode());
                    inv.setInventoryId(row.getId());
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
                dto.setAvailableQuantity(sumNetSplit);
                Long defaultDispenseLocationId = inventoryBatch.getDefaultDispenseLocationId(productCompositeKey);
                if (defaultDispenseLocationId != null) {
                    dto.setDefaultDispenseLocationId(defaultDispenseLocationId);
                }
            }

            // Asm：单价来自批量定价；药耗有库存时按批号对齐
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
                ChargeItemDefinition selected;
                if (ProductTableNameHelper.isMedicationOrDevice(productRow.getProductTableName())) {
                    String leadingLot = null;
                    if (inventoryRows != null && !inventoryRows.isEmpty()) {
                        leadingLot = inventoryRows.get(0).getLotNumber();
                    }
                    if (StringUtils.isNotEmpty(leadingLot)) {
                        // 有 FIFO 批号：按批号匹配 LOT_NUMBER_PRICE；对不上则参考零售价 SALE
                        Map<String, ChargeItemDefinition> lotPriceIndex =
                            clinicalCatalogPriceResolver.indexLotNumberPriceRows(lines);
                        selected = clinicalCatalogPriceResolver.resolveLotNumberPriceLineForLot(
                            lotPriceIndex, leadingLot);
                        if (selected == null) {
                            selected = clinicalCatalogPriceResolver.selectReferenceSalePriceLine(lines);
                        }
                    } else {
                        // 无可用库存：展示参考零售价 SALE（开不出，不按批号 priority）
                        selected = clinicalCatalogPriceResolver.selectReferenceSalePriceLine(lines);
                    }
                } else {
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
        this.attachPackageChildren(rows);
        return rows;
    }

    /**
     * 批量回填默认执行科室展示名（{@code defaultPerformOrgId} 为机构主键，非库位）。
     *
     * @param rows 当前页目录行
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
     * 当前页诊疗目录批量挂载带子项（禁逐行查库；选品列表不展示，签发打印展开用）。
     *
     * @param rows 当前页目录行
     */
    private void attachPackageChildren(List<InpatientDoctorStationAdviceCatalogRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> activityIds = new LinkedHashSet<>();
        for (InpatientDoctorStationAdviceCatalogRowDto row : rows) {
            if (row == null || row.getProductDefinitionId() == null) {
                continue;
            }
            // 仅诊疗活动定义才有目录子项
            if (!TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(row.getProductTableName())) {
                continue;
            }
            activityIds.add(row.getProductDefinitionId());
        }
        if (activityIds.isEmpty()) {
            return;
        }
        Map<Long, List<ActivityDefinitionChildQueryModel>> modelsByActivityId =
            this.activityDefinitionQueryService.listChildrenByActivityIds(activityIds);
        Map<Long, List<AdvicePackageChildItemDto>> childrenByActivityId = new HashMap<>();
        for (Map.Entry<Long, List<ActivityDefinitionChildQueryModel>> entry : modelsByActivityId.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            childrenByActivityId.put(entry.getKey(), AdviceExtensionJsonParser.toPackageChildItems(entry.getValue()));
        }
        for (InpatientDoctorStationAdviceCatalogRowDto row : rows) {
            if (row == null || row.getProductDefinitionId() == null) {
                continue;
            }
            List<AdvicePackageChildItemDto> found = childrenByActivityId.get(row.getProductDefinitionId());
            if (found == null) {
                row.setPackageChildren(Collections.emptyList());
            } else {
                // 有子项：挂给签发打印；目录表格仍只显示父项名
                row.setPackageChildren(found);
            }
        }
    }

    /**
     * 从产品读模型装配目录行「与库存、定价无关」的基础展示字段。
     *
     * @param productRow 当前页一条产品定义读模型
     * @return 已填充基础字段的 DTO，不含库存与定价单价
     */
    private InpatientDoctorStationAdviceCatalogRowDto baseRow(ProductDefinitionDisplayDto productRow) {
        InpatientDoctorStationAdviceCatalogRowDto dto = new InpatientDoctorStationAdviceCatalogRowDto();
        dto.setProductDefinitionId(productRow.getProductDefinitionId());
        dto.setProductTableName(productRow.getProductTableName());
        dto.setBusNo(productRow.getProductBusNo());
        // 目录排序号透出；药品等无该字段时保持 null
        dto.setSortOrder(productRow.getSortOrder());
        dto.setName(productRow.getProductName());
        // 商品名：药品主档 merchandise_name；非药品为空
        dto.setMerchandiseName(productRow.getMerchandiseName());
        dto.setPyCode(productRow.getPyStr());
        dto.setWbCode(productRow.getWbStr());
        dto.setCategoryCode(productRow.getCategoryCode());
        dto.setCategoryCodeDictText(productRow.getCategoryCodeDictText());
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
        // 申请单分类 chips：业务类型码 + 术语展示名（与目录分类 categoryCode 独立）
        if (StringUtils.isNotEmpty(productRow.getActivityTypeCode())) {
            dto.setActivityTypeCode(productRow.getActivityTypeCode());
        }
        dto.setActivityTypeDictText(productRow.getActivityTypeDictText());
        dto.setDefaultPerformOrgId(productRow.getLocationId());
        dto.setDefaultPerformOrgName(productRow.getLocationName());
        // 医保等级：读模型已回填 chrgitmLvText，开立页单价旁展示
        dto.setChrgitmLv(productRow.getChrgitmLv());
        dto.setChrgitmLvText(productRow.getChrgitmLvText());
        return dto;
    }
}
