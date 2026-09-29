/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.assembler;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.application.outpatient.doctorstation.advice.support.ServiceApplyFormOrchestrator;
import com.openhis.health.application.shared.advice.SkinTestCompanionDisplaySupport;
import com.openhis.health.application.shared.advice.AdviceChargeDisplayStatusAssembler;
import com.openhis.health.application.shared.option.FrequencyOptionLoader;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.advice.InpatientEntrustOrderGateSupport;
import com.openhis.health.application.shared.advice.InpatientStandingOrderSplittableSettingResolver;
import com.openhis.health.application.shared.advice.OrderCatalogDisplayAssembler;
import com.openhis.health.application.shared.advice.OrderRequestCatalogWarmAssembler;
import com.openhis.health.application.shared.advice.StandingOrderOralPackageInventorySupport;
import com.openhis.health.application.shared.product.MedicationDailyDoseDisplayAssembler;
import com.openhis.health.application.shared.product.MedicationDosageDisplayAssembler;
import com.openhis.health.application.shared.product.MedicationDosageDisplayAssembler.MedicationDosageDisplayResult;
import com.openhis.health.application.shared.product.MedicationMerchandiseDisplaySupport;
import com.openhis.health.application.shared.product.MedicationPackageSplitQuantityDisplayAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.shared.advice.OrderRequestCatalogWarmContext;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceRowDto;
import com.openhis.health.application.outpatient.doctorstation.advice.assembler.ServiceApplyFormAssembler;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.entity.EncounterDiagnosis;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.clinical.allergyintolerance.queries.model.AllergyIntoleranceRecordQueryModel;
import com.openhis.health.domain.clinical.allergyintolerance.service.AllergyIntoleranceQueryService;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.custom.encounterhistory.queries.model.EncounterHistoryMilestoneQueryModel;
import com.openhis.health.domain.custom.encounterhistory.service.EncounterHistoryQueryService;
import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.application.shared.advice.InpatientOrderListSkeleton;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.shared.advice.AdviceExtensionJsonParser.AdvicePackageChildItemDto;
import com.openhis.health.application.shared.advice.AdviceExtensionJsonParser;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.EncounterDiagnosisIdsSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.MedicationCommonConstants;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.ActivityType;
import com.openhis.health.domain.share.enums.ClinicalStatus;
import com.openhis.health.domain.share.enums.EnumInterface;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.terminology.entity.Concept;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequest;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestDetailListModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.health.application.shared.advice.ServiceRequestDetailListAssembler;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院医生站医嘱统一列表装配器（IF-IP-DOR-10 / Asm）。
 *
 * <p><b>职责边界</b>：在 {@link InpatientOrderListSkeleton} 读模型行基础上，叠加药/诊请求明细、护士执行后费用归并、
 * 术语展示名与发放库位展示；不调用写模型、不签发。</p>
 *
 * <p><b>住院特规</b>：仅药+诊（禁止耗材行，BR-IP-ORD-012）；开嘱侧不挂费；
 * 列表费用与 {@code fifoDispenseLocation*} 来自护士执行后的 ChargeItem/Dispense。列表主序来自 OrderInfo。</p>
 *
 * <p>目录类目与单位/途径等术语预热复用 {@link OrderRequestCatalogWarmAssembler}、
 * {@link TerminologyOptionLoader}（B.6.1）。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientDoctorStationAdviceListAssembler {

    /** 药品医嘱读侧：按医生站生成来源过滤本就诊明细。 */
    private final MedicationRequestQueryService medicationRequestQueryService;
    /** 诊疗申请读侧：按医生站生成来源过滤本就诊明细。 */
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ServiceRequestDetailListAssembler serviceRequestDetailListAssembler;
    /** 本就诊收费项：护士执行 ORDER_EXECUTE 费用汇总与状态归并。 */
    private final ChargeItemQueryService chargeItemQueryService;
    /** 药品发放读侧：发放锚点→申请映射、执行后发药库位展示。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    /** 目录定义与类目术语预热（B.6.1）。 */
    private final OrderRequestCatalogWarmAssembler orderRequestCatalogWarmAssembler;
    /** 术语码表 label 批量解析。 */
    private final TerminologyOptionLoader terminologyOptionLoader;
    /** 频次展示码表：cus_frequency 主数据。 */
    private final FrequencyOptionLoader frequencyOptionLoader;
    /** 开立/录入医师姓名批量解析。 */
    private final PractitionerQueryService practitionerQueryService;
    /** 术语概念单条回退查询（如用法说明）。 */
    private final CodeSystemQueryService codeSystemQueryService;
    /** 诊断定义 id → 展示名（药品行关联诊断）。 */
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    /** 就诊诊断读侧：由 {@code encounterDiagnosisIds} 解析诊断定义展示名。 */
    private final EncounterQueryService encounterQueryService;
    /** 库位名称批量解析（执行科室/护士执行后发药库位）。 */
    private final LocationQueryService locationQueryService;
    /** 离院/转科里程碑：出院方式等列表展示字段回源。 */
    private final EncounterHistoryQueryService encounterHistoryQueryService;

    /** 诊疗套餐子项批量加载（B.6.2，禁止透出 children_json 串）。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    /** 申请单 DTO 与编排 Model 互转。 */
    private final ServiceApplyFormAssembler serviceApplyFormAssembler;
    /** 皮试结果读侧：护士登记 AllergyIntolerance 回填列表。 */
    private final AllergyIntoleranceQueryService allergyIntoleranceQueryService;

    /** 嘱托医嘱展示名解析（BR-IP-ORD-028④；与护士站医嘱查看同源）。 */
    private final InpatientEntrustOrderGateSupport inpatientEntrustOrderGateSupport;

    /** 口服一盒一点剩余（BR-IP-EXE-038，与护士站同源）。 */
    private final StandingOrderOralPackageInventorySupport standingOrderOralPackageInventorySupport;

    /** 院点长嘱可拆开关。 */
    private final InpatientStandingOrderSplittableSettingResolver standingOrderSplittableSettingResolver;

    /** 执行 Procedure 只读（剩余领入事实）。 */
    private final ProcedureQueryService procedureQueryService;

    /**
     * 装配住院医生站医嘱统一列表行。
     *
     * @param encounterId   就诊主键
     * @param patientId     患者主键
     * @param orderRows     OrderInfo 读模型行（已由 RM 过滤医生来源与长临）
     * @param currentUserId 当前用户（预留 onlyMine 扩展）
     * @return 排序后的列表行
     */
    public List<InpatientDoctorStationAdviceRowDto> assembleRows(
        Long encounterId,
        Long patientId,
        List<InpatientOrderListSkeleton> orderRows,
        Long currentUserId
    ) {
        return assembleAdviceQueryRows(encounterId, patientId, orderRows, currentUserId, null, null, true);
    }

    /**
     * IF-IP-DOR-22：装配单就诊下全类型医嘱行，支持可选科室与请求态过滤。
     *
     * @param encounterId           就诊主键
     * @param patientId             患者主键
     * @param orderRows             已合并的 OrderInfo 读模型行
     * @param currentUserId         当前用户（预留 onlyMine 扩展）
     * @param orderingDepartmentId  开单科室；{@code null} 不过滤
     * @param requestStatusEnumIn   请求态集合；空不过滤
     * @param excludeTcmMedication  为 {@code true} 时排除 {@code tcmFlag=true} 的中药饮片行（开立 Tab 与中药 Tab 分域）
     * @return 已排序的列表行
     */
    public List<InpatientDoctorStationAdviceRowDto> assembleAdviceQueryRows(
        Long encounterId,
        Long patientId,
        List<InpatientOrderListSkeleton> orderRows,
        Long currentUserId,
        Long orderingDepartmentId,
        List<String> requestStatusEnumIn,
        boolean excludeTcmMedication
    ) {
        // Pre：本就诊全部收费行，归并护士执行后 ORDER_EXECUTE 费用（开嘱侧不挂费）。
        ChargeItemQueryCommand chargeQuery = new ChargeItemQueryCommand();
        chargeQuery.setEncounterId(encounterId);
        List<ChargeItem> chargeItems = chargeItemQueryService.listChargeItemsByEncounterId(chargeQuery);
        // 发放锚点 → 药品申请，兼容费用挂在 dispense 或 request 上的两种持久化形态。
        Map<Long, Long> dispenseIdToMedReqId = collectMedicationDispenseServiceIds(chargeItems);
        Map<Long, AdviceChargeRollup> medicationRollup =
            rollupChargesForMedicationRequests(chargeItems, dispenseIdToMedReqId);
        Map<Long, AdviceChargeRollup> serviceRollup =
            rollupChargesForAnchoredTable(chargeItems, TableNameConstants.WKF_SERVICE_REQUEST);

        // Pre：药/诊请求按医生站开立来源拉平，供 OrderInfo 行补全剂量、频次等明细字段。
        String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        List<MedicationRequestListQueryModel> medicationRows =
            medicationRequestQueryService.listMedicationRequestsByEncounterForDoctorStation(encounterId, doctorSource);
        // Pre：开立 Tab 与中医 Tab 分域，排除 tcm_flag=true 饮片行（与门诊 IF-DOR-10 一致）
        if (excludeTcmMedication) {
            medicationRows = excludeTcmMedicationRows(medicationRows);
        }
        List<ServiceRequestListModel> serviceRows =
            serviceRequestQueryService.listServiceRequestsByGenerateSource(encounterId, doctorSource);
        Map<Long, MedicationRequestListQueryModel> medicationDetailById = new HashMap<>();
        for (MedicationRequestListQueryModel row : medicationRows) {
            if (row.getId() != null) {
                medicationDetailById.put(row.getId(), row);
            }
        }
        Map<Long, ServiceRequestListModel> serviceDetailById = new HashMap<>();
        for (ServiceRequestListModel row : serviceRows) {
            if (row.getId() != null) {
                serviceDetailById.put(row.getId(), row);
            }
        }
        Set<Long> orderServiceRequestIds = new HashSet<>();
        for (InpatientOrderListSkeleton order : orderRows) {
            if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType()) && order.getOrderId() != null) {
                orderServiceRequestIds.add(order.getOrderId());
            }
        }
        if (!orderServiceRequestIds.isEmpty()) {
            serviceDetailById.putAll(serviceRequestQueryService.mapListModelsByIds(orderServiceRequestIds));
        }
        Map<Long, String> dischargeOutWayCodeByServiceRequestId =
            indexDischargeOutWayCodes(encounterId, orderServiceRequestIds);
        Map<String, String> outWayLabelByCode =
            terminologyOptionLoader.buildLabelMap(DoctorStationAdviceConstants.TERMINOLOGY_OUT_WAY_CODE_SYSTEM_NAME);

        List<Long> applyFormServiceRequestIds = new ArrayList<>();
        for (ServiceRequestListModel serviceDetail : serviceDetailById.values()) {
            // 申请单行（AR 前缀）须批量预取 Detail，供列表回填 serviceRequestDetails。
            if (serviceDetail.getId() != null
                && ServiceApplyFormOrchestrator.isRequisitionApplicationPrescriptionNo(serviceDetail.getPrescriptionNo())) {
                applyFormServiceRequestIds.add(serviceDetail.getId());
            }
        }
        Map<Long, List<ServiceRequestDetailListModel>> applyFormDetailsByRequestId =
            serviceRequestDetailListAssembler.mapDetailListModelsByServiceRequestIds(applyFormServiceRequestIds);

        Set<Long> medDefIds = new HashSet<>();
        Set<Long> activityDefIds = new HashSet<>();
        Set<Long> adviceEncounterDiagnosisIds = new HashSet<>();
        List<Long> medReqIdsForFifo = new ArrayList<>();
        for (InpatientOrderListSkeleton order : orderRows) {
            if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
                if (order.getMedicationId() != null) {
                    medDefIds.add(order.getMedicationId());
                }
                if (order.getMedReqId() != null) {
                    medReqIdsForFifo.add(order.getMedReqId());
                }
                MedicationRequestListQueryModel medDetail = medicationDetailById.get(order.getMedReqId());
                if (medDetail != null) {
                    if (medDetail.getMedicationId() != null) {
                        medDefIds.add(medDetail.getMedicationId());
                    }
                    collectEncounterDiagnosisIdsFromIds(medDetail.getEncounterDiagnosisIds(), adviceEncounterDiagnosisIds);
                }
            } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
                ServiceRequestListModel serviceDetail = serviceDetailById.get(order.getOrderId());
                Long activityIdForCatalog = resolveCatalogActivityId(order.getActivityId(), serviceDetail);
                if (activityIdForCatalog != null) {
                    activityDefIds.add(activityIdForCatalog);
                }
                if (serviceDetail != null) {
                    Long detailActivityId = resolveCatalogActivityId(serviceDetail.getActivityId(), serviceDetail);
                    if (detailActivityId != null) {
                        activityDefIds.add(detailActivityId);
                    }
                    collectEncounterDiagnosisIdsFromIds(serviceDetail.getEncounterDiagnosisIds(), adviceEncounterDiagnosisIds);
                }
            }
        }

        Map<Long, Long> conditionDefinitionIdByEncounterDiagnosisId = new HashMap<>();
        Set<Long> conditionDefinitionIds = new HashSet<>();
        if (!adviceEncounterDiagnosisIds.isEmpty()) {
            for (EncounterDiagnosis ed : encounterQueryService.listEncounterDiagnosisEntitiesByIds(adviceEncounterDiagnosisIds)) {
                if (ed.getId() != null && ed.getConditionDefinitionId() != null) {
                    conditionDefinitionIdByEncounterDiagnosisId.put(ed.getId(), ed.getConditionDefinitionId());
                    conditionDefinitionIds.add(ed.getConditionDefinitionId());
                }
            }
        }

        // Pre：批量预热药/诊定义与类目术语，避免逐行查定义表。
        OrderRequestCatalogWarmContext catalogWarm =
            orderRequestCatalogWarmAssembler.prepareByDefinitionIds(activityDefIds, medDefIds, Set.of());
        Map<Long, MedicationDefinition> medicationDefinitionById = catalogWarm.getMedicationDefinitionById();
        Map<Long, ActivityDefinition> activityDefinitionById = catalogWarm.getActivityDefinitionById();
        Map<Long, List<ActivityDefinitionChildQueryModel>> packageChildrenByActivityId =
            activityDefinitionQueryService.listChildrenByActivityIds(activityDefIds);
        Map<String, String> medicationCategoryLabelByCode = catalogWarm.getMedicationCategoryLabelByCode();
        Map<String, String> activityCategoryLabelByCode = catalogWarm.getActivityCategoryLabelByCode();

        Map<String, String> unitLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        Map<String, String> methodLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE);
        Map<String, String> rateLabelByCode = frequencyOptionLoader.buildRateLabelMap();
        Map<String, Integer> timesPerDayByRateCode = frequencyOptionLoader.buildTimesPerDayMap();
        Map<String, String> doseUnitLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSE_UNIT);
        Map<String, String> dosageInstructionLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSAGE_INSTRUCTION);
        Map<String, String> speedLabelByCode =
            terminologyOptionLoader.buildLabelMap(DoctorStationAdviceConstants.TERMINOLOGY_SPEED_CODE_SYSTEM_NAME);
        Map<Long, String> conditionDefinitionDisplayById = loadConditionDefinitionDisplayNames(conditionDefinitionIds);

        Map<String, ProductDefinitionDisplayDto> productByKey = batchLoadProducts(medDefIds, activityDefIds);
        Map<Long, Long> fifoLocByMedReq =
            medicationDispenseQueryService.mapMedicationRequestToPrimaryDispenseLocationId(medReqIdsForFifo);
        Set<Long> locationIds = new HashSet<>();
        for (Long locId : fifoLocByMedReq.values()) {
            if (locId != null) {
                locationIds.add(locId);
            }
        }
        for (MedicationRequestListQueryModel medDetail : medicationDetailById.values()) {
            if (medDetail.getPerformLocation() != null) {
                locationIds.add(medDetail.getPerformLocation());
            }
        }
        for (ServiceRequestListModel serviceDetail : serviceDetailById.values()) {
            if (serviceDetail.getLocationId() != null) {
                locationIds.add(serviceDetail.getLocationId());
            }
        }
        Map<Long, String> locationNameById = locationQueryService.mapNamesByIds(locationIds);

        Set<Long> practitionerIds = new HashSet<>();
        for (InpatientOrderListSkeleton order : orderRows) {
            if (order.getMedRequesterId() != null) {
                practitionerIds.add(order.getMedRequesterId());
            }
            if (order.getRequesterId() != null) {
                practitionerIds.add(order.getRequesterId());
            }
            // 停嘱人：列表「停嘱时间 + 人名」展示（BR-IP-ORD-031）。
            if (order.getStopperId() != null) {
                practitionerIds.add(order.getStopperId());
            }
        }
        for (MedicationRequestListQueryModel medDetail : medicationDetailById.values()) {
            if (medDetail.getRequesterId() != null) {
                practitionerIds.add(medDetail.getRequesterId());
            }
            if (medDetail.getRecorderId() != null) {
                practitionerIds.add(medDetail.getRecorderId());
            }
            if (medDetail.getStopperId() != null) {
                practitionerIds.add(medDetail.getStopperId());
            }
        }
        for (ServiceRequestListModel serviceDetail : serviceDetailById.values()) {
            if (serviceDetail.getRequesterId() != null) {
                practitionerIds.add(serviceDetail.getRequesterId());
            }
            if (serviceDetail.getStopperId() != null) {
                practitionerIds.add(serviceDetail.getStopperId());
            }
        }
        Map<Long, String> practitionerNameById = loadPractitionerNames(practitionerIds);

        // Pre：皮试药 nurse 登记结果（companion doctor-prescription SR → AllergyIntolerance，BR-IP-ORD-016/024）
        Map<Long, AllergyIntoleranceRecordQueryModel> skinTestOutcomeByMedicationRequestId =
            this.loadSkinTestOutcomeByMedicationRequestId(encounterId, medicationRows);
        // Pre：长嘱皮试药换批提示（发放批号 ≠ 最新皮试批号 → needAppendSkinTestFlag）
        Map<Long, Boolean> needAppendSkinTestByMedicationRequestId =
            this.loadNeedAppendSkinTestFlagByMedicationRequestId(
                medicationRows, skinTestOutcomeByMedicationRequestId);
        // Pre：皮试药 requestId → 药名+规格，供 companion「皮试检查」挂关联文案
        Map<Long, String> medicationAdviceNameByRequestId =
            this.buildMedicationAdviceNameByRequestId(medicationRows, medicationDefinitionById, productByKey);

        // Asm：按 OrderInfo 行类型分支映射；非药/诊类型跳过（读模型已过滤，此处防御性 continue）。
        List<InpatientDoctorStationAdviceRowDto> unifiedRows = new ArrayList<>();
        for (InpatientOrderListSkeleton order : orderRows) {
            InpatientDoctorStationAdviceRowDto line = new InpatientDoctorStationAdviceRowDto();
            line.setEncounterId(encounterId);
            line.setPatientId(patientId);
            // 分支：药品医嘱行——明细、护士执行后费用与发放库位由 mapMedicationOrderLine 装配。
            if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
                MedicationRequestListQueryModel medDetail = medicationDetailById.get(order.getMedReqId());
                // 读侧排除退药逆向复制行（refund_medicine_id 非空），与门诊 IF-DOR-10 BR-ORD-030 同口径
                if (medDetail != null
                    && AdviceChargeDisplayStatusAssembler.isRefundOrderCopyRequest(medDetail.getRefundMedicineId())) {
                    continue;
                }
                if (excludeTcmMedication) {
                    if (medDetail == null || Boolean.TRUE.equals(medDetail.getTcmFlag())) {
                        continue;
                    }
                }
                Long medOrgId = null;
                String medStatusEnum = order.getStatusEnum();
                if (medDetail != null) {
                    // 明细存在时以药嘱 org/status 为准；否则回落 OrderInfo 骨架上的状态。
                    medOrgId = medDetail.getOrgId();
                    medStatusEnum = medDetail.getStatusEnum();
                }
                if (!matchesAdviceQuerySourceFilter(
                    medOrgId,
                    medStatusEnum,
                    orderingDepartmentId,
                    requestStatusEnumIn)) {
                    // IF-IP-DOR-22 可选过滤：开单科室或发布态不匹配时跳过本药品行。
                    continue;
                }
                mapMedicationOrderLine(order, line, productByKey, medicationRollup, practitionerNameById,
                    fifoLocByMedReq, locationNameById, currentUserId,
                    medicationDetailById.get(order.getMedReqId()),
                    medicationDefinitionById, unitLabelByCode, methodLabelByCode, rateLabelByCode,
                    timesPerDayByRateCode,
                    doseUnitLabelByCode, dosageInstructionLabelByCode, speedLabelByCode,
                    medicationCategoryLabelByCode, conditionDefinitionDisplayById,
                    conditionDefinitionIdByEncounterDiagnosisId,
                    skinTestOutcomeByMedicationRequestId,
                    needAppendSkinTestByMedicationRequestId);
            // 分支：诊疗医嘱行——套餐子项与出院/转科特规由 mapServiceOrderLine 装配。
            } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
                ServiceRequestListModel serviceDetail = serviceDetailById.get(order.getOrderId());
                // 读侧排除退费逆向复制行（refund_service_id 非空），与门诊 IF-DOR-10 BR-ORD-030 同口径
                if (serviceDetail != null
                    && AdviceChargeDisplayStatusAssembler.isRefundOrderCopyRequest(serviceDetail.getRefundServiceId())) {
                    continue;
                }
                Long serviceOrgId = null;
                String serviceStatusEnum = order.getStatusEnum();
                if (serviceDetail != null) {
                    // 明细存在时以诊疗 org/status 为准；否则回落 OrderInfo 骨架上的状态。
                    serviceOrgId = serviceDetail.getOrgId();
                    serviceStatusEnum = serviceDetail.getStatusEnum();
                }
                if (!matchesAdviceQuerySourceFilter(
                    serviceOrgId,
                    serviceStatusEnum,
                    orderingDepartmentId,
                    requestStatusEnumIn)) {
                    // IF-IP-DOR-22 可选过滤：开单科室或发布态不匹配时跳过本诊疗行。
                    continue;
                }
                mapServiceOrderLine(order, line, productByKey, serviceRollup, practitionerNameById, currentUserId,
                    serviceDetailById.get(order.getOrderId()),
                    activityDefinitionById, packageChildrenByActivityId, unitLabelByCode, rateLabelByCode,
                    activityCategoryLabelByCode, conditionDefinitionDisplayById,
                    conditionDefinitionIdByEncounterDiagnosisId, locationNameById,
                    dischargeOutWayCodeByServiceRequestId, outWayLabelByCode,
                    medicationAdviceNameByRequestId);
            } else {
                // 非药/诊类型：读模型已过滤，防御性跳过。
                continue;
            }
            unifiedRows.add(line);
        }
        for (InpatientDoctorStationAdviceRowDto line : unifiedRows) {
            if (line.getRequestId() == null) {
                continue;
            }
            ServiceRequestListModel serviceDetail = serviceDetailById.get(line.getRequestId());
            if (serviceDetail == null) {
                continue;
            }
            line.setPrescriptionNo(serviceDetail.getPrescriptionNo());
            // 申请单行透出部位/样本摘要，非申请单行保持 null。
            if (ServiceApplyFormOrchestrator.isRequisitionApplicationPrescriptionNo(serviceDetail.getPrescriptionNo())) {
                line.setServiceRequestDetails(serviceApplyFormAssembler.toDetailSummaryDtos(
                    ServiceApplyFormOrchestrator.toDetailSummaryModels(
                        applyFormDetailsByRequestId.get(serviceDetail.getId()))));
            }
        }
        // 口服一盒一点剩余展示（BR-IP-EXE-038；与 IF-IP-EXE-01 同源）
        this.enrichOralPackageInventory(unifiedRows, productByKey, methodLabelByCode);
        // 列表默认排序：开立时间（reqOpenedTime，空则回落 reqAuthoredTime）倒序；成组药按组内最晚开立时刻聚邻。
        this.sortUnifiedAdviceRowsByOpenedTimeDesc(unifiedRows);
        return unifiedRows;
    }

    /**
     * 为命中一盒一点或拆零下限的长期药行回填剩余/已吃/下次领用日；改量链 basedOn 回显。
     *
     * @param unifiedRows       统一行
     * @param productByKey      目录
     * @param methodLabelByCode 用法名
     */
    private void enrichOralPackageInventory(
        List<InpatientDoctorStationAdviceRowDto> unifiedRows,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<String, String> methodLabelByCode) {
        if (unifiedRows == null || unifiedRows.isEmpty()) {
            return;
        }
        boolean applyPackageBatch = !standingOrderSplittableSettingResolver.isSplittable();
        LocalDate asOf = LocalDate.now();
        for (InpatientDoctorStationAdviceRowDto line : unifiedRows) {
            if (line == null || line.getRequestId() == null) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(line.getProductTableName())) {
                continue;
            }
            MedicationRequest display = medicationRequestQueryService.findById(line.getRequestId());
            if (display == null) {
                continue;
            }
            // 改量链回显（无论是否命中剩余规则）
            if (TableNameConstants.MED_MEDICATION_REQUEST.equals(display.getBasedOnTable())) {
                line.setBasedOnTable(display.getBasedOnTable());
                line.setBasedOnId(display.getBasedOnId());
            }
            if (!TherapyType.LONG_TERM.getCode().equals(display.getTherapyEnum())) {
                continue;
            }
            ProductDefinitionDisplayDto product = null;
            if (display.getMedicationId() != null && productByKey != null) {
                String key = ProductDefinitionKeyUtil.buildCompositeKey(
                    TableNameConstants.MED_MEDICATION_DEFINITION, display.getMedicationId());
                product = productByKey.get(key);
            }
            List<MedicationRequest> chain =
                standingOrderOralPackageInventorySupport.loadDoseChangeChain(display.getId());
            Map<Long, List<ProcedureRecordQueryModel>> proceduresByRequestId = new HashMap<>();
            for (MedicationRequest mr : chain) {
                if (mr == null || mr.getId() == null) {
                    continue;
                }
                proceduresByRequestId.put(
                    mr.getId(),
                    procedureQueryService.getProcedureRecords(
                        mr.getId(), TableNameConstants.MED_MEDICATION_REQUEST));
            }
            // 035 一盒一点优先；否则尝试 036 拆零下限剩余（与列表切批优先级一致）
            StandingOrderOralPackageInventorySupport.OralPackageInventorySnapshot snap =
                standingOrderOralPackageInventorySupport.compute(
                    display,
                    proceduresByRequestId,
                    product,
                    methodLabelByCode,
                    asOf,
                    StandingOrderOralPackageInventorySupport.UnitClaimMode.PACKAGE,
                    applyPackageBatch);
            if (!snap.isApplied()) {
                snap = standingOrderOralPackageInventorySupport.compute(
                    display,
                    proceduresByRequestId,
                    product,
                    methodLabelByCode,
                    asOf,
                    StandingOrderOralPackageInventorySupport.UnitClaimMode.MIN_SPLIT,
                    applyPackageBatch);
            }
            if (!snap.isApplied() || !snap.isDisplayHost()) {
                continue;
            }
            // 从未有效领用：不回填已吃/剩余/下次领用（避免「已吃0/剩余0」误导）
            if (snap.getClaimedSplitQuantity() == null
                || snap.getClaimedSplitQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            line.setOralPackageBatchInventoryApplied(Boolean.TRUE);
            line.setOralPackageConsumedSplitQuantity(snap.getConsumedSplitQuantity());
            line.setOralPackageRemainSplitQuantity(snap.getRemainSplitQuantity());
            line.setOralPackageSplitUnitDisplay(snap.getSplitUnitDisplay());
            line.setOralPackageNextClaimDate(snap.getNextClaimDate());
        }
    }

    /**
     * 住院医嘱统一列表按开立时间倒序（{@code reqOpenedTime}，空则 {@code reqAuthoredTime}）。
     *
     * <p>成组药品以组内最晚开立时刻作为簇排序键，保证同组连续便于前端合并展示；
     * 皮试检查 companion 与 based_on 皮试药同簇（药在前、检查在后）；
     * 簇内再按 {@code groupId}、{@code requestId} 稳定次序。</p>
     *
     * @param unifiedRows 待排序统一行（原地排序）
     */
    private void sortUnifiedAdviceRowsByOpenedTimeDesc(List<InpatientDoctorStationAdviceRowDto> unifiedRows) {
        if (unifiedRows == null || unifiedRows.isEmpty()) {
            return;
        }
        Map<Long, String> groupMaxOpenedAt = new HashMap<>();
        Map<Long, String> medicationOpenedAtByRequestId = new HashMap<>();
        for (InpatientDoctorStationAdviceRowDto row : unifiedRows) {
            String openedAt = this.resolveAdviceOpenedInstantForSort(row);
            Long groupId = this.resolveMedicationGroupIdForSort(row);
            if (groupId != null && openedAt != null) {
                String existing = groupMaxOpenedAt.get(groupId);
                // 组内取最晚开立时刻，避免同组因逐行 now() 微差被其他医嘱拆开。
                if (existing == null || openedAt.compareTo(existing) > 0) {
                    groupMaxOpenedAt.put(groupId, openedAt);
                }
            }
            // 皮试 companion 聚邻：记录皮试药开立时刻
            if (this.isMedicationAdviceRow(row) && row.getRequestId() != null && openedAt != null) {
                medicationOpenedAtByRequestId.put(row.getRequestId(), openedAt);
            }
        }
        unifiedRows.sort(Comparator
            .comparing((InpatientDoctorStationAdviceRowDto row) ->
                    this.resolveAdviceListClusterOpenedAt(row, groupMaxOpenedAt, medicationOpenedAtByRequestId),
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(this::resolveMedicationGroupIdForSort, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(this::resolveSkinTestClusterKeyForSort, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(this::resolveSkinTestCompanionRankForSort)
            .thenComparing(InpatientDoctorStationAdviceRowDto::getRequestId, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    /**
     * 列表簇排序键：成组药用组内最晚开立时刻；皮试 companion 用锚定皮试药开立时刻；其余用本行开立时刻。
     *
     * @param row                            列表行
     * @param groupMaxOpenedAt               成组 groupId → 最晚开立时刻
     * @param medicationOpenedAtByRequestId  药品 requestId → 开立时刻
     * @return ISO 本地时间串；无时刻时为 null（排后）
     */
    private String resolveAdviceListClusterOpenedAt(
        InpatientDoctorStationAdviceRowDto row,
        Map<Long, String> groupMaxOpenedAt,
        Map<Long, String> medicationOpenedAtByRequestId) {
        // companion：跟锚皮试药同簇，避免追加临嘱开立更晚被拆到列表顶端
        if (row != null && row.getBasedOnMedicationRequestId() != null && medicationOpenedAtByRequestId != null) {
            String linkedOpened = medicationOpenedAtByRequestId.get(row.getBasedOnMedicationRequestId());
            if (linkedOpened != null) {
                return linkedOpened;
            }
        }
        Long groupId = this.resolveMedicationGroupIdForSort(row);
        if (groupId != null) {
            String groupOpened = groupMaxOpenedAt.get(groupId);
            if (groupOpened != null) {
                return groupOpened;
            }
        }
        return this.resolveAdviceOpenedInstantForSort(row);
    }

    /**
     * 皮试药/companion 聚邻键：药=自身 id；检查=basedOn 药 id；其它 null。
     *
     * @param row 列表行
     * @return 聚邻键
     */
    private Long resolveSkinTestClusterKeyForSort(InpatientDoctorStationAdviceRowDto row) {
        if (row == null) {
            return null;
        }
        if (row.getBasedOnMedicationRequestId() != null) {
            return SkinTestCompanionDisplaySupport.resolveSkinTestClusterKey(
                null, row.getBasedOnMedicationRequestId());
        }
        if (this.isMedicationAdviceRow(row) && Boolean.TRUE.equals(row.getSkinTestFlag())) {
            return SkinTestCompanionDisplaySupport.resolveSkinTestClusterKey(row.getRequestId(), null);
        }
        return null;
    }

    /**
     * 同皮试簇内次序：皮试药 0，companion 检查 1，其它 2。
     *
     * @param row 列表行
     * @return 次序
     */
    private int resolveSkinTestCompanionRankForSort(InpatientDoctorStationAdviceRowDto row) {
        if (row == null) {
            return 2;
        }
        if (row.getBasedOnMedicationRequestId() != null) {
            return 1;
        }
        if (this.isMedicationAdviceRow(row) && Boolean.TRUE.equals(row.getSkinTestFlag())) {
            return 0;
        }
        return 2;
    }

    /**
     * 是否药品医嘱行（定义表名为药品定义）。
     *
     * @param row 列表行
     * @return true 表示药品行
     */
    private boolean isMedicationAdviceRow(InpatientDoctorStationAdviceRowDto row) {
        if (row == null) {
            return false;
        }
        return TableNameConstants.MED_MEDICATION_DEFINITION.equalsIgnoreCase(
            StringUtils.trimToEmpty(row.getProductTableName()));
    }

    /**
     * 单行开立排序键：优先 {@code reqOpenedTime}，空则回落 {@code reqAuthoredTime}。
     *
     * @param row 列表行
     * @return ISO 本地时间串；皆空时 null
     */
    private String resolveAdviceOpenedInstantForSort(InpatientDoctorStationAdviceRowDto row) {
        if (row == null) {
            return null;
        }
        // 业务主轴：开立时刻优先，空则回落签发追溯列。
        if (row.getReqOpenedTime() != null) {
            return row.getReqOpenedTime();
        }
        return row.getReqAuthoredTime();
    }

    /**
     * 药品成组排序键：仅药品定义表且 {@code groupId} 非空时参与成组聚邻。
     *
     * @param row 列表行
     * @return 成组主键；非成组药/诊返回 null
     */
    private Long resolveMedicationGroupIdForSort(InpatientDoctorStationAdviceRowDto row) {
        if (row == null || row.getGroupId() == null) {
            return null;
        }
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(row.getProductTableName())) {
            return null;
        }
        return row.getGroupId();
    }

    /**
     * 将 OrderInfo 药品行与请求明细、收费归并结果装配为统一列表 DTO。
     *
     * <p>优先使用 {@link MedicationRequestListQueryModel} 明细；缺失时回落读模型列。叠加护士执行后费用与发放库位展示。</p>
     */
    private void mapMedicationOrderLine(
        InpatientOrderListSkeleton order,
        InpatientDoctorStationAdviceRowDto line,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, AdviceChargeRollup> medicationRollup,
        Map<Long, String> practitionerNameById,
        Map<Long, Long> fifoLocByMedReq,
        Map<Long, String> locationNameById,
        Long currentUserId,
        MedicationRequestListQueryModel medDetail,
        Map<Long, MedicationDefinition> medicationDefinitionById,
        Map<String, String> unitLabelByCode,
        Map<String, String> methodLabelByCode,
        Map<String, String> rateLabelByCode,
        Map<String, Integer> timesPerDayByRateCode,
        Map<String, String> doseUnitLabelByCode,
        Map<String, String> dosageInstructionLabelByCode,
        Map<String, String> speedLabelByCode,
        Map<String, String> medicationCategoryLabelByCode,
        Map<Long, String> conditionDefinitionDisplayById,
        Map<Long, Long> conditionDefinitionIdByEncounterDiagnosisId,
        Map<Long, AllergyIntoleranceRecordQueryModel> skinTestOutcomeByMedicationRequestId,
        Map<Long, Boolean> needAppendSkinTestByMedicationRequestId
    ) {
        Long requestId = order.getMedReqId();
        String productTable = TableNameConstants.MED_MEDICATION_DEFINITION;
        fillStableKeys(line, requestId, productTable);
        Long medDefId = order.getMedicationId();
        if (medDetail != null && medDetail.getMedicationId() != null) {
            medDefId = medDetail.getMedicationId();
        }
        line.setProductDefinitionId(medDefId);
        line.setProductTableName(productTable);
        // 展示：优先领域药品定义；缺失时回落产品读模型名称；有商品名则括号追加（打印勿复用）。
        MedicationDefinition def = medicationDefinitionById.get(medDefId);
        if (def != null) {
            line.setAdviceDisplayName(MedicationMerchandiseDisplaySupport.formatDisplayName(
                def.getName(), def.getMerchandiseName()));
            line.setSpecificationText(StringUtils.defaultIfBlank(def.getTotalVolume(), def.getComprisedText()));
            fillMedicationCatalogCategory(line, def, medicationCategoryLabelByCode);
        } else {
            ProductDefinitionDisplayDto product = productByKey.get(
                ProductDefinitionKeyUtil.buildCompositeKey(productTable, medDefId));
            if (product != null) {
                line.setAdviceDisplayName(MedicationMerchandiseDisplaySupport.formatDisplayName(
                    product.getProductName(), product.getMerchandiseName()));
            }
        }
        if (medDetail != null) {
            // 明细优先：请求表字段比 OrderInfo 投影更完整（剂量、频次、皮试等）。
            line.setQuantity(medDetail.getQuantity());
            line.setUnitCode(medDetail.getUnitCode());
            ProductDefinitionDisplayDto medProduct = productByKey.get(
                ProductDefinitionKeyUtil.buildCompositeKey(productTable, medDefId));
            line.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                unitLabelByCode, medDetail.getUnitCode(), medProduct));
            line.setDose(medDetail.getDose());
            line.setDoseUnitCode(medDetail.getDoseUnitCode());
            line.setDoseUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveDoseUnitDisplayName(
                doseUnitLabelByCode, medDetail.getDoseUnitCode(), medProduct));
            // Asm：单次剂量展示串 + 拆零结构化字段（单位相同时无括号）
            MedicationDosageDisplayResult dosageResult = MedicationDosageDisplayAssembler.build(
                medDetail.getDose(),
                medDetail.getDoseUnitCode(),
                doseUnitLabelByCode,
                medProduct);
            line.setDosageDisplay(dosageResult.getDosageDisplay());
            line.setSplitDose(dosageResult.getSplitDose());
            line.setSplitDoseUnitCode(dosageResult.getSplitDoseUnitCode());
            line.setSplitDoseUnitName(dosageResult.getSplitDoseUnitName());
            // 首日用量回显：仅长期有意义；单位展示名与剂量单位同解析链（BR-IP-ORD-030）
            line.setFirstDose(medDetail.getFirstDose());
            line.setFirstDoseUnitCode(medDetail.getFirstDoseUnitCode());
            line.setFirstDoseUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveDoseUnitDisplayName(
                doseUnitLabelByCode, medDetail.getFirstDoseUnitCode(), medProduct));
            // 分次 schedule 回显：有值时剂量列走序列展示（BR-IP-ORD-037）
            line.setDoseSchedule(medDetail.getDoseSchedule());
            line.setQuantitySchedule(medDetail.getQuantitySchedule());
            if (StringUtils.isNotEmpty(medDetail.getDoseSchedule())) {
                String scheduleUnitName = ProductDefinitionCatalogUnitBandsAssembler.resolveDoseUnitDisplayName(
                    doseUnitLabelByCode, medDetail.getDoseUnitCode(), medProduct);
                line.setDosageDisplay(this.buildDoseScheduleDisplay(
                    medDetail.getDoseSchedule(), scheduleUnitName));
            }
            line.setMethodCode(medDetail.getMethodCode());
            line.setMethodName(OrderCatalogDisplayAssembler.resolveCategoryDisplay(methodLabelByCode, medDetail.getMethodCode()));
            line.setRateCode(medDetail.getRateCode());
            line.setRateName(OrderCatalogDisplayAssembler.resolveCategoryDisplay(rateLabelByCode, medDetail.getRateCode()));
            line.setLotNumber(medDetail.getLotNumber());
            line.setDispensePerDuration(medDetail.getDispensePerDuration());
            line.setRemark(medDetail.getRemark());
            line.setDosageInstructionCode(medDetail.getDosageInstruction());
            line.setDosageInstructionText(resolveSingleConceptDisplay(
                TerminologyCodeSystemNameConstants.DOSAGE_INSTRUCTION, medDetail.getDosageInstruction(), dosageInstructionLabelByCode));
            line.setSpeedCode(medDetail.getSpeedCode());
            line.setSpeedName(OrderCatalogDisplayAssembler.resolveCategoryDisplay(speedLabelByCode, medDetail.getSpeedCode()));
            line.setSkinTestFlag(medDetail.getSkinTestFlag());
            line.setSkinTestFlagText(formatYesNoChinese(medDetail.getSkinTestFlag()));
            // 皮试结果回填（BR-IP-ORD-016）：锚 skin_test_flag，结果来自护士登记 AllergyIntolerance
            if (Boolean.TRUE.equals(medDetail.getSkinTestFlag()) && requestId != null
                && skinTestOutcomeByMedicationRequestId != null) {
                AllergyIntoleranceRecordQueryModel skinTestOutcome =
                    skinTestOutcomeByMedicationRequestId.get(requestId);
                if (skinTestOutcome != null && StringUtils.isNotEmpty(skinTestOutcome.getClinicalStatusEnum())) {
                    line.setSkinTestOutcomeCode(skinTestOutcome.getClinicalStatusEnum());
                    line.setSkinTestOutcomeText(
                        this.resolveEnumCodeDisplay(ClinicalStatus.class, skinTestOutcome.getClinicalStatusEnum()));
                }
            }
            // 换批提示：仅长嘱皮试药且发放批号与最新皮试批号不一致时为 true
            if (needAppendSkinTestByMedicationRequestId != null && requestId != null) {
                line.setNeedAppendSkinTestFlag(
                    Boolean.TRUE.equals(needAppendSkinTestByMedicationRequestId.get(requestId)));
            }
            line.setInjectionFlag(medDetail.getInfusionFlag());
            line.setInjectionFlagText(formatYesNoChinese(medDetail.getInfusionFlag()));
            line.setEncounterDiagnosisIds(medDetail.getEncounterDiagnosisIds());
            line.setConditionDefinitionName(resolveConditionDefinitionDisplayNames(
                medDetail.getEncounterDiagnosisIds(),
                conditionDefinitionIdByEncounterDiagnosisId,
                conditionDefinitionDisplayById));
            line.setTcmFlag(medDetail.getTcmFlag());
            line.setDecoctionDelegatedFlag(medDetail.getSufferingFlag());
            line.setChineseHerbsDoseQuantity(toIntegerHerbDoses(medDetail.getChineseHerbsDoseQuantity(), medDetail.getTcmFlag()));
            line.setBasedOnServiceRequestId(resolveBasedOnServiceRequestId(medDetail.getBasedOnTable(), medDetail.getBasedOnId()));
            line.setPrescriptionNo(medDetail.getPrescriptionNo());
            line.setGroupId(medDetail.getGroupId());
            line.setSortNumber(medDetail.getSortNumber());
            if (StringUtils.isNotEmpty(medDetail.getTherapyEnum())) {
                line.setTherapyTypeCode(medDetail.getTherapyEnum());
            }
            Long performLoc = medDetail.getPerformLocation();
            if (performLoc != null) {
                line.setPerformLocationId(performLoc);
                line.setPerformLocationName(locationNameById.get(performLoc));
            }
            Long medAuthorId = resolveMedicationAuthorId(medDetail);
            line.setAuthoredById(medAuthorId);
            line.setAuthoredByName(practitionerNameById.get(medAuthorId));
            // 时间字段与落库列同名：开立 / 签发 / 停嘱。
            LocalDateTime medOpenedAt = medDetail.getReqOpenedTime();
            if (medOpenedAt == null) {
                medOpenedAt = medDetail.getReqAuthoredTime();
            }
            line.setReqOpenedTime(formatRequestInstantForApi(medOpenedAt));
            line.setReqAuthoredTime(formatRequestInstantForApi(medDetail.getReqAuthoredTime()));
            line.setStopTime(formatRequestInstantForApi(medDetail.getStopTime()));
            line.setStopperId(medDetail.getStopperId());
            line.setStopperName(practitionerNameById.get(medDetail.getStopperId()));
            // 停嘱校对人：非空时前端禁用取消停嘱
            line.setStopPerformerCheckId(medDetail.getStopPerformerCheckId());
            line.setStatusEnum(medDetail.getStatusEnum());
            line.setStatusEnumText(resolveRequestStatusDisplay(medDetail.getStatusEnum()));
            line.setIsAuthoredByCurrentUser(resolveIsMine(currentUserId, medAuthorId));
        } else {
            // 仅读模型：请求明细未命中时用 OrderInfo 药品列回填基础字段。
            line.setQuantity(order.getQuantity());
            line.setUnitCode(order.getUnitCode());
            ProductDefinitionDisplayDto medProductFromOrder = productByKey.get(
                ProductDefinitionKeyUtil.buildCompositeKey(productTable, medDefId));
            line.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                unitLabelByCode, order.getUnitCode(), medProductFromOrder));
            line.setTherapyTypeCode(order.getTherapyEnum());
            line.setStatusEnum(order.getStatusEnum());
            line.setStatusEnumText(resolveRequestStatusDisplay(order.getStatusEnum()));
            Long authorId = order.getMedRequesterId();
            line.setAuthoredById(authorId);
            line.setAuthoredByName(practitionerNameById.get(authorId));
            line.setReqOpenedTime(formatRequestInstantForApi(order.resolveOrderOpenedTime()));
            line.setReqAuthoredTime(formatRequestInstantForApi(order.getReqAuthoredTime()));
            line.setStopTime(formatRequestInstantForApi(order.getStopTime()));
            line.setStopperId(order.getStopperId());
            line.setStopperName(practitionerNameById.get(order.getStopperId()));
            line.setStopPerformerCheckId(order.getStopPerformerCheckId());
            line.setPerformLocationId(order.getLocationId());
            if (order.getLocationId() != null) {
                line.setPerformLocationName(locationNameById.get(order.getLocationId()));
            }
            line.setIsAuthoredByCurrentUser(resolveIsMine(currentUserId, authorId));
        }
        if (StringUtils.isEmpty(line.getTherapyTypeCode())) {
            line.setTherapyTypeCode(order.getTherapyEnum());
        }
        fillTherapyDisplay(line);
        // 长期药日剂量：有 schedule 各段求和，否则单次拆零量 × 每日次数 →「每日三片」
        fillMedicationDailyDoseDisplay(line, timesPerDayByRateCode);
        // 包装装量后缀：1片x100（前端与规格拼成 5mg1片x100）
        fillMedicationPackageSplitRatio(line, productByKey, productTable, medDefId);
        // 项目类别（甲/乙/丙）：来自药品定义读模型已回填的 CHRGITM_LV 文案
        fillChrgitmLvFromProduct(line, productByKey, productTable, medDefId);
        // 护士执行后费用归并（锚定 med_medication_request 或 dispense）。
        applyChargeRollup(line, medicationRollup.get(requestId));
        fillLineAmountByUnitAndQuantity(line);
        Long fifoLoc = fifoLocByMedReq.get(requestId);
        line.setFifoDispenseLocationId(fifoLoc);
        if (fifoLoc != null) {
            line.setFifoDispenseLocationName(locationNameById.get(fifoLoc));
        }
        line.setContentSummary(buildMedicationSummary(line));
    }

    /**
     * 将 OrderInfo 诊疗行与 ServiceRequest 明细、收费归并结果装配为统一列表 DTO。
     *
     * <p>活动定义子表提供套餐子项；长期医嘱通常无费用列展示。</p>
     */
    private void mapServiceOrderLine(
        InpatientOrderListSkeleton order,
        InpatientDoctorStationAdviceRowDto line,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, AdviceChargeRollup> serviceRollup,
        Map<Long, String> practitionerNameById,
        Long currentUserId,
        ServiceRequestListModel serviceDetail,
        Map<Long, ActivityDefinition> activityDefinitionById,
        Map<Long, List<ActivityDefinitionChildQueryModel>> packageChildrenByActivityId,
        Map<String, String> unitLabelByCode,
        Map<String, String> rateLabelByCode,
        Map<String, String> activityCategoryLabelByCode,
        Map<Long, String> conditionDefinitionDisplayById,
        Map<Long, Long> conditionDefinitionIdByEncounterDiagnosisId,
        Map<Long, String> locationNameById,
        Map<Long, String> dischargeOutWayCodeByServiceRequestId,
        Map<String, String> outWayLabelByCode,
        Map<Long, String> medicationAdviceNameByRequestId
    ) {
        Long requestId = order.getOrderId();
        String productTable = TableNameConstants.WKF_ACTIVITY_DEFINITION;
        fillStableKeys(line, requestId, productTable);
        String serviceCategory = resolveServiceCategoryEnum(serviceDetail);
        line.setServiceCategoryEnum(serviceCategory);
        Long activityId = resolveCatalogActivityId(order.getActivityId(), serviceDetail);
        if (activityId != null) {
            line.setProductDefinitionId(activityId);
        }
        line.setProductTableName(productTable);
        if (!isSpecialServiceCategory(serviceCategory)) {
            ActivityDefinition def = activityDefinitionById.get(activityId);
            if (def != null) {
                line.setAdviceDisplayName(def.getName());
                List<AdvicePackageChildItemDto> packageChildren =
                    AdviceExtensionJsonParser.toPackageChildItems(packageChildrenByActivityId.get(def.getId()));
                line.setPackageChildren(packageChildren);
                fillServiceCatalogAndActivityType(line, def, activityCategoryLabelByCode);
            } else if (activityId != null) {
                ProductDefinitionDisplayDto product = productByKey.get(
                    ProductDefinitionKeyUtil.buildCompositeKey(productTable, activityId));
                if (product != null) {
                    line.setAdviceDisplayName(product.getProductName());
                }
            }
        }
        if (serviceDetail != null) {
            line.setQuantity(serviceDetail.getQuantity());
            line.setUnitCode(serviceDetail.getUnitCode());
            line.setExecutionCount(serviceDetail.getExecutionCount());
            ProductDefinitionDisplayDto svcProduct = productByKey.get(
                ProductDefinitionKeyUtil.buildCompositeKey(productTable, activityId));
            line.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                unitLabelByCode, serviceDetail.getUnitCode(), svcProduct));
            line.setRateCode(serviceDetail.getRateCode());
            line.setRateName(OrderCatalogDisplayAssembler.resolveCategoryDisplay(rateLabelByCode, serviceDetail.getRateCode()));
            line.setRemark(serviceDetail.getRemark());
            line.setEntrustContent(serviceDetail.getEntrustContent());
            line.setBasedOnServiceRequestId(resolveBasedOnServiceRequestId(serviceDetail.getBasedOnTable(), serviceDetail.getBasedOnId()));
            // 皮试检查 companion：挂 based_on 皮试药，展示名追加「（关联：药名 规格）」
            Long basedOnMedReqId = SkinTestCompanionDisplaySupport.resolveBasedOnMedicationRequestId(
                serviceDetail.getBasedOnTable(), serviceDetail.getBasedOnId());
            if (basedOnMedReqId != null) {
                line.setBasedOnMedicationRequestId(basedOnMedReqId);
                String linkedName = null;
                if (medicationAdviceNameByRequestId != null) {
                    linkedName = medicationAdviceNameByRequestId.get(basedOnMedReqId);
                }
                line.setSkinTestLinkedMedicationName(linkedName);
                line.setAdviceDisplayName(SkinTestCompanionDisplaySupport.appendLinkedMedicationHint(
                    line.getAdviceDisplayName(), linkedName));
            }
            line.setEncounterDiagnosisIds(serviceDetail.getEncounterDiagnosisIds());
            line.setConditionDefinitionName(resolveConditionDefinitionDisplayNames(
                serviceDetail.getEncounterDiagnosisIds(),
                conditionDefinitionIdByEncounterDiagnosisId,
                conditionDefinitionDisplayById));
            line.setAuthoredById(serviceDetail.getRequesterId());
            line.setAuthoredByName(practitionerNameById.get(serviceDetail.getRequesterId()));
            // 时间字段与落库列同名：开立 / 签发 / 停嘱。
            LocalDateTime svcOpenedAt = serviceDetail.getReqOpenedTime();
            if (svcOpenedAt == null) {
                svcOpenedAt = serviceDetail.getReqAuthoredTime();
            }
            line.setReqOpenedTime(formatRequestInstantForApi(svcOpenedAt));
            line.setReqAuthoredTime(formatRequestInstantForApi(serviceDetail.getReqAuthoredTime()));
            line.setStopTime(formatRequestInstantForApi(serviceDetail.getStopTime()));
            line.setStopperId(serviceDetail.getStopperId());
            line.setStopperName(practitionerNameById.get(serviceDetail.getStopperId()));
            // 停嘱校对人：非空时前端禁用取消停嘱
            line.setStopPerformerCheckId(serviceDetail.getStopPerformerCheckId());
            line.setSortNumber(serviceDetail.getSortNumber());
            line.setTherapyTypeCode(serviceDetail.getTherapyEnum());
            line.setStatusEnum(serviceDetail.getStatusEnum());
            line.setStatusEnumText(resolveRequestStatusDisplay(serviceDetail.getStatusEnum()));
            Long performLoc = serviceDetail.getLocationId();
            if (performLoc != null) {
                line.setPerformLocationId(performLoc);
                line.setPerformLocationName(locationNameById.get(performLoc));
            }
            line.setIsAuthoredByCurrentUser(resolveIsMine(currentUserId, serviceDetail.getRequesterId()));
        } else {
            line.setQuantity(order.getQuantity());
            line.setUnitCode(order.getUnitCode());
            ProductDefinitionDisplayDto svcProductFromOrder = productByKey.get(
                ProductDefinitionKeyUtil.buildCompositeKey(productTable, activityId));
            line.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                unitLabelByCode, order.getUnitCode(), svcProductFromOrder));
            line.setStatusEnum(order.getStatusEnum());
            line.setStatusEnumText(resolveRequestStatusDisplay(order.getStatusEnum()));
            line.setAuthoredById(order.getRequesterId());
            line.setAuthoredByName(practitionerNameById.get(order.getRequesterId()));
            line.setReqOpenedTime(formatRequestInstantForApi(order.resolveOrderOpenedTime()));
            line.setReqAuthoredTime(formatRequestInstantForApi(order.getReqAuthoredTime()));
            line.setStopTime(formatRequestInstantForApi(order.getStopTime()));
            line.setStopperId(order.getStopperId());
            line.setStopperName(practitionerNameById.get(order.getStopperId()));
            line.setStopPerformerCheckId(order.getStopPerformerCheckId());
            line.setPerformLocationId(order.getLocationId());
            if (order.getLocationId() != null) {
                line.setPerformLocationName(locationNameById.get(order.getLocationId()));
            }
            line.setIsAuthoredByCurrentUser(resolveIsMine(currentUserId, order.getRequesterId()));
        }
        fillTherapyDisplay(line);
        // 项目类别（甲/乙/丙）：来自诊疗定义读模型已回填的 CHRGITM_LV 文案
        fillChrgitmLvFromProduct(line, productByKey, productTable, activityId);
        applyChargeRollup(line, serviceRollup.get(requestId));
        fillLineAmountByUnitAndQuantity(line);
        enrichSpecialServiceOrderLine(
            line, serviceCategory, serviceDetail, requestId, dischargeOutWayCodeByServiceRequestId, outWayLabelByCode);
        // 出院/转科：明细走 outWayCode、reasonText、plannedDischargeTime 等专用列，不写 contentSummary。
        if (!isSpecialServiceCategory(serviceCategory)) {
            line.setContentSummary(buildServiceSummary(line));
        }
    }

    /**
     * 药品行窄列摘要：名称 + 剂量途径频次，兜底处方号或「药品医嘱」。
     *
     * @param line 已填充展示字段的列表行
     * @return 列表窄列展示文案
     */
    private String buildMedicationSummary(InpatientDoctorStationAdviceRowDto line) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotEmpty(line.getAdviceDisplayName())) {
            sb.append(line.getAdviceDisplayName());
        }
        if (line.getDose() != null) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(line.getDose());
            if (StringUtils.isNotEmpty(line.getDoseUnitName())) {
                sb.append(line.getDoseUnitName());
            } else if (StringUtils.isNotEmpty(line.getDoseUnitCode())) {
                sb.append(line.getDoseUnitCode());
            }
        }
        if (StringUtils.isNotEmpty(line.getMethodName()) || StringUtils.isNotEmpty(line.getRateName())) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            if (StringUtils.isNotEmpty(line.getMethodName())) {
                sb.append(line.getMethodName());
            }
            if (StringUtils.isNotEmpty(line.getRateName())) {
                if (StringUtils.isNotEmpty(line.getMethodName())) {
                    sb.append(',');
                }
                sb.append(line.getRateName());
            }
        }
        if (sb.length() == 0 && StringUtils.isNotEmpty(line.getPrescriptionNo())) {
            sb.append(DoctorStationAdviceConstants.ADVICE_LIST_PRESCRIPTION_LABEL_PREFIX).append(line.getPrescriptionNo());
        }
        if (sb.length() == 0) {
            sb.append(DoctorStationAdviceConstants.ADVICE_LIST_MEDICATION_NARROW_FALLBACK_INPATIENT);
        }
        return sb.toString();
    }

    /**
     * 诊疗行窄列摘要：嘱托行优先 {@code entrustContent}（BR-IP-ORD-028）；否则活动定义名称或「诊疗医嘱」。
     *
     * @param line 已填充展示字段的列表行
     * @return 列表窄列展示文案
     */
    private String buildServiceSummary(InpatientDoctorStationAdviceRowDto line) {
        // 嘱托/常规诊疗：摘要与护士站同源（BR-IP-ORD-028④）
        String resolved = inpatientEntrustOrderGateSupport.resolveServiceAdviceDisplayName(
            line.getServiceCategoryEnum(),
            line.getAdviceDisplayName(),
            line.getEntrustContent());
        if (StringUtils.isNotEmpty(resolved)) {
            return resolved;
        }
        // 缺名称时用住院诊疗窄列兜底文案
        return DoctorStationAdviceConstants.ADVICE_LIST_SERVICE_NARROW_FALLBACK_INPATIENT;
    }

    /**
     * 出院/转科类 ServiceRequest 列表展示字段（不依赖活动定义 catalog）。
     */
    private void enrichSpecialServiceOrderLine(
        InpatientDoctorStationAdviceRowDto line,
        String serviceCategory,
        ServiceRequestListModel serviceDetail,
        Long requestId,
        Map<Long, String> dischargeOutWayCodeByServiceRequestId,
        Map<String, String> outWayLabelByCode
    ) {
        if (!isSpecialServiceCategory(serviceCategory)) {
            return;
        }
        if (InpatientNurseStationConstants.SERVICE_CATEGORY_DISCHARGE.equals(serviceCategory)) {
            line.setAdviceDisplayName(DoctorStationAdviceConstants.ADVICE_LIST_DISCHARGE_DISPLAY_NAME_INPATIENT);
            String outWayCode = dischargeOutWayCodeByServiceRequestId.get(requestId);
            line.setOutWayCode(outWayCode);
            line.setOutWayName(OrderCatalogDisplayAssembler.resolveCategoryDisplay(outWayLabelByCode, outWayCode));
        } else if (InpatientNurseStationConstants.SERVICE_CATEGORY_TRANSFER.equals(serviceCategory)) {
            line.setAdviceDisplayName(DoctorStationAdviceConstants.ADVICE_LIST_TRANSFER_DISPLAY_NAME_INPATIENT);
        }
        if (serviceDetail == null) {
            return;
        }
        if (StringUtils.isNotEmpty(serviceDetail.getReasonText())) {
            line.setReasonText(serviceDetail.getReasonText());
        }
        if (serviceDetail.getOccurrenceStartTime() != null) {
            line.setPlannedDischargeTime(formatRequestInstantForApi(serviceDetail.getOccurrenceStartTime()));
            if (StringUtils.isEmpty(line.getReqOpenedTime())) {
                line.setReqOpenedTime(line.getPlannedDischargeTime());
            }
        }
        if (StringUtils.isEmpty(line.getTherapyTypeCode()) && StringUtils.isNotEmpty(serviceDetail.getTherapyEnum())) {
            line.setTherapyTypeCode(normalizeTherapyTypeCode(serviceDetail.getTherapyEnum()));
            fillTherapyDisplay(line);
        } else if (StringUtils.isNotEmpty(line.getTherapyTypeCode())) {
            line.setTherapyTypeCode(normalizeTherapyTypeCode(line.getTherapyTypeCode()));
            fillTherapyDisplay(line);
        }
        if (StringUtils.isEmpty(line.getStatusEnum()) && StringUtils.isNotEmpty(serviceDetail.getStatusEnum())) {
            line.setStatusEnum(serviceDetail.getStatusEnum());
            line.setStatusEnumText(resolveRequestStatusDisplay(serviceDetail.getStatusEnum()));
        }
    }

    /**
     * 按就诊里程碑索引：出院 ServiceRequest → out_way_code。
     */
    private Map<Long, String> indexDischargeOutWayCodes(Long encounterId, Set<Long> serviceRequestIds) {
        Map<Long, String> map = new HashMap<>();
        if (encounterId == null || serviceRequestIds == null || serviceRequestIds.isEmpty()) {
            return map;
        }
        List<EncounterHistoryMilestoneQueryModel> milestones =
            encounterHistoryQueryService.listMilestonesByEncounter(encounterId);
        for (EncounterHistoryMilestoneQueryModel milestone : milestones) {
            if (milestone == null || milestone.getRequestId() == null) {
                continue;
            }
            if (!CustomConstants.EncounterHistoryMilestoneName.DISCHARGE.equals(milestone.getName())) {
                continue;
            }
            if (!serviceRequestIds.contains(milestone.getRequestId())) {
                continue;
            }
            if (StringUtils.isNotEmpty(milestone.getOutWayCode())) {
                map.put(milestone.getRequestId(), milestone.getOutWayCode().trim());
            }
        }
        return map;
    }

    /**
     * 从 ServiceRequest 列表行解析服务类目持久化码（category_enum）。
     *
     * @param serviceDetail 诊疗申请明细；允许 null
     * @return 类目 code；无值时 null
     */
    private static String resolveServiceCategoryEnum(ServiceRequestListModel serviceDetail) {
        if (serviceDetail == null || StringUtils.isEmpty(serviceDetail.getCategoryEnum())) {
            return null;
        }
        return serviceDetail.getCategoryEnum().trim();
    }

    /**
     * 判断是否为出院/转科等特殊诊疗类目（不依赖活动定义 catalog）。
     *
     * @param serviceCategory {@link InpatientNurseStationConstants} 类目 code
     * @return 出院或转科时为 true
     */
    private static boolean isSpecialServiceCategory(String serviceCategory) {
        return InpatientNurseStationConstants.SERVICE_CATEGORY_DISCHARGE.equals(serviceCategory)
            || InpatientNurseStationConstants.SERVICE_CATEGORY_TRANSFER.equals(serviceCategory);
    }

    /**
     * 出院/转科等占位 activity_id（0 或 null）不参与活动定义 catalog 预热。
     */
    private static Long resolveCatalogActivityId(Long orderActivityId, ServiceRequestListModel serviceDetail) {
        Long candidate = orderActivityId;
        if (serviceDetail != null && serviceDetail.getActivityId() != null) {
            candidate = serviceDetail.getActivityId();
        }
        if (candidate == null || candidate <= 0L) {
            return null;
        }
        if (serviceDetail != null && isSpecialServiceCategory(resolveServiceCategoryEnum(serviceDetail))) {
            return null;
        }
        return candidate;
    }

    /**
     * 回填药品目录类目 code 与术语展示名。
     */
    private void fillMedicationCatalogCategory(
        InpatientDoctorStationAdviceRowDto line,
        MedicationDefinition def,
        Map<String, String> medicationCategoryLabelByCode
    ) {
        if (def == null || StringUtils.isEmpty(def.getCategoryCode())) {
            return;
        }
        line.setCatalogCategoryCode(def.getCategoryCode().trim());
        line.setCatalogCategoryName(
            OrderCatalogDisplayAssembler.resolveCategoryDisplay(medicationCategoryLabelByCode, def.getCategoryCode()));
    }

    /**
     * 分次剂量序列展示：各段拼单位，如 {@code 2mg,1mg}（BR-IP-ORD-037）。
     *
     * @param doseSchedule 计量逗号串；非空
     * @param unitName     剂量单位展示名；可空
     * @return 序列展示文案
     */
    private String buildDoseScheduleDisplay(String doseSchedule, String unitName) {
        String[] parts = doseSchedule.split(",");
        StringBuilder sb = new StringBuilder();
        String unit = StringUtils.trimToEmpty(unitName);
        for (int i = 0; i < parts.length; i++) {
            String seg = StringUtils.trimToEmpty(parts[i]);
            if (seg.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(seg);
            if (!unit.isEmpty()) {
                sb.append(unit);
            }
        }
        if (sb.length() == 0) {
            return null;
        }
        return sb.toString();
    }

    /**
     * 回填药品包装装量后缀：目录包装相对拆零倍数 →「1片x100」（与规格拼成 5mg1片x100）。
     *
     * @param line          列表行
     * @param productByKey  产品读模型
     * @param productTable  药品定义表名
     * @param medicationId  药品定义主键
     */
    private void fillMedicationPackageSplitRatio(
        InpatientDoctorStationAdviceRowDto line,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        String productTable,
        Long medicationId
    ) {
        if (medicationId == null || productByKey == null) {
            return;
        }
        ProductDefinitionDisplayDto product = productByKey.get(
            ProductDefinitionKeyUtil.buildCompositeKey(productTable, medicationId));
        if (product == null) {
            return;
        }
        // 数值口径：与目录 package_unit_ratio 同源（legacy 字段名 partPercent）
        line.setPartPercent(product.getPackageUnitRatio());
        line.setPackageSplitRatioText(
            MedicationPackageSplitQuantityDisplayAssembler.buildPackageSplitRatioText(product));
    }

    /**
     * 长期药回填日剂量友好文案（每日三片）；临时/出院带药不填。
     *
     * <p>有 {@code quantitySchedule}/{@code doseSchedule} 时按各段求和（禁止首项×次数；
     * BR-IP-ORD-037⑨ / BR-IP-EXE-040）。</p>
     *
     * @param line                   已填 dose/splitDose/rateCode/therapyTypeCode/schedule 的列表行
     * @param timesPerDayByRateCode  频次码 → 每日次数
     */
    private void fillMedicationDailyDoseDisplay(
        InpatientDoctorStationAdviceRowDto line,
        Map<String, Integer> timesPerDayByRateCode
    ) {
        if (line == null) {
            return;
        }
        if (!TherapyType.LONG_TERM.getCode().equals(StringUtils.trimToEmpty(line.getTherapyTypeCode()))) {
            return;
        }
        Integer timesPerDay = null;
        if (timesPerDayByRateCode != null && StringUtils.isNotEmpty(line.getRateCode())) {
            timesPerDay = timesPerDayByRateCode.get(line.getRateCode());
        }
        // 有拆零结构化量优先；同单位时 splitDose 为空，回落 dose + 拆零/剂量单位名
        BigDecimal perDoseQty = line.getSplitDose();
        String perDoseUnit = line.getSplitDoseUnitName();
        if (perDoseQty == null) {
            perDoseQty = line.getDose();
            if (StringUtils.isEmpty(perDoseUnit)) {
                perDoseUnit = line.getDoseUnitName();
            }
        }
        // 拆零日总量单位：优先列表 unitName，否则拆零结构化单位
        String quantityUnitName = line.getUnitName();
        if (StringUtils.isEmpty(quantityUnitName)) {
            quantityUnitName = line.getSplitDoseUnitName();
        }
        line.setDailyDoseDisplay(
            MedicationDailyDoseDisplayAssembler.buildDailyDoseDisplayPreferringSchedule(
                line.getQuantitySchedule(),
                quantityUnitName,
                line.getDoseSchedule(),
                line.getDoseUnitName(),
                perDoseQty,
                perDoseUnit,
                timesPerDay));
    }

    /**
     * 回填诊疗目录类目与活动类型（检查/检验等）展示列。
     */
    private void fillServiceCatalogAndActivityType(
        InpatientDoctorStationAdviceRowDto line,
        ActivityDefinition def,
        Map<String, String> activityCategoryLabelByCode
    ) {
        if (StringUtils.isNotEmpty(def.getCategoryCode())) {
            line.setCatalogCategoryCode(def.getCategoryCode().trim());
            line.setCatalogCategoryName(
                OrderCatalogDisplayAssembler.resolveCategoryDisplay(activityCategoryLabelByCode, def.getCategoryCode()));
        }
        if (StringUtils.isNotEmpty(def.getTypeEnum())) {
            line.setActivityTypeCode(def.getTypeEnum().trim());
            ActivityType activityType = EnumUtil.fromCode(ActivityType.class, def.getTypeEnum().trim());
            if (activityType != null) {
                line.setActivityTypeName(activityType.getDisplay());
            }
        }
    }

    /**
     * 从医嘱绑定 ids 串 收集就诊诊断主键（去重写入 target）。
     *
     * @param idsText    {@code encounter_diagnosis_ids} 原始串
     * @param target 收集容器
     */
    private static void collectEncounterDiagnosisIdsFromIds(String idsText, Set<Long> target) {
        if (target == null || StringUtils.isEmpty(idsText)) {
            return;
        }
        String[] parts = idsText.split(",");
        for (String part : parts) {
            if (StringUtils.isEmpty(part)) {
                continue;
            }
            String trimmed = part.trim();
            if (StringUtils.isEmpty(trimmed)) {
                continue;
            }
            try {
                target.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                // 跳过非法段，避免整表装配失败
            }
        }
    }

    /**
     * 按绑定 ids 全量拼诊断展示名（顿号；读侧一药多诊断回显）。
     *
     * @param idsText {@code encounter_diagnosis_ids}
     * @param conditionDefinitionIdByEncounterDiagnosisId 就诊诊断 id → 诊断定义 id
     * @param conditionDefinitionDisplayById              诊断定义 id → 名称
     * @return 拼接名；无命中时 null
     */
    private static String resolveConditionDefinitionDisplayNames(
        String idsText,
        Map<Long, Long> conditionDefinitionIdByEncounterDiagnosisId,
        Map<Long, String> conditionDefinitionDisplayById) {
        return EncounterDiagnosisIdsSupport.joinOrderedDisplayNames(idsText, encounterDiagnosisId -> {
            if (conditionDefinitionIdByEncounterDiagnosisId == null
                || conditionDefinitionDisplayById == null) {
                return null;
            }
            Long definitionId = conditionDefinitionIdByEncounterDiagnosisId.get(encounterDiagnosisId);
            if (definitionId == null) {
                return null;
            }
            return conditionDefinitionDisplayById.get(definitionId);
        });
    }

    /**
     * 批量解析诊断定义主键 → 展示名称（药品/诊疗关联诊断列）。
     *
     * @param ids 诊断定义主键集合
     * @return id → 名称映射
     */
    private Map<Long, String> loadConditionDefinitionDisplayNames(Set<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        List<ConditionDefinitionBatchItemQueryModel> items =
            conditionDefinitionQueryService.getConditionDefinitionListByIds(new ArrayList<>(ids));
        for (ConditionDefinitionBatchItemQueryModel item : items) {
            if (item.getId() != null) {
                map.put(item.getId(), StringUtils.defaultString(item.getName()));
            }
        }
        return map;
    }

    /**
     * 药品开立人：优先 requester，缺省 recorder（与门诊列表口径一致）。
     */
    private static Long resolveMedicationAuthorId(MedicationRequestListQueryModel row) {
        if (row.getRequesterId() != null) {
            return row.getRequesterId();
        }
        return row.getRecorderId();
    }

    /**
     * 单条术语概念展示：先查预热 label 缓存，未命中再查编码系统。
     */
    private String resolveSingleConceptDisplay(String codeSystemName, String code, Map<String, String> cache) {
        String fromCache = OrderCatalogDisplayAssembler.resolveCategoryDisplay(cache, code);
        if (fromCache != null) {
            return fromCache;
        }
        Concept concept = codeSystemQueryService.findConceptByCodeSystemNameAndCode(codeSystemName, code);
        if (concept == null) {
            return null;
        }
        return concept.getDisplay();
    }

    /**
     * 中药付数：仅 TCM 标志为真时取整数量，否则不展示。
     */
    private Integer toIntegerHerbDoses(BigDecimal raw, Boolean tcmFlag) {
        if (raw == null || !Boolean.TRUE.equals(tcmFlag)) {
            return null;
        }
        return raw.intValue();
    }

    /**
     * 解析「基于诊疗申请」外键：仅当 basedOn 锚定 {@code wkf_service_request} 时返回 id。
     */
    private Long resolveBasedOnServiceRequestId(String basedOnTable, Long basedOnId) {
        if (basedOnId == null) {
            return null;
        }
        if (TableNameConstants.WKF_SERVICE_REQUEST.equalsIgnoreCase(trimToEmpty(basedOnTable))) {
            return basedOnId;
        }
        return null;
    }

    /**
     * 批量加载皮试药 nurse 登记结果：皮试药 id → AllergyIntolerance（经 doctor-prescription 皮试检查 SR 关联）。
     *
     * <p>BR 最新覆盖：同一皮试药可挂多条 companion 皮试检查 SR（换批重做），须索引<strong>全部</strong>
     * companion id；再在 Allergy 中取 {@code recordedDate} 最新一条（空排后，同刻取更大 id）回填 outcome。</p>
     *
     * @param encounterId     就诊主键
     * @param medicationRows  本就诊药品请求列表
     * @return 皮试药 id → 过敏/皮试记录（最新一次）
     */
    private Map<Long, AllergyIntoleranceRecordQueryModel> loadSkinTestOutcomeByMedicationRequestId(
        Long encounterId,
        List<MedicationRequestListQueryModel> medicationRows
    ) {
        Map<Long, AllergyIntoleranceRecordQueryModel> outcomeByMedicationRequestId = new HashMap<>();
        if (encounterId == null || medicationRows == null || medicationRows.isEmpty()) {
            return outcomeByMedicationRequestId;
        }
        List<Long> skinTestMedicationRequestIds = new ArrayList<>();
        for (MedicationRequestListQueryModel row : medicationRows) {
            if (Boolean.TRUE.equals(row.getSkinTestFlag()) && row.getId() != null) {
                skinTestMedicationRequestIds.add(row.getId());
            }
        }
        if (skinTestMedicationRequestIds.isEmpty()) {
            return outcomeByMedicationRequestId;
        }
        Long skinTestActivityId;
        try {
            skinTestActivityId = activityDefinitionQueryService.requireIdByExactName(
                GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME);
        } catch (BusinessException ex) {
            return outcomeByMedicationRequestId;
        }
        List<ServiceRequest> companionServiceRequests = serviceRequestQueryService.listServiceRequestsByBasedOnAnchors(
            TableNameConstants.MED_MEDICATION_REQUEST, skinTestMedicationRequestIds);
        // BR 最新覆盖：索引该皮试药下全部 companion SR id（禁止 putIfAbsent 只留首条）
        Map<Long, Set<Long>> companionSrIdsByMedicationRequestId = new HashMap<>();
        String doctorPrescriptionSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        for (ServiceRequest companion : companionServiceRequests) {
            if (companion == null || Boolean.TRUE.equals(companion.getIsDeleted())) {
                continue;
            }
            if (!doctorPrescriptionSource.equals(companion.getGenerateSourceEnum())) {
                continue;
            }
            if (!skinTestActivityId.equals(companion.getActivityId())) {
                continue;
            }
            if (companion.getBasedOnId() == null || companion.getId() == null) {
                continue;
            }
            Set<Long> companionIds = companionSrIdsByMedicationRequestId.get(companion.getBasedOnId());
            if (companionIds == null) {
                companionIds = new LinkedHashSet<>();
                companionSrIdsByMedicationRequestId.put(companion.getBasedOnId(), companionIds);
            }
            companionIds.add(companion.getId());
        }
        if (companionSrIdsByMedicationRequestId.isEmpty()) {
            return outcomeByMedicationRequestId;
        }
        List<AllergyIntoleranceRecordQueryModel> allergyRecords =
            allergyIntoleranceQueryService.getRecordInfo(encounterId);
        if (allergyRecords == null) {
            allergyRecords = List.of();
        }
        for (Map.Entry<Long, Set<Long>> entry : companionSrIdsByMedicationRequestId.entrySet()) {
            AllergyIntoleranceRecordQueryModel latest = null;
            // 在该皮试药全部 companion requestId 上取 recordedDate 最新的 Allergy
            for (AllergyIntoleranceRecordQueryModel allergyRecord : allergyRecords) {
                if (allergyRecord == null || allergyRecord.getRequestId() == null) {
                    continue;
                }
                if (!entry.getValue().contains(allergyRecord.getRequestId())) {
                    continue;
                }
                if (this.isLaterAllergyRecordForSkinTestOutcome(allergyRecord, latest)) {
                    latest = allergyRecord;
                }
            }
            if (latest != null) {
                outcomeByMedicationRequestId.put(entry.getKey(), latest);
            }
        }
        return outcomeByMedicationRequestId;
    }

    /**
     * 长嘱皮试药换批提示：比较最新皮试登记批号与该药发放批号集合。
     *
     * <p>无既往皮试批号视为首次，不下发 true；临嘱/非皮试跳过。软提示，不抛业务异常。</p>
     *
     * @param medicationRows                      本就诊药品请求列表
     * @param skinTestOutcomeByMedicationRequestId 皮试药 → 最新 Allergy（含 lotNumber）
     * @return 皮试药 id → 是否建议追加皮试检查
     */
    private Map<Long, Boolean> loadNeedAppendSkinTestFlagByMedicationRequestId(
        List<MedicationRequestListQueryModel> medicationRows,
        Map<Long, AllergyIntoleranceRecordQueryModel> skinTestOutcomeByMedicationRequestId
    ) {
        Map<Long, Boolean> flagByMedReqId = new HashMap<>();
        if (medicationRows == null || medicationRows.isEmpty()) {
            return flagByMedReqId;
        }
        List<Long> longTermSkinTestMedReqIds = new ArrayList<>();
        for (MedicationRequestListQueryModel row : medicationRows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (!Boolean.TRUE.equals(row.getSkinTestFlag())) {
                continue;
            }
            if (!TherapyType.LONG_TERM.getCode().equals(row.getTherapyEnum())) {
                continue;
            }
            longTermSkinTestMedReqIds.add(row.getId());
        }
        if (longTermSkinTestMedReqIds.isEmpty()) {
            return flagByMedReqId;
        }
        List<MedicationDispense> dispenses =
            medicationDispenseQueryService.listByMedReqIds(longTermSkinTestMedReqIds);
        Map<Long, Set<String>> dispenseLotsByMedReqId = new HashMap<>();
        if (dispenses != null) {
            for (MedicationDispense dispense : dispenses) {
                if (dispense == null || Boolean.TRUE.equals(dispense.getIsDeleted())) {
                    continue;
                }
                if (dispense.getMedReqId() == null) {
                    continue;
                }
                String lot = StringUtils.trimToEmpty(dispense.getLotNumber());
                if (StringUtils.isEmpty(lot)) {
                    continue;
                }
                Set<String> lots = dispenseLotsByMedReqId.get(dispense.getMedReqId());
                if (lots == null) {
                    lots = new HashSet<>();
                    dispenseLotsByMedReqId.put(dispense.getMedReqId(), lots);
                }
                lots.add(lot);
            }
        }
        for (Long medReqId : longTermSkinTestMedReqIds) {
            AllergyIntoleranceRecordQueryModel outcome = null;
            if (skinTestOutcomeByMedicationRequestId != null) {
                outcome = skinTestOutcomeByMedicationRequestId.get(medReqId);
            }
            String lastSkinTestLot = "";
            if (outcome != null) {
                lastSkinTestLot = StringUtils.trimToEmpty(outcome.getLotNumber());
            }
            // 无既往皮试批号：首次使用，不提示追加
            if (StringUtils.isEmpty(lastSkinTestLot)) {
                continue;
            }
            Set<String> dispenseLots = dispenseLotsByMedReqId.get(medReqId);
            if (dispenseLots == null || dispenseLots.isEmpty()) {
                continue;
            }
            // 任一发放批号与最新皮试批号不等 → 建议换批追加
            boolean needAppend = false;
            for (String dispenseLot : dispenseLots) {
                if (dispenseLot != null && !dispenseLot.equals(lastSkinTestLot)) {
                    needAppend = true;
                    break;
                }
            }
            if (needAppend) {
                flagByMedReqId.put(medReqId, Boolean.TRUE);
            }
        }
        return flagByMedReqId;
    }

    /**
     * 药品 requestId → 关联展示标签（药名 + 规格），供皮试检查 companion「（关联：…）」文案。
     *
     * @param medicationRows              本就诊药品请求
     * @param medicationDefinitionById    药品定义
     * @param productByKey                产品读模型
     * @return requestId → 标签；无名且无规格时不入表
     */
    private Map<Long, String> buildMedicationAdviceNameByRequestId(
        List<MedicationRequestListQueryModel> medicationRows,
        Map<Long, MedicationDefinition> medicationDefinitionById,
        Map<String, ProductDefinitionDisplayDto> productByKey
    ) {
        Map<Long, String> nameByRequestId = new HashMap<>();
        if (medicationRows == null || medicationRows.isEmpty()) {
            return nameByRequestId;
        }
        for (MedicationRequestListQueryModel row : medicationRows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            String name = null;
            String spec = null;
            Long medDefId = row.getMedicationId();
            MedicationDefinition def = null;
            if (medDefId != null && medicationDefinitionById != null) {
                def = medicationDefinitionById.get(medDefId);
            }
            if (def != null) {
                name = StringUtils.trimToEmpty(def.getName());
                // 规格与医嘱列表 specificationText 同源（totalVolume → comprisedText）
                spec = OrderCatalogDisplayAssembler.resolveMedicationSpecText(def);
            }
            ProductDefinitionDisplayDto product = null;
            if (medDefId != null && productByKey != null) {
                product = productByKey.get(
                    ProductDefinitionKeyUtil.buildCompositeKey(
                        TableNameConstants.MED_MEDICATION_DEFINITION, medDefId));
            }
            if (StringUtils.isEmpty(name) && product != null) {
                name = StringUtils.trimToEmpty(product.getProductName());
            }
            if (StringUtils.isEmpty(spec) && product != null) {
                spec = StringUtils.trimToEmpty(product.getTotalVolume());
            }
            String label = SkinTestCompanionDisplaySupport.formatLinkedMedicationLabel(name, spec);
            if (StringUtils.isNotEmpty(label)) {
                nameByRequestId.put(row.getId(), label);
            }
        }
        return nameByRequestId;
    }

    /**
     * 皮试结果「最新覆盖」比较：{@code recordedDate} 非空优先于空；同刻取更大 id。
     *
     * @param candidate 候选 Allergy
     * @param baseline  当前最新；可空
     * @return true 表示 candidate 应覆盖 baseline
     */
    private boolean isLaterAllergyRecordForSkinTestOutcome(
        AllergyIntoleranceRecordQueryModel candidate,
        AllergyIntoleranceRecordQueryModel baseline) {
        if (candidate == null) {
            return false;
        }
        if (baseline == null) {
            return true;
        }
        LocalDateTime candidateDate = candidate.getRecordedDate();
        LocalDateTime baselineDate = baseline.getRecordedDate();
        // recordedDate 非空优先于空（nulls last）
        if (candidateDate != null && baselineDate == null) {
            return true;
        }
        if (candidateDate == null && baselineDate != null) {
            return false;
        }
        if (candidateDate != null && baselineDate != null) {
            int cmp = candidateDate.compareTo(baselineDate);
            if (cmp > 0) {
                return true;
            }
            if (cmp < 0) {
                return false;
            }
        }
        // 同刻或均空：更大 id 优先
        Long candidateId = candidate.getId();
        Long baselineId = baseline.getId();
        if (candidateId == null) {
            return false;
        }
        if (baselineId == null) {
            return true;
        }
        return candidateId.compareTo(baselineId) > 0;
    }

    /**
     * 持久化枚举 code → 展示文案（皮试临床状态等）。
     *
     * @param enumClass 枚举类型
     * @param code      持久化码
     * @return 展示名；未命中时 {@code null}
     */
    private <E extends Enum<E> & EnumInterface> String resolveEnumCodeDisplay(Class<E> enumClass, String code) {
        if (StringUtils.isEmpty(code)) {
            return null;
        }
        E enumValue = EnumUtil.fromCode(enumClass, code);
        if (enumValue == null) {
            return null;
        }
        return enumValue.getDisplay();
    }

    /**
     * 将布尔标志格式化为中文「是/否」展示文案。
     *
     * @param flag 业务标志，{@code null} 表示未设置
     * @return 展示文案；未设置时返回 {@code null}
     */
    private String formatYesNoChinese(Boolean flag) {
        if (flag == null) {
            return null;
        }
        if (Boolean.TRUE.equals(flag)) {
            return MedicationCommonConstants.WORKSTATION_BOOLEAN_DISPLAY_YES;
        }
        return MedicationCommonConstants.WORKSTATION_BOOLEAN_DISPLAY_NO;
    }

    /**
     * 批量加载药品/诊疗产品读模型行（复合键 Map）。
     */
    private Map<String, ProductDefinitionDisplayDto> batchLoadProducts(Set<Long> medDefIds, Set<Long> activityDefIds) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (Long id : medDefIds) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(id);
            lookups.add(lookup);
        }
        for (Long id : activityDefIds) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
            lookup.setProductDefinitionId(id);
            lookups.add(lookup);
        }
        if (lookups.isEmpty()) {
            return new HashMap<>();
        }
        return productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
    }

    /**
     * 列表行「项目类别」：从产品读模型回填医保等级码与展示文案（甲类/乙类/丙类）。
     *
     * @param line                统一列表行
     * @param productByKey        产品读模型（复合键）
     * @param productTable        定义表名
     * @param productDefinitionId 定义主键
     */
    private void fillChrgitmLvFromProduct(
        InpatientDoctorStationAdviceRowDto line,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        String productTable,
        Long productDefinitionId
    ) {
        if (line == null || productByKey == null || productDefinitionId == null) {
            return;
        }
        ProductDefinitionDisplayDto product = productByKey.get(
            ProductDefinitionKeyUtil.buildCompositeKey(productTable, productDefinitionId));
        if (product == null) {
            return;
        }
        line.setChrgitmLv(product.getChrgitmLv());
        line.setChrgitmLvText(product.getChrgitmLvText());
    }

    /**
     * 治疗类型 code → 中文展示（{@link TherapyType}：长期/临时/出院带药）。
     *
     * @param line 已写入 therapyTypeCode 的列表行
     */
    private void fillTherapyDisplay(InpatientDoctorStationAdviceRowDto line) {
        if (StringUtils.isEmpty(line.getTherapyTypeCode())) {
            return;
        }
        line.setTherapyTypeCode(normalizeTherapyTypeCode(line.getTherapyTypeCode()));
        TherapyType therapyType = EnumUtil.fromCode(TherapyType.class, line.getTherapyTypeCode());
        if (therapyType != null) {
            line.setTherapyTypeText(therapyType.getDisplay());
        }
    }

    /**
     * 兼容历史数据：部分出院/转科申请曾持久化 therapy_enum 为 sortOrder 数字串 {@code 2}。
     *
     * @param therapyCode 持久化长临 code
     * @return 归一化后的 {@link TherapyType#getCode()}
     */
    private static String normalizeTherapyTypeCode(String therapyCode) {
        if ("2".equals(trimToEmpty(therapyCode))) {
            return TherapyType.TEMPORARY.getCode();
        }
        return therapyCode;
    }

    /**
     * 列表行稳定主键：requestId + 产品表名复合键，供前端勾选/签发定位。
     */
    private void fillStableKeys(InpatientDoctorStationAdviceRowDto line, Long requestId, String productTableName) {
        line.setRequestId(requestId);
        line.setListRowKey(buildListRowKey(requestId, productTableName));
    }

    /**
     * 将多笔 {@link ChargeItem} 归并结果写入列表行费用展示列（长期无 rollup 时跳过）。
     */
    private void applyChargeRollup(InpatientDoctorStationAdviceRowDto line, AdviceChargeRollup rollup) {
        if (rollup != null) {
            line.setChargeStatusEnum(rollup.chargeStatusEnum);
            line.setChargeStatusEnumText(
                AdviceChargeDisplayStatusAssembler.resolveChargeStatusDisplayTextForAdviceList(rollup.chargeStatusEnum));
            line.setPrimaryChargeItemId(rollup.primaryChargeItemId);
            if (!rollup.chargeItemIds.isEmpty()) {
                line.setChargeItemIds(new ArrayList<>(rollup.chargeItemIds));
            }
            line.setLineDisplayUnitPrice(scaleChargeDisplay(rollup.lineDisplayUnitPrice));
            line.setLineDisplayTotalPrice(scaleChargeDisplay(rollup.lineDisplayTotalPrice));
        }
        applyListDisplayStatus(line);
    }

    /**
     * 住院医嘱列表行：合并医嘱状态与收费状态为 displayStatus 展示列。
     *
     * @param line 列表行 DTO
     */
    private void applyListDisplayStatus(InpatientDoctorStationAdviceRowDto line) {
        AdviceChargeDisplayStatusAssembler.ListDisplayStatus display =
            AdviceChargeDisplayStatusAssembler.resolveAdviceListDisplayStatus(
                line.getStatusEnum(), line.getStatusEnumText(), line.getChargeStatusEnum());
        line.setDisplayStatusEnum(display.displayStatusEnum());
        line.setDisplayStatusEnumText(display.displayStatusEnumText());
    }

    /**
     * 列表金额展示统一小数位（与 {@link DoctorStationAdviceConstants#ADVICE_LIST_CHARGE_DISPLAY_DECIMAL_SCALE} 一致）。
     */
    private static BigDecimal scaleChargeDisplay(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return MonetaryAmountSupport.scaleCalc(value);
    }

    /**
     * 行级「数量×单价」乘积展示（与归并单价同源）。
     */
    private void fillLineAmountByUnitAndQuantity(InpatientDoctorStationAdviceRowDto line) {
        if (line.getQuantity() == null || line.getLineDisplayUnitPrice() == null) {
            return;
        }
        BigDecimal product = line.getQuantity().multiply(line.getLineDisplayUnitPrice());
        line.setLineAmountByUnitAndQuantity(scaleChargeDisplay(product));
    }

    /**
     * 批量解析医师主键 → 姓名。
     */
    private Map<Long, String> loadPractitionerNames(Set<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        List<Practitioner> list = practitionerQueryService.findAllByIds(ids);
        for (Practitioner p : list) {
            map.put(p.getId(), p.getName());
        }
        return map;
    }

    /**
     * 收集需参与「发放 id→药品申请 id」映射的发药主键（兼容 service 锚发放与 dispense_* 列）。
     *
     * @param chargeItems 本就诊全部收费行
     * @return 发药 id → 药品申请 id
     */
    private Map<Long, Long> collectMedicationDispenseServiceIds(List<ChargeItem> chargeItems) {
        Set<Long> dispenseIds = new HashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
                continue;
            }
            // 旧式：计费业务来源直接锚发放表。
            if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(chargeItem.getServiceTable())) {
                dispenseIds.add(chargeItem.getServiceId());
            }
            // 新式：发放键落在 dispense_*，与 service_*（医嘱请求）并存。
            if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(chargeItem.getDispenseTable())
                && chargeItem.getDispenseId() != null) {
                dispenseIds.add(chargeItem.getDispenseId());
            }
        }
        return medicationDispenseQueryService.mapMedicationDispenseIdsToMedReqIds(dispenseIds);
    }

    /**
     * 将收费行归并到药品申请：含直接锚申请与经发药映射的申请。
     */
    private Map<Long, AdviceChargeRollup> rollupChargesForMedicationRequests(
        List<ChargeItem> chargeItems,
        Map<Long, Long> dispenseIdToMedicationReq
    ) {
        Map<Long, AdviceChargeRollup> map = new HashMap<>();
        for (ChargeItem chargeItem : chargeItems) {
            // 逻辑删费用不参与患者列表态与金额汇总。
            if (Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
                continue;
            }
            Long medicationRequestId = null;
            if (TableNameConstants.MED_MEDICATION_REQUEST.equals(chargeItem.getServiceTable())) {
                medicationRequestId = chargeItem.getServiceId();
            } else if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(chargeItem.getServiceTable())) {
                medicationRequestId = dispenseIdToMedicationReq.get(chargeItem.getServiceId());
            } else if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(chargeItem.getDispenseTable())
                && chargeItem.getDispenseId() != null) {
                medicationRequestId = dispenseIdToMedicationReq.get(chargeItem.getDispenseId());
            }
            if (medicationRequestId == null) {
                continue;
            }
            AdviceChargeRollup rollup = map.computeIfAbsent(medicationRequestId, k -> new AdviceChargeRollup());
            rollup.absorb(chargeItem, this);
        }
        return map;
    }

    /**
     * 将收费行按 {@code service_table} 锚定归并到指定请求表（诊疗等单锚场景）。
     */
    private Map<Long, AdviceChargeRollup> rollupChargesForAnchoredTable(List<ChargeItem> chargeItems, String serviceTable) {
        Map<Long, AdviceChargeRollup> map = new HashMap<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
                continue;
            }
            if (!serviceTable.equals(chargeItem.getServiceTable())) {
                continue;
            }
            AdviceChargeRollup rollup = map.computeIfAbsent(chargeItem.getServiceId(), k -> new AdviceChargeRollup());
            rollup.absorb(chargeItem, this);
        }
        return map;
    }

    /**
     * 开立/结束等业务时刻格式化为 API 约定 ISO 本地日期时间串。
     *
     * @param requestTime 域内时刻
     * @return ISO 本地时间串；null 入参返回 null
     */
    private String formatRequestInstantForApi(LocalDateTime requestTime) {
        if (requestTime == null) {
            return null;
        }
        return GeneralSharedConstants.ISO_LOCAL_DATE_TIME_FORMATTER.format(requestTime);
    }

    /**
     * 列表行稳定键：requestId + 分隔符 + 产品表名（与产品读模型复合键规则一致）。
     */
    private static String buildListRowKey(Long requestId, String productTableName) {
        if (requestId == null) {
            return null;
        }
        return requestId + String.valueOf(GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR) + productTableName;
    }

    /**
     * 是否当前登录用户开立（仅当双方 id 均非空且相等时为 true）。
     */
    private static Boolean resolveIsMine(Long currentUserId, Long authoredById) {
        if (currentUserId == null || authoredById == null) {
            return Boolean.FALSE;
        }
        if (currentUserId.equals(authoredById)) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    /**
     * 医嘱请求状态 code → 中文展示（{@link RequestStatus}）。
     */
    private String resolveRequestStatusDisplay(String statusCode) {
        if (StringUtils.isEmpty(statusCode)) {
            return null;
        }
        RequestStatus status = EnumUtil.fromCode(RequestStatus.class, statusCode);
        if (status != null) {
            return status.getDisplay();
        }
        return statusCode;
    }

    /**
     * 从药品请求列表剔除 {@code tcm_flag=true} 的中药饮片行（开立医嘱 Tab 与中医 Tab 分域）。
     *
     * @param rows 医生站来源药品请求；允许为 {@code null} 或空
     * @return 非饮片行列表；入参为空时原样返回
     */
    private static List<MedicationRequestListQueryModel> excludeTcmMedicationRows(
        List<MedicationRequestListQueryModel> rows) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        List<MedicationRequestListQueryModel> result = new ArrayList<>();
        for (MedicationRequestListQueryModel row : rows) {
            // 饮片行由中药处方弹窗承载，避免开立 Tab 重复展示
            if (Boolean.TRUE.equals(row.getTcmFlag())) {
                continue;
            }
            result.add(row);
        }
        return result;
    }

    /**
     * IF-IP-DOR-22 可选过滤：开单科室（{@code org_id}）与发布态（{@code RequestStatus}）联合收窄。
     *
     * @param orgId                 请求开立科室
     * @param statusEnum            请求发布态 code
     * @param orderingDepartmentId  前端传入的开单科室；null 表示不按科室过滤
     * @param requestStatusEnumIn   发布态白名单；空表示不按状态过滤
     * @return 是否保留该行
     */
    private static boolean matchesAdviceQuerySourceFilter(
        Long orgId,
        String statusEnum,
        Long orderingDepartmentId,
        List<String> requestStatusEnumIn) {
        if (orderingDepartmentId != null) {
            // 科室过滤：请求无 org 或 org 与筛选科室不一致时剔除（BR-MRQ-029 只读 org_id）。
            if (orgId == null || !orderingDepartmentId.equals(orgId)) {
                return false;
            }
        }
        if (requestStatusEnumIn == null || requestStatusEnumIn.isEmpty()) {
            return true;
        }
        // 状态白名单非空但行上无发布态：无法命中白名单，剔除。
        if (statusEnum == null) {
            return false;
        }
        return requestStatusEnumIn.contains(statusEnum);
    }

    /**
     * 同一医嘱请求下多笔 {@link ChargeItem} 的列表展示归并器（状态、金额、代表行 id）。
     */
    private static final class AdviceChargeRollup {
        private String chargeStatusEnum;
        private final LinkedHashSet<Long> chargeItemIds = new LinkedHashSet<>();
        private BigDecimal lineDisplayUnitPrice;
        private BigDecimal lineDisplayTotalPrice = BigDecimal.ZERO;
        private Long primaryChargeItemId;
        private LocalDateTime primaryOccurrenceTime;

        /**
         * 吸收一条收费明细：累计金额、维护主展示行（按发生时刻与 id 决胜）。
         *
         * @param item  收费行
         * @param outer 外层装配器（用于状态归并函数）
         */
        private void absorb(ChargeItem item, InpatientDoctorStationAdviceListAssembler outer) {
            if (StringUtils.isNotEmpty(item.getStatusEnum())) {
                chargeStatusEnum = AdviceChargeDisplayStatusAssembler.mergeChargeStatusForRollup(
                    chargeStatusEnum, item.getStatusEnum());
            }
            if (item.getId() != null) {
                chargeItemIds.add(item.getId());
            }
            BigDecimal piece = item.getTotalPrice();
            if (piece == null) {
                piece = BigDecimal.ZERO;
            }
            lineDisplayTotalPrice = lineDisplayTotalPrice.add(piece);
            considerPrimary(item);
        }

        /**
         * 在多条收费行中选取「发生时刻最新、同刻 id 更大」的代表行作为主展示锚点。
         *
         * @param item 当前收费行
         */
        private void considerPrimary(ChargeItem item) {
            LocalDateTime occ = item.getOccurrenceTime();
            if (occ == null) {
                occ = LocalDateTime.MIN;
            }
            if (primaryChargeItemId == null) {
                primaryChargeItemId = item.getId();
                primaryOccurrenceTime = occ;
                lineDisplayUnitPrice = item.getUnitPrice();
                return;
            }
            if (occ.isAfter(primaryOccurrenceTime)) {
                primaryChargeItemId = item.getId();
                primaryOccurrenceTime = occ;
                lineDisplayUnitPrice = item.getUnitPrice();
                return;
            }
            if (occ.equals(primaryOccurrenceTime) && item.getId() != null && primaryChargeItemId != null) {
                if (item.getId() > primaryChargeItemId) {
                    primaryChargeItemId = item.getId();
                    lineDisplayUnitPrice = item.getUnitPrice();
                }
            }
        }
    }
}
