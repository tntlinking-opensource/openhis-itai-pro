/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dispense.support;

import com.openhis.health.application.shared.option.FrequencyOptionLoader;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.advice.OrderCatalogDisplayAssembler;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.shared.pharmacy.MedicationDispenseSlipQueryLoader;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.EncounterMedicationDispenseSlipRowDto;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.MedicationDispenseInventoryDetailDto;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.MedicationDispenseSlipPageResult;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseSlipPageQuery;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.application.shared.product.ClinicalCatalogDispenseLocationResolver;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.DoctorStationAdviceScopeTab;
import com.openhis.health.domain.share.constants.MedicationCommonConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
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
 * 就诊药品发药单 slip 分页读路径（门诊 IF-OPP-03 / 住院 IF-IPP-03），供 AppService 与 report-adapter 共用。
 */
@Component
@RequiredArgsConstructor
public class OutpatientEncounterMedicationDispenseSlipPageLoader {

    private final EncounterContextReader encounterContextReader;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final ClinicalCatalogDispenseLocationResolver clinicalCatalogDispenseLocationResolver;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final LocationQueryService locationQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final MedicationDispenseSlipQueryLoader medicationDispenseSlipQueryLoader;
    private final EncounterMedicationDispenseSlipReadAssembler encounterMedicationDispenseSlipReadAssembler;
    private final EncounterMedicationDispenseSlipPrintEnricher encounterMedicationDispenseSlipPrintEnricher;
    private final FrequencyOptionLoader frequencyOptionLoader;
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * 门诊就诊药品发药单 slip 分页（IF-OPP-03）。
     *
     * @param query 分页与过滤入参；{@code null} 时使用默认空查询
     * @return slip 行分页结果（含库存候选、定价与打印 enrich）
     */
    public MedicationDispenseSlipPageResult pageOutpatientEncounterMedicationDispenseSlips(
            EncounterMedicationDispenseSlipLoaderQuery query) {
        if (query == null) {
            query = new EncounterMedicationDispenseSlipLoaderQuery();
        }
        // Pre：判断是否患者历史 Tab（跨就诊查 slip，不加载当前就诊快照）
        boolean patientHistoryScope = isPatientHistoryScope(query);
        OutpatientEncounterContextQueryModel outpatientSnapshot = null;
        if (query.getEncounterId() != null && !patientHistoryScope) {
            // Pre：经 EncounterContextFacade 读门诊就诊快照（B.6.4）
            outpatientSnapshot =
                encounterContextReader.getOutpatientContextByEncounterId(query.getEncounterId()).orElse(null);
        }
        String encounterBusNo = null;
        Long receptionOrganizationId = null;
        if (outpatientSnapshot != null) {
            encounterBusNo = outpatientSnapshot.getEncounterBusNo();
            receptionOrganizationId = outpatientSnapshot.getOrganizationId();
        }

        MedicationDispenseSlipPageQuery slipQuery =
            toDomainSlipQuery(query);
        slipQuery.setOutpatientMedicationOnly(true);
        medicationDispenseSlipQueryLoader.enrichResolvedFilters(slipQuery);
        Page<MedicationDispenseSlipQueryModel> slipPage =
            medicationDispenseQueryService.pageSlip(slipQuery);

        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId;
        if (patientHistoryScope) {
            // 历史医嘱：按 slip 行上的药嘱主键批量索引，不再加载当前就诊全量药嘱。
            medicationRequestByMedReqId = indexMedicationRequestsByMedReqIds(slipPage.getContent());
        } else {
            medicationRequestByMedReqId = indexMedicationRequests(query.getEncounterId());
        }
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId =
            loadProductByMedicationId(slipPage.getContent());
        Map<Long, List<MedicationDispenseInventoryDetailDto>> inventoryDetailListByDispenseId;
        if (patientHistoryScope) {
            inventoryDetailListByDispenseId = Collections.emptyMap();
        } else {
            inventoryDetailListByDispenseId = buildOutpatientInventoryDetails(slipPage.getContent(), query,
                medicationRequestByMedReqId, productByMedicationId, receptionOrganizationId);
            enrichInventoryDisplayNames(inventoryDetailListByDispenseId);
        }

        PractitionerNameBundle names = resolvePractitionerAndLocationNames(
            slipPage.getContent(), medicationRequestByMedReqId);
        List<Long> slipDispenseIds = collectDispenseIds(slipPage.getContent());
        Map<Long, BigDecimal> chargeUnitPriceByDispenseId = new HashMap<>();
        Map<Long, BigDecimal> chargeTotalPriceByDispenseId = new HashMap<>();
        if (patientHistoryScope) {
            mergeChargePricingByEncounterGroups(slipPage.getContent(), chargeUnitPriceByDispenseId,
                chargeTotalPriceByDispenseId);
        } else {
            List<ChargeItem> chargeRowsForSlip = chargeItemQueryService.listChargeItemsForMedicationDispensePricing(
                query.getEncounterId(), slipDispenseIds);
            mergeChargePricingFromChargeItems(chargeRowsForSlip, chargeUnitPriceByDispenseId,
                chargeTotalPriceByDispenseId);
        }
        Map<Long, String> traceNoEffectiveByDispenseId = loadTraceNos(slipDispenseIds);

        Map<String, String> rateLabelByCode = frequencyOptionLoader.buildRateLabelMap();
        Map<String, String> methodLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE);
        Map<String, String> tcmMethodLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.TCM_METHOD_CODE);
        Map<String, String> doseUnitLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSE_UNIT);
        Map<String, String> unitLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        Map<String, String> rxFlagLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.RX_FLAG);
        Map<String, String> partAttributeLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.PART_ATTRIBUTE_ENUM);
        Map<String, String> dosageInstructionLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSAGE_INSTRUCTION);
        MedicationDispenseSlipPageResult result = encounterMedicationDispenseSlipReadAssembler.assembleOutpatientSlipPage(
            slipPage,
            medicationRequestByMedReqId,
            productByMedicationId,
            inventoryDetailListByDispenseId,
            query.getTcmOnly(),
            query.getWesternOnly(),
            names.dispensingPractitionerNameByDispenseId(),
            encounterBusNo,
            names.orderingDepartmentNameByOrgId(),
            names.orderingPractitionerNameById(),
            names.dispenseLocationNameByLocationId(),
            chargeUnitPriceByDispenseId,
            chargeTotalPriceByDispenseId,
            traceNoEffectiveByDispenseId,
            rateLabelByCode,
            methodLabelByCode,
            tcmMethodLabelByCode,
            doseUnitLabelByCode,
            unitLabelByCode,
            rxFlagLabelByCode,
            partAttributeLabelByCode,
            dosageInstructionLabelByCode);
        if (patientHistoryScope) {
            applyEncounterBusNoPerRow(result.getContent());
        } else if (result.getContent() != null && !result.getContent().isEmpty()) {
            Map<Long, String> preparerPractitionerNameByDispenseId =
                resolvePreparerPractitionerNames(slipPage.getContent());
            encounterMedicationDispenseSlipPrintEnricher.enrichOutpatientPrintFields(
                result.getContent(), outpatientSnapshot, preparerPractitionerNameByDispenseId);
        }
        return result;
    }

    /**
     * 判定是否为医生站「历史医嘱」查询范围（须同时携带患者与当前就诊排除锚点）。
     *
     * @param query slip 分页入参
     * @return {@code true} 时按 {@code patientId} 查全量并排除 {@code encounterId}
     */
    private static boolean isPatientHistoryScope(EncounterMedicationDispenseSlipLoaderQuery query) {
        return query != null
            && DoctorStationAdviceScopeTab.HISTORY.equals(query.getAdviceScopeTabCode())
            && query.getPatientId() != null
            && query.getEncounterId() != null;
    }

    /**
     * 应用层 slip 查询 → 领域 {@link com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseSlipPageQuery}（含历史医嘱排除锚点）。
     *
     * @param query 应用层分页入参
     * @return 领域分页查询
     */
    private static com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseSlipPageQuery toDomainSlipQuery(
            EncounterMedicationDispenseSlipLoaderQuery query) {
        com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseSlipPageQuery slipQuery =
            new com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseSlipPageQuery();
        if (isPatientHistoryScope(query)) {
            slipQuery.setPatientId(query.getPatientId());
            slipQuery.setExcludeEncounterId(query.getEncounterId());
        } else {
            slipQuery.setEncounterId(query.getEncounterId());
            if (query.getPatientId() != null) {
                slipQuery.setPatientId(query.getPatientId());
            }
        }
        slipQuery.setLocationId(query.getLocationId());
        slipQuery.setLocationIds(query.getLocationIds());
        slipQuery.setOrderingDepartmentId(query.getOrderingDepartmentId());
        if (query.getDispenseStatusEnumIn() != null && !query.getDispenseStatusEnumIn().isEmpty()) {
            slipQuery.setDispenseStatusEnumIn(query.getDispenseStatusEnumIn());
        }
        slipQuery.setRequestStatusEnumIn(query.getRequestStatusEnumIn());
        PageQueryBinder.bind(query, slipQuery);
        return slipQuery;
    }

    /**
     * 单就诊下全部用药请求 → medReqId 索引 Map。
     *
     * @param encounterId 就诊主键
     * @return medReqId → 用药请求读模型
     */
    private Map<Long, MedicationRequestListQueryModel> indexMedicationRequests(Long encounterId) {
        List<MedicationRequestListQueryModel> medicationRequestRows;
        if (encounterId != null) {
            medicationRequestRows =
                medicationRequestQueryService.listMedicationRequestsByEncounter(encounterId);
        } else {
            medicationRequestRows = List.of();
        }
        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId = new HashMap<>();
        for (MedicationRequestListQueryModel row : medicationRequestRows) {
            if (row.getId() != null) {
                medicationRequestByMedReqId.put(row.getId(), row);
            }
        }
        return medicationRequestByMedReqId;
    }

    /**
     * 从 slip 行收集 medReqId 并批量索引用药请求读模型。
     *
     * @param slipRows 发药 slip 读模型行
     * @return medReqId → 用药请求读模型
     */
    private Map<Long, MedicationRequestListQueryModel> indexMedicationRequestsByMedReqIds(
            List<MedicationDispenseSlipQueryModel> slipRows) {
        LinkedHashSet<Long> medReqIds = new LinkedHashSet<>();
        for (MedicationDispenseSlipQueryModel slipRow : slipRows) {
            if (slipRow != null && slipRow.getMedReqId() != null) {
                medReqIds.add(slipRow.getMedReqId());
            }
        }
        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId = new HashMap<>();
        for (MedicationRequestListQueryModel row
            : medicationRequestQueryService.listMedicationRequestListModelsByIds(medReqIds)) {
            if (row != null && row.getId() != null) {
                medicationRequestByMedReqId.put(row.getId(), row);
            }
        }
        return medicationRequestByMedReqId;
    }

    /**
     * 按就诊分组批量加载发药定价 ChargeItem 并合并至单价/总价 Map。
     *
     * @param slipRows                    slip 行
     * @param chargeUnitPriceByDispenseId 输出：dispenseId → 单价
     * @param chargeTotalPriceByDispenseId 输出：dispenseId → 总价
     */
    private void mergeChargePricingByEncounterGroups(
            List<MedicationDispenseSlipQueryModel> slipRows,
            Map<Long, BigDecimal> chargeUnitPriceByDispenseId,
            Map<Long, BigDecimal> chargeTotalPriceByDispenseId) {
        Map<Long, List<Long>> dispenseIdsByEncounterId = new HashMap<>();
        for (MedicationDispenseSlipQueryModel slipRow : slipRows) {
            if (slipRow == null || slipRow.getDispenseId() == null || slipRow.getEncounterId() == null) {
                continue;
            }
            dispenseIdsByEncounterId
                .computeIfAbsent(slipRow.getEncounterId(), ignored -> new ArrayList<>())
                .add(slipRow.getDispenseId());
        }
        for (Map.Entry<Long, List<Long>> entry : dispenseIdsByEncounterId.entrySet()) {
            List<ChargeItem> chargeRows = chargeItemQueryService.listChargeItemsForMedicationDispensePricing(
                entry.getKey(), entry.getValue());
            mergeChargePricingFromChargeItems(chargeRows, chargeUnitPriceByDispenseId, chargeTotalPriceByDispenseId);
        }
    }

    /**
     * 批量解析各行 encounterBusNo 并回填 DTO（门诊就诊上下文）。
     *
     * @param rows 发药 slip 展示行
     */
    private void applyEncounterBusNoPerRow(List<EncounterMedicationDispenseSlipRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        if (encounterIds.isEmpty()) {
            return;
        }
        Map<Long, String> encounterBusNoByEncounterId = new HashMap<>();
        for (OutpatientEncounterContextQueryModel context
            : encounterContextReader.listOutpatientContextsByEncounterIdsOrdered(new ArrayList<>(encounterIds))) {
            if (context != null && context.getEncounterId() != null) {
                encounterBusNoByEncounterId.put(context.getEncounterId(), context.getEncounterBusNo());
            }
        }
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            row.setEncounterBusNo(encounterBusNoByEncounterId.get(row.getEncounterId()));
        }
    }

    /**
     * 从 slip 行批量加载药品目录展示并按 medicationId 索引。
     *
     * @param slipRows 发药 slip 读模型行
     * @return medicationId → 产品展示 DTO
     */
    private Map<Long, ProductDefinitionDisplayDto> loadProductByMedicationId(
            List<MedicationDispenseSlipQueryModel> slipRows) {
        List<ProductDefinitionBatchLookup> productLookups = new ArrayList<>();
        for (MedicationDispenseSlipQueryModel slipRow : slipRows) {
            if (slipRow == null || slipRow.getMedicationId() == null) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(slipRow.getMedicationId());
            productLookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> productRowsByKey =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(productLookups);
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId = new HashMap<>();
        for (MedicationDispenseSlipQueryModel slipRow : slipRows) {
            if (slipRow == null || slipRow.getMedicationId() == null) {
                continue;
            }
            String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, slipRow.getMedicationId());
            ProductDefinitionDisplayDto productRow = productRowsByKey.get(compositeKey);
            if (productRow != null) {
                productByMedicationId.put(slipRow.getMedicationId(), productRow);
            }
        }
        return productByMedicationId;
    }

    /**
     * 门诊发药 slip：按候选药房 location 解析各 dispense 可用库存明细（批号/可用量/单位）。
     *
     * @param slipRows                   slip 行
     * @param query                      加载入参（location 回落用）
     * @param medicationRequestByMedReqId 用药请求索引
     * @param productByMedicationId      药品目录索引
     * @param receptionOrganizationId      接诊机构（候选 location 模板）
     * @return dispenseId → 库存明细列表
     */
    private Map<Long, List<MedicationDispenseInventoryDetailDto>> buildOutpatientInventoryDetails(
            List<MedicationDispenseSlipQueryModel> slipRows,
            EncounterMedicationDispenseSlipLoaderQuery query,
            Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId,
            Map<Long, ProductDefinitionDisplayDto> productByMedicationId,
            Long receptionOrganizationId) {
        Map<Long, List<MedicationDispenseInventoryDetailDto>> inventoryDetailListByDispenseId = new HashMap<>();
        for (MedicationDispenseSlipQueryModel slipRow : slipRows) {
            if (slipRow == null || slipRow.getDispenseId() == null) {
                continue;
            }
            MedicationRequestListQueryModel medicationRequestRow = medicationRequestByMedReqId.get(slipRow.getMedReqId());
            Long templateOrganizationId = receptionOrganizationId;
            if (medicationRequestRow != null && medicationRequestRow.getOrgId() != null) {
                templateOrganizationId = medicationRequestRow.getOrgId();
            }
            ProductDefinitionDisplayDto productRow = productByMedicationId.get(slipRow.getMedicationId());
            String distributionCategoryCode = MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE;
            if (productRow != null && StringUtils.isNotEmpty(productRow.getCategoryCode())) {
                distributionCategoryCode = productRow.getCategoryCode();
            }
            String methodCode = null;
            if (medicationRequestRow != null) {
                methodCode = medicationRequestRow.getMethodCode();
            }
            List<Long> candidateLocationIds = clinicalCatalogDispenseLocationResolver.listApplicableCandidateLocationIds(
                templateOrganizationId,
                TableNameConstants.MED_MEDICATION_DEFINITION,
                distributionCategoryCode,
                slipRow.getMedicationId(),
                methodCode,
                null);
            if (candidateLocationIds.isEmpty()) {
                candidateLocationIds = new ArrayList<>();
                if (query.getLocationId() != null) {
                    candidateLocationIds.add(query.getLocationId());
                }
            }
            List<InventoryCatalogAvailabilityRowModel> inventoryPhysicalRows =
                inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                    TableNameConstants.MED_MEDICATION_DEFINITION,
                    List.of(slipRow.getMedicationId()),
                    candidateLocationIds);
            List<MedicationDispenseInventoryDetailDto> inventoryDetailDtos = new ArrayList<>();
            for (InventoryCatalogAvailabilityRowModel inventoryPhysicalRow : inventoryPhysicalRows) {
                if (inventoryPhysicalRow == null) {
                    continue;
                }
                MedicationDispenseInventoryDetailDto inventoryDetailDto = new MedicationDispenseInventoryDetailDto();
                inventoryDetailDto.setPharmacyOrganizationId(inventoryPhysicalRow.getLocationId());
                inventoryDetailDto.setLotNumber(inventoryPhysicalRow.getLotNumber());
                BigDecimal availableQuantity = BigDecimal.ZERO;
                if (inventoryPhysicalRow.getQuantity() != null) {
                    availableQuantity = inventoryPhysicalRow.getQuantity();
                }
                inventoryDetailDto.setAvailableQuantity(availableQuantity);
                String inventoryUnitCode = slipRow.getUnitCode();
                if (StringUtils.isEmpty(inventoryUnitCode) && productRow != null) {
                    inventoryUnitCode = productRow.getPurchaseUnitCode();
                }
                if (StringUtils.isEmpty(inventoryUnitCode) && productRow != null) {
                    inventoryUnitCode = productRow.getStockUnitCode();
                }
                if (inventoryUnitCode != null) {
                    inventoryDetailDto.setUnitCode(inventoryUnitCode);
                } else {
                    inventoryDetailDto.setUnitCode("");
                }
                inventoryDetailDtos.add(inventoryDetailDto);
            }
            inventoryDetailListByDispenseId.put(slipRow.getDispenseId(), inventoryDetailDtos);
        }
        return inventoryDetailListByDispenseId;
    }

    /**
     * 库存明细回补药房 location 展示名与单位字典文案。
     *
     * @param inventoryDetailListByDispenseId dispenseId → 库存明细
     */
    private void enrichInventoryDisplayNames(
            Map<Long, List<MedicationDispenseInventoryDetailDto>> inventoryDetailListByDispenseId) {
        LinkedHashSet<Long> inventoryLocationIdSet = new LinkedHashSet<>();
        for (List<MedicationDispenseInventoryDetailDto> invList : inventoryDetailListByDispenseId.values()) {
            if (invList == null) {
                continue;
            }
            for (MedicationDispenseInventoryDetailDto invDto : invList) {
                if (invDto != null && invDto.getPharmacyOrganizationId() != null) {
                    inventoryLocationIdSet.add(invDto.getPharmacyOrganizationId());
                }
            }
        }
        Map<Long, String> inventoryLocationNameById = locationQueryService.mapNamesByIds(inventoryLocationIdSet);
        Map<String, String> unitLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        for (List<MedicationDispenseInventoryDetailDto> invList : inventoryDetailListByDispenseId.values()) {
            if (invList == null) {
                continue;
            }
            for (MedicationDispenseInventoryDetailDto invDto : invList) {
                if (invDto == null) {
                    continue;
                }
                if (invDto.getPharmacyOrganizationId() != null) {
                    invDto.setPharmacyDisplayName(inventoryLocationNameById.get(invDto.getPharmacyOrganizationId()));
                }
                invDto.setUnitText(
                    OrderCatalogDisplayAssembler.resolveCategoryDisplay(unitLabelByCode, invDto.getUnitCode()));
            }
        }
    }

    /**
     * 批量解析发药人/开单人/开单科室/发药 location 展示名 bundle。
     *
     * @param slipRows                    slip 行
     * @param medicationRequestByMedReqId 用药请求索引
     * @return 各维度 id → 名称 Map 组合
     */
    private PractitionerNameBundle resolvePractitionerAndLocationNames(
            List<MedicationDispenseSlipQueryModel> slipRows,
            Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId) {
        Set<Long> dispenserPractitionerIds = new LinkedHashSet<>();
        Set<Long> orderingPractitionerIds = new LinkedHashSet<>();
        Set<Long> orderingDepartmentOrganizationIds = new LinkedHashSet<>();
        Set<Long> dispenseLocationIds = new LinkedHashSet<>();
        for (MedicationDispenseSlipQueryModel slipRow : slipRows) {
            if (slipRow == null) {
                continue;
            }
            if (slipRow.getDispenserId() != null) {
                dispenserPractitionerIds.add(slipRow.getDispenserId());
            }
            if (slipRow.getMedRequesterId() != null) {
                orderingPractitionerIds.add(slipRow.getMedRequesterId());
            }
            MedicationRequestListQueryModel mr = medicationRequestByMedReqId.get(slipRow.getMedReqId());
            if (mr != null && mr.getOrgId() != null) {
                orderingDepartmentOrganizationIds.add(mr.getOrgId());
            }
            if (slipRow.getLocationId() != null) {
                dispenseLocationIds.add(slipRow.getLocationId());
            }
        }
        Map<Long, String> dispensingPractitionerNameByDispenseId = new HashMap<>();
        Map<Long, Practitioner> dispenserById = loadPractitionersByIds(dispenserPractitionerIds);
        for (MedicationDispenseSlipQueryModel slipRow : slipRows) {
            if (slipRow == null || slipRow.getDispenseId() == null || slipRow.getDispenserId() == null) {
                continue;
            }
            Practitioner dispenser = dispenserById.get(slipRow.getDispenserId());
            if (dispenser != null) {
                dispensingPractitionerNameByDispenseId.put(slipRow.getDispenseId(), dispenser.getName());
            }
        }
        Map<Long, Practitioner> orderingById = loadPractitionersByIds(orderingPractitionerIds);
        Map<Long, String> orderingPractitionerNameById = new HashMap<>();
        for (Long practitionerId : orderingPractitionerIds) {
            Practitioner p = orderingById.get(practitionerId);
            if (p != null) {
                orderingPractitionerNameById.put(practitionerId, p.getName());
            }
        }
        Map<Long, String> orderingDepartmentNameByOrgId =
            organizationQueryService.mapNamesByIds(orderingDepartmentOrganizationIds);
        Map<Long, String> dispenseLocationNameByLocationId = locationQueryService.mapNamesByIds(dispenseLocationIds);
        return new PractitionerNameBundle(
            dispensingPractitionerNameByDispenseId,
            orderingDepartmentNameByOrgId,
            orderingPractitionerNameById,
            dispenseLocationNameByLocationId);
    }

    /**
     * 解析各 slip 行配药人（preparer）姓名 → dispenseId 索引。
     *
     * @param slipRows 发药 slip 行
     * @return dispenseId → 配药人姓名
     */
    private Map<Long, String> resolvePreparerPractitionerNames(List<MedicationDispenseSlipQueryModel> slipRows) {
        Set<Long> preparerPractitionerIds = new LinkedHashSet<>();
        for (MedicationDispenseSlipQueryModel slipRow : slipRows) {
            if (slipRow != null && slipRow.getPreparerId() != null) {
                preparerPractitionerIds.add(slipRow.getPreparerId());
            }
        }
        Map<Long, Practitioner> preparerById = loadPractitionersByIds(preparerPractitionerIds);
        Map<Long, String> preparerPractitionerNameByDispenseId = new HashMap<>();
        for (MedicationDispenseSlipQueryModel slipRow : slipRows) {
            if (slipRow == null || slipRow.getDispenseId() == null || slipRow.getPreparerId() == null) {
                continue;
            }
            Practitioner preparer = preparerById.get(slipRow.getPreparerId());
            if (preparer != null) {
                preparerPractitionerNameByDispenseId.put(slipRow.getDispenseId(), preparer.getName());
            }
        }
        return preparerPractitionerNameByDispenseId;
    }

    /**
     * 从 slip 行收集 dispenseId 列表（保序）。
     *
     * @param slipRows 发药 slip 行
     * @return dispenseId 列表
     */
    private static List<Long> collectDispenseIds(List<MedicationDispenseSlipQueryModel> slipRows) {
        List<Long> slipDispenseIds = new ArrayList<>();
        for (MedicationDispenseSlipQueryModel slipRow : slipRows) {
            if (slipRow != null && slipRow.getDispenseId() != null) {
                slipDispenseIds.add(slipRow.getDispenseId());
            }
        }
        return slipDispenseIds;
    }

    /**
     * 批量加载发放实体有效 traceNo（trim 后）→ dispenseId 索引。
     *
     * @param slipDispenseIds 发放 id 列表
     * @return dispenseId → traceNo
     */
    private Map<Long, String> loadTraceNos(List<Long> slipDispenseIds) {
        Map<Long, String> traceNoEffectiveByDispenseId = new HashMap<>();
        List<MedicationDispense> dispenseEntitiesForTrace =
            medicationDispenseQueryService.listMedicationDispensesByIds(slipDispenseIds);
        for (MedicationDispense dispenseEntity : dispenseEntitiesForTrace) {
            if (dispenseEntity == null || dispenseEntity.getId() == null) {
                continue;
            }
            if (StringUtils.isNotEmpty(dispenseEntity.getTraceNo())) {
                traceNoEffectiveByDispenseId.put(dispenseEntity.getId(), dispenseEntity.getTraceNo().trim());
            }
        }
        return traceNoEffectiveByDispenseId;
    }

    /**
     * 按 id 逐条加载 Practitioner 并索引（展示名解析用）。
     *
     * @param practitionerIds 医务人员 id 集合
     * @return practitionerId → 实体
     */
    private Map<Long, Practitioner> loadPractitionersByIds(Set<Long> practitionerIds) {
        Map<Long, Practitioner> practitionerById = new HashMap<>();
        if (practitionerIds == null) {
            return practitionerById;
        }
        for (Long practitionerId : practitionerIds) {
            if (practitionerId == null) {
                continue;
            }
            Practitioner practitioner = practitionerQueryService.findById(practitionerId);
            if (practitioner != null) {
                practitionerById.put(practitionerId, practitioner);
            }
        }
        return practitionerById;
    }

    /**
     * 将 ChargeItem 行按 dispenseId 聚合为单价/总价并写入输出 Map。
     *
     * @param chargeRows     定价 charge 行
     * @param unitPriceOut   输出：dispenseId → 单价
     * @param totalPriceOut  输出：dispenseId → 总价
     */
    private static void mergeChargePricingFromChargeItems(
            List<ChargeItem> chargeRows,
            Map<Long, BigDecimal> unitPriceOut,
            Map<Long, BigDecimal> totalPriceOut) {
        Map<Long, BigDecimal> totalByDispense = new HashMap<>();
        Map<Long, BigDecimal> qtyByDispense = new HashMap<>();
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow == null || chargeRow.getDispenseId() == null) {
                continue;
            }
            Long dispenseKey = chargeRow.getDispenseId();
            if (chargeRow.getTotalPrice() != null) {
                totalByDispense.merge(dispenseKey, chargeRow.getTotalPrice(), BigDecimal::add);
            }
            if (chargeRow.getQuantityValue() != null) {
                qtyByDispense.merge(dispenseKey, chargeRow.getQuantityValue(), BigDecimal::add);
            }
        }
        for (Map.Entry<Long, BigDecimal> entry : totalByDispense.entrySet()) {
            Long dispenseKey = entry.getKey();
            BigDecimal totalVal = entry.getValue();
            totalPriceOut.put(dispenseKey, totalVal);
            BigDecimal qtyVal = qtyByDispense.get(dispenseKey);
            if (qtyVal != null && qtyVal.compareTo(BigDecimal.ZERO) > 0) {
                unitPriceOut.put(dispenseKey, MonetaryAmountSupport.unitPriceFromLineTotal(totalVal, qtyVal));
            }
        }
    }

    /**
     * 发药 slip 列表/打印 enrich 所需的医务人员与 location 展示名快照。
     *
     * @param dispensingPractitionerNameByDispenseId 发药记录 id → 发药人姓名
     * @param orderingDepartmentNameByOrgId          开单科室 orgId → 科室名
     * @param orderingPractitionerNameById           开单医生 practitionerId → 姓名
     * @param dispenseLocationNameByLocationId       发药 locationId → 库房/药房名
     */
    private record PractitionerNameBundle(
            Map<Long, String> dispensingPractitionerNameByDispenseId,
            Map<Long, String> orderingDepartmentNameByOrgId,
            Map<Long, String> orderingPractitionerNameById,
            Map<Long, String> dispenseLocationNameByLocationId) {
    }
}
