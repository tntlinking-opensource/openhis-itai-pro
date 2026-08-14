/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.dispense.support;

import com.openhis.health.application.shared.diagnosis.EncounterDiagnosisSnapshotAssembler;
import com.openhis.health.application.shared.financial.ContractExpenseNatureAssembler;
import com.openhis.health.application.shared.financial.EncounterContractDisplayAssembler;
import com.openhis.health.application.shared.option.FrequencyOptionLoader;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.shared.pharmacy.MedicationDispenseSlipQueryLoader;
import com.openhis.health.application.inpatient.pharmacy.dispense.dto.EncounterMedicationDispenseSlipRowDto;
import com.openhis.health.application.inpatient.pharmacy.dispense.dto.MedicationDispenseSlipPageResult;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.constants.OutpatientPharmacyPrescriptionQueryConstants;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.procedure.entity.Procedure;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    /** 就诊优先合同展示名（与发药队列 / 门诊卡片 contractDisplay 同口径）。 */
    private final EncounterContractDisplayAssembler encounterContractDisplayAssembler;
    /** 药嘱 contract_id → 费用性质分类文案（就诊合同缺失时兜底）。 */
    private final ContractExpenseNatureAssembler contractExpenseNatureAssembler;
    /** 护士执行 recorded_time：住院按日虚拟发药单日键真源。 */
    private final ProcedureQueryService procedureQueryService;
    /** 就诊诊断列表：笺面诊断空时回落整次住院诊断摘要。 */
    private final EncounterQueryService encounterQueryService;
    /** 患者手机号回填。 */
    private final PatientQueryService patientQueryService;
    /** 锚定库房×批号账面剩余（笺面剩余库存，非候选改选）。 */
    private final InventoryItemQueryService inventoryItemQueryService;

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
        // Asm：费用性质与门诊发药/队列卡片同口径（就诊合同名优先）
        this.applyFeeNatureText(result.getContent(), medicationRequestByMedReqId);
        // Asm：主档贴码单位/比例（cus_trace_number）回填，供前端待扫个数换算
        this.enrichTraceUnitSnapshotFromMaster(result.getContent());
        // Asm：护士执行 recorded_time → 住院按日虚拟发药单日键（与汇总 planned 轴无关）
        this.enrichRecordedTimeFromProcedures(result.getContent(), dispenseEntitiesForTrace);
        // Asm：笺面患者/开单侧回显 + 调配人 + 诊断兜底 + 锚定批号剩余库存
        this.enrichInpatientSheetDisplayFields(
            result.getContent(),
            query.getEncounterId(),
            patientHistoryScope,
            dispenseEntitiesForTrace,
            names.dispensingPractitionerNameByDispenseId());
        return result;
    }

    /**
     * 按发放行挂接的 {@code procedure_id} 批量回填护士认定实际执行时刻。
     *
     * <p>日分单真源为 {@code cln_procedure.recorded_time}；无执行挂接或无 recorded 时字段保持空，
     * 前端落入「未登记执行时间」虚拟日桶。</p>
     *
     * @param rows              已装配 slip 行（就地写 {@code recordedTime}）
     * @param dispenseEntities  本页发放聚合（取 procedureId；与 trace/lot 回补同源一次加载）
     */
    private void enrichRecordedTimeFromProcedures(
            List<EncounterMedicationDispenseSlipRowDto> rows,
            List<MedicationDispense> dispenseEntities) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        // Pre：procedureId → recorded_time；优先用本页已载发放实体，避免再扫行集合
        LinkedHashSet<Long> procedureIds = new LinkedHashSet<>();
        Map<Long, Long> procedureIdByDispenseId = new HashMap<>();
        if (dispenseEntities != null) {
            for (MedicationDispense dispenseEntity : dispenseEntities) {
                if (dispenseEntity == null || dispenseEntity.getId() == null) {
                    continue;
                }
                Long procedureId = dispenseEntity.getProcedureId();
                if (procedureId == null) {
                    continue;
                }
                procedureIdByDispenseId.put(dispenseEntity.getId(), procedureId);
                procedureIds.add(procedureId);
            }
        }
        // 行上已有 procedureId（slip 投影）时补入，防止实体列表与装配行偶发不一致
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null || row.getProcedureId() == null) {
                continue;
            }
            procedureIds.add(row.getProcedureId());
            if (row.getDispenseId() != null) {
                procedureIdByDispenseId.putIfAbsent(row.getDispenseId(), row.getProcedureId());
            }
        }
        if (procedureIds.isEmpty()) {
            return;
        }
        Map<Long, LocalDateTime> recordedByProcedureId = new HashMap<>();
        for (Procedure procedure : procedureQueryService.listByIds(procedureIds)) {
            if (procedure == null || procedure.getId() == null || procedure.getRecordedTime() == null) {
                continue;
            }
            recordedByProcedureId.put(procedure.getId(), procedure.getRecordedTime());
        }
        // Loop：逐行写回执行主键（若仅实体有）与 recorded_time
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null || row.getDispenseId() == null) {
                continue;
            }
            Long procedureId = row.getProcedureId();
            if (procedureId == null) {
                procedureId = procedureIdByDispenseId.get(row.getDispenseId());
                // 装配未带 procedureId 时用实体补齐，保证前端日键与扫码映射可追溯
                if (procedureId != null) {
                    row.setProcedureId(procedureId);
                }
            }
            if (procedureId == null) {
                continue;
            }
            LocalDateTime recordedTime = recordedByProcedureId.get(procedureId);
            if (recordedTime != null) {
                row.setRecordedTime(recordedTime);
            }
        }
    }

    /**
     * 住院发药笺面回显字段补齐（患者摘要、调配人、诊断兜底、锚定批号剩余）。
     *
     * <p>不装配候选 {@code inventoryDetailList}（BR-IPP-002）；剩余库存仅认行上库房×批号账面。</p>
     *
     * @param rows                 已装配 slip 行
     * @param encounterId          当前就诊；历史医嘱 scope 时可为 null（按行 encounter 跳过整诊兜底）
     * @param patientHistoryScope  是否历史医嘱范围
     * @param dispenseEntities     本页发放聚合（取 preparerId）
     * @param dispenserNames       发药人姓名索引（装配阶段已解析）
     */
    private void enrichInpatientSheetDisplayFields(
            List<EncounterMedicationDispenseSlipRowDto> rows,
            Long encounterId,
            boolean patientHistoryScope,
            List<MedicationDispense> dispenseEntities,
            Map<Long, String> dispenserNames) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        // Pre：住院上下文 → 姓名/年龄/性别；患者主档 → 手机号
        String patientName = null;
        String ageText = null;
        String genderEnumText = null;
        String patientPhone = null;
        Long patientId = null;
        if (!patientHistoryScope && encounterId != null) {
            InpatientEncounterContextQueryModel inpatientContext =
                EncounterContextReader.getInpatientContextByEncounterId(encounterId).orElse(null);
            if (inpatientContext != null) {
                patientName = trimToNull(inpatientContext.getPatientName());
                ageText = trimToNull(inpatientContext.getAgeText());
                genderEnumText = resolveGenderText(inpatientContext.getGenderEnum());
                patientId = inpatientContext.getPatientId();
            }
        }
        if (patientId == null && dispenseEntities != null) {
            for (MedicationDispense dispenseEntity : dispenseEntities) {
                if (dispenseEntity != null && dispenseEntity.getPatientId() != null) {
                    patientId = dispenseEntity.getPatientId();
                    break;
                }
            }
        }
        if (patientId != null) {
            Optional<Patient> patientOpt = patientQueryService.findOptionalPatientEntityById(patientId);
            if (patientOpt.isPresent()) {
                patientPhone = trimToNull(patientOpt.get().getPhone());
                // 上下文缺性别时，回落患者主档
                if (genderEnumText == null) {
                    genderEnumText = resolveGenderText(patientOpt.get().getGenderEnum());
                }
            }
        }
        // Pre：调配药师姓名（配药后有 preparerId；待发可空）
        Map<Long, String> preparerNameByDispenseId = resolvePreparerNamesByDispense(dispenseEntities);
        Map<Long, String> dispenserNameByDispenseId =
            dispenserNames == null ? Map.of() : dispenserNames;
        // Pre：药嘱诊断空时，回落整次住院诊断并集
        String encounterDiagnosisSummary = null;
        if (!patientHistoryScope && encounterId != null) {
            encounterDiagnosisSummary = resolveEncounterDiagnosisSummary(encounterId);
        }
        // Loop：写回笺面患者/人员/诊断兜底
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (StringUtils.isEmpty(row.getPatientName()) && patientName != null) {
                row.setPatientName(patientName);
            }
            if (StringUtils.isEmpty(row.getAgeText()) && ageText != null) {
                row.setAgeText(ageText);
            }
            if (StringUtils.isEmpty(row.getGenderEnumText()) && genderEnumText != null) {
                row.setGenderEnumText(genderEnumText);
            }
            if (StringUtils.isEmpty(row.getPatientPhone()) && patientPhone != null) {
                row.setPatientPhone(patientPhone);
            }
            if (row.getDispenseId() != null) {
                if (StringUtils.isEmpty(row.getPreparerPractitionerName())) {
                    row.setPreparerPractitionerName(preparerNameByDispenseId.get(row.getDispenseId()));
                }
                if (StringUtils.isEmpty(row.getDispensingPractitionerName())) {
                    row.setDispensingPractitionerName(dispenserNameByDispenseId.get(row.getDispenseId()));
                }
            }
            // 诊断：药嘱并集为空时用就诊诊断兜底，避免笺面「诊断」全 —
            if (StringUtils.isEmpty(row.getConditionSummaryText())
                && StringUtils.isNotEmpty(encounterDiagnosisSummary)) {
                row.setConditionSummaryText(encounterDiagnosisSummary);
            }
        }
        // Asm：锚定库房×批号账面剩余（与门诊 enrichRemainingQuantityFromInventory 同口径）
        this.enrichRemainingQuantityFromInventory(rows);
    }

    /**
     * 按发放聚合 preparerId 批量解析调配药师姓名。
     *
     * @param dispenseEntities 本页发放实体
     * @return dispenseId → 调配人姓名
     */
    private Map<Long, String> resolvePreparerNamesByDispense(List<MedicationDispense> dispenseEntities) {
        if (dispenseEntities == null || dispenseEntities.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<Long> preparerIds = new LinkedHashSet<>();
        Map<Long, Long> preparerIdByDispenseId = new HashMap<>();
        for (MedicationDispense dispenseEntity : dispenseEntities) {
            if (dispenseEntity == null || dispenseEntity.getId() == null || dispenseEntity.getPreparerId() == null) {
                continue;
            }
            preparerIds.add(dispenseEntity.getPreparerId());
            preparerIdByDispenseId.put(dispenseEntity.getId(), dispenseEntity.getPreparerId());
        }
        if (preparerIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> nameByPreparerId = new HashMap<>();
        Map<Long, Practitioner> preparerById = loadPractitionersByIds(preparerIds);
        for (Map.Entry<Long, Practitioner> entry : preparerById.entrySet()) {
            Practitioner practitioner = entry.getValue();
            if (practitioner == null || StringUtils.isEmpty(practitioner.getName())) {
                continue;
            }
            nameByPreparerId.put(entry.getKey(), practitioner.getName().trim());
        }
        Map<Long, String> preparerNameByDispenseId = new HashMap<>();
        for (Map.Entry<Long, Long> entry : preparerIdByDispenseId.entrySet()) {
            String name = nameByPreparerId.get(entry.getValue());
            if (StringUtils.isNotEmpty(name)) {
                preparerNameByDispenseId.put(entry.getKey(), name);
            }
        }
        return preparerNameByDispenseId;
    }

    /**
     * 整次住院就诊诊断展示名并集（药嘱未绑诊断时笺面兜底）。
     *
     * @param encounterId 就诊主键
     * @return 顿号拼接诊断名；无诊断时 null
     */
    private String resolveEncounterDiagnosisSummary(Long encounterId) {
        if (encounterId == null) {
            return null;
        }
        List<EncounterDiagnosisQueryModel> diagnoses =
            encounterQueryService.listEncounterDiagnoses(encounterId);
        if (diagnoses == null || diagnoses.isEmpty()) {
            return null;
        }
        StringBuilder idsText = new StringBuilder();
        for (EncounterDiagnosisQueryModel diagnosis : diagnoses) {
            if (diagnosis == null || diagnosis.getDiagnosisId() == null) {
                continue;
            }
            if (idsText.length() > 0) {
                idsText.append(',');
            }
            idsText.append(diagnosis.getDiagnosisId());
        }
        if (idsText.length() == 0) {
            return null;
        }
        String summary =
            this.encounterDiagnosisSnapshotAssembler.joinDisplayNamesByEncounterDiagnosisIdsText(
                idsText.toString());
        return trimToNull(summary);
    }

    /**
     * 按发放行已锚定的库房×药品×批号现查账面剩余数量并回填 {@code remainingQuantity}。
     *
     * <p>与门诊同构；不返回候选库存改选列表。</p>
     *
     * @param rows 已装配发药 slip 行
     */
    private void enrichRemainingQuantityFromInventory(List<EncounterMedicationDispenseSlipRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> medicationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> locationIds = new LinkedHashSet<>();
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getMedicationId() == null || row.getDispenseLocationId() == null
                || StringUtils.isEmpty(row.getLotNumber())) {
                continue;
            }
            medicationIds.add(row.getMedicationId());
            locationIds.add(row.getDispenseLocationId());
        }
        if (medicationIds.isEmpty() || locationIds.isEmpty()) {
            return;
        }
        List<InventoryCatalogAvailabilityRowModel> physicalRows =
            inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                new ArrayList<>(medicationIds),
                new ArrayList<>(locationIds));
        Map<String, BigDecimal> remainingByMedicationLocationLot = new HashMap<>();
        for (InventoryCatalogAvailabilityRowModel physicalRow : physicalRows) {
            if (physicalRow == null || physicalRow.getItemId() == null || physicalRow.getLocationId() == null) {
                continue;
            }
            if (StringUtils.isEmpty(physicalRow.getLotNumber()) || physicalRow.getQuantity() == null) {
                continue;
            }
            String key = buildMedicationLocationLotKey(
                physicalRow.getItemId(), physicalRow.getLocationId(), physicalRow.getLotNumber());
            BigDecimal current = remainingByMedicationLocationLot.get(key);
            if (current == null) {
                remainingByMedicationLocationLot.put(key, physicalRow.getQuantity());
            } else {
                remainingByMedicationLocationLot.put(key, current.add(physicalRow.getQuantity()));
            }
        }
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getMedicationId() == null || row.getDispenseLocationId() == null
                || StringUtils.isEmpty(row.getLotNumber())) {
                continue;
            }
            String key = buildMedicationLocationLotKey(
                row.getMedicationId(), row.getDispenseLocationId(), row.getLotNumber());
            BigDecimal remaining = remainingByMedicationLocationLot.get(key);
            if (remaining == null) {
                row.setRemainingQuantity(BigDecimal.ZERO);
            } else {
                row.setRemainingQuantity(remaining);
            }
        }
    }

    /**
     * 组装「药品+库房+批号」内存匹配键（批号 trim，与行上取值口径一致）。
     *
     * @param medicationId 药品定义主键
     * @param locationId   库房主键
     * @param lotNumber    批号
     * @return 匹配键
     */
    private static String buildMedicationLocationLotKey(Long medicationId, Long locationId, String lotNumber) {
        String lot = lotNumber == null ? "" : lotNumber.trim();
        return medicationId + "|" + locationId + "|" + lot;
    }

    /**
     * 性别 code → 展示文案（与门诊 PrintEnricher 同口径）。
     *
     * @param genderEnum 性别枚举 code
     * @return 展示文案；无法解析时回落原 code
     */
    private static String resolveGenderText(String genderEnum) {
        AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, genderEnum);
        if (gender != null) {
            return gender.getDisplay();
        }
        return trimToNull(genderEnum);
    }

    /**
     * 空白串归一为 null。
     *
     * @param raw 原始串
     * @return trim 后非空串，否则 null
     */
    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    /**
     * 回填 slip 行费用性质展示文案（患者维度，与门诊发药 enrich 同优先级）。
     *
     * <p>优先就诊合同名；空则药嘱 {@code contract_id} 分类文案；再空回落「自费」。</p>
     *
     * @param rows                        已装配 slip 行
     * @param medicationRequestByMedReqId 药嘱索引
     */
    private void applyFeeNatureText(
        List<EncounterMedicationDispenseSlipRowDto> rows,
        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        Set<Long> contractIds = new HashSet<>();
        // Loop：收集本页就诊与药嘱合同，供一次批量解析
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
            if (row.getMedReqId() == null || medicationRequestByMedReqId == null) {
                continue;
            }
            MedicationRequestListQueryModel requestRow = medicationRequestByMedReqId.get(row.getMedReqId());
            if (requestRow != null && requestRow.getContractId() != null) {
                contractIds.add(requestRow.getContractId());
            }
        }
        Map<Long, String> contractDisplayByEncounterId;
        if (encounterIds.isEmpty()) {
            contractDisplayByEncounterId = Map.of();
        } else {
            contractDisplayByEncounterId =
                this.encounterContractDisplayAssembler.mapContractDisplayByEncounterIds(encounterIds);
        }
        Map<Long, String> feeNatureTextByContractId;
        if (contractIds.isEmpty()) {
            feeNatureTextByContractId = Map.of();
        } else {
            feeNatureTextByContractId =
                this.contractExpenseNatureAssembler.mapCategoryTextByContractIdIn(contractIds);
        }
        // Loop：逐行写回费用性质；优先级与门诊 EncounterMedicationDispenseSlipPrintEnricher 一致
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null) {
                continue;
            }
            String feeNatureText = null;
            if (row.getEncounterId() != null) {
                feeNatureText = contractDisplayByEncounterId.get(row.getEncounterId());
            }
            if (StringUtils.isEmpty(feeNatureText)
                && row.getMedReqId() != null
                && medicationRequestByMedReqId != null) {
                MedicationRequestListQueryModel requestRow = medicationRequestByMedReqId.get(row.getMedReqId());
                if (requestRow != null && requestRow.getContractId() != null) {
                    feeNatureText = feeNatureTextByContractId.get(requestRow.getContractId());
                }
            }
            if (StringUtils.isEmpty(feeNatureText)) {
                feeNatureText = OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_SELF_CHARGE_LABEL;
            }
            row.setFeeNatureText(feeNatureText);
        }
    }

    /**
     * 按发放库房+药品+批号回填主档贴码单位/比例（本库优先，否则全院同批）。
     *
     * <p>有主档时覆盖装配阶段预填的药典值；无主档则保留药典回落。</p>
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
                (code, ratio, codeText) -> {
                    // Asm：主档贴码单位/比例/术语文案（扫码弹窗药品明细展示）
                    target.setTraceUnitCode(code);
                    target.setTraceUnitRatio(ratio);
                    target.setTraceUnitCodeText(codeText);
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
