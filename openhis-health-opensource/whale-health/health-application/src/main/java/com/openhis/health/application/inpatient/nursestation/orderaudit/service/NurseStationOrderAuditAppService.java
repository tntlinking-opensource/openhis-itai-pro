/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderaudit.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.application.shared.validator.CurrentManagedWardLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.advice.InpatientAdviceRevokeGate;
import com.openhis.health.application.inpatient.nursestation.orderaudit.assembler.NurseStationOrderAuditListAssembler;
import com.openhis.health.application.shared.inpatient.InpatientTransferDischargeHoldOrchestrator;
import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderAuditPatientGroupDto;
import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderAuditWriteItemDto;
import com.openhis.health.application.inpatient.nursestation.orderaudit.mapper.NurseStationOrderAuditMapper;
import com.openhis.health.application.inpatient.nursestation.orderaudit.request.ListNurseStationOrderAuditRequest;
import com.openhis.health.application.inpatient.nursestation.orderaudit.request.RejectNurseStationOrdersRequest;
import com.openhis.health.application.inpatient.nursestation.orderaudit.request.VerifyNurseStationOrdersRequest;
import com.openhis.health.application.inpatient.nursestation.orderaudit.response.ListNurseStationOrderAuditResponse;
import com.openhis.health.application.inpatient.nursestation.orderaudit.response.RejectNurseStationOrdersResponse;
import com.openhis.health.application.inpatient.nursestation.orderaudit.response.VerifyNurseStationOrdersResponse;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.inpatient.nursestation.orderexecute.support.InpatientNurseStationTcmOrchestrator;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.domain.custom.encounterhistory.queries.model.EncounterHistoryMilestoneQueryModel;
import com.openhis.health.domain.custom.encounterhistory.service.EncounterHistoryQueryService;
import com.openhis.health.domain.medication.medicationrequest.command.RejectMedicationOrderCommand;
import com.openhis.health.domain.medication.medicationrequest.command.VerifyMedicationOrderCommand;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestDomainService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.application.shared.advice.InpatientOrderListSkeleton;
import com.openhis.health.application.shared.advice.InpatientOrderListMergeAssembler;
import com.openhis.health.application.shared.advice.InpatientOrderLookupLoader;
import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.workflow.servicerequest.command.RejectServiceRequestToDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.VerifyServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestQueryModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院护士站医嘱校对应用编排（AS-IP-NUR-003：IF-IP-AUD-01～03）。
 *
 * <p>待校列表：管理病区内多选患者的待审/已审医嘱，按 Tab 与长临筛选。</p>
 * <p>校对（IF-IP-AUD-02）：药品/诊疗审核通过；转科/出院类医嘱联动就诊状态。</p>
 * <p>退回（IF-IP-AUD-03）：{@code ACTIVE}/{@code VERIFIED} 均可退回草稿；须 **BR-IP-AUD-013**
 *（医生开立、非派生行、无净执行/在途发药/不可回退费用）；转科/出院里程碑同步撤销 {@code ON_HOLD}（GAP-ATD-008）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NurseStationOrderAuditAppService {

    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final CurrentManagedWardLocationIdsResolver managedWardLocationIdsResolver;
    private final InpatientOrderListMergeAssembler inpatientOrderListMergeAssembler;
    private final InpatientOrderLookupLoader inpatientOrderLookupLoader;
    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextReader EncounterContextReader;
    private final EncounterHistoryQueryService encounterHistoryQueryService;
    private final MedicationRequestDomainService medicationRequestDomainService;
    private final ServiceRequestDomainService serviceRequestDomainService;
    private final EncounterDomainService encounterDomainService;
    private final NurseStationOrderAuditMapper nurseStationOrderAuditMapper;
    private final NurseStationOrderAuditListAssembler nurseStationOrderAuditListAssembler;

    private final InpatientTransferDischargeHoldOrchestrator transferDischargeHoldOrchestrationSupport;

    /** 签退/校对退回门控（BR-IP-AUD-013；落点 module.common.validator）。 */
    private final InpatientAdviceRevokeGate adviceRevokeGate;

    /** 中药 Tab 筛选与整方扩行。 */
    private final InpatientNurseStationTcmOrchestrator inpatientNurseStationTcmOrchestrator;

    /**
     * IF-IP-AUD-01：待校/已校医嘱列表，按管理病区与 Tab、长临筛选后按就诊分组返回。
     *
     * @param request 多选就诊、Tab、长临筛选
     * @return 按就诊分组的医嘱行
     * @throws BusinessException EX-IP-AUD-101/102 或参数非法
     */
    public ListNurseStationOrderAuditResponse listNurseStationOrderAudit(ListNurseStationOrderAuditRequest request) {
        try {
            this.assertListAuditRequestValid(request);
            List<Long> managedWardIds = managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty();
            List<Long> requestedEncounterIds = this.distinctEncounterIds(request.getEncounterIds());
            InpatientEncounterPageQuery visibilityQuery =
                nurseStationOrderAuditMapper.toInpatientEncounterPageQueryForAudit(requestedEncounterIds, managedWardIds);
            List<InpatientEncounterContextQueryModel> visibleRows =
                EncounterContextReader.listInpatientEncounterContexts(visibilityQuery);
            List<Long> visibleEncounterIds = this.extractEncounterIds(visibleRows);
            if (visibleEncounterIds.isEmpty()) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_AUD_102,
                    "所选患者不在护士站可见范围");
            }
            List<String> statusEnumIn = nurseStationOrderAuditMapper.resolveStatusEnumIn(request.getAuditTab());
            String therapyEnum = nurseStationOrderAuditMapper.resolveTherapyEnum(request.getTherapyType());
            List<InpatientOrderListSkeleton> skeleton = inpatientOrderListMergeAssembler.mergeNurseStationAuditOrders(
                visibleEncounterIds, statusEnumIn, therapyEnum);
            List<InpatientOrderListSkeleton> filtered =
                nurseStationOrderAuditMapper.filterAuditQueueRows(skeleton);
            String medicineCategoryTab = inpatientNurseStationTcmOrchestrator.resolveMedicineCategoryTabOrDefault(
                request.getMedicineCategoryTab());
            String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
            Map<Long, List<MedicationRequestListQueryModel>> medByEncounter = new HashMap<>();
            Map<Long, List<ServiceRequestListModel>> svcByEncounter = new HashMap<>();
            for (Long encounterId : visibleEncounterIds) {
                List<MedicationRequestListQueryModel> medRows =
                    medicationRequestQueryService.listMedicationRequestsByEncounterForDoctorStation(
                        encounterId, doctorSource);
                // Pre：按中西药 Tab 收窄药品明细
                medRows = inpatientNurseStationTcmOrchestrator.filterMedRowsByMedicineCategory(medRows, medicineCategoryTab);
                medByEncounter.put(encounterId, medRows);
                if (inpatientNurseStationTcmOrchestrator.includeServiceRequestOnTab(medicineCategoryTab)) {
                    List<ServiceRequestListModel> svcRows =
                        serviceRequestQueryService.listServiceRequestsByGenerateSource(encounterId, doctorSource);
                    svcByEncounter.put(encounterId, svcRows);
                } else {
                    svcByEncounter.put(encounterId, new ArrayList<>());
                }
            }
            Map<Long, MedicationRequestListQueryModel> medDetailById =
                inpatientNurseStationTcmOrchestrator.indexMedDetailById(medByEncounter);
            // Gate：骨架行按中西药 Tab 与 tcmFlag 对齐
            filtered = inpatientNurseStationTcmOrchestrator.filterSkeletonByMedicineCategory(
                filtered, medDetailById, medicineCategoryTab);
            List<NurseStationOrderAuditPatientGroupDto> groups =
                nurseStationOrderAuditListAssembler.assemblePatientGroups(
                    filtered, medByEncounter, svcByEncounter, medicineCategoryTab);
            return nurseStationOrderAuditMapper.toListNurseStationOrderAuditResponse(groups);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_AUD_101,
                "医嘱校对列表加载失败，请稍后重试");
        }
    }

    /**
     * IF-IP-AUD-02：批量校对通过；药品/诊疗分别走领域 verify，转科/出院类联动就诊状态。
     *
     * @param request 待校对项
     * @return 处理结果
     * @throws BusinessException EX-IP-AUD-201/202/203
     */
    public VerifyNurseStationOrdersResponse verifyNurseStationOrders(VerifyNurseStationOrdersRequest request) {
        if (request == null) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "请求不能为空");
        }
        this.assertWriteItemsGate(request.getItems());
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_AUD_201,
            "未登录或登录已失效，无法校对医嘱");
        Long performerCheckId = operatorId;
        LocalDateTime checkTime = LocalDateTime.now();
        List<NurseStationOrderAuditWriteItemDto> items = request.getItems();
        Set<Long> visibleEncounterIds = this.resolveVisibleEncounterIdsForWrite(items, true);
        Map<Long, List<MedicationRequestListQueryModel>> medByEncounter = this.loadMedByEncounterForWrite(visibleEncounterIds);
        // Pre·药嘱写扩行：中药整方 / 西药成组一并扩成逐条校对（Gate 条数按扩行前勾选计）
        List<NurseStationOrderAuditWriteItemDto> expandedItems =
            this.expandAuditWriteItemsByTcmPrescription(items, medByEncounter);
        Map<Long, ServiceRequestListModel> serviceDetailById =
            this.indexServiceRequestListById(visibleEncounterIds);
        for (NurseStationOrderAuditWriteItemDto item : expandedItems) {
            this.assertWriteItemActiveAndVisible(item, visibleEncounterIds, true);
        }
        int processed = 0;
        for (NurseStationOrderAuditWriteItemDto item : expandedItems) {
            if (!ReadModelOrderTypeConstants.MED_REQUEST.equals(item.getOrderType())) {
                continue;
            }
            VerifyMedicationOrderCommand command = nurseStationOrderAuditMapper.toVerifyMedicationOrderCommand(
                item, operatorId, performerCheckId, checkTime);
            medicationRequestDomainService.verifyMedicationOrder(command);
            processed = processed + 1;
        }
        List<Long> verifiedServiceRequestIds = new ArrayList<>();
        for (NurseStationOrderAuditWriteItemDto item : items) {
            if (!ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(item.getOrderType())) {
                continue;
            }
            VerifyServiceRequestCommand command = nurseStationOrderAuditMapper.toVerifyServiceRequestCommand(
                item, operatorId, performerCheckId, checkTime);
            serviceRequestDomainService.verifyServiceRequest(command);
            verifiedServiceRequestIds.add(item.getRequestId());
            processed = processed + 1;
        }
        for (Long serviceRequestId : verifiedServiceRequestIds) {
            try {
                this.orchestrateEncounterAfterServiceVerified(
                    serviceRequestId, serviceDetailById, operatorId, checkTime);
            } catch (BusinessException ex) {
                if (ErrorCodeConstants.ENCOUNTER_INVALID_STATUS.equals(ex.getErrorCode())) {
                    throw new BusinessException(
                        InpatientNurseStationConstants.EX_IP_AUD_203,
                        "患者流转状态已变更，请刷新后重试");
                }
                throw ex;
            } catch (OptimisticLockingFailureException ex) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_AUD_203,
                    "患者流转状态已变更，请刷新后重试");
            }
        }
        return nurseStationOrderAuditMapper.toVerifyNurseStationOrdersResponse(processed);
    }

    /**
     * IF-IP-AUD-03：批量退回草稿；须 **BR-IP-AUD-013** 门控后写库；转科/出院类同步撤销相关就诊编排。
     *
     * @param request 待退回项与统一原因
     * @return 处理结果
     * @throws BusinessException EX-IP-AUD-301（状态/门控/来源）、EX-IP-AUD-302（不可见）、EX-IP-AUD-203（逆向编排）
     */
    public RejectNurseStationOrdersResponse rejectNurseStationOrders(RejectNurseStationOrdersRequest request) {
        // Gate：请求体与批量条数上限
        if (request == null) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "请求不能为空");
        }
        this.assertWriteItemsGate(request.getItems());
        // Gate：操作人须为当前 Practitioner
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_AUD_301,
            "未登录或登录已失效，无法退回医嘱");
        Long performerCheckId = operatorId;
        LocalDateTime checkTime = LocalDateTime.now();
        String reason = request.getReason();
        List<NurseStationOrderAuditWriteItemDto> items = request.getItems();
        // Pre：写项所属就诊须在护士站可见范围
        Set<Long> visibleEncounterIds = this.resolveVisibleEncounterIdsForWrite(items, false);
        Map<Long, List<MedicationRequestListQueryModel>> medByEncounter = this.loadMedByEncounterForWrite(visibleEncounterIds);
        // Pre·药嘱写扩行：退回须覆盖中药整方 / 西药成组全部成员行
        List<NurseStationOrderAuditWriteItemDto> expandedItems =
            this.expandAuditWriteItemsByTcmPrescription(items, medByEncounter);
        // Pre：状态/来源/下游事实门禁（ACTIVE/VERIFIED 均须过签退门控，防直调 API 恶意退回）
        for (NurseStationOrderAuditWriteItemDto item : expandedItems) {
            this.assertWriteItemRejectableAndVisible(item, visibleEncounterIds);
        }
        int processed = 0;
        // Cmd：药品申请退回草稿并写校对人追溯
        for (NurseStationOrderAuditWriteItemDto item : expandedItems) {
            if (!ReadModelOrderTypeConstants.MED_REQUEST.equals(item.getOrderType())) {
                continue;
            }
            RejectMedicationOrderCommand command = nurseStationOrderAuditMapper.toRejectMedicationOrderCommand(
                item, reason, operatorId, performerCheckId, checkTime);
            medicationRequestDomainService.rejectMedicationOrder(command);
            processed = processed + 1;
        }
        List<Long> rejectedServiceRequestIds = new ArrayList<>();
        // Cmd：诊疗申请退回草稿
        for (NurseStationOrderAuditWriteItemDto item : items) {
            if (!ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(item.getOrderType())) {
                continue;
            }
            RejectServiceRequestToDraftCommand command =
                nurseStationOrderAuditMapper.toRejectServiceRequestToDraftCommand(
                    item, reason, operatorId, performerCheckId, checkTime);
            serviceRequestDomainService.rejectServiceRequestToDraft(command);
            rejectedServiceRequestIds.add(item.getRequestId());
            processed = processed + 1;
        }
        // Cmd：转科/出院类诊疗同步撤销 ON_HOLD 过渡态（GAP-ATD-008）
        for (Long serviceRequestId : rejectedServiceRequestIds) {
            transferDischargeHoldOrchestrationSupport.tryRevertHoldAfterServiceRequestReverted(
                serviceRequestId,
                operatorId,
                checkTime,
                InpatientNurseStationConstants.EX_IP_AUD_203);
        }
        // Asm：映射处理条数
        return nurseStationOrderAuditMapper.toRejectNurseStationOrdersResponse(processed);
    }

    /**
     * 加载写路径涉及的就诊药品明细（整方扩行用）。
     *
     * @param encounterIds 可见就诊集合
     * @return 就诊 → 药品明细
     */
    private Map<Long, List<MedicationRequestListQueryModel>> loadMedByEncounterForWrite(Set<Long> encounterIds) {
        Map<Long, List<MedicationRequestListQueryModel>> map = new HashMap<>();
        if (encounterIds == null) {
            return map;
        }
        String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        for (Long encounterId : encounterIds) {
            if (encounterId == null) {
                continue;
            }
            map.put(encounterId,
                medicationRequestQueryService.listMedicationRequestsByEncounterForDoctorStation(
                    encounterId, doctorSource));
        }
        return map;
    }

    /**
     * 药嘱勾选写项扩行：中药按 prescription_no 整方；西药按 group_id 成组；诊疗原样。
     *
     * @param items          原始写项
     * @param medByEncounter 就诊药品明细
     * @return 扩行后的写项（去重）
     */
    private List<NurseStationOrderAuditWriteItemDto> expandAuditWriteItemsByTcmPrescription(
        List<NurseStationOrderAuditWriteItemDto> items,
        Map<Long, List<MedicationRequestListQueryModel>> medByEncounter) {
        List<NurseStationOrderAuditWriteItemDto> expanded = new ArrayList<>();
        Set<String> dedupe = new LinkedHashSet<>();
        if (items == null) {
            return expanded;
        }
        for (NurseStationOrderAuditWriteItemDto item : items) {
            if (item == null) {
                continue;
            }
            if (!ReadModelOrderTypeConstants.MED_REQUEST.equals(item.getOrderType())) {
                // 诊疗行不参与药嘱方级/成组扩行
                expanded.add(item);
                continue;
            }
            Long encounterId = this.resolveEncounterIdForWriteItem(item);
            List<MedicationRequestListQueryModel> encounterMeds = null;
            if (encounterId != null) {
                encounterMeds = medByEncounter.get(encounterId);
            }
            // 统一扩行：中药整方优先，否则西药同 group_id
            List<Long> requestIds = inpatientNurseStationTcmOrchestrator.expandMedRequestIdsForNurseWrite(
                encounterId, item.getRequestId(), encounterMeds);
            for (Long requestId : requestIds) {
                String dedupeKey = item.getOrderType() + ":" + requestId;
                if (dedupe.contains(dedupeKey)) {
                    continue;
                }
                dedupe.add(dedupeKey);
                NurseStationOrderAuditWriteItemDto clone = new NurseStationOrderAuditWriteItemDto();
                clone.setOrderType(item.getOrderType());
                clone.setRequestId(requestId);
                expanded.add(clone);
            }
        }
        return expanded;
    }

    /**
     * 列表请求校验：就诊非空、Tab/长临白名单。
     *
     * @param request 列表请求
     */
    private void assertListAuditRequestValid(ListNurseStationOrderAuditRequest request) {
        if (request == null || request.getEncounterIds() == null || request.getEncounterIds().isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "请选择至少一名患者");
        }
        String tab = trimToEmpty(request.getAuditTab());
        if (!this.isAllowedAuditTab(tab)) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "校对 Tab 不合法");
        }
        String therapy = request.getTherapyType();
        if (StringUtils.isEmpty(therapy)) {
            return;
        }
        if (!this.isAllowedTherapyFilter(trimToEmpty(therapy))) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "长临筛选不合法");
        }
        inpatientNurseStationTcmOrchestrator.resolveMedicineCategoryTabOrDefault(request.getMedicineCategoryTab());
    }

    /**
     * 写请求校验：items 非空且不超过批量上限。
     *
     * @param items 写项列表
     */
    private void assertWriteItemsGate(List<NurseStationOrderAuditWriteItemDto> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "请勾选至少一条医嘱");
        }
        if (items.size() > InpatientNurseStationConstants.AUDIT_WRITE_BATCH_MAX) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "单次最多处理50条医嘱");
        }
        for (NurseStationOrderAuditWriteItemDto item : items) {
            if (item == null || item.getRequestId() == null || StringUtils.isEmpty(item.getOrderType())) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                    "医嘱锚点不完整");
            }
            if (!ReadModelOrderTypeConstants.MED_REQUEST.equals(item.getOrderType())
                && !ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(item.getOrderType())) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                    "医嘱类型须为 MED_REQUEST 或 SERVICE_REQUEST");
            }
        }
    }

    /**
     * 写前可见 encounter 集合（BR-IP-AUD-012）。
     *
     * @param items      写项
     * @param verifyPath true=校对（202），false=退回（302）
     * @return 可见就诊 id 集合
     */
    private Set<Long> resolveVisibleEncounterIdsForWrite(
        List<NurseStationOrderAuditWriteItemDto> items,
        boolean verifyPath) {
        List<Long> managedWardIds = managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty();
        Set<Long> encounterIdsFromItems = new LinkedHashSet<>();
        for (NurseStationOrderAuditWriteItemDto item : items) {
            Long encounterId = this.resolveEncounterIdForWriteItem(item);
            if (encounterId != null) {
                encounterIdsFromItems.add(encounterId);
            }
        }
        if (encounterIdsFromItems.isEmpty()) {
            if (verifyPath) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_AUD_202,
                    "无法解析医嘱所属就诊");
            }
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_AUD_302,
                "无法解析医嘱所属就诊");
        }
        InpatientEncounterPageQuery query = nurseStationOrderAuditMapper.toInpatientEncounterPageQueryForAudit(
            new ArrayList<>(encounterIdsFromItems), managedWardIds);
        List<InpatientEncounterContextQueryModel> visible =
            EncounterContextReader.listInpatientEncounterContexts(query);
        return new HashSet<>(this.extractEncounterIds(visible));
    }

    /**
     * 单条写项 ACTIVE 与可见性门禁。
     *
     * @param item              写项
     * @param visibleEncounters   可见就诊集合
     * @param verifyPath          true=校对路径（201/202），false=退回路径（301/302）
     */
    private void assertWriteItemActiveAndVisible(
        NurseStationOrderAuditWriteItemDto item,
        Set<Long> visibleEncounters,
        boolean verifyPath) {
        String notActiveCode;
        String notVisibleCode;
        if (verifyPath) {
            notActiveCode = InpatientNurseStationConstants.EX_IP_AUD_201;
            notVisibleCode = InpatientNurseStationConstants.EX_IP_AUD_202;
        } else {
            notActiveCode = InpatientNurseStationConstants.EX_IP_AUD_301;
            notVisibleCode = InpatientNurseStationConstants.EX_IP_AUD_302;
        }
        Long encounterId = this.resolveEncounterIdForWriteItem(item);
        if (encounterId == null || !visibleEncounters.contains(encounterId)) {
            throw new BusinessException(notVisibleCode, "就诊不在护士站可见范围");
        }
        boolean active;
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(item.getOrderType())) {
            active = medicationRequestQueryService.checkMedicationRequestSignOffAllowed(item.getRequestId());
        } else if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(item.getOrderType())) {
            Boolean withdrawable = serviceRequestQueryService.validateServiceRequestWithdrawable(item.getRequestId());
            active = Boolean.TRUE.equals(withdrawable);
        } else {
            throw new BusinessException(notActiveCode, "不支持的医嘱类型");
        }
        if (!active) {
            throw new BusinessException(notActiveCode, "仅已发送医嘱允许校对");
        }
    }

    /**
     * 单条退回写项：须 ACTIVE/VERIFIED、医生开立来源、无下游阻塞事实（BR-IP-AUD-013）。
     *
     * @param item              写项
     * @param visibleEncounters 可见就诊集合
     */
    private void assertWriteItemRejectableAndVisible(
        NurseStationOrderAuditWriteItemDto item,
        Set<Long> visibleEncounters) {
        String notActiveCode = InpatientNurseStationConstants.EX_IP_AUD_301;
        String notVisibleCode = InpatientNurseStationConstants.EX_IP_AUD_302;
        Long encounterId = this.resolveEncounterIdForWriteItem(item);
        if (encounterId == null || !visibleEncounters.contains(encounterId)) {
            throw new BusinessException(notVisibleCode, "就诊不在护士站可见范围");
        }
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(item.getOrderType())) {
            // 药品：状态白名单 + 来源/派生 + 签退门控
            this.assertMedicationRequestRejectable(item.getRequestId(), encounterId, notActiveCode);
            return;
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(item.getOrderType())) {
            // 诊疗：同上（无发药 Gate，仅执行/费用）
            this.assertServiceRequestRejectable(item.getRequestId(), encounterId, notActiveCode);
            return;
        }
        throw new BusinessException(notActiveCode, "不支持的医嘱类型");
    }

    /**
     * 药品申请退回门禁：白名单状态 + 医生开立 + 签退门控（未校对/已校对均校验下游事实）。
     *
     * @param requestId   药品申请主键
     * @param encounterId 就诊主键
     * @param errorCode   业务语义码
     */
    private void assertMedicationRequestRejectable(Long requestId, Long encounterId, String errorCode) {
        MedicationRequest request = medicationRequestQueryService.findById(requestId);
        if (request == null) {
            throw new BusinessException(errorCode, "药品申请不存在");
        }
        String status = request.getStatusEnum();
        if (!RequestStatus.ACTIVE.getCode().equals(status)
            && !RequestStatus.VERIFIED.getCode().equals(status)) {
            // 停嘱/作废等态禁止退回，防伪造 requestId 直调 API
            throw new BusinessException(errorCode, "仅未校对或已校对且执行/发药/费用已处理完毕的医嘱可退回");
        }
        this.assertDoctorPrescriptionMedicationRejectable(request, errorCode);
        // 加载下游事实：经 Procedure/MedDispense/ChargeItem QS（B.6.4，与医生站签退同口径）
        adviceRevokeGate.assertMedicationWithdrawAllowed(
            encounterId,
            List.of(requestId),
            errorCode,
            InpatientNurseStationConstants.MSG_IP_AUD_REJECT_BLOCKED);
    }

    /**
     * 诊疗申请退回门禁：白名单状态 + 医生开立 + 签退门控（未校对/已校对均校验下游事实）。
     *
     * @param requestId   诊疗申请主键
     * @param encounterId 就诊主键
     * @param errorCode   业务语义码
     */
    private void assertServiceRequestRejectable(Long requestId, Long encounterId, String errorCode) {
        ServiceRequestListModel detail = serviceRequestQueryService.getListModelById(requestId);
        if (detail == null) {
            throw new BusinessException(errorCode, "诊疗申请不存在");
        }
        String status = detail.getStatusEnum();
        if (!RequestStatus.ACTIVE.getCode().equals(status)
            && !RequestStatus.VERIFIED.getCode().equals(status)) {
            throw new BusinessException(errorCode, "仅未校对或已校对且执行/费用已处理完毕的医嘱可退回");
        }
        this.assertDoctorPrescriptionServiceRejectable(detail, errorCode);
        // 加载下游事实：净执行 + 不可回退费用（B.6.4）
        adviceRevokeGate.assertServiceRequestWithdrawAllowed(
            encounterId,
            List.of(requestId),
            errorCode,
            InpatientNurseStationConstants.MSG_IP_AUD_REJECT_BLOCKED);
    }

    /**
     * 药品须为医生开立且非退药负向行（对齐 BR-IP-AUD-002，防伪造锚点退回；认 refund_medicine_id，不认 based_on）。
     *
     * @param request   药品申请
     * @param errorCode 业务语义码
     */
    private void assertDoctorPrescriptionMedicationRejectable(MedicationRequest request, String errorCode) {
        if (!GenerateSource.DOCTOR_PRESCRIPTION.getCode().equals(request.getGenerateSourceEnum())) {
            throw new BusinessException(errorCode, "仅医生开立医嘱允许校对退回");
        }
        if (request.getRefundMedicineId() != null) {
            throw new BusinessException(errorCode, "退药关联行不允许校对退回");
        }
    }

    /**
     * 诊疗须为医生开立且非退诊疗/负向行（对齐 BR-IP-AUD-002，防伪造锚点退回）。
     *
     * @param detail    诊疗列表行
     * @param errorCode 业务语义码
     */
    private void assertDoctorPrescriptionServiceRejectable(ServiceRequestListModel detail, String errorCode) {
        if (!GenerateSource.DOCTOR_PRESCRIPTION.getCode().equals(detail.getGenerateSourceEnum())) {
            throw new BusinessException(errorCode, "仅医生开立医嘱允许校对退回");
        }
        if (detail.getRefundServiceId() != null) {
            throw new BusinessException(errorCode, "退诊疗关联行不允许校对退回");
        }
        BigDecimal quantity = detail.getQuantity();
        if (quantity != null && quantity.signum() < 0) {
            throw new BusinessException(errorCode, "负向诊疗行不允许校对退回");
        }
    }

    /**
     * 从 OrderInfo 读模型解析写项所属就诊（BR-IP-AUD-012 关联；状态门禁走域查询服务）。
     *
     * @param item 写项
     * @return 就诊主键
     */
    private Long resolveEncounterIdForWriteItem(NurseStationOrderAuditWriteItemDto item) {
        return inpatientOrderLookupLoader.resolveEncounterId(item.getOrderType(), item.getRequestId());
    }

    /**
     * 诊疗校对通过后转科/出院 Encounter 编排（BR-IP-AUD-011；AS-SDD §3.3 步骤 8～10）。
     *
     * @param serviceRequestId  刚校对的诊疗申请主键
     * @param serviceDetailById 校对前缓存的诊疗列表行（含开立时间/备注）
     * @param operatorId        操作人
     * @param checkTime           校对时间
     */
    private void orchestrateEncounterAfterServiceVerified(
        Long serviceRequestId,
        Map<Long, ServiceRequestListModel> serviceDetailById,
        Long operatorId,
        LocalDateTime checkTime) {
        ServiceRequestQueryModel head = serviceRequestQueryService.mustGetServiceRequest(serviceRequestId);
        String category = head.getCategoryEnum();
        ServiceRequestListModel detail = serviceDetailById.get(serviceRequestId);
        Long encounterId = null;
        if (detail != null) {
            encounterId = detail.getEncounterId();
        }
        if (encounterId == null) {
            encounterId = inpatientOrderLookupLoader.resolveEncounterId(
                ReadModelOrderTypeConstants.SERVICE_REQUEST, serviceRequestId);
        }
        if (encounterId == null) {
            return;
        }
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            encounterId,
            EncounterClass.IMP.getCode(),
            InpatientNurseStationConstants.EX_IP_AUD_203,
            "仅住院就诊允许转科/出院联动");
        boolean isTransfer = InpatientNurseStationConstants.SERVICE_CATEGORY_TRANSFER.equals(category);
        boolean isDischarge = InpatientNurseStationConstants.SERVICE_CATEGORY_DISCHARGE.equals(category);
        if (!isTransfer && !isDischarge) {
            return;
        }
        String statusCode = encounter.getStatusEnum();
        if (EncounterStatus.IN_PROGRESS.getCode().equals(statusCode)) {
            if (serviceRequestQueryService.hasVerifiedOppositeTransferOrDischargeRequest(
                encounterId, category, serviceRequestId)) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_AUD_203,
                    "同就诊已存在已校对的转科/出院医嘱，无法重复编排");
            }
            this.executeTransferOrDischargeOrchestration(
                isTransfer, encounterId, encounter, serviceRequestId, serviceDetailById, operatorId, checkTime);
            return;
        }
        if (EncounterStatus.ON_HOLD.getCode().equals(statusCode)) {
            Long initiatingRequestId = this.resolveOnHoldInitiatingServiceRequestId(encounterId, isTransfer);
            if (initiatingRequestId != null && initiatingRequestId.equals(serviceRequestId)) {
                return;
            }
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_AUD_203,
                "患者已处于转科/出院过渡态，无法重复编排");
        }
        throw new BusinessException(
            InpatientNurseStationConstants.EX_IP_AUD_203,
            "当前就诊状态不允许转科/出院联动");
    }

    /**
     * 执行转科或出院就诊编排（调用方已确认 {@code IN_PROGRESS}）。
     */
    private void executeTransferOrDischargeOrchestration(
        boolean isTransfer,
        Long encounterId,
        EncounterDetailQueryModel encounter,
        Long serviceRequestId,
        Map<Long, ServiceRequestListModel> serviceDetailById,
        Long operatorId,
        LocalDateTime checkTime) {
        if (isTransfer) {
            Long toDepartmentId = this.resolveTransferTargetOrganizationId(encounterId, serviceRequestId);
            if (toDepartmentId == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_AUD_203,
                    "转科目标科室未配置");
            }
            encounterDomainService.initiateDepartmentTransfer(
                nurseStationOrderAuditMapper.toInitiateDepartmentTransferCommand(
                    encounterId,
                    encounter.getOrganizationId(),
                    toDepartmentId,
                    operatorId,
                    checkTime));
            return;
        }
        String outWayCode = this.resolveDischargeOutWayCode(encounterId, serviceRequestId);
        if (StringUtils.isEmpty(outWayCode)) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_AUD_203,
                "出院方式未配置");
        }
        LocalDateTime dischargedAt = checkTime;
        String summary = null;
        ServiceRequestListModel detail = serviceDetailById.get(serviceRequestId);
        if (detail != null) {
            // AS-SDD：dischargedAt 优先诊嘱开立时刻 reqOpenedTime（空回落 reqAuthoredTime，再回落 checkTime/now）。
            if (detail.getReqOpenedTime() != null) {
                dischargedAt = detail.getReqOpenedTime();
            } else if (detail.getReqAuthoredTime() != null) {
                dischargedAt = detail.getReqAuthoredTime();
            }
            summary = detail.getRemark();
            if (StringUtils.isEmpty(summary)) {
                summary = detail.getEntrustContent();
            }
        }
        encounterDomainService.dischargeInpatientEncounter(
            nurseStationOrderAuditMapper.toDischargeInpatientEncounterCommand(
                encounterId, outWayCode, dischargedAt, summary, operatorId));
    }

    /**
     * 解析 {@code ON_HOLD} 过渡态由哪条诊疗申请发起（里程碑 requestId 对齐）。
     *
     * @param encounterId 就诊主键
     * @param transfer    为 true 查转科里程碑，否则查出院里程碑
     * @return 发起申请主键；未命中时为 null
     */
    private Long resolveOnHoldInitiatingServiceRequestId(Long encounterId, boolean transfer) {
        String milestoneName;
        if (transfer) {
            milestoneName = CustomConstants.EncounterHistoryMilestoneName.TRANSFER;
        } else {
            milestoneName = CustomConstants.EncounterHistoryMilestoneName.DISCHARGE;
        }
        List<EncounterHistoryMilestoneQueryModel> milestones =
            encounterHistoryQueryService.listMilestonesByEncounter(encounterId);
        for (EncounterHistoryMilestoneQueryModel milestone : milestones) {
            if (milestone == null) {
                continue;
            }
            if (!milestoneName.equals(milestone.getName())) {
                continue;
            }
            return milestone.getRequestId();
        }
        return null;
    }

    /**
     * 可见就诊下医生开立诊疗申请 id → 列表行（转科/出院载荷回落）。
     *
     * @param visibleEncounterIds 可见就诊集合
     * @return 诊疗申请主键索引
     */
    private Map<Long, ServiceRequestListModel> indexServiceRequestListById(Set<Long> visibleEncounterIds) {
        Map<Long, ServiceRequestListModel> byId = new HashMap<>();
        String doctorSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        for (Long encounterId : visibleEncounterIds) {
            List<ServiceRequestListModel> rows =
                serviceRequestQueryService.listServiceRequestsByGenerateSource(encounterId, doctorSource);
            for (ServiceRequestListModel row : rows) {
                if (row != null && row.getId() != null) {
                    byId.put(row.getId(), row);
                }
            }
        }
        return byId;
    }

    /**
     * 解析转科里程碑目标科室（requestId 匹配且 name=transfer）。
     *
     * @param encounterId      就诊主键
     * @param serviceRequestId 诊疗申请主键
     * @return 目标科室；未配置时为 null
     */
    private Long resolveTransferTargetOrganizationId(Long encounterId, Long serviceRequestId) {
        List<EncounterHistoryMilestoneQueryModel> milestones =
            encounterHistoryQueryService.listMilestonesByEncounter(encounterId);
        for (EncounterHistoryMilestoneQueryModel milestone : milestones) {
            if (milestone == null) {
                continue;
            }
            if (!CustomConstants.EncounterHistoryMilestoneName.TRANSFER.equals(milestone.getName())) {
                continue;
            }
            if (serviceRequestId != null && serviceRequestId.equals(milestone.getRequestId())) {
                return milestone.getTargetOrganizationId();
            }
        }
        return null;
    }

    /**
     * 解析出院里程碑出院方式（requestId 匹配且 name=discharge）。
     *
     * @param encounterId      就诊主键
     * @param serviceRequestId 诊疗申请主键
     * @return 出院方式 code；未配置时为 null
     */
    private String resolveDischargeOutWayCode(Long encounterId, Long serviceRequestId) {
        List<EncounterHistoryMilestoneQueryModel> milestones =
            encounterHistoryQueryService.listMilestonesByEncounter(encounterId);
        for (EncounterHistoryMilestoneQueryModel milestone : milestones) {
            if (milestone == null) {
                continue;
            }
            if (!CustomConstants.EncounterHistoryMilestoneName.DISCHARGE.equals(milestone.getName())) {
                continue;
            }
            if (serviceRequestId != null && serviceRequestId.equals(milestone.getRequestId())) {
                return milestone.getOutWayCode();
            }
        }
        return null;
    }

    /**
     * 就诊 id 去重（LinkedHashSet 保序）；跳过 null。
     *
     * @param encounterIds 原始列表
     * @return 去重列表
     */
    private List<Long> distinctEncounterIds(List<Long> encounterIds) {
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        if (encounterIds != null) {
            for (Long id : encounterIds) {
                if (id != null) {
                    set.add(id);
                }
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * 从住院信息读模型行提取 encounterId。
     *
     * @param rows 读模型行
     * @return encounterId 列表
     */
    private List<Long> extractEncounterIds(List<InpatientEncounterContextQueryModel> rows) {
        List<Long> ids = new ArrayList<>();
        if (rows == null) {
            return ids;
        }
        for (InpatientEncounterContextQueryModel row : rows) {
            if (row != null && row.getEncounterId() != null) {
                ids.add(row.getEncounterId());
            }
        }
        return ids;
    }

    /**
     * 校对 Tab 白名单（BR-IP-AUD-003）。
     *
     * @param tab auditTab
     * @return 是否允许
     */
    private boolean isAllowedAuditTab(String tab) {
        if (InpatientNurseStationConstants.AUDIT_TAB_PENDING.equals(tab)) {
            return true;
        }
        if (InpatientNurseStationConstants.AUDIT_TAB_PASSED.equals(tab)) {
            return true;
        }
        if (InpatientNurseStationConstants.AUDIT_TAB_STOPPED.equals(tab)) {
            return true;
        }
        if (InpatientNurseStationConstants.AUDIT_TAB_SUSPENDED.equals(tab)) {
            return true;
        }
        return false;
    }

    /**
     * 长临筛选白名单（BR-IP-AUD-004）。
     *
     * @param therapy therapyType
     * @return 是否允许
     */
    private boolean isAllowedTherapyFilter(String therapy) {
        if (InpatientNurseStationConstants.THERAPY_FILTER_ALL.equals(therapy)) {
            return true;
        }
        if (InpatientNurseStationConstants.THERAPY_FILTER_LONG.equals(therapy)) {
            return true;
        }
        if (InpatientNurseStationConstants.THERAPY_FILTER_TEMP.equals(therapy)) {
            return true;
        }
        return false;
    }
}
