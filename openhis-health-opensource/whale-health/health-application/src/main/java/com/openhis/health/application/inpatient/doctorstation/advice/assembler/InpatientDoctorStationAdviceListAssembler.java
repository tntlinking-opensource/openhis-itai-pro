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

package com.openhis.health.application.inpatient.doctorstation.advice.assembler;

import com.openhis.health.module.common.assembler.advice.ServiceApplyFormOrchestrationSupport;
import com.openhis.health.module.common.assembler.advice.AdviceChargeDisplayStatusSupport;
import com.openhis.health.module.common.assembler.option.FrequencyOptionLoader;
import com.openhis.health.module.common.assembler.option.TerminologyOptionLoader;
import com.openhis.health.module.common.assembler.order.OrderCatalogDisplayAssembler;
import com.openhis.health.module.common.assembler.order.OrderRequestCatalogWarmAssembler;
import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.module.querysupport.order.dto.OrderRequestCatalogWarmContext;
import com.openhis.health.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceRowDto;
import com.openhis.health.application.outpatient.doctorstation.advice.assembler.ServiceApplyFormAssembler;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.custom.encounterhistory.queries.model.EncounterHistoryMilestoneQueryModel;
import com.openhis.health.domain.custom.encounterhistory.service.EncounterHistoryQueryService;
import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.module.querysupport.order.dto.InpatientOrderListSkeleton;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.module.querysupport.advice.dto.AdvicePackageChildItemDto;
import com.openhis.health.module.querysupport.advice.AdviceExtensionJsonSupport;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.MedicationCommonConstants;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.ActivityType;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.terminology.entity.Concept;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestDetailListModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
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
    /** 本就诊收费项：护士执行 ORDER_EXECUTE 费用汇总与状态归并。 */
    private final ChargeItemQueryService chargeItemQueryService;
    /** 药品发放读侧：发放锚点→申请映射、执行后发药库位展示。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
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
    /** 库位名称批量解析（执行科室/护士执行后发药库位）。 */
    private final LocationQueryService locationQueryService;
    /** 离院/转科里程碑：出院方式等列表展示字段回源。 */
    private final EncounterHistoryQueryService encounterHistoryQueryService;

    /** 诊疗套餐子项批量加载（B.6.2，禁止透出 children_json 串）。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    /** 申请单 DTO 与编排 Model 互转。 */
    private final ServiceApplyFormAssembler serviceApplyFormAssembler;

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
        return assembleAdviceQueryRows(encounterId, patientId, orderRows, currentUserId, null, null);
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
     * @return 已排序的列表行
     */
    public List<InpatientDoctorStationAdviceRowDto> assembleAdviceQueryRows(
        Long encounterId,
        Long patientId,
        List<InpatientOrderListSkeleton> orderRows,
        Long currentUserId,
        Long orderingDepartmentId,
        List<String> requestStatusEnumIn
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
                && ServiceApplyFormOrchestrationSupport.isRequisitionApplicationPrescriptionNo(serviceDetail.getPrescriptionNo())) {
                applyFormServiceRequestIds.add(serviceDetail.getId());
            }
        }
        Map<Long, List<ServiceRequestDetailListModel>> applyFormDetailsByRequestId =
            serviceRequestQueryService.mapDetailListModelsByServiceRequestIds(applyFormServiceRequestIds);

        Set<Long> medDefIds = new HashSet<>();
        Set<Long> activityDefIds = new HashSet<>();
        Set<Long> conditionDefinitionIds = new HashSet<>();
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
                    if (medDetail.getConditionDefinitionId() != null) {
                        conditionDefinitionIds.add(medDetail.getConditionDefinitionId());
                    }
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
                    if (serviceDetail.getConditionDefinitionId() != null) {
                        conditionDefinitionIds.add(serviceDetail.getConditionDefinitionId());
                    }
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
        }
        for (MedicationRequestListQueryModel medDetail : medicationDetailById.values()) {
            if (medDetail.getRequesterId() != null) {
                practitionerIds.add(medDetail.getRequesterId());
            }
            if (medDetail.getRecorderId() != null) {
                practitionerIds.add(medDetail.getRecorderId());
            }
        }
        for (ServiceRequestListModel serviceDetail : serviceDetailById.values()) {
            if (serviceDetail.getRequesterId() != null) {
                practitionerIds.add(serviceDetail.getRequesterId());
            }
        }
        Map<Long, String> practitionerNameById = loadPractitionerNames(practitionerIds);

        // Asm：按 OrderInfo 行类型分支映射；非药/诊类型跳过（读模型已过滤，此处防御性 continue）。
        List<InpatientDoctorStationAdviceRowDto> unifiedRows = new ArrayList<>();
        for (InpatientOrderListSkeleton order : orderRows) {
            InpatientDoctorStationAdviceRowDto line = new InpatientDoctorStationAdviceRowDto();
            line.setEncounterId(encounterId);
            line.setPatientId(patientId);
            // 分支：药品医嘱行——明细、护士执行后费用与发放库位由 mapMedicationOrderLine 装配。
            if (ReadModelOrderTypeConstants.MED_REQUEST.equals(order.getOrderType())) {
                MedicationRequestListQueryModel medDetail = medicationDetailById.get(order.getMedReqId());
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
                    doseUnitLabelByCode, dosageInstructionLabelByCode, speedLabelByCode,
                    medicationCategoryLabelByCode, conditionDefinitionDisplayById);
            // 分支：诊疗医嘱行——套餐子项与出院/转科特规由 mapServiceOrderLine 装配。
            } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(order.getOrderType())) {
                ServiceRequestListModel serviceDetail = serviceDetailById.get(order.getOrderId());
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
                    activityCategoryLabelByCode, conditionDefinitionDisplayById, locationNameById,
                    dischargeOutWayCodeByServiceRequestId, outWayLabelByCode);
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
            if (ServiceApplyFormOrchestrationSupport.isRequisitionApplicationPrescriptionNo(serviceDetail.getPrescriptionNo())) {
                line.setServiceRequestDetails(serviceApplyFormAssembler.toDetailSummaryDtos(
                    ServiceApplyFormOrchestrationSupport.toDetailSummaryModels(
                        applyFormDetailsByRequestId.get(serviceDetail.getId()))));
            }
        }
        // 列表默认排序：组内序号升序，同组内按开立时间倒序（与医生站展示习惯一致）。
        unifiedRows.sort(Comparator
            .comparing(InpatientDoctorStationAdviceRowDto::getSortNumber, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(InpatientDoctorStationAdviceRowDto::getAuthoredAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return unifiedRows;
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
        Map<String, String> doseUnitLabelByCode,
        Map<String, String> dosageInstructionLabelByCode,
        Map<String, String> speedLabelByCode,
        Map<String, String> medicationCategoryLabelByCode,
        Map<Long, String> conditionDefinitionDisplayById
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
        // 展示：优先领域药品定义；缺失时回落产品读模型名称。
        MedicationDefinition def = medicationDefinitionById.get(medDefId);
        if (def != null) {
            line.setAdviceDisplayName(def.getName());
            line.setSpecificationText(StringUtils.defaultIfBlank(def.getTotalVolume(), def.getComprisedText()));
            fillMedicationCatalogCategory(line, def, medicationCategoryLabelByCode);
        } else {
            ProductDefinitionDisplayDto product = productByKey.get(
                ProductDefinitionKeyUtil.buildCompositeKey(productTable, medDefId));
            if (product != null) {
                line.setAdviceDisplayName(product.getProductName());
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
            line.setInjectionFlag(medDetail.getInfusionFlag());
            line.setInjectionFlagText(formatYesNoChinese(medDetail.getInfusionFlag()));
            line.setConditionDefinitionId(medDetail.getConditionDefinitionId());
            line.setEncounterDiagnosisId(medDetail.getEncounterDiagnosisId());
            if (medDetail.getConditionDefinitionId() != null) {
                line.setConditionDefinitionName(conditionDefinitionDisplayById.get(medDetail.getConditionDefinitionId()));
            }
            line.setDecoctionDelegatedFlag(medDetail.getSufferingFlag());
            line.setChineseHerbsDoseQuantity(toIntegerHerbDoses(medDetail.getChineseHerbsDoseQuantity(), medDetail.getTcmFlag()));
            line.setBasedOnServiceRequestId(resolveBasedOnServiceRequestId(medDetail.getBasedOnTable(), medDetail.getBasedOnId()));
            line.setPrescriptionNo(medDetail.getPrescriptionNo());
            line.setGroupId(medDetail.getGroupId());
            line.setSortNumber(medDetail.getSortNumber());
            if (StringUtils.isNotBlank(medDetail.getTherapyEnum())) {
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
            line.setAuthoredAt(formatAuthoredInstantForApi(medDetail.getReqAuthoredTime()));
            line.setEndTime(formatAuthoredInstantForApi(medDetail.getEffectiveDoseEnd()));
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
            line.setAuthoredAt(formatAuthoredInstantForApi(order.getMedAuthoredTime()));
            line.setEndTime(formatAuthoredInstantForApi(order.getEffectiveDoseEnd()));
            line.setPerformLocationId(order.getLocationId());
            if (order.getLocationId() != null) {
                line.setPerformLocationName(locationNameById.get(order.getLocationId()));
            }
            line.setIsAuthoredByCurrentUser(resolveIsMine(currentUserId, authorId));
        }
        if (StringUtils.isBlank(line.getTherapyTypeCode())) {
            line.setTherapyTypeCode(order.getTherapyEnum());
        }
        fillTherapyDisplay(line);
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
        Map<Long, String> locationNameById,
        Map<Long, String> dischargeOutWayCodeByServiceRequestId,
        Map<String, String> outWayLabelByCode
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
                    AdviceExtensionJsonSupport.toPackageChildItems(packageChildrenByActivityId.get(def.getId()));
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
            line.setConditionDefinitionId(serviceDetail.getConditionDefinitionId());
            line.setEncounterDiagnosisId(serviceDetail.getEncounterDiagnosisId());
            if (serviceDetail.getConditionDefinitionId() != null) {
                line.setConditionDefinitionName(conditionDefinitionDisplayById.get(serviceDetail.getConditionDefinitionId()));
            }
            line.setAuthoredById(serviceDetail.getRequesterId());
            line.setAuthoredByName(practitionerNameById.get(serviceDetail.getRequesterId()));
            line.setAuthoredAt(formatAuthoredInstantForApi(serviceDetail.getReqAuthoredTime()));
            line.setEndTime(formatAuthoredInstantForApi(serviceDetail.getOccurrenceEndTime()));
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
            line.setAuthoredAt(formatAuthoredInstantForApi(order.getAuthoredTime()));
            line.setPerformLocationId(order.getLocationId());
            if (order.getLocationId() != null) {
                line.setPerformLocationName(locationNameById.get(order.getLocationId()));
            }
            line.setIsAuthoredByCurrentUser(resolveIsMine(currentUserId, order.getRequesterId()));
        }
        fillTherapyDisplay(line);
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
        if (StringUtils.isNotBlank(line.getMethodName()) || StringUtils.isNotBlank(line.getRateName())) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            if (StringUtils.isNotBlank(line.getMethodName())) {
                sb.append(line.getMethodName());
            }
            if (StringUtils.isNotBlank(line.getRateName())) {
                if (StringUtils.isNotBlank(line.getMethodName())) {
                    sb.append(',');
                }
                sb.append(line.getRateName());
            }
        }
        if (sb.length() == 0 && StringUtils.isNotBlank(line.getPrescriptionNo())) {
            sb.append(DoctorStationAdviceConstants.ADVICE_LIST_PRESCRIPTION_LABEL_PREFIX).append(line.getPrescriptionNo());
        }
        if (sb.length() == 0) {
            sb.append(DoctorStationAdviceConstants.ADVICE_LIST_MEDICATION_NARROW_FALLBACK_INPATIENT);
        }
        return sb.toString();
    }

    /**
     * 诊疗行窄列摘要：活动定义名称或「诊疗医嘱」。
     *
     * @param line 已填充展示字段的列表行
     * @return 列表窄列展示文案
     */
    private String buildServiceSummary(InpatientDoctorStationAdviceRowDto line) {
        if (StringUtils.isNotBlank(line.getAdviceDisplayName())) {
            return line.getAdviceDisplayName();
        }
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
        if (StringUtils.isNotBlank(serviceDetail.getReasonText())) {
            line.setReasonText(serviceDetail.getReasonText());
        }
        if (serviceDetail.getOccurrenceStartTime() != null) {
            line.setPlannedDischargeTime(formatAuthoredInstantForApi(serviceDetail.getOccurrenceStartTime()));
            if (StringUtils.isBlank(line.getAuthoredAt())) {
                line.setAuthoredAt(line.getPlannedDischargeTime());
            }
        }
        if (StringUtils.isBlank(line.getTherapyTypeCode()) && StringUtils.isNotBlank(serviceDetail.getTherapyEnum())) {
            line.setTherapyTypeCode(normalizeTherapyTypeCode(serviceDetail.getTherapyEnum()));
            fillTherapyDisplay(line);
        } else if (StringUtils.isNotBlank(line.getTherapyTypeCode())) {
            line.setTherapyTypeCode(normalizeTherapyTypeCode(line.getTherapyTypeCode()));
            fillTherapyDisplay(line);
        }
        if (StringUtils.isBlank(line.getStatusEnum()) && StringUtils.isNotBlank(serviceDetail.getStatusEnum())) {
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
            if (StringUtils.isNotBlank(milestone.getOutWayCode())) {
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
        if (serviceDetail == null || StringUtils.isBlank(serviceDetail.getCategoryEnum())) {
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
        if (def == null || StringUtils.isBlank(def.getCategoryCode())) {
            return;
        }
        line.setCatalogCategoryCode(def.getCategoryCode().trim());
        line.setCatalogCategoryName(
            OrderCatalogDisplayAssembler.resolveCategoryDisplay(medicationCategoryLabelByCode, def.getCategoryCode()));
    }

    /**
     * 回填诊疗目录类目与活动类型（检查/检验等）展示列。
     */
    private void fillServiceCatalogAndActivityType(
        InpatientDoctorStationAdviceRowDto line,
        ActivityDefinition def,
        Map<String, String> activityCategoryLabelByCode
    ) {
        if (StringUtils.isNotBlank(def.getCategoryCode())) {
            line.setCatalogCategoryCode(def.getCategoryCode().trim());
            line.setCatalogCategoryName(
                OrderCatalogDisplayAssembler.resolveCategoryDisplay(activityCategoryLabelByCode, def.getCategoryCode()));
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
        if (TableNameConstants.WKF_SERVICE_REQUEST.equalsIgnoreCase(StringUtils.trimToEmpty(basedOnTable))) {
            return basedOnId;
        }
        return null;
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
        return productDefinitionBatchLoader.loadAsMap(lookups);
    }

    /**
     * 长临医嘱类型 code → 中文展示（{@link TherapyType}）。
     *
     * @param line 已写入 therapyTypeCode 的列表行
     */
    private void fillTherapyDisplay(InpatientDoctorStationAdviceRowDto line) {
        if (StringUtils.isBlank(line.getTherapyTypeCode())) {
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
        if ("2".equals(StringUtils.trimToEmpty(therapyCode))) {
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

    private void applyListDisplayStatus(InpatientDoctorStationAdviceRowDto line) {
        AdviceChargeDisplayStatusSupport.ListDisplayStatus display =
            AdviceChargeDisplayStatusSupport.resolveAdviceListDisplayStatus(
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
        return value.setScale(DoctorStationAdviceConstants.ADVICE_LIST_CHARGE_DISPLAY_DECIMAL_SCALE, RoundingMode.HALF_UP);
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
     * 开立/结束时间格式化为 API 约定 ISO 本地日期时间串。
     */
    private String formatAuthoredInstantForApi(LocalDateTime authoredTime) {
        if (authoredTime == null) {
            return null;
        }
        return GeneralSharedConstants.ISO_LOCAL_DATE_TIME_FORMATTER.format(authoredTime);
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
        if (StringUtils.isBlank(statusCode)) {
            return null;
        }
        RequestStatus status = EnumUtil.fromCode(RequestStatus.class, statusCode);
        if (status != null) {
            return status.getDisplay();
        }
        return statusCode;
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
