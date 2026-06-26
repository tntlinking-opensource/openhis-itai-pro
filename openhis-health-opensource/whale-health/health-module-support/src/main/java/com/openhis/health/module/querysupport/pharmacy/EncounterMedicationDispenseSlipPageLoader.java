/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.querysupport.pharmacy;

import com.openhis.health.module.common.assembler.option.FrequencyOptionLoader;
import com.openhis.health.module.common.assembler.option.TerminologyOptionLoader;
import com.openhis.health.module.common.assembler.order.OrderCatalogDisplayAssembler;
import com.openhis.health.module.common.pagination.PageQueryBinder;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.module.querysupport.pharmacy.dto.EncounterMedicationDispenseSlipRowDto;
import com.openhis.health.module.querysupport.pharmacy.dto.MedicationDispenseInventoryDetailDto;
import com.openhis.health.module.querysupport.pharmacy.dto.MedicationDispenseSlipPageResult;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.module.common.assembler.product.ClinicalCatalogDispenseLocationResolveSupport;
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
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
public class EncounterMedicationDispenseSlipPageLoader {

    private final EncounterContextFacade encounterContextFacade;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final ClinicalCatalogDispenseLocationResolveSupport clinicalCatalogDispenseLocationResolveSupport;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final LocationQueryService locationQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final PharmacyDispenseSlipQuerySupport pharmacyDispenseSlipQuerySupport;
    private final InpatientMedicationDispenseChargePricingLoader inpatientMedicationDispenseChargePricingLoader;
    private final EncounterMedicationDispenseSlipReadAssembler encounterMedicationDispenseSlipReadAssembler;
    private final EncounterMedicationDispenseSlipPrintEnricher encounterMedicationDispenseSlipPrintEnricher;
    private final FrequencyOptionLoader frequencyOptionLoader;
    private final TerminologyOptionLoader terminologyOptionLoader;

    public MedicationDispenseSlipPageResult pageOutpatientEncounterMedicationDispenseSlips(
            EncounterMedicationDispenseSlipLoaderQuery query) {
        if (query == null) {
            query = new EncounterMedicationDispenseSlipLoaderQuery();
        }
        boolean patientHistoryScope = isPatientHistoryScope(query);
        OutpatientEncounterContextQueryModel outpatientSnapshot = null;
        if (query.getEncounterId() != null && !patientHistoryScope) {
            outpatientSnapshot =
                encounterContextFacade.getOutpatientContextByEncounterId(query.getEncounterId()).orElse(null);
        }
        String encounterBusNo = null;
        Long receptionOrganizationId = null;
        if (outpatientSnapshot != null) {
            encounterBusNo = outpatientSnapshot.getEncounterBusNo();
            receptionOrganizationId = outpatientSnapshot.getOrganizationId();
        }

        com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseSlipPageQuery slipQuery =
            toDomainSlipQuery(query);
        slipQuery.setOutpatientMedicationOnly(true);
        pharmacyDispenseSlipQuerySupport.enrichResolvedFilters(slipQuery);
        Page<MedicationDispenseSlipQueryModel> slipPage =
            medicationDispenseQueryService.pageMedicationDispenseSlip(slipQuery);

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
            partAttributeLabelByCode);
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

    public MedicationDispenseSlipPageResult pageInpatientEncounterMedicationDispenseSlips(
            EncounterMedicationDispenseSlipLoaderQuery query) {
        if (query == null) {
            query = new EncounterMedicationDispenseSlipLoaderQuery();
        }
        // 历史医嘱 Tab：按患者查全量并排除当前就诊，不再加载当前住院快照。
        boolean patientHistoryScope = isPatientHistoryScope(query);
        String encounterBusNo = null;
        if (query.getEncounterId() != null && !patientHistoryScope) {
            InpatientEncounterContextQueryModel inpatientContext =
                encounterContextFacade.getInpatientContextByEncounterId(query.getEncounterId()).orElse(null);
            if (inpatientContext != null) {
                encounterBusNo = inpatientContext.getEncounterBusNo();
            }
        }
        com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseSlipPageQuery slipQuery =
            toDomainSlipQuery(query);
        slipQuery.setInpatientMedicationOnly(true);
        pharmacyDispenseSlipQuerySupport.enrichResolvedFilters(slipQuery);
        Page<MedicationDispenseSlipQueryModel> slipPage =
            medicationDispenseQueryService.pageMedicationDispenseSlip(slipQuery);

        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId;
        if (patientHistoryScope) {
            // 历史医嘱：按 slip 行上的药嘱主键批量索引，不再加载当前住院快照药嘱全集。
            medicationRequestByMedReqId = indexMedicationRequestsByMedReqIds(slipPage.getContent());
        } else {
            medicationRequestByMedReqId = indexMedicationRequests(query.getEncounterId());
        }
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId =
            loadProductByMedicationId(slipPage.getContent());
        PractitionerNameBundle names = resolvePractitionerAndLocationNames(
            slipPage.getContent(), medicationRequestByMedReqId);
        List<Long> slipDispenseIds = collectDispenseIds(slipPage.getContent());
        Map<Long, String> traceNoEffectiveByDispenseId = new HashMap<>();
        Map<Long, String> lotNumberEffectiveByDispenseId = new HashMap<>();
        List<MedicationDispense> dispenseEntitiesForTrace =
            medicationDispenseQueryService.listMedicationDispensesByIds(slipDispenseIds);
        for (MedicationDispense dispenseEntity : dispenseEntitiesForTrace) {
            if (dispenseEntity == null || dispenseEntity.getId() == null) {
                continue;
            }
            if (StringUtils.isNotBlank(dispenseEntity.getTraceNo())) {
                traceNoEffectiveByDispenseId.put(dispenseEntity.getId(), dispenseEntity.getTraceNo().trim());
            }
            if (StringUtils.isNotBlank(dispenseEntity.getLotNumber())) {
                lotNumberEffectiveByDispenseId.put(dispenseEntity.getId(), dispenseEntity.getLotNumber().trim());
            }
        }
        Map<Long, BigDecimal> chargeUnitPriceByDispenseId = new HashMap<>();
        Map<Long, BigDecimal> chargeTotalPriceByDispenseId = new HashMap<>();
        if (patientHistoryScope) {
            mergeChargePricingByEncounterGroups(slipPage.getContent(), chargeUnitPriceByDispenseId,
                chargeTotalPriceByDispenseId);
        } else {
            inpatientMedicationDispenseChargePricingLoader.mergePendingSettlementPricingMaps(
                query.getEncounterId(),
                slipDispenseIds,
                chargeUnitPriceByDispenseId,
                chargeTotalPriceByDispenseId);
        }
        Map<String, String> rateLabelByCode = frequencyOptionLoader.buildRateLabelMap();
        Map<String, String> methodLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE);
        Map<String, String> doseUnitLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSE_UNIT);
        Map<String, String> unitLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        Map<String, String> partAttributeLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.PART_ATTRIBUTE_ENUM);
        Map<String, String> rxFlagLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.RX_FLAG);
        MedicationDispenseSlipPageResult result = encounterMedicationDispenseSlipReadAssembler.assembleInpatientSlipPage(
            slipPage,
            medicationRequestByMedReqId,
            productByMedicationId,
            query.getTcmOnly(),
            query.getWesternOnly(),
            names.dispensingPractitionerNameByDispenseId(),
            encounterBusNo,
            names.orderingDepartmentNameByOrgId(),
            names.orderingPractitionerNameById(),
            names.dispenseLocationNameByLocationId(),
            traceNoEffectiveByDispenseId,
            lotNumberEffectiveByDispenseId,
            chargeUnitPriceByDispenseId,
            chargeTotalPriceByDispenseId,
            rateLabelByCode,
            methodLabelByCode,
            doseUnitLabelByCode,
            unitLabelByCode,
            rxFlagLabelByCode,
            partAttributeLabelByCode);
        if (patientHistoryScope) {
            applyEncounterBusNoPerRow(result.getContent());
        }
        return result;
    }

    /**
     * 处方打印：先门诊 slip，无数据再住院 slip，统一返回 querysupport slip 行 DTO。
     */
    public List<EncounterMedicationDispenseSlipRowDto> listSlipRowsForPrescriptionReport(
            EncounterMedicationDispenseSlipLoaderQuery query) {
        MedicationDispenseSlipPageResult outpatientPage = pageOutpatientEncounterMedicationDispenseSlips(query);
        List<EncounterMedicationDispenseSlipRowDto> rows = outpatientPage.getContent();
        if (rows != null && !rows.isEmpty()) {
            return rows;
        }
        EncounterMedicationDispenseSlipLoaderQuery inpatientQuery = toInpatientSlipQuery(query);
        MedicationDispenseSlipPageResult inpatientPage = pageInpatientEncounterMedicationDispenseSlips(inpatientQuery);
        List<EncounterMedicationDispenseSlipRowDto> inpatientRows = inpatientPage.getContent();
        if (inpatientRows != null) {
            return inpatientRows;
        }
        return List.of();
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
        slipQuery.setOrderingDepartmentId(query.getOrderingDepartmentId());
        if (query.getDispenseStatusEnumIn() != null && !query.getDispenseStatusEnumIn().isEmpty()) {
            slipQuery.setDispenseStatusEnumIn(query.getDispenseStatusEnumIn());
        }
        slipQuery.setRequestStatusEnumIn(query.getRequestStatusEnumIn());
        PageQueryBinder.bind(query, slipQuery);
        return slipQuery;
    }

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
            : encounterContextFacade.listOutpatientContextsByEncounterIdsOrdered(new ArrayList<>(encounterIds))) {
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
            productDefinitionBatchLoader.loadAsMap(productLookups);
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
            if (productRow != null && StringUtils.isNotBlank(productRow.getCategoryCode())) {
                distributionCategoryCode = productRow.getCategoryCode();
            }
            String methodCode = null;
            if (medicationRequestRow != null) {
                methodCode = medicationRequestRow.getMethodCode();
            }
            List<Long> candidateLocationIds = clinicalCatalogDispenseLocationResolveSupport.listApplicableCandidateLocationIds(
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
                if (StringUtils.isBlank(inventoryUnitCode) && productRow != null) {
                    inventoryUnitCode = productRow.getPurchaseUnitCode();
                }
                if (StringUtils.isBlank(inventoryUnitCode) && productRow != null) {
                    inventoryUnitCode = productRow.getStockUnitCode();
                }
                if (inventoryUnitCode != null) {
                    inventoryDetailDto.setUnitCode(inventoryUnitCode);
                } else {
                    inventoryDetailDto.setUnitCode(StringUtils.EMPTY);
                }
                inventoryDetailDtos.add(inventoryDetailDto);
            }
            inventoryDetailListByDispenseId.put(slipRow.getDispenseId(), inventoryDetailDtos);
        }
        return inventoryDetailListByDispenseId;
    }

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

    private static List<Long> collectDispenseIds(List<MedicationDispenseSlipQueryModel> slipRows) {
        List<Long> slipDispenseIds = new ArrayList<>();
        for (MedicationDispenseSlipQueryModel slipRow : slipRows) {
            if (slipRow != null && slipRow.getDispenseId() != null) {
                slipDispenseIds.add(slipRow.getDispenseId());
            }
        }
        return slipDispenseIds;
    }

    private Map<Long, String> loadTraceNos(List<Long> slipDispenseIds) {
        Map<Long, String> traceNoEffectiveByDispenseId = new HashMap<>();
        List<MedicationDispense> dispenseEntitiesForTrace =
            medicationDispenseQueryService.listMedicationDispensesByIds(slipDispenseIds);
        for (MedicationDispense dispenseEntity : dispenseEntitiesForTrace) {
            if (dispenseEntity == null || dispenseEntity.getId() == null) {
                continue;
            }
            if (StringUtils.isNotBlank(dispenseEntity.getTraceNo())) {
                traceNoEffectiveByDispenseId.put(dispenseEntity.getId(), dispenseEntity.getTraceNo().trim());
            }
        }
        return traceNoEffectiveByDispenseId;
    }

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
                unitPriceOut.put(dispenseKey, totalVal.divide(qtyVal, 4, RoundingMode.HALF_UP));
            }
        }
    }

    private static EncounterMedicationDispenseSlipLoaderQuery toInpatientSlipQuery(EncounterMedicationDispenseSlipLoaderQuery source) {
        EncounterMedicationDispenseSlipLoaderQuery target = new EncounterMedicationDispenseSlipLoaderQuery();
        target.setEncounterId(source.getEncounterId());
        target.setLocationId(source.getLocationId());
        target.setWesternOnly(source.getWesternOnly());
        target.setTcmOnly(source.getTcmOnly());
        target.setDispenseStatusEnumIn(source.getDispenseStatusEnumIn());
        target.setPageNo(source.getPageNo());
        target.setPageSize(source.getPageSize());
        return target;
    }

    private record PractitionerNameBundle(
            Map<Long, String> dispensingPractitionerNameByDispenseId,
            Map<Long, String> orderingDepartmentNameByOrgId,
            Map<Long, String> orderingPractitionerNameById,
            Map<Long, String> dispenseLocationNameByLocationId) {
    }
}
