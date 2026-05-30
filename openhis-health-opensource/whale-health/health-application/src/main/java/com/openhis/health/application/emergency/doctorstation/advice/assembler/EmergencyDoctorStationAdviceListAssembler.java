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

package com.openhis.health.application.emergency.doctorstation.advice.assembler;

import com.openhis.health.application.common.assembler.advice.AdviceChargeDisplayStatusSupport;
import com.openhis.health.application.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.querysupport.advice.dto.AdvicePackageChildItemDto;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.querysupport.advice.AdviceExtensionJsonSupport;
import com.openhis.health.application.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceRowDto;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.repository.DeviceDefinitionRepository;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.repository.PractitionerRepository;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.repository.MedicationDefinitionRepository;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.ActivityType;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.terminology.entity.Concept;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.repository.ActivityDefinitionRepository;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
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
 * <p>装配私有方法保留在本类直至满足《代码生成通用规范》B.6.1「跨 ≥2 个 AppService/MapperImpl 同构重复」再上浮
 * {@code application.common} 或复用 {@code domain.share.utils}，避免单场景碎片工具类。</p>
 */
@Component
@RequiredArgsConstructor
public class EmergencyDoctorStationAdviceListAssembler {

    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final DeviceRequestQueryService deviceRequestQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final DeviceDispenseQueryService deviceDispenseQueryService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final MedicationDefinitionRepository medicationDefinitionRepository;
    private final ActivityDefinitionRepository activityDefinitionRepository;
    /** 诊疗套餐子项批量加载（B.6.2，禁止透出 children_json 串）。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final DeviceDefinitionRepository deviceDefinitionRepository;
    private final PractitionerRepository practitionerRepository;
    private final CodeSystemQueryService codeSystemQueryService;
    /** 诊断定义 id → 展示名（药品行 {@link DoctorStationAdviceRowDto#getConditionDefinitionName()}）。 */
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    /** 库位名称批量解析（FIFO 发放首选库位展示）。 */
    private final LocationQueryService locationQueryService;

    /**
     * 装配医生站当前就诊下的统一医嘱列表行。
     *
     * @param encounterId   就诊主键
     * @param patientId     患者主键（契约对齐，与就诊一致性由调用方门控）
     * @param onlyMine      为 {@code true} 时仅保留当前用户开立行
     * @param currentUserId 当前登录用户主键；仅 {@code onlyMine=true} 时参与过滤
     * @return 已合并、已排序的列表行
     */
    public List<DoctorStationAdviceRowDto> assembleRows(Long encounterId, Long patientId, Boolean onlyMine, Long currentUserId) {
        // 前置：三类医嘱按医生站生成来源拉平（与 AS BR-ORD-010 一致：药/材/诊疗均使用医生站来源常量过滤）
        List<MedicationRequestListQueryModel> medicationRows =
            medicationRequestQueryService.listMedicationRequestsByEncounterForDoctorStation(
                encounterId, GenerateSource.DOCTOR_PRESCRIPTION.getCode());
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

        Map<Long, MedicationDefinition> medicationDefinitionById = loadMedicationDefinitions(medicationDefIds);
        Map<Long, ActivityDefinition> activityDefinitionById = loadActivityDefinitions(activityDefIds);
        Map<Long, List<ActivityDefinitionChildQueryModel>> packageChildrenByActivityId =
            activityDefinitionQueryService.listChildrenByActivityIds(activityDefIds);
        Map<Long, DeviceDefinition> deviceDefinitionById = loadDeviceDefinitions(deviceDefIds);
        Map<Long, String> practitionerNameById = loadPractitionerNames(practitionerIds);
        Map<Long, String> conditionDefinitionDisplayByDefinitionId =
            loadConditionDefinitionDisplayNames(adviceConditionDefinitionIds);

        Map<String, String> unitLabelByCode = buildConceptLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        Map<String, String> methodLabelByCode = buildConceptLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE);
        Map<String, String> rateLabelByCode = buildConceptLabelMap(TerminologyCodeSystemNameConstants.RATE_CODE);
        Map<String, String> doseUnitLabelByCode = buildConceptLabelMap(TerminologyCodeSystemNameConstants.DOSE_UNIT);
        Map<String, String> dosageInstructionLabelByCode = buildConceptLabelMap(TerminologyCodeSystemNameConstants.DOSAGE_INSTRUCTION);
        Map<String, String> medicationCategoryLabelByCode = buildConceptLabelMap(TerminologyCodeSystemNameConstants.MEDICATION_CATEGORY_CODE);
        Map<String, String> activityCategoryLabelByCode = buildConceptLabelMap(TerminologyCodeSystemNameConstants.ACTIVITY_CATEGORY_CODE);
        Map<String, String> deviceCategoryLabelByCode = buildConceptLabelMap(TerminologyCodeSystemNameConstants.DEVICE_CATEGORY_CODE);
        // 滴速码表：见 DoctorStationAdviceConstants#TERMINOLOGY_SPEED_CODE_SYSTEM_NAME（无种子则为空映射）
        Map<String, String> speedLabelByCode = buildConceptLabelMap(DoctorStationAdviceConstants.TERMINOLOGY_SPEED_CODE_SYSTEM_NAME);
        Map<String, ProductDefinitionDisplayDto> productByKey =
            batchLoadProducts(medicationDefIds, activityDefIds, deviceDefIds);

        List<Long> medicationRequestIdsForFifo = new ArrayList<>();
        for (MedicationRequestListQueryModel row : medicationRows) {
            if (row.getId() != null) {
                medicationRequestIdsForFifo.add(row.getId());
            }
        }
        List<Long> deviceRequestIdsForFifo = new ArrayList<>();
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

        List<DoctorStationAdviceRowDto> unifiedRows = new ArrayList<>();

        // 装配：药品行——患者可见的医嘱名、剂量频次等由定义+字典拼装，金额来自收费归并
        for (MedicationRequestListQueryModel medicationRow : medicationRows) {
            Long medAuthorId = resolveMedicationAuthorId(medicationRow);
            if (Boolean.TRUE.equals(onlyMine) && currentUserId != null && !currentUserId.equals(medAuthorId)) {
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
            line.setMethodName(pickLabel(methodLabelByCode, medicationRow.getMethodCode()));
            line.setRateCode(medicationRow.getRateCode());
            line.setRateName(pickLabel(rateLabelByCode, medicationRow.getRateCode()));
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
            line.setSpeedName(pickLabel(speedLabelByCode, medicationRow.getSpeedCode()));
            line.setSkinTestFlag(medicationRow.getSkinTestFlag());
            line.setSkinTestFlagText(formatYesNoChinese(medicationRow.getSkinTestFlag()));
            line.setInjectionFlag(medicationRow.getInfusionFlag());
            line.setInjectionFlagText(formatYesNoChinese(medicationRow.getInfusionFlag()));
            line.setConditionDefinitionId(medicationRow.getConditionDefinitionId());
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
            if (Boolean.TRUE.equals(onlyMine) && currentUserId != null && !currentUserId.equals(serviceRow.getRequesterId())) {
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
            ProductDefinitionDisplayDto activityProduct = productByKey.get(
                ProductDefinitionKeyUtil.buildCompositeKey(
                    TableNameConstants.WKF_ACTIVITY_DEFINITION, serviceRow.getActivityId()));
            line.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                unitLabelByCode, serviceRow.getUnitCode(), activityProduct));
            line.setRateCode(serviceRow.getRateCode());
            line.setRateName(pickLabel(rateLabelByCode, serviceRow.getRateCode()));
            line.setPerformLocationId(serviceRow.getLocationId());
            line.setRemark(serviceRow.getRemark());
            line.setEntrustContent(serviceRow.getEntrustContent());
            line.setRequestExtensions(AdviceExtensionJsonSupport.parseRequestExtensions(serviceRow.getContentJson()));
            line.setBasedOnServiceRequestId(resolveBasedOnServiceRequestId(serviceRow.getBasedOnTable(), serviceRow.getBasedOnId()));
            line.setConditionDefinitionId(serviceRow.getConditionDefinitionId());
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
            unifiedRows.add(line);
        }

        // 装配：耗材行——规格优先取请求侧描述，缺省回落器材定义体积类展示
        for (DeviceRequestListModel deviceRow : deviceRows) {
            if (Boolean.TRUE.equals(onlyMine) && currentUserId != null && !currentUserId.equals(deviceRow.getRequesterId())) {
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
            line.setRateName(pickLabel(rateLabelByCode, deviceRow.getRateCode()));
            line.setLotNumber(deviceRow.getLotNumber());
            line.setPerformLocationId(deviceRow.getPerformLocation());
            line.setRemark(deviceRow.getRemark());
            line.setBasedOnServiceRequestId(resolveBasedOnServiceRequestId(deviceRow.getBasedOnTable(), deviceRow.getBasedOnId()));
            line.setConditionDefinitionId(deviceRow.getConditionDefinitionId());
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

        // 装配：合并后按医生站主表排序号升序，再按开立时间倒序，与 AS BR-ORD-018 一致
        unifiedRows.sort(Comparator
            .comparing(DoctorStationAdviceRowDto::getSortNumber, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(DoctorStationAdviceRowDto::getAuthoredAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return unifiedRows;
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

    /** 药品行：目录分类（{@code med_medication_definition.category_code}）。 */
    private void fillMedicationCatalogCategory(
        DoctorStationAdviceRowDto line,
        MedicationDefinition def,
        Map<String, String> medicationCategoryLabelByCode
    ) {
        if (def == null || StringUtils.isBlank(def.getCategoryCode())) {
            return;
        }
        line.setCatalogCategoryCode(def.getCategoryCode().trim());
        line.setCatalogCategoryName(pickLabel(medicationCategoryLabelByCode, def.getCategoryCode()));
    }

    /** 诊疗行：目录类别 + 活动类型（检验/检查/护理等）。 */
    private void fillServiceCatalogAndActivityType(
        DoctorStationAdviceRowDto line,
        ActivityDefinition def,
        Map<String, String> activityCategoryLabelByCode
    ) {
        if (StringUtils.isNotBlank(def.getCategoryCode())) {
            line.setCatalogCategoryCode(def.getCategoryCode().trim());
            line.setCatalogCategoryName(pickLabel(activityCategoryLabelByCode, def.getCategoryCode()));
        }
        if (StringUtils.isNotBlank(def.getTypeEnum())) {
            line.setActivityTypeCode(def.getTypeEnum().trim());
            ActivityType activityType = EnumUtil.fromCode(ActivityType.class, def.getTypeEnum().trim());
            if (activityType != null) {
                line.setActivityTypeName(activityType.getDisplay());
            }
        }
    }

    /** 耗材行：目录分类。 */
    private void fillDeviceCatalogCategory(
        DoctorStationAdviceRowDto line,
        DeviceDefinition def,
        Map<String, String> deviceCategoryLabelByCode
    ) {
        if (def == null || StringUtils.isBlank(def.getCategoryCode())) {
            return;
        }
        line.setCatalogCategoryCode(def.getCategoryCode().trim());
        line.setCatalogCategoryName(pickLabel(deviceCategoryLabelByCode, def.getCategoryCode()));
    }

    /** 以主展示单价与请求数量推算金额，便于与 lineDisplayTotalPrice 对拍。 */
    private void fillLineAmountByUnitAndQuantity(DoctorStationAdviceRowDto line) {
        if (line.getQuantity() == null || line.getLineDisplayUnitPrice() == null) {
            return;
        }
        BigDecimal product = line.getQuantity().multiply(line.getLineDisplayUnitPrice());
        line.setLineAmountByUnitAndQuantity(scaleChargeDisplay(product));
    }

    /**
     * 批量加载药品定义（一次 I/O，禁止循环单条仓储）。
     *
     * @param ids 定义主键集合
     * @return id → 定义实体
     */
    private Map<Long, MedicationDefinition> loadMedicationDefinitions(Set<Long> ids) {
        Map<Long, MedicationDefinition> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        List<MedicationDefinition> list = medicationDefinitionRepository.findAllById(ids);
        for (MedicationDefinition def : list) {
            map.put(def.getId(), def);
        }
        return map;
    }

    /**
     * 批量加载诊疗活动定义。
     *
     * @param ids 定义主键集合
     * @return id → 定义实体
     */
    private Map<Long, ActivityDefinition> loadActivityDefinitions(Set<Long> ids) {
        Map<Long, ActivityDefinition> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        List<Long> idList = new ArrayList<>(ids);
        List<ActivityDefinition> list = activityDefinitionRepository.findByIdIn(idList);
        for (ActivityDefinition def : list) {
            map.put(def.getId(), def);
        }
        return map;
    }

    /**
     * 批量加载耗材/器材定义。
     *
     * @param ids 定义主键集合
     * @return id → 定义实体
     */
    private Map<Long, DeviceDefinition> loadDeviceDefinitions(Set<Long> ids) {
        Map<Long, DeviceDefinition> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        List<DeviceDefinition> list = deviceDefinitionRepository.findAllByIdIn(ids);
        for (DeviceDefinition def : list) {
            map.put(def.getId(), def);
        }
        return map;
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
        List<Practitioner> list = practitionerRepository.findAllById(ids);
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
     */
    private static Long resolveMedicationAuthorId(MedicationRequestListQueryModel row) {
        if (row.getRequesterId() != null) {
            return row.getRequesterId();
        }
        return row.getRecorderId();
    }

    /**
     * 将某编码系统下全部概念转为「码 → 展示名」映射，供列表行批量查展示（走术语缓存）。
     *
     * @param codeSystemName 编码体系名称
     * @return 码到展示名；体系名为空时返回空映射
     */
    private Map<String, String> buildConceptLabelMap(String codeSystemName) {
        Map<String, String> map = new HashMap<>();
        if (StringUtils.isBlank(codeSystemName)) {
            return map;
        }
        List<OptionDto> options = codeSystemQueryService.getOptionListByCodeSystemName(codeSystemName.trim());
        for (OptionDto option : options) {
            if (option.getValue() != null) {
                map.put(option.getValue(), option.getLabel());
            }
        }
        return map;
    }

    /**
     * 从预构建字典中取展示名。
     *
     * @param dict 码表映射
     * @param code 业务码
     * @return 展示名；未命中时 {@code null}
     */
    /**
     * 批量加载药品/诊疗/耗材产品读模型（单位展示名回落字典文本）。
     */
    private Map<String, ProductDefinitionDisplayDto> batchLoadProducts(
        Set<Long> medDefIds,
        Set<Long> activityDefIds,
        Set<Long> deviceDefIds
    ) {
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

    private String pickLabel(Map<String, String> dict, String code) {
        if (dict == null || StringUtils.isBlank(code)) {
            return null;
        }
        return dict.get(code.trim());
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
        String fromCache = pickLabel(cache, code);
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
        if (TableNameConstants.WKF_SERVICE_REQUEST.equalsIgnoreCase(StringUtils.trimToEmpty(basedOnTable))) {
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
            sb.append("处方 ").append(line.getPrescriptionNo());
        }
        if (sb.length() == 0) {
            sb.append("药品申请");
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
        return "诊疗申请";
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
        return "耗材申请";
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
        private void absorb(ChargeItem item, EmergencyDoctorStationAdviceListAssembler outer) {
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
