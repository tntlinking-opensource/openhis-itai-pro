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
import com.openhis.health.domain.custom.servicelocationtemplate.entity.ServiceLocationTemplate;
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
import com.openhis.health.application.shared.tracenumber.DispenseTraceUnitSnapshotEnricher;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
    private final EncounterMedicationDispenseSlipDecoctionChargeEnricher
        encounterMedicationDispenseSlipDecoctionChargeEnricher;
    private final FrequencyOptionLoader frequencyOptionLoader;
    private final TerminologyOptionLoader terminologyOptionLoader;
    /** 发药行主档贴码快照回填（与住院/汇总同口径）。 */
    private final DispenseTraceUnitSnapshotEnricher dispenseTraceUnitSnapshotEnricher;

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

        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId =
            indexMedicationRequestsByMedReqIds(slipPage.getContent());
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId =
            loadProductByMedicationId(slipPage.getContent());
        // Pre：术语/频次展示字典一次加载（库存行单位文案与 slip 装配共用 UNIT_CODE，避免二次打库）
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
        Map<Long, List<MedicationDispenseInventoryDetailDto>> inventoryDetailListByDispenseId;
        if (patientHistoryScope) {
            inventoryDetailListByDispenseId = Collections.emptyMap();
        } else {
            // Pre：候选库存按「机构模板一次 + 多品×多库位一次」批量装配，禁止按行 N+1 打库存
            inventoryDetailListByDispenseId = buildOutpatientInventoryDetails(slipPage.getContent(), query,
                medicationRequestByMedReqId, productByMedicationId, receptionOrganizationId);
            enrichInventoryDisplayNames(inventoryDetailListByDispenseId, unitLabelByCode);
        }

        PractitionerNameBundle names = resolvePractitionerAndLocationNames(
            slipPage.getContent(), medicationRequestByMedReqId);
        List<Long> slipDispenseIds = collectDispenseIds(slipPage.getContent());
        Map<Long, BigDecimal> chargeUnitPriceByDispenseId = new HashMap<>();
        Map<Long, BigDecimal> chargeTotalPriceByDispenseId = new HashMap<>();
        // 非历史 scope：保留收费行供打印划价（收费人）回补
        List<ChargeItem> chargeRowsForSlip = List.of();
        if (patientHistoryScope) {
            mergeChargePricingByEncounterGroups(slipPage.getContent(), chargeUnitPriceByDispenseId,
                chargeTotalPriceByDispenseId);
        } else {
            chargeRowsForSlip = chargeItemQueryService.listChargeItemsForMedicationDispensePricing(
                query.getEncounterId(), slipDispenseIds);
            mergeChargePricingFromChargeItems(chargeRowsForSlip, chargeUnitPriceByDispenseId,
                chargeTotalPriceByDispenseId);
        }
        Map<Long, String> traceNoEffectiveByDispenseId = loadTraceNosFromSlipRows(slipPage.getContent());

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
            // Pre：划价人=收费人（结算收费明细 enterer_id → 医务人员姓名）
            Map<Long, String> pricingPractitionerNameByDispenseId =
                resolvePricingCashierNamesByDispenseId(chargeRowsForSlip);
            encounterMedicationDispenseSlipPrintEnricher.enrichOutpatientPrintFields(
                result.getContent(),
                outpatientSnapshot,
                preparerPractitionerNameByDispenseId,
                pricingPractitionerNameByDispenseId,
                medicationRequestByMedReqId);
            // Asm：中药方按处方号回填同方代煎费（发药单/列表明细展示）
            encounterMedicationDispenseSlipDecoctionChargeEnricher.enrichDecoctionChargeByPrescriptionNo(
                query.getEncounterId(), result.getContent());
            // Asm：主档贴码单位/比例（cus_trace_number）回填，供前端待扫个数换算
            this.enrichTraceUnitSnapshotFromMaster(result.getContent());
            // Asm：发放库房×批号现查剩余数量（发药单笺面「剩余数量」展示）
            this.enrichRemainingQuantityFromInventory(result.getContent());
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
     * 从 slip 行收集 medReqId 并批量索引用药请求读模型。
     *
     * <p>门诊点患者与历史医嘱统一走本路径：只加载本页发放行关联药嘱，禁止
     * {@code listMedicationRequestsByEncounter} 全就诊扫表。</p>
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
        // Gate：本页无药嘱锚点则不打库
        if (medReqIds.isEmpty()) {
            return medicationRequestByMedReqId;
        }
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
     * <p>对齐 {@code ClinicalCatalogInventoryPageBatchLoader}：机构模板一次加载 + 多品×多库位一次库存查询，
     * 再按行候选库位切片；禁止按 slip 行循环打 {@code findAvailableCatalogRowsForOrder}。</p>
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
        // Gate：无 slip 行则无需查库存
        if (slipRows == null || slipRows.isEmpty()) {
            return inventoryDetailListByDispenseId;
        }
        // Pre：按开立机构分组（同机构模板只加载一次）
        Map<Long, List<MedicationDispenseSlipQueryModel>> slipsByTemplateOrgKey = new HashMap<>();
        for (MedicationDispenseSlipQueryModel slipRow : slipRows) {
            if (slipRow == null || slipRow.getDispenseId() == null) {
                continue;
            }
            MedicationRequestListQueryModel medicationRequestRow =
                medicationRequestByMedReqId.get(slipRow.getMedReqId());
            Long templateOrganizationId = receptionOrganizationId;
            // 有药嘱开立科室时优先用开立科室做取药模板机构
            if (medicationRequestRow != null && medicationRequestRow.getOrgId() != null) {
                templateOrganizationId = medicationRequestRow.getOrgId();
            }
            Long groupKey = templateOrganizationId;
            // 无机构时用哨兵键，仍允许回落 query.locationId 装候选
            if (groupKey == null) {
                groupKey = 0L;
            }
            slipsByTemplateOrgKey.computeIfAbsent(groupKey, ignored -> new ArrayList<>()).add(slipRow);
        }
        // Pre：内存解析每行候选库位，并汇总多品×多库位查询窗口
        Map<Long, List<Long>> candidateLocationIdsByDispenseId = new HashMap<>();
        LinkedHashSet<Long> unionMedicationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> unionCandidateLocationIds = new LinkedHashSet<>();
        for (Map.Entry<Long, List<MedicationDispenseSlipQueryModel>> orgEntry : slipsByTemplateOrgKey.entrySet()) {
            Long groupKey = orgEntry.getKey();
            Long orgId = null;
            // 哨兵 0L 表示无机构，跳过模板加载
            if (groupKey != null && groupKey.longValue() != 0L) {
                orgId = groupKey;
            }
            List<ServiceLocationTemplate> organizationTemplates =
                clinicalCatalogDispenseLocationResolver.loadOrganizationTemplateRows(orgId);
            for (MedicationDispenseSlipQueryModel slipRow : orgEntry.getValue()) {
                if (slipRow == null || slipRow.getDispenseId() == null) {
                    continue;
                }
                MedicationRequestListQueryModel medicationRequestRow =
                    medicationRequestByMedReqId.get(slipRow.getMedReqId());
                ProductDefinitionDisplayDto productRow = productByMedicationId.get(slipRow.getMedicationId());
                String distributionCategoryCode =
                    MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE;
                if (productRow != null && StringUtils.isNotEmpty(productRow.getCategoryCode())) {
                    distributionCategoryCode = productRow.getCategoryCode();
                }
                String methodCode = null;
                if (medicationRequestRow != null) {
                    methodCode = medicationRequestRow.getMethodCode();
                }
                List<Long> candidateLocationIds;
                // 有机构：模板内存 match；无机构：空列表后回落显式 locationId
                if (orgId != null) {
                    candidateLocationIds =
                        clinicalCatalogDispenseLocationResolver.listApplicableCandidateLocationIdsFromLoaded(
                            organizationTemplates,
                            TableNameConstants.MED_MEDICATION_DEFINITION,
                            distributionCategoryCode,
                            slipRow.getMedicationId(),
                            methodCode,
                            null);
                } else {
                    candidateLocationIds = List.of();
                }
                if (candidateLocationIds.isEmpty()) {
                    candidateLocationIds = new ArrayList<>();
                    // 模板无候选时回落请求显式库房，保证待发仍可选批号
                    if (query.getLocationId() != null) {
                        candidateLocationIds.add(query.getLocationId());
                    }
                }
                candidateLocationIdsByDispenseId.put(slipRow.getDispenseId(), candidateLocationIds);
                if (slipRow.getMedicationId() != null) {
                    unionMedicationIds.add(slipRow.getMedicationId());
                }
                unionCandidateLocationIds.addAll(candidateLocationIds);
            }
        }
        // Cmd：同表一次批量拉可用库存（多品 × 多库位）
        List<InventoryCatalogAvailabilityRowModel> allPhysicalRows;
        if (unionMedicationIds.isEmpty() || unionCandidateLocationIds.isEmpty()) {
            allPhysicalRows = List.of();
        } else {
            allPhysicalRows = inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                new ArrayList<>(unionMedicationIds),
                new ArrayList<>(unionCandidateLocationIds));
        }
        // Asm：按 dispense 行候选库位切片回填明细 DTO
        for (MedicationDispenseSlipQueryModel slipRow : slipRows) {
            if (slipRow == null || slipRow.getDispenseId() == null) {
                continue;
            }
            ProductDefinitionDisplayDto productRow = productByMedicationId.get(slipRow.getMedicationId());
            List<Long> candidateLocationIds = candidateLocationIdsByDispenseId.get(slipRow.getDispenseId());
            if (candidateLocationIds == null) {
                candidateLocationIds = List.of();
            }
            Set<Long> candidateLocationIdSet = new HashSet<>(candidateLocationIds);
            List<MedicationDispenseInventoryDetailDto> inventoryDetailDtos = new ArrayList<>();
            for (InventoryCatalogAvailabilityRowModel inventoryPhysicalRow : allPhysicalRows) {
                if (inventoryPhysicalRow == null) {
                    continue;
                }
                // 仅保留本药品且落在该行候选库位内的批次
                if (slipRow.getMedicationId() == null
                    || !slipRow.getMedicationId().equals(inventoryPhysicalRow.getItemId())) {
                    continue;
                }
                if (!candidateLocationIdSet.contains(inventoryPhysicalRow.getLocationId())) {
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
     * @param unitLabelByCode                 单位术语 code→展示名（调用方一次加载复用）
     */
    private void enrichInventoryDisplayNames(
            Map<Long, List<MedicationDispenseInventoryDetailDto>> inventoryDetailListByDispenseId,
            Map<String, String> unitLabelByCode) {
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
        Map<String, String> unitLabels = unitLabelByCode;
        if (unitLabels == null) {
            unitLabels = Map.of();
        }
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
                    OrderCatalogDisplayAssembler.resolveCategoryDisplay(unitLabels, invDto.getUnitCode()));
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
     * 解析处方划价人（收费人）姓名：取结算收费明细 {@code enterer_id}（BR-OPC-016 与 payment.enterer_id 同源）。
     *
     * @param chargeRows 发药定价收费明细
     * @return dispenseId → 收费人姓名；未结算或无收费人时不写入
     */
    private Map<Long, String> resolvePricingCashierNamesByDispenseId(List<ChargeItem> chargeRows) {
        Map<Long, String> pricingNameByDispenseId = new HashMap<>();
        if (chargeRows == null || chargeRows.isEmpty()) {
            return pricingNameByDispenseId;
        }
        LinkedHashSet<Long> cashierPractitionerIds = new LinkedHashSet<>();
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow != null && chargeRow.getEntererId() != null) {
                cashierPractitionerIds.add(chargeRow.getEntererId());
            }
        }
        Map<Long, Practitioner> cashierById = loadPractitionersByIds(cashierPractitionerIds);
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow == null || chargeRow.getDispenseId() == null || chargeRow.getEntererId() == null) {
                continue;
            }
            if (pricingNameByDispenseId.containsKey(chargeRow.getDispenseId())) {
                continue;
            }
            Practitioner cashier = cashierById.get(chargeRow.getEntererId());
            if (cashier == null || StringUtils.isEmpty(cashier.getName())) {
                continue;
            }
            pricingNameByDispenseId.put(chargeRow.getDispenseId(), cashier.getName().trim());
        }
        return pricingNameByDispenseId;
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
     * 从已分页 slip 投影提取有效 traceNo（trim 后）→ dispenseId 索引。
     *
     * <p>pageSlip 投影已含 {@code traceNo}，禁止再 {@code listMedicationDispensesByIds} 二次打库。</p>
     *
     * @param slipRows 发药 slip 读模型行
     * @return dispenseId → traceNo
     */
    private Map<Long, String> loadTraceNosFromSlipRows(List<MedicationDispenseSlipQueryModel> slipRows) {
        Map<Long, String> traceNoEffectiveByDispenseId = new HashMap<>();
        // Gate：无行则空 Map
        if (slipRows == null || slipRows.isEmpty()) {
            return traceNoEffectiveByDispenseId;
        }
        for (MedicationDispenseSlipQueryModel slipRow : slipRows) {
            if (slipRow == null || slipRow.getDispenseId() == null) {
                continue;
            }
            // 仅非空追溯码写入索引，供装配回填
            if (StringUtils.isNotEmpty(slipRow.getTraceNo())) {
                traceNoEffectiveByDispenseId.put(slipRow.getDispenseId(), slipRow.getTraceNo().trim());
            }
        }
        return traceNoEffectiveByDispenseId;
    }

    /**
     * 按发放行已锚定的库房×药品×批号现查账面剩余数量并回填 {@code remainingQuantity}。
     *
     * <p>与候选 {@code inventoryDetailList} 分列：本方法只认行上 {@code dispenseLocationId}+{@code lotNumber}，
     * 一次 {@code findAvailableCatalogRowsForOrder} 批量取数后内存切片，禁止按行 N+1。</p>
     *
     * @param rows 已装配的发药 slip 行
     */
    private void enrichRemainingQuantityFromInventory(List<EncounterMedicationDispenseSlipRowDto> rows) {
        // Gate：无行则无需查库存
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> medicationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> locationIds = new LinkedHashSet<>();
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null) {
                continue;
            }
            // 库房、药品、批号三者齐全才参与现查；缺任一无法定位账面批
            if (row.getMedicationId() == null || row.getDispenseLocationId() == null
                || StringUtils.isEmpty(row.getLotNumber())) {
                continue;
            }
            medicationIds.add(row.getMedicationId());
            locationIds.add(row.getDispenseLocationId());
        }
        // Gate：本页无可定位的库房×批号行
        if (medicationIds.isEmpty() || locationIds.isEmpty()) {
            return;
        }
        // Pre：多品×多发放库房一次拉可用库存行（正数量/可用品/未过期口径）
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
        // Asm：按行键回填；有库房+批号但无可用账面行时置 0（已耗尽仍需展示剩余）
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
     * 组装「药品+库房+批号」内存匹配键（批号 trim，与库存行等值口径一致）。
     *
     * @param medicationId 药品定义主键
     * @param locationId   发放库房主键
     * @param lotNumber    批号（调用方保证非空）
     * @return 非空匹配键
     */
    private static String buildMedicationLocationLotKey(Long medicationId, Long locationId, String lotNumber) {
        String lot = "";
        if (lotNumber != null) {
            lot = lotNumber.trim();
        }
        return medicationId + "|" + locationId + "|" + lot;
    }

    /**
     * 按发放库房+药品+批号回填主档贴码单位/比例（本库优先，否则全院同批）。
     *
     * <p>有主档时覆盖装配阶段预填的药典 {@code trace_unit_*}；无主档则保留药典回落。</p>
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
            String lot = row.getLotNumber();
            if (StringUtils.isEmpty(lot) && row.getInventoryDetailList() != null
                && !row.getInventoryDetailList().isEmpty()) {
                // 行上批号空时回落首个库存候选批号，便于打开扫码前算待扫
                MedicationDispenseInventoryDetailDto firstInv = row.getInventoryDetailList().get(0);
                if (firstInv != null) {
                    lot = firstInv.getLotNumber();
                }
            }
            Long locationId = row.getDispenseLocationId();
            if (locationId == null && row.getInventoryDetailList() != null
                && !row.getInventoryDetailList().isEmpty()) {
                MedicationDispenseInventoryDetailDto firstInv = row.getInventoryDetailList().get(0);
                if (firstInv != null) {
                    locationId = firstInv.getPharmacyOrganizationId();
                }
            }
            EncounterMedicationDispenseSlipRowDto target = row;
            requests.add(new DispenseTraceUnitSnapshotEnricher.Request(
                locationId,
                row.getMedicationId(),
                lot,
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
     * 按 id 批量加载 Practitioner 并索引（展示名解析用；禁止逐 id findById）。
     *
     * @param practitionerIds 医务人员 id 集合
     * @return practitionerId → 实体
     */
    private Map<Long, Practitioner> loadPractitionersByIds(Set<Long> practitionerIds) {
        Map<Long, Practitioner> practitionerById = new HashMap<>();
        // Gate：空集合不打库
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            return practitionerById;
        }
        // Pre：一次 findAllById，避免发药人/开单人/配药人/收费人多次 N+1
        for (Practitioner practitioner : practitionerQueryService.findAllByIds(practitionerIds)) {
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            practitionerById.put(practitioner.getId(), practitioner);
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
