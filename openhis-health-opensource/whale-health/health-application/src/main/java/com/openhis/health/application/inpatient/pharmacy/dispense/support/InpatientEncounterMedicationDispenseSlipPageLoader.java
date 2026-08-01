/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.dispense.support;

import com.openhis.health.application.shared.diagnosis.EncounterDiagnosisSnapshotAssembler;
import com.openhis.health.application.shared.option.FrequencyOptionLoader;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.shared.pharmacy.MedicationDispenseSlipQueryLoader;
import com.openhis.health.application.inpatient.pharmacy.dispense.dto.EncounterMedicationDispenseSlipRowDto;
import com.openhis.health.application.inpatient.pharmacy.dispense.dto.MedicationDispenseSlipPageResult;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.DoctorStationAdviceScopeTab;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.application.shared.tracenumber.DispenseTraceUnitSnapshotEnricher;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 就诊药品发药单 slip 分页读路径（门诊 IF-OPP-03 / 住院 IF-IPP-03），供 AppService 与 report-adapter 共用。
 */
@Component
@RequiredArgsConstructor
public class InpatientEncounterMedicationDispenseSlipPageLoader {

    private final EncounterContextReader EncounterContextReader;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final LocationQueryService locationQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final MedicationDispenseSlipQueryLoader medicationDispenseSlipQueryLoader;
    private final InpatientMedicationDispenseChargePricingLoader inpatientMedicationDispenseChargePricingLoader;
    private final EncounterMedicationDispenseSlipReadAssembler encounterMedicationDispenseSlipReadAssembler;
    private final FrequencyOptionLoader frequencyOptionLoader;
    private final TerminologyOptionLoader terminologyOptionLoader;
    /** 方头诊断并集：同处方号各药行 encounter_diagnosis_ids → 顿号展示名。 */
    private final EncounterDiagnosisSnapshotAssembler encounterDiagnosisSnapshotAssembler;
    /** 发药行主档贴码快照回填（与门诊/汇总同口径）。 */
    private final DispenseTraceUnitSnapshotEnricher dispenseTraceUnitSnapshotEnricher;

    /**
     * 住院就诊药品发药单 slip 分页（IF-IPP-03）。
     *
     * @param query 分页与过滤入参；{@code null} 时使用默认空查询
     * @return slip 行分页结果
     */
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
                EncounterContextReader.getInpatientContextByEncounterId(query.getEncounterId()).orElse(null);
            if (inpatientContext != null) {
                encounterBusNo = inpatientContext.getEncounterBusNo();
            }
        }
        com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseSlipPageQuery slipQuery =
            toDomainSlipQuery(query);
        slipQuery.setInpatientMedicationOnly(true);
        medicationDispenseSlipQueryLoader.enrichResolvedFilters(slipQuery);
        Page<MedicationDispenseSlipQueryModel> slipPage =
            medicationDispenseQueryService.pageSlip(slipQuery);

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
            if (StringUtils.isNotEmpty(dispenseEntity.getTraceNo())) {
                traceNoEffectiveByDispenseId.put(dispenseEntity.getId(), dispenseEntity.getTraceNo().trim());
            }
            if (StringUtils.isNotEmpty(dispenseEntity.getLotNumber())) {
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
        Map<String, String> dosageInstructionLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSAGE_INSTRUCTION);
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
            partAttributeLabelByCode,
            dosageInstructionLabelByCode);
        if (patientHistoryScope) {
            applyEncounterBusNoPerRow(result.getContent());
        }
        // Asm：西药方头诊断 = 同处方号绑定诊断并集（与门诊发药 slip 口径一致）
        this.applyWesternPrescriptionDiagnosisUnion(result.getContent(), medicationRequestByMedReqId);
        // Asm：主档贴码单位/比例（cus_trace_number）回填，供前端待扫个数换算
        this.enrichTraceUnitSnapshotFromMaster(result.getContent());
        return result;
    }

    /**
     * 按发放库房+药品+批号回填主档贴码单位/比例（本库优先，否则全院同批）。
     *
     * @param rows 已装配的发药 slip 行
     */
    private void enrichTraceUnitSnapshotFromMaster(List<EncounterMedicationDispenseSlipRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<DispenseTraceUnitSnapshotEnricher.Request> requests = new ArrayList<>();
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null || row.getMedicationId() == null) {
                continue;
            }
            EncounterMedicationDispenseSlipRowDto target = row;
            requests.add(new DispenseTraceUnitSnapshotEnricher.Request(
                row.getDispenseLocationId(),
                row.getMedicationId(),
                row.getLotNumber(),
                (code, ratio) -> {
                    target.setTraceUnitCode(code);
                    target.setTraceUnitRatio(ratio);
                },
                target::setLotHasTraceMaster
            ));
        }
        this.dispenseTraceUnitSnapshotEnricher.enrich(requests);
    }

    /**
     * 为住院西药 slip 行写入同处方号诊断并集展示文案（强制覆盖行级摘要）。
     *
     * @param rows                        已装配 slip 行
     * @param medicationRequestByMedReqId 药嘱索引（取 encounter_diagnosis_ids）
     */
    private void applyWesternPrescriptionDiagnosisUnion(
        List<EncounterMedicationDispenseSlipRowDto> rows,
        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId) {
        // Gate：无行或无药嘱则跳过
        if (rows == null || rows.isEmpty() || medicationRequestByMedReqId == null || medicationRequestByMedReqId.isEmpty()) {
            return;
        }
        Map<String, List<String>> idsTextsByKey = new LinkedHashMap<>();
        // Loop：按处方号收集西药行绑定 ids
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null || Boolean.TRUE.equals(row.getTcmFlag())) {
                continue;
            }
            Long medReqId = row.getMedReqId();
            if (medReqId == null) {
                continue;
            }
            MedicationRequestListQueryModel requestRow = medicationRequestByMedReqId.get(medReqId);
            if (requestRow == null) {
                continue;
            }
            String key = inpatientPrescriptionGroupKey(row);
            List<String> bucket = idsTextsByKey.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                idsTextsByKey.put(key, bucket);
            }
            bucket.add(requestRow.getEncounterDiagnosisIds());
        }
        Map<String, String> unionByKey =
            this.encounterDiagnosisSnapshotAssembler.mapUnionDisplayNameByGroupKey(idsTextsByKey);
        // Loop：强制写回方头诊断全集
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null || Boolean.TRUE.equals(row.getTcmFlag())) {
                continue;
            }
            String unionSummary = unionByKey.get(inpatientPrescriptionGroupKey(row));
            if (StringUtils.isNotEmpty(unionSummary)) {
                row.setConditionSummaryText(unionSummary);
            }
        }
    }

    /**
     * 住院 slip 处方分组键：优先处方号，否则发放单 id。
     *
     * @param row slip 行
     * @return 分组键
     */
    private static String inpatientPrescriptionGroupKey(EncounterMedicationDispenseSlipRowDto row) {
        if (StringUtils.isNotEmpty(row.getPrescriptionNo())) {
            return row.getPrescriptionNo().trim();
        }
        if (row.getDispenseId() != null) {
            return "dispense:" + row.getDispenseId();
        }
        return "row:" + row.getMedReqId();
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
     * 批量解析各行 encounterBusNo 并回填 DTO（住院就诊上下文）。
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
        for (InpatientEncounterContextQueryModel context
            : EncounterContextReader.listInpatientContextsByEncounterIdsOrdered(new ArrayList<>(encounterIds))) {
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
            MedicationRequestListQueryModel mr = medicationRequestByMedReqId.get(slipRow.getMedReqId());
            // 开单医生 id：优先 slip 投影，缺省回落药嘱 requester_id（与 slip 行装配一致）
            Long orderingPractitionerId = slipRow.getMedRequesterId();
            if (orderingPractitionerId == null && mr != null) {
                orderingPractitionerId = mr.getRequesterId();
            }
            if (orderingPractitionerId != null) {
                orderingPractitionerIds.add(orderingPractitionerId);
            }
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
