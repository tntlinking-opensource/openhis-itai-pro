/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.outpatient.doctorstation.advice.assembler;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.module.orchestration.advice.ServiceApplyFormOrchestrationSupport;
import com.openhis.health.module.common.assembler.advice.TcmMethodCodeDisplaySupport;
import com.openhis.health.module.common.assembler.option.FrequencyOptionLoader;
import com.openhis.health.module.common.assembler.option.TerminologyOptionLoader;
import com.openhis.health.module.common.assembler.advice.AdviceChargeDisplayStatusSupport;
import com.openhis.health.module.common.assembler.order.OrderCatalogDisplayAssembler;
import com.openhis.health.module.common.assembler.order.OrderRequestCatalogWarmAssembler;
import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.module.querysupport.advice.dto.AdvicePackageChildItemDto;
import com.openhis.health.module.querysupport.advice.AdviceExtensionJsonSupport;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.order.dto.OrderRequestCatalogWarmContext;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.enums.ClinicalStatus;
import com.openhis.health.domain.share.enums.EnumInterface;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceRowDto;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.allergyintolerance.queries.model.AllergyIntoleranceRecordQueryModel;
import com.openhis.health.domain.clinical.allergyintolerance.service.AllergyIntoleranceQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.ActivityType;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.terminology.entity.Concept;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequest;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestDetailListModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * 应用层装配器（IF-DOR-10 / Asm）：把跨聚合只读查询结果装配为医生站「医嘱统一列表」行 DTO。
 *
 * <p><b>职责边界</b>：查询结果合并、展示字段填充、收费明细归并（多 {@link ChargeItem} → 行级展示态与金额），不调用写模型、不签发。</p>
 *
 * <p><b>数据来源</b>：药品/诊疗/耗材请求经域查询服务按医生站生成来源过滤；收费项按 {@code service_table}+{@code service_id} 锚到请求或发放后再映射；
 * 定义主数据批量加载后填充 {@code adviceDisplayName} 等；术语字典批量加载后填充 {@code *Name} 展示列。</p>
 *
 * <p>目录定义与三类类目术语预热复用 {@link OrderRequestCatalogWarmAssembler}；其它术语码表走 {@link TerminologyOptionLoader}（B.6.1）。</p>
 */
@Component
@RequiredArgsConstructor
public class DoctorStationAdviceListAssembler {

    /** 药品申请读侧：按医生站生成来源过滤本就诊明细。 */
    private final MedicationRequestQueryService medicationRequestQueryService;
    /** 诊疗服务申请读侧：按医生站生成来源过滤本就诊明细。 */
    private final ServiceRequestQueryService serviceRequestQueryService;
    /** 耗材请求读侧：按医生站生成来源过滤本就诊明细。 */
    private final DeviceRequestQueryService deviceRequestQueryService;
    /** 收费明细读侧：本就诊全部 ChargeItem，供列表费用归并。 */
    private final ChargeItemQueryService chargeItemQueryService;
    /** 药品发放读侧：发放锚点映射与 FIFO 首选库位。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    /** 耗材发放读侧：发放锚点映射与 FIFO 首选库位。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;
    /** 目录定义与三类类目术语预热（B.6.1）。 */
    private final OrderRequestCatalogWarmAssembler orderRequestCatalogWarmAssembler;
    /** 产品定义批量加载：单位 code 未命中术语时回落各槽字典文本。 */
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    /** 术语码表 label 批量解析。 */
    private final TerminologyOptionLoader terminologyOptionLoader;
    /** 频次展示码表：cus_frequency 主数据。 */
    private final FrequencyOptionLoader frequencyOptionLoader;
    /** 开立/录入医师姓名批量解析。 */
    private final PractitionerQueryService practitionerQueryService;
    /** 申请单 DTO 与编排 Model 互转。 */
    private final ServiceApplyFormAssembler serviceApplyFormAssembler;
    /** 术语概念单条回退查询（预热 map 未命中时）。 */
    private final CodeSystemQueryService codeSystemQueryService;
    /** 诊断定义 id → 展示名（药品行 {@link DoctorStationAdviceRowDto#getConditionDefinitionName()}）。 */
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    /** 库位名称批量解析（FIFO 发放首选库位展示）。 */
    private final LocationQueryService locationQueryService;

    /** 诊疗套餐子项批量加载（B.6.2，禁止透出 children_json 串）。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

    /** 过敏/皮试临床结果读侧：按就诊加载，供药品行 {@code skinTestOutcome*} 回填。 */
    private final AllergyIntoleranceQueryService allergyIntoleranceQueryService;

    /**
     * 装配医生站当前就诊下的统一医嘱列表行（IF-DOR-10）。
     *
     * @param encounterId   就诊主键
     * @param patientId     患者主键（契约对齐，与就诊一致性由调用方门控）
     * @param onlyMine      为 {@code true} 时仅保留当前用户开立行
     * @param currentUserId 当前登录用户主键；仅 {@code onlyMine=true} 时参与过滤
     * @return 已合并、已排序的列表行
     */
    public List<DoctorStationAdviceRowDto> assembleRows(Long encounterId, Long patientId, Boolean onlyMine, Long currentUserId) {
        return assembleAdviceQueryRows(encounterId, patientId, onlyMine, currentUserId, null, null, true);
    }

    /**
     * IF-DOR-20：装配单就诊下全类型医嘱行，支持可选科室与请求态过滤。
     *
     * @param encounterId           就诊主键
     * @param patientId             患者主键
     * @param onlyMine              是否仅本人开立
     * @param currentUserId         当前用户
     * @param orderingDepartmentId  开单科室；{@code null} 不过滤
     * @param requestStatusEnumIn   请求态集合；空不过滤
     * @return 已合并、已排序的列表行
     */
    public List<DoctorStationAdviceRowDto> assembleAdviceQueryRows(
        Long encounterId,
        Long patientId,
        Boolean onlyMine,
        Long currentUserId,
        Long orderingDepartmentId,
        List<String> requestStatusEnumIn) {
        return assembleAdviceQueryRows(
            encounterId, patientId, onlyMine, currentUserId, orderingDepartmentId, requestStatusEnumIn, false);
    }

    /**
     * IF-DOR-10 / IF-DOR-20：装配单就诊下全类型医嘱行，支持可选科室与请求态过滤。
     *
     * @param excludeTcmMedication 为 {@code true} 时排除 {@code tcmFlag=true} 的中药饮片行（开立医嘱 Tab 与中药 Tab 分域）
     */
    public List<DoctorStationAdviceRowDto> assembleAdviceQueryRows(
        Long encounterId,
        Long patientId,
        Boolean onlyMine,
        Long currentUserId,
        Long orderingDepartmentId,
        List<String> requestStatusEnumIn,
        boolean excludeTcmMedication) {
        // 前置：三类医嘱按医生站生成来源拉平（与 AS BR-ORD-010 一致：药/材/诊疗均使用医生站来源常量过滤）
        List<MedicationRequestListQueryModel> medicationRows =
            medicationRequestQueryService.listMedicationRequestsByEncounterForDoctorStation(
                encounterId, GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        // Pre：开立 Tab 与中医 Tab 分域，排除 tcm_flag=true 饮片行（IF-DOR-10；IF-DOR-20 不过滤）
        if (excludeTcmMedication) {
            medicationRows = excludeTcmMedicationRows(medicationRows);
        }
        List<ServiceRequestListModel> serviceRows =
            serviceRequestQueryService.listServiceRequestsByGenerateSource(
                encounterId, GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        List<DeviceRequestListModel> deviceRows =
            deviceRequestQueryService.listDeviceRequestsByEncounterForDoctorStation(
                encounterId, GenerateSource.DOCTOR_PRESCRIPTION.getCode());

        // 前置：本就诊全部收费行，用于按发放锚点归并到「申请」维度（患者账单与列表态一致）
        ChargeItemQueryCommand chargeQuery = new ChargeItemQueryCommand();
        chargeQuery.setEncounterId(encounterId);
        List<ChargeItem> chargeItems = chargeItemQueryService.listChargeItemsByEncounterId(chargeQuery);

        // 前置：发放物理行 id → 申请 id，保证挂在发药/发材上的费用能回到医生站主表行
        Map<Long, Long> medicationDispenseIdToRequestId = collectMedicationDispenseServiceIds(chargeItems);
        Map<Long, Long> deviceDispenseIdToRequestId = collectDeviceDispenseServiceIds(chargeItems);

        // 前置：按请求主键归并收费状态、金额与代表收费行（多 ChargeItem 场景）
        Map<Long, AdviceChargeRollup> medicationRollup = rollupChargesForMedicationRequests(chargeItems, medicationDispenseIdToRequestId);
        Map<Long, AdviceChargeRollup> serviceRollup = rollupChargesForAnchoredTable(chargeItems, TableNameConstants.WKF_SERVICE_REQUEST);
        Map<Long, AdviceChargeRollup> deviceRollup = rollupChargesForDeviceRequests(chargeItems, deviceDispenseIdToRequestId);

        Set<Long> medicationDefIds = new HashSet<>();
        Set<Long> activityDefIds = new HashSet<>();
        Set<Long> deviceDefIds = new HashSet<>();
        Set<Long> practitionerIds = new HashSet<>();
        Set<Long> adviceConditionDefinitionIds = new HashSet<>();
        // Loop：扫描药品行，收集定义 id、开立者与关联诊断，供后续批量预热与展示回填。
        for (MedicationRequestListQueryModel row : medicationRows) {
            if (row.getMedicationId() != null) {
                medicationDefIds.add(row.getMedicationId());
            }
            if (row.getRecorderId() != null) {
                practitionerIds.add(row.getRecorderId());
            }
            if (row.getRequesterId() != null) {
                practitionerIds.add(row.getRequesterId());
            }
            if (row.getConditionDefinitionId() != null) {
                adviceConditionDefinitionIds.add(row.getConditionDefinitionId());
            }
        }
        // Loop：扫描诊疗行，收集活动定义 id、开立者与诊断定义 id。
        for (ServiceRequestListModel row : serviceRows) {
            if (row.getActivityId() != null) {
                activityDefIds.add(row.getActivityId());
            }
            if (row.getRequesterId() != null) {
                practitionerIds.add(row.getRequesterId());
            }
            if (row.getConditionDefinitionId() != null) {
                adviceConditionDefinitionIds.add(row.getConditionDefinitionId());
            }
        }
        // Loop：扫描耗材行，收集器材定义 id、开立者与诊断定义 id。
        for (DeviceRequestListModel row : deviceRows) {
            if (row.getDeviceDefId() != null) {
                deviceDefIds.add(row.getDeviceDefId());
            }
            if (row.getRequesterId() != null) {
                practitionerIds.add(row.getRequesterId());
            }
            if (row.getConditionDefinitionId() != null) {
                adviceConditionDefinitionIds.add(row.getConditionDefinitionId());
            }
        }

        OrderRequestCatalogWarmContext catalogWarm =
            orderRequestCatalogWarmAssembler.prepareByDefinitionIds(activityDefIds, medicationDefIds, deviceDefIds);
        Map<Long, MedicationDefinition> medicationDefinitionById = catalogWarm.getMedicationDefinitionById();
        Map<Long, ActivityDefinition> activityDefinitionById = catalogWarm.getActivityDefinitionById();
        Map<Long, List<ActivityDefinitionChildQueryModel>> packageChildrenByActivityId =
            activityDefinitionQueryService.listChildrenByActivityIds(activityDefIds);
        Map<Long, DeviceDefinition> deviceDefinitionById = catalogWarm.getDeviceDefinitionById();
        Map<Long, String> practitionerNameById = loadPractitionerNames(practitionerIds);
        Map<Long, String> conditionDefinitionDisplayByDefinitionId =
            loadConditionDefinitionDisplayNames(adviceConditionDefinitionIds);

        Map<String, String> unitLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        Map<String, String> methodLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE);
        Map<String, String> tcmMethodLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.TCM_METHOD_CODE);
        Map<String, String> rateLabelByCode = frequencyOptionLoader.buildRateLabelMap();
        Map<String, String> doseUnitLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSE_UNIT);
        Map<String, String> dosageInstructionLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSAGE_INSTRUCTION);
        Map<String, String> medicationCategoryLabelByCode = catalogWarm.getMedicationCategoryLabelByCode();
        Map<String, String> activityCategoryLabelByCode = catalogWarm.getActivityCategoryLabelByCode();
        Map<String, String> deviceCategoryLabelByCode = catalogWarm.getDeviceCategoryLabelByCode();
        // 滴速码表：见 DoctorStationAdviceConstants#TERMINOLOGY_SPEED_CODE_SYSTEM_NAME（无种子则为空映射）
        Map<String, String> speedLabelByCode =
            terminologyOptionLoader.buildLabelMap(DoctorStationAdviceConstants.TERMINOLOGY_SPEED_CODE_SYSTEM_NAME);
        Map<String, ProductDefinitionDisplayDto> productByKey =
            batchLoadProducts(medicationDefIds, activityDefIds, deviceDefIds);

        List<Long> medicationRequestIdsForFifo = new ArrayList<>();
        // Loop：收集药品请求主键，供 FIFO 首选发放库位批量解析。
        for (MedicationRequestListQueryModel row : medicationRows) {
            if (row.getId() != null) {
                medicationRequestIdsForFifo.add(row.getId());
            }
        }
        List<Long> deviceRequestIdsForFifo = new ArrayList<>();
        // Loop：收集耗材请求主键，供 FIFO 首选发放库位批量解析。
        for (DeviceRequestListModel row : deviceRows) {
            if (row.getId() != null) {
                deviceRequestIdsForFifo.add(row.getId());
            }
        }
        Map<Long, Long> fifoMedDispenseLocationByRequest =
            medicationDispenseQueryService.mapMedicationRequestToPrimaryDispenseLocationId(medicationRequestIdsForFifo);
        Map<Long, Long> fifoDevDispenseLocationByRequest =
            deviceDispenseQueryService.mapDeviceRequestToPrimaryDispenseLocationId(deviceRequestIdsForFifo);
        Set<Long> fifoLocationIds = new HashSet<>();
        // Loop：合并药/材 FIFO 库位 id，一次批量解析库位名称。
        for (Long locId : fifoMedDispenseLocationByRequest.values()) {
            if (locId != null) {
                fifoLocationIds.add(locId);
            }
        }
        for (Long locId : fifoDevDispenseLocationByRequest.values()) {
            if (locId != null) {
                fifoLocationIds.add(locId);
            }
        }
        Map<Long, String> fifoLocationNameById = locationQueryService.mapNamesByIds(fifoLocationIds);

        List<Long> applyFormServiceRequestIds = new ArrayList<>();
        for (ServiceRequestListModel serviceRow : serviceRows) {
            // 申请单行（AR 前缀）须批量预取 Detail，供列表回填 serviceRequestDetails。
            if (serviceRow.getId() != null
                && ServiceApplyFormOrchestrationSupport.isRequisitionApplicationPrescriptionNo(serviceRow.getPrescriptionNo())) {
                applyFormServiceRequestIds.add(serviceRow.getId());
            }
        }
        Map<Long, List<ServiceRequestDetailListModel>> applyFormDetailsByRequestId =
            serviceRequestQueryService.mapDetailListModelsByServiceRequestIds(applyFormServiceRequestIds);

        // Pre：皮试药（med_medication_request.skin_test_flag=true）经 companion 皮试检查 SR 关联 AllergyIntolerance 临床结果
        Map<Long, AllergyIntoleranceRecordQueryModel> skinTestOutcomeByMedicationRequestId =
            this.loadSkinTestOutcomeByMedicationRequestId(encounterId, medicationRows);

        List<DoctorStationAdviceRowDto> unifiedRows = new ArrayList<>();

        // 装配：药品行——患者可见的医嘱名、剂量频次等由定义+字典拼装，金额来自收费归并
        for (MedicationRequestListQueryModel medicationRow : medicationRows) {
            Long medAuthorId = resolveMedicationAuthorId(medicationRow);
            // onlyMine：非当前用户开立的药品行不进入统一列表，避免「只看本人」失效。
            if (Boolean.TRUE.equals(onlyMine) && currentUserId != null && !currentUserId.equals(medAuthorId)) {
                continue;
            }
            if (!matchesAdviceQuerySourceFilter(
                medicationRow.getOrgId(), medicationRow.getStatusEnum(), orderingDepartmentId, requestStatusEnumIn)) {
                // IF-DOR-20 可选过滤：开单科室或发布态不匹配时跳过本药品行。
                continue;
            }
            DoctorStationAdviceRowDto line = new DoctorStationAdviceRowDto();
            fillStableKeys(line, medicationRow.getId(), TableNameConstants.MED_MEDICATION_DEFINITION);
            line.setEncounterId(medicationRow.getEncounterId());
            line.setPatientId(medicationRow.getPatientId());
            line.setProductDefinitionId(medicationRow.getMedicationId());
            line.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            MedicationDefinition def = medicationDefinitionById.get(medicationRow.getMedicationId());
            if (def != null) {
                line.setAdviceDisplayName(def.getName());
                line.setSpecificationText(StringUtils.defaultIfBlank(def.getTotalVolume(), def.getComprisedText()));
            }
            line.setQuantity(medicationRow.getQuantity());
            line.setUnitCode(medicationRow.getUnitCode());
            ProductDefinitionDisplayDto medProduct = productByKey.get(
                ProductDefinitionKeyUtil.buildCompositeKey(
                    TableNameConstants.MED_MEDICATION_DEFINITION, medicationRow.getMedicationId()));
            line.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                unitLabelByCode, medicationRow.getUnitCode(), medProduct));
            line.setDose(medicationRow.getDose());
            line.setDoseUnitCode(medicationRow.getDoseUnitCode());
            line.setDoseUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveDoseUnitDisplayName(
                doseUnitLabelByCode, medicationRow.getDoseUnitCode(), medProduct));
            line.setMethodCode(medicationRow.getMethodCode());
            line.setMethodName(TcmMethodCodeDisplaySupport.resolveMethodDisplayName(
                tcmMethodLabelByCode, methodLabelByCode, medicationRow.getTcmFlag(), medicationRow.getMethodCode()));
            line.setRateCode(medicationRow.getRateCode());
            line.setRateName(OrderCatalogDisplayAssembler.resolveCategoryDisplay(rateLabelByCode, medicationRow.getRateCode()));
            line.setLotNumber(medicationRow.getLotNumber());
            line.setDispensePerDuration(medicationRow.getDispensePerDuration());
            line.setPerformLocationId(medicationRow.getPerformLocation());
            line.setRemark(medicationRow.getRemark());
            line.setRequestExtensions(AdviceExtensionJsonSupport.parseRequestExtensions(medicationRow.getContentJson()));
            fillMedicationCatalogCategory(line, def, medicationCategoryLabelByCode);
            line.setDosageInstructionCode(medicationRow.getDosageInstruction());
            line.setDosageInstructionText(resolveSingleConceptDisplay(
                TerminologyCodeSystemNameConstants.DOSAGE_INSTRUCTION, medicationRow.getDosageInstruction(), dosageInstructionLabelByCode));
            line.setSpeedCode(medicationRow.getSpeedCode());
            line.setSpeedName(OrderCatalogDisplayAssembler.resolveCategoryDisplay(speedLabelByCode, medicationRow.getSpeedCode()));
            line.setSkinTestFlag(medicationRow.getSkinTestFlag());
            line.setSkinTestFlagText(formatYesNoChinese(medicationRow.getSkinTestFlag()));
            // 皮试结果回填：锚 med_medication_request.skin_test_flag，结果来自护士登记 AllergyIntolerance（非 SR 本身）
            if (Boolean.TRUE.equals(medicationRow.getSkinTestFlag()) && medicationRow.getId() != null) {
                AllergyIntoleranceRecordQueryModel skinTestOutcome =
                    skinTestOutcomeByMedicationRequestId.get(medicationRow.getId());
                if (skinTestOutcome != null && StringUtils.isNotBlank(skinTestOutcome.getClinicalStatusEnum())) {
                    line.setSkinTestOutcomeCode(skinTestOutcome.getClinicalStatusEnum());
                    line.setSkinTestOutcomeText(
                        this.resolveEnumCodeDisplay(ClinicalStatus.class, skinTestOutcome.getClinicalStatusEnum()));
                }
            }
            line.setInjectionFlag(medicationRow.getInfusionFlag());
            line.setInjectionFlagText(formatYesNoChinese(medicationRow.getInfusionFlag()));
            line.setConditionDefinitionId(medicationRow.getConditionDefinitionId());
            line.setEncounterDiagnosisId(medicationRow.getEncounterDiagnosisId());
            if (medicationRow.getConditionDefinitionId() != null) {
                line.setConditionDefinitionName(
                    conditionDefinitionDisplayByDefinitionId.get(medicationRow.getConditionDefinitionId()));
            }
            line.setDecoctionDelegatedFlag(medicationRow.getSufferingFlag());
            line.setChineseHerbsDoseQuantity(toIntegerHerbDoses(medicationRow.getChineseHerbsDoseQuantity(), medicationRow.getTcmFlag()));
            line.setBasedOnServiceRequestId(resolveBasedOnServiceRequestId(medicationRow.getBasedOnTable(), medicationRow.getBasedOnId()));
            line.setPrescriptionNo(medicationRow.getPrescriptionNo());
            line.setGroupId(medicationRow.getGroupId());
            line.setAuthoredById(medAuthorId);
            line.setAuthoredByName(practitionerNameById.get(medAuthorId));
            line.setAuthoredAt(formatAuthoredInstantForApi(medicationRow.getReqAuthoredTime()));
            line.setEndTime(formatAuthoredInstantForApi(medicationRow.getEffectiveDoseEnd()));
            line.setSortNumber(medicationRow.getSortNumber());
            line.setStatusEnum(medicationRow.getStatusEnum());
            line.setStatusEnumText(resolveRequestStatusDisplay(medicationRow.getStatusEnum()));
            applyChargeRollup(line, medicationRollup.get(medicationRow.getId()));
            fillLineAmountByUnitAndQuantity(line);
            line.setIsAuthoredByCurrentUser(resolveIsMine(currentUserId, medAuthorId));
            line.setContentSummary(buildMedicationSummary(line));
            Long fifoLoc = fifoMedDispenseLocationByRequest.get(medicationRow.getId());
            line.setFifoDispenseLocationId(fifoLoc);
            if (fifoLoc != null) {
                line.setFifoDispenseLocationName(fifoLocationNameById.get(fifoLoc));
            } else {
                line.setFifoDispenseLocationName(null);
            }
            unifiedRows.add(line);
        }

        // 装配：诊疗行——套餐子项 JSON 取自活动定义；状态码兼容历史数字串
        for (ServiceRequestListModel serviceRow : serviceRows) {
            // onlyMine：非当前用户开立的诊疗行跳过。
            if (Boolean.TRUE.equals(onlyMine) && currentUserId != null && !currentUserId.equals(serviceRow.getRequesterId())) {
                continue;
            }
            if (!matchesAdviceQuerySourceFilter(
                serviceRow.getOrgId(), serviceRow.getStatusEnum(), orderingDepartmentId, requestStatusEnumIn)) {
                // IF-DOR-20 可选过滤：开单科室或发布态不匹配时跳过本诊疗行。
                continue;
            }
            DoctorStationAdviceRowDto line = new DoctorStationAdviceRowDto();
            fillStableKeys(line, serviceRow.getId(), TableNameConstants.WKF_ACTIVITY_DEFINITION);
            line.setEncounterId(serviceRow.getEncounterId());
            line.setPatientId(serviceRow.getPatientId());
            line.setProductDefinitionId(serviceRow.getActivityId());
            line.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
            ActivityDefinition def = activityDefinitionById.get(serviceRow.getActivityId());
            if (def != null) {
                line.setAdviceDisplayName(def.getName());
                List<AdvicePackageChildItemDto> packageChildren =
                    AdviceExtensionJsonSupport.toPackageChildItems(packageChildrenByActivityId.get(def.getId()));
                line.setPackageChildren(packageChildren);
                fillServiceCatalogAndActivityType(line, def, activityCategoryLabelByCode);
            }
            line.setQuantity(serviceRow.getQuantity());
            line.setUnitCode(serviceRow.getUnitCode());
            line.setExecutionCount(serviceRow.getExecutionCount());
            ProductDefinitionDisplayDto activityProduct = productByKey.get(
                ProductDefinitionKeyUtil.buildCompositeKey(
                    TableNameConstants.WKF_ACTIVITY_DEFINITION, serviceRow.getActivityId()));
            line.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                unitLabelByCode, serviceRow.getUnitCode(), activityProduct));
            line.setRateCode(serviceRow.getRateCode());
            line.setRateName(OrderCatalogDisplayAssembler.resolveCategoryDisplay(rateLabelByCode, serviceRow.getRateCode()));
            line.setPerformLocationId(serviceRow.getLocationId());
            line.setRemark(serviceRow.getRemark());
            line.setEntrustContent(serviceRow.getEntrustContent());
            line.setRequestExtensions(AdviceExtensionJsonSupport.parseRequestExtensions(serviceRow.getContentJson()));
            line.setBasedOnServiceRequestId(resolveBasedOnServiceRequestId(serviceRow.getBasedOnTable(), serviceRow.getBasedOnId()));
            line.setBasedOnMedicationRequestId(
                resolveBasedOnMedicationRequestId(serviceRow.getBasedOnTable(), serviceRow.getBasedOnId()));
            line.setConditionDefinitionId(serviceRow.getConditionDefinitionId());
            line.setEncounterDiagnosisId(serviceRow.getEncounterDiagnosisId());
            if (serviceRow.getConditionDefinitionId() != null) {
                line.setConditionDefinitionName(
                    conditionDefinitionDisplayByDefinitionId.get(serviceRow.getConditionDefinitionId()));
            }
            line.setAuthoredById(serviceRow.getRequesterId());
            line.setAuthoredByName(practitionerNameById.get(serviceRow.getRequesterId()));
            line.setAuthoredAt(formatAuthoredInstantForApi(serviceRow.getReqAuthoredTime()));
            line.setEndTime(formatAuthoredInstantForApi(serviceRow.getOccurrenceEndTime()));
            line.setSortNumber(serviceRow.getSortNumber());
            line.setStatusEnum(serviceRow.getStatusEnum());
            line.setStatusEnumText(resolveRequestStatusDisplay(serviceRow.getStatusEnum()));
            applyChargeRollup(line, serviceRollup.get(serviceRow.getId()));
            fillLineAmountByUnitAndQuantity(line);
            line.setIsAuthoredByCurrentUser(resolveIsMine(currentUserId, serviceRow.getRequesterId()));
            line.setContentSummary(buildServiceSummary(line));
            line.setPrescriptionNo(serviceRow.getPrescriptionNo());
            // 申请单行透出部位/样本摘要，非申请单行保持 null。
            if (ServiceApplyFormOrchestrationSupport.isRequisitionApplicationPrescriptionNo(serviceRow.getPrescriptionNo())) {
                line.setServiceRequestDetails(serviceApplyFormAssembler.toDetailSummaryDtos(
                    ServiceApplyFormOrchestrationSupport.toDetailSummaryModels(
                        applyFormDetailsByRequestId.get(serviceRow.getId()))));
            }
            unifiedRows.add(line);
        }

        // 装配：耗材行——规格优先取请求侧描述，缺省回落器材定义体积类展示
        for (DeviceRequestListModel deviceRow : deviceRows) {
            // onlyMine：非当前用户开立的耗材行跳过。
            if (Boolean.TRUE.equals(onlyMine) && currentUserId != null && !currentUserId.equals(deviceRow.getRequesterId())) {
                continue;
            }
            // 耗材无 org_id：科室过滤仅作用药/诊；耗材仅按 requestStatusEnumIn 收窄。
            if (!matchesAdviceQueryStatusFilter(deviceRow.getStatusEnum(), requestStatusEnumIn)) {
                // IF-DOR-20 可选过滤：发布态不匹配时跳过本耗材行。
                continue;
            }
            DoctorStationAdviceRowDto line = new DoctorStationAdviceRowDto();
            fillStableKeys(line, deviceRow.getId(), TableNameConstants.ADM_DEVICE_DEFINITION);
            line.setEncounterId(deviceRow.getEncounterId());
            line.setPatientId(deviceRow.getPatientId());
            line.setProductDefinitionId(deviceRow.getDeviceDefId());
            line.setProductTableName(TableNameConstants.ADM_DEVICE_DEFINITION);
            DeviceDefinition def = deviceDefinitionById.get(deviceRow.getDeviceDefId());
            if (def != null) {
                line.setAdviceDisplayName(def.getName());
                line.setSpecificationText(StringUtils.defaultIfBlank(deviceRow.getDeviceSpecifications(), def.getTotalVolume()));
                fillDeviceCatalogCategory(line, def, deviceCategoryLabelByCode);
            } else {
                line.setSpecificationText(deviceRow.getDeviceSpecifications());
            }
            line.setQuantity(deviceRow.getQuantity());
            line.setUnitCode(deviceRow.getUnitCode());
            ProductDefinitionDisplayDto deviceProduct = productByKey.get(
                ProductDefinitionKeyUtil.buildCompositeKey(
                    TableNameConstants.ADM_DEVICE_DEFINITION, deviceRow.getDeviceDefId()));
            line.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                unitLabelByCode, deviceRow.getUnitCode(), deviceProduct));
            line.setRateCode(deviceRow.getRateCode());
            line.setRateName(OrderCatalogDisplayAssembler.resolveCategoryDisplay(rateLabelByCode, deviceRow.getRateCode()));
            line.setLotNumber(deviceRow.getLotNumber());
            line.setPerformLocationId(deviceRow.getPerformLocation());
            line.setRemark(deviceRow.getRemark());
            line.setBasedOnServiceRequestId(resolveBasedOnServiceRequestId(deviceRow.getBasedOnTable(), deviceRow.getBasedOnId()));
            line.setConditionDefinitionId(deviceRow.getConditionDefinitionId());
            line.setEncounterDiagnosisId(deviceRow.getEncounterDiagnosisId());
            if (deviceRow.getConditionDefinitionId() != null) {
                line.setConditionDefinitionName(
                    conditionDefinitionDisplayByDefinitionId.get(deviceRow.getConditionDefinitionId()));
            }
            line.setAuthoredById(deviceRow.getRequesterId());
            line.setAuthoredByName(practitionerNameById.get(deviceRow.getRequesterId()));
            line.setAuthoredAt(formatAuthoredInstantForApi(deviceRow.getReqAuthoredTime()));
            line.setSortNumber(deviceRow.getSortNumber());
            line.setStatusEnum(deviceRow.getStatusEnum());
            line.setStatusEnumText(resolveRequestStatusDisplay(deviceRow.getStatusEnum()));
            applyChargeRollup(line, deviceRollup.get(deviceRow.getId()));
            fillLineAmountByUnitAndQuantity(line);
            line.setIsAuthoredByCurrentUser(resolveIsMine(currentUserId, deviceRow.getRequesterId()));
            line.setContentSummary(buildDeviceSummary(line));
            Long fifoDevLoc = fifoDevDispenseLocationByRequest.get(deviceRow.getId());
            line.setFifoDispenseLocationId(fifoDevLoc);
            if (fifoDevLoc != null) {
                line.setFifoDispenseLocationName(fifoLocationNameById.get(fifoDevLoc));
            } else {
                line.setFifoDispenseLocationName(null);
            }
            unifiedRows.add(line);
        }

        // 装配：申请单号聚邻后按 sortNumber、开立时间、requestId 排序（BR-NUR-015 / BR-ORD-018）
        unifiedRows.sort(Comparator
            .comparing((DoctorStationAdviceRowDto row) ->
                ServiceApplyFormOrchestrationSupport.resolveApplyFormGroupingSortKey(row.getPrescriptionNo()))
            .thenComparing(DoctorStationAdviceRowDto::getSortNumber, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(DoctorStationAdviceRowDto::getAuthoredAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(DoctorStationAdviceRowDto::getRequestId, Comparator.nullsLast(Comparator.naturalOrder())));
        return unifiedRows;
    }

    /**
     * 批量解析皮试药（{@code med_medication_request.skin_test_flag=true}）在护士登记后的临床结果。
     *
     * <p>链路：皮试药 id → companion 皮试检查 {@code ServiceRequest}（{@code based_on_table/id}）→
     * {@code AllergyIntolerance.request_id} → {@code clinical_status_enum}。</p>
     *
     * @param encounterId    就诊主键
     * @param medicationRows 本就诊药品请求读模型
     * @return 皮试药申请主键 → 过敏记录；无登记或未勾选皮试时为空 map
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
            // 仅 med_medication_request.skin_test_flag=true 的药品行需要查护士登记结果
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
            // 主数据未配置「皮试检查」时列表仍可读，仅不回填皮试结果列
            return outcomeByMedicationRequestId;
        }
        List<ServiceRequest> companionServiceRequests = serviceRequestQueryService.listServiceRequestsByBasedOnAnchors(
            TableNameConstants.MED_MEDICATION_REQUEST, skinTestMedicationRequestIds);
        Map<Long, Long> skinTestServiceRequestIdByMedicationRequestId = new HashMap<>();
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
            if (companion.getBasedOnId() != null && companion.getId() != null) {
                skinTestServiceRequestIdByMedicationRequestId.putIfAbsent(companion.getBasedOnId(), companion.getId());
            }
        }
        if (skinTestServiceRequestIdByMedicationRequestId.isEmpty()) {
            return outcomeByMedicationRequestId;
        }
        Map<Long, AllergyIntoleranceRecordQueryModel> allergyByServiceRequestId = new HashMap<>();
        List<AllergyIntoleranceRecordQueryModel> allergyRecords =
            allergyIntoleranceQueryService.getRecordInfo(encounterId);
        for (AllergyIntoleranceRecordQueryModel allergyRecord : allergyRecords) {
            if (allergyRecord != null && allergyRecord.getRequestId() != null) {
                allergyByServiceRequestId.put(allergyRecord.getRequestId(), allergyRecord);
            }
        }
        for (Map.Entry<Long, Long> entry : skinTestServiceRequestIdByMedicationRequestId.entrySet()) {
            Long medicationRequestId = entry.getKey();
            Long skinTestServiceRequestId = entry.getValue();
            AllergyIntoleranceRecordQueryModel allergy = allergyByServiceRequestId.get(skinTestServiceRequestId);
            if (allergy != null) {
                outcomeByMedicationRequestId.put(medicationRequestId, allergy);
            }
        }
        return outcomeByMedicationRequestId;
    }

    /**
     * 持久化枚举 code → 展示文案（皮试临床状态等）。
     *
     * @param enumClass 枚举类型
     * @param code      持久化码
     * @return 展示名；未命中时 {@code null}
     */
    private <E extends Enum<E> & EnumInterface> String resolveEnumCodeDisplay(Class<E> enumClass, String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        E enumValue = EnumUtil.fromCode(enumClass, code);
        if (enumValue == null) {
            return null;
        }
        return enumValue.getDisplay();
    }

    /**
     * 写入请求主键与稳定行键（IF-DOR-10 / A.9.1）。
     *
     * @param line              目标行
     * @param requestId         请求主键
     * @param productTableName  定义表物理名
     */
    private void fillStableKeys(DoctorStationAdviceRowDto line, Long requestId, String productTableName) {
        line.setRequestId(requestId);
        line.setListRowKey(buildListRowKey(requestId, productTableName));
    }

    /**
     * 将收费归并结果写入列表行（主展示行、全部 id、归并态与展示金额）。
     *
     * @param line   目标行
     * @param rollup 归并缓冲；无关联收费时为 {@code null}
     */
    private void applyChargeRollup(DoctorStationAdviceRowDto line, AdviceChargeRollup rollup) {
        if (rollup != null) {
            line.setChargeStatusEnum(rollup.chargeStatusEnum);
            line.setChargeStatusEnumText(
                AdviceChargeDisplayStatusSupport.resolveChargeStatusDisplayTextForAdviceList(rollup.chargeStatusEnum));
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
     * 回填列表「状态」列展示态（退费优先于请求态，不修改 {@link DoctorStationAdviceRowDto#getStatusEnum()}）。
     *
     * @param line 已写入请求态与收费汇总态的列表行
     */
    private void applyListDisplayStatus(DoctorStationAdviceRowDto line) {
        AdviceChargeDisplayStatusSupport.ListDisplayStatus display =
            AdviceChargeDisplayStatusSupport.resolveAdviceListDisplayStatus(
                line.getStatusEnum(), line.getStatusEnumText(), line.getChargeStatusEnum());
        line.setDisplayStatusEnum(display.displayStatusEnum());
        line.setDisplayStatusEnumText(display.displayStatusEnumText());
    }

    /**
     * 与收费库 {@code adm_charge_item} 存值一致（decimal 元），仅做展示标度，不做分/元换算。
     */
    private static BigDecimal scaleChargeDisplay(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(DoctorStationAdviceConstants.ADVICE_LIST_CHARGE_DISPLAY_DECIMAL_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 回填药品目录类目 code 与术语展示名（{@code med_medication_definition.category_code}）。
     *
     * @param line                         目标列表行
     * @param def                          药品定义；无类目时跳过
     * @param medicationCategoryLabelByCode  类目 code → 展示名预热 map
     */
    private void fillMedicationCatalogCategory(
        DoctorStationAdviceRowDto line,
        MedicationDefinition def,
        Map<String, String> medicationCategoryLabelByCode
    ) {
        if (def == null || StringUtils.isBlank(def.getCategoryCode())) {
            return;
        }
        line.setCatalogCategoryCode(def.getCategoryCode().trim());
        line.setCatalogCategoryName(OrderCatalogDisplayAssembler.resolveCategoryDisplay(medicationCategoryLabelByCode, def.getCategoryCode()));
    }

    /**
     * 回填诊疗目录类目与活动类型展示列（检验/检查/护理等）。
     *
     * @param line                       目标列表行
     * @param def                        活动定义
     * @param activityCategoryLabelByCode 类目 code → 展示名预热 map
     */
    private void fillServiceCatalogAndActivityType(
        DoctorStationAdviceRowDto line,
        ActivityDefinition def,
        Map<String, String> activityCategoryLabelByCode
    ) {
        if (StringUtils.isNotBlank(def.getCategoryCode())) {
            line.setCatalogCategoryCode(def.getCategoryCode().trim());
            line.setCatalogCategoryName(OrderCatalogDisplayAssembler.resolveCategoryDisplay(activityCategoryLabelByCode, def.getCategoryCode()));
        }
        if (StringUtils.isNotBlank(def.getTypeEnum())) {
            line.setActivityTypeCode(def.getTypeEnum().trim());
            ActivityType activityType = EnumUtil.fromCode(ActivityType.class, def.getTypeEnum().trim());
            if (activityType != null) {
                line.setActivityTypeName(activityType.getDisplay());
            }
        }
    }

    /**
     * 回填耗材目录类目 code 与术语展示名。
     *
     * @param line                    目标列表行
     * @param def                     器材定义；无类目时跳过
     * @param deviceCategoryLabelByCode 类目 code → 展示名预热 map
     */
    private void fillDeviceCatalogCategory(
        DoctorStationAdviceRowDto line,
        DeviceDefinition def,
        Map<String, String> deviceCategoryLabelByCode
    ) {
        if (def == null || StringUtils.isBlank(def.getCategoryCode())) {
            return;
        }
        line.setCatalogCategoryCode(def.getCategoryCode().trim());
        line.setCatalogCategoryName(OrderCatalogDisplayAssembler.resolveCategoryDisplay(deviceCategoryLabelByCode, def.getCategoryCode()));
    }

    /**
     * 以主展示单价与请求数量推算金额，便于与 lineDisplayTotalPrice 对拍。
     *
     * @param line 已写入单价与数量的列表行（原地 set lineAmountByUnitAndQuantity）
     */
    private void fillLineAmountByUnitAndQuantity(DoctorStationAdviceRowDto line) {
        if (line.getQuantity() == null || line.getLineDisplayUnitPrice() == null) {
            return;
        }
        BigDecimal product = line.getQuantity().multiply(line.getLineDisplayUnitPrice());
        line.setLineAmountByUnitAndQuantity(scaleChargeDisplay(product));
    }

    /**
     * 批量加载医务人员姓名（开立者/申请人展示）。
     *
     * @param ids 医务人员主键集合
     * @return id → 姓名
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
     * 批量解析医嘱行上诊断定义主键为展示名，供统一列表 {@code conditionDefinitionName} 列回显（药/诊/材请求共用）。
     *
     * @param ids 诊断定义主键集合（已去重）
     * @return id → 名称；无命中时该 id 不在 map 中
     */
    private Map<Long, String> loadConditionDefinitionDisplayNames(Set<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        List<Long> idList = new ArrayList<>(ids);
        List<ConditionDefinitionBatchItemQueryModel> items = conditionDefinitionQueryService.getConditionDefinitionListByIds(idList);
        for (ConditionDefinitionBatchItemQueryModel item : items) {
            if (item.getId() == null) {
                continue;
            }
            map.put(item.getId(), StringUtils.defaultString(item.getName()));
        }
        return map;
    }

    /**
     * 开立人：优先 {@code requesterId}（申请医师），未落库时回落 {@code recorderId}。
     *
     * @param row 药品请求列表读模型行
     * @return 开立者/申请人主键；均缺失时 null
     */
    private static Long resolveMedicationAuthorId(MedicationRequestListQueryModel row) {
        if (row.getRequesterId() != null) {
            return row.getRequesterId();
        }
        return row.getRecorderId();
    }

    /**
     * 解析单码展示：先查装配阶段预热的 map，未命中再回源单条概念（用于体量较小的补充码表）。
     *
     * @param codeSystemName 编码体系名称
     * @param code             业务码
     * @param cache            预热映射
     * @return 展示名；无概念时 {@code null}
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
     * 中药付数：仅当明确中医场景标记为真时回传整数付数，避免非中药行误展示付数。
     *
     * @param raw     持久化付数
     * @param tcmFlag 是否中医相关
     * @return 付数；不应展示时为 {@code null}
     */
    private Integer toIntegerHerbDoses(BigDecimal raw, Boolean tcmFlag) {
        if (raw == null) {
            return null;
        }
        if (!Boolean.TRUE.equals(tcmFlag)) {
            return null;
        }
        return raw.intValue();
    }

    /**
     * 当前置锚为服务申请表时，显式暴露为 {@code basedOnServiceRequestId} 供前端关联皮试等链路。
     *
     * @param basedOnTable 上游锚表物理名
     * @param basedOnId    上游锚主键
     * @return 服务申请 id；非服务申请锚时 {@code null}
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
     * 当前置锚为药品申请表时，显式暴露为 {@code basedOnMedicationRequestId}（皮试检查 companion SR 等）。
     *
     * @param basedOnTable 上游锚表物理名
     * @param basedOnId    上游锚主键
     * @return 药品申请 id；非药品锚时 {@code null}
     */
    private Long resolveBasedOnMedicationRequestId(String basedOnTable, Long basedOnId) {
        if (basedOnId == null) {
            return null;
        }
        if (TableNameConstants.MED_MEDICATION_REQUEST.equalsIgnoreCase(trimToEmpty(basedOnTable))) {
            return basedOnId;
        }
        return null;
    }

    /**
     * 与「仅看本人」过滤一致：比较当前登录用户与开立者/申请人主键。
     *
     * @param currentUserId 当前用户主键
     * @param authorId      开立者或申请人主键
     * @return 是否同一人开立
     */
    private Boolean resolveIsMine(Long currentUserId, Long authorId) {
        if (currentUserId == null || authorId == null) {
            return Boolean.FALSE;
        }
        if (currentUserId.equals(authorId)) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    /**
     * 布尔标志转中文是/否，供皮试、注射等窄列展示。
     *
     * @param flag 标志值
     * @return 「是」「否」或 {@code null}
     */
    private String formatYesNoChinese(Boolean flag) {
        if (flag == null) {
            return null;
        }
        if (Boolean.TRUE.equals(flag)) {
            return "是";
        }
        return "否";
    }

    /**
     * 将药品/耗材侧 {@link RequestStatus} 持久化码解析为展示文案；未命中枚举时回落为原码。
     *
     * @param statusEnum 持久化状态码
     * @return 展示文案
     */
    private String resolveRequestStatusDisplay(String statusEnum) {
        RequestStatus parsed = EnumUtil.fromCode(RequestStatus.class, statusEnum);
        if (parsed != null) {
            return parsed.getDisplay();
        }
        return StringUtils.defaultIfBlank(statusEnum, "");
    }

    /**
     * 收费明细状态码 → 展示名（优先枚举，其次原样回落）。
     *
     * @param statusEnum 收费状态持久化码
     * @return 展示文案
     */
    /**
     * 窄列摘要：医嘱名 + 剂量单位，避免列表仅依赖长 JSON。
     *
     * @param line 已填充的药品行
     * @return 摘要字符串
     */
    private String buildMedicationSummary(DoctorStationAdviceRowDto line) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(line.getAdviceDisplayName())) {
            sb.append(line.getAdviceDisplayName());
        }
        if (line.getDose() != null) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(line.getDose());
            if (StringUtils.isNotBlank(line.getDoseUnitName())) {
                sb.append(line.getDoseUnitName());
            } else if (StringUtils.isNotBlank(line.getDoseUnitCode())) {
                sb.append(line.getDoseUnitCode());
            }
        }
        if (sb.length() == 0 && StringUtils.isNotBlank(line.getPrescriptionNo())) {
            sb.append(DoctorStationAdviceConstants.ADVICE_LIST_PRESCRIPTION_LABEL_PREFIX).append(line.getPrescriptionNo());
        }
        if (sb.length() == 0) {
            sb.append(DoctorStationAdviceConstants.ADVICE_LIST_MEDICATION_NARROW_FALLBACK_OUTPATIENT);
        }
        return sb.toString();
    }

    /**
     * 诊疗行窄列摘要。
     *
     * @param line 诊疗行
     * @return 摘要字符串
     */
    private String buildServiceSummary(DoctorStationAdviceRowDto line) {
        if (StringUtils.isNotBlank(line.getAdviceDisplayName())) {
            return line.getAdviceDisplayName();
        }
        return DoctorStationAdviceConstants.ADVICE_LIST_SERVICE_NARROW_FALLBACK_OUTPATIENT;
    }

    /**
     * 耗材行窄列摘要。
     *
     * @param line 耗材行
     * @return 摘要字符串
     */
    private String buildDeviceSummary(DoctorStationAdviceRowDto line) {
        if (StringUtils.isNotBlank(line.getAdviceDisplayName())) {
            return line.getAdviceDisplayName();
        }
        return DoctorStationAdviceConstants.ADVICE_LIST_DEVICE_NARROW_FALLBACK_OUTPATIENT;
    }

    /**
     * 收集需参与「发放 id→药品申请 id」映射的发药主键集合。
     *
     * <p>兼容两种持久化：① 旧式 {@code service_table=发放表} 且 {@code service_id=发放主键}；
     * ② 新式 {@code service_table=医嘱请求表} 且另行落地 {@code dispense_table+dispense_id}（DM-ADM-011 三锚点）。</p>
     *
     * @param chargeItems 就诊收费项
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
            // 新式：发放键落在 dispense_*，与 service_*（医嘱请求）并存，供映射缓存一并批量解析。
            if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(chargeItem.getDispenseTable())
                && chargeItem.getDispenseId() != null) {
                dispenseIds.add(chargeItem.getDispenseId());
            }
        }
        return medicationDispenseQueryService.mapMedicationDispenseIdsToMedReqIds(dispenseIds);
    }

    /**
     * 收集需参与「耗材发放 id→耗材申请 id」映射的发放主键集合（与药品对称，兼容 service 锚发放表或 dispense_* 列）。
     *
     * @param chargeItems 就诊收费项
     * @return 发放 id → 耗材申请 id
     */
    private Map<Long, Long> collectDeviceDispenseServiceIds(List<ChargeItem> chargeItems) {
        Set<Long> dispenseIds = new HashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
                continue;
            }
            if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(chargeItem.getServiceTable())) {
                dispenseIds.add(chargeItem.getServiceId());
            }
            if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(chargeItem.getDispenseTable())
                && chargeItem.getDispenseId() != null) {
                dispenseIds.add(chargeItem.getDispenseId());
            }
        }
        return deviceDispenseQueryService.mapDeviceDispenseIdsToDeviceReqIds(dispenseIds);
    }

    /**
     * 将收费行归并到药品申请：含直接锚申请与经发药映射的申请。
     *
     * @param chargeItems               就诊收费项
     * @param dispenseIdToMedicationReq 发药主键→申请主键
     * @return 申请主键→归并结果
     */
    private Map<Long, AdviceChargeRollup> rollupChargesForMedicationRequests(
        List<ChargeItem> chargeItems,
        Map<Long, Long> dispenseIdToMedicationReq
    ) {
        Map<Long, AdviceChargeRollup> map = new HashMap<>();
        for (ChargeItem chargeItem : chargeItems) {
            // 逻辑删费用不参与患者列表态与金额汇总
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
                // 签发 FIFO 分段费用：service_* 已为医嘱请求时优先走上分支；本分支兜底仅锚 dispense_* 的历史或异常行。
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
     * 将收费行归并到耗材申请（对称于药品）。
     *
     * @param chargeItems          就诊收费项
     * @param dispenseIdToDeviceReq 发放主键→申请主键
     * @return 申请主键→归并结果
     */
    private Map<Long, AdviceChargeRollup> rollupChargesForDeviceRequests(
        List<ChargeItem> chargeItems,
        Map<Long, Long> dispenseIdToDeviceReq
    ) {
        Map<Long, AdviceChargeRollup> map = new HashMap<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
                continue;
            }
            // 耗材：费用锚申请或锚发放，经映射回到同一耗材申请行
            Long deviceRequestId = null;
            if (TableNameConstants.WKF_DEVICE_REQUEST.equals(chargeItem.getServiceTable())) {
                deviceRequestId = chargeItem.getServiceId();
            } else if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(chargeItem.getServiceTable())) {
                deviceRequestId = dispenseIdToDeviceReq.get(chargeItem.getServiceId());
            } else if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(chargeItem.getDispenseTable())
                && chargeItem.getDispenseId() != null) {
                deviceRequestId = dispenseIdToDeviceReq.get(chargeItem.getDispenseId());
            }
            if (deviceRequestId == null) {
                continue;
            }
            AdviceChargeRollup rollup = map.computeIfAbsent(deviceRequestId, k -> new AdviceChargeRollup());
            rollup.absorb(chargeItem, this);
        }
        return map;
    }

    /**
     * 将收费行归并到指定锚表（如诊疗直接锚 {@code wkf_service_request}）。
     *
     * @param chargeItems  就诊收费项
     * @param serviceTable 锚表物理名，须与 {@link ChargeItem#getServiceTable()} 一致
     * @return 业务主键→归并结果
     */
    private Map<Long, AdviceChargeRollup> rollupChargesForAnchoredTable(List<ChargeItem> chargeItems, String serviceTable) {
        Map<Long, AdviceChargeRollup> map = new HashMap<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
                continue;
            }
            // 仅处理锚表与目标表一致的行（诊疗为 wkf_service_request）
            if (!serviceTable.equals(chargeItem.getServiceTable())) {
                continue;
            }
            AdviceChargeRollup rollup = map.computeIfAbsent(chargeItem.getServiceId(), k -> new AdviceChargeRollup());
            rollup.absorb(chargeItem, this);
        }
        return map;
    }

    /**
     * 开立/业务时刻格式化为 API 约定 ISO local 字符串。
     *
     * @param authoredTime 时刻
     * @return 格式化串；{@code null} 入参返回 {@code null}
     */
    private String formatAuthoredInstantForApi(LocalDateTime authoredTime) {
        if (authoredTime == null) {
            return null;
        }
        return GeneralSharedConstants.ISO_LOCAL_DATE_TIME_FORMATTER.format(authoredTime);
    }

    /**
     * 批量加载药品/诊疗/耗材产品读模型（单位展示名回落字典文本）。
     *
     * @param medDefIds      药品定义主键集合
     * @param activityDefIds 活动定义主键集合
     * @param deviceDefIds   器材定义主键集合
     * @return 复合键 → 产品展示 DTO
     */
    private Map<String, ProductDefinitionDisplayDto> batchLoadProducts(
        Set<Long> medDefIds,
        Set<Long> activityDefIds,
        Set<Long> deviceDefIds
    ) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        // Loop：组装药品定义批量 lookup 项。
        for (Long id : medDefIds) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(id);
            lookups.add(lookup);
        }
        // Loop：组装诊疗活动定义批量 lookup 项。
        for (Long id : activityDefIds) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
            lookup.setProductDefinitionId(id);
            lookups.add(lookup);
        }
        // Loop：组装耗材定义批量 lookup 项。
        for (Long id : deviceDefIds) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.ADM_DEVICE_DEFINITION);
            lookup.setProductDefinitionId(id);
            lookups.add(lookup);
        }
        if (lookups.isEmpty()) {
            return new HashMap<>();
        }
        return productDefinitionBatchLoader.loadAsMap(lookups);
    }

    /**
     * 构造医生站医嘱列表行稳定键（A.9.1：SOH 分隔）。
     *
     * @param requestId        请求主键
     * @param productTableName 定义表物理名
     * @return 稳定键；{@code requestId} 为 null 时返回 null
     */
    private static String buildListRowKey(Long requestId, String productTableName) {
        if (requestId == null) {
            return null;
        }
        return requestId + String.valueOf(GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR) + productTableName;
    }

    /**
     * IF-DOR-20 历史 Tab：跨历史就诊装配全类型医嘱行（排除当前就诊）。
     *
     * @param patientId             患者主键
     * @param excludeEncounterId    当前就诊排除锚点
     * @param orderingDepartmentId  开单科室；{@code null} 不过滤
     * @param requestStatusEnumIn   请求态集合；空不过滤
     * @return 按开立时间倒序合并后的全量行
     */
    public List<DoctorStationAdviceRowDto> assemblePatientHistoryAdviceRows(
        Long patientId,
        Long excludeEncounterId,
        Long orderingDepartmentId,
        List<String> requestStatusEnumIn) {
        // Pre：药/诊/材三类请求并集收集历史就诊 id，排除当前工作站就诊。
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        encounterIds.addAll(
            medicationRequestQueryService.listDistinctEncounterIdsByPatientForDoctorStation(patientId, excludeEncounterId));
        encounterIds.addAll(
            serviceRequestQueryService.listDistinctEncounterIdsByPatientForDoctorStation(patientId, excludeEncounterId));
        encounterIds.addAll(
            deviceRequestQueryService.listDistinctEncounterIdsByPatientForDoctorStation(patientId, excludeEncounterId));
        List<DoctorStationAdviceRowDto> merged = new ArrayList<>();
        // Loop：逐历史就诊复用单就诊装配，继承科室/状态过滤口径。
        for (Long historyEncounterId : encounterIds) {
            merged.addAll(assembleAdviceQueryRows(
                historyEncounterId, patientId, false, null, orderingDepartmentId, requestStatusEnumIn));
        }
        // Asm：跨就诊结果按开立时间倒序，同时间再按就诊倒序。
        merged.sort(Comparator
            .comparing(DoctorStationAdviceRowDto::getAuthoredAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(DoctorStationAdviceRowDto::getEncounterId, Comparator.nullsLast(Comparator.reverseOrder())));
        return merged;
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
            // 饮片行由 AS-OP-DOR-010 IF-DOR-34 承载，避免开立 Tab 重复展示
            if (Boolean.TRUE.equals(row.getTcmFlag())) {
                continue;
            }
            result.add(row);
        }
        return result;
    }

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
        return matchesAdviceQueryStatusFilter(statusEnum, requestStatusEnumIn);
    }

    /**
     * IF-DOR-20 发布态白名单过滤；空集合表示不过滤。
     */
    private static boolean matchesAdviceQueryStatusFilter(String statusEnum, List<String> requestStatusEnumIn) {
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
     * 同一医嘱请求下多条收费行的归并缓冲：状态 rollup、金额汇总、代表行选择（BR-ORD-016 列表展示侧）。
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
        private void absorb(ChargeItem item, DoctorStationAdviceListAssembler outer) {
            if (StringUtils.isNotBlank(item.getStatusEnum())) {
                chargeStatusEnum = AdviceChargeDisplayStatusSupport.mergeChargeStatusForRollup(
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