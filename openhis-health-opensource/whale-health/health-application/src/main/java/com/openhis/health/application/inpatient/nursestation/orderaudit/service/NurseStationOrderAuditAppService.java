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
import com.openhis.health.domain.medication.medicationrequest.command.CancelVerifyStopMedicationOrderCommand;
import com.openhis.health.domain.medication.medicationrequest.command.RejectMedicationOrderCommand;
import com.openhis.health.domain.medication.medicationrequest.command.VerifyMedicationOrderCommand;
import com.openhis.health.domain.medication.medicationrequest.command.VerifyStopMedicationOrderCommand;
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
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.workflow.servicerequest.command.CancelVerifyStopServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.RejectServiceRequestToDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.VerifyServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.VerifyStopServiceRequestCommand;
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
import org.springframework.transaction.annotation.Transactional;

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
 * <p>待校列表：管理病区内多选患者的待审/已审医嘱，按 Tab 与长临筛选；
 * pending 含开嘱待校（ACTIVE）与停嘱待校（长期 STOPPED 且 {@code stopPerformerCheckId} 为空）。</p>
 * <p>校对（IF-IP-AUD-02）：ACTIVE 走开嘱校对；STOPPED 走停嘱校对（仅写停嘱校对痕迹）；
 * 转科/出院联动仅开嘱校对成功后触发。停嘱校对不自动 skip 剩余 pending——
 * 真 pending 由执行 Tab / IF-IP-EXE-04 处理，改量假 pending 由 BR-IP-EXE-038 抑制。</p>
 * <p>退回（IF-IP-AUD-03）：{@code ACTIVE}/{@code VERIFIED} 退回草稿（须 **BR-IP-AUD-013**）；
 * 已停嘱校对走取消停嘱校对（{@code EX-IP-AUD-204}，不改状态）；
 * 转科/出院里程碑同步撤销 {@code ON_HOLD} 仅开嘱退回路径触发。</p>
 * <p>写路径整批同事务（**BR-IP-AUD-010**）。</p>
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
            // Gate：读列表在科优先；未命中按就诊主键回落（病案侧栏打印出院患者长/临嘱）
            List<InpatientEncounterContextQueryModel> visibleRows =
                EncounterContextReader.listNurseStationReadableEncounterContexts(
                    requestedEncounterIds, managedWardIds);
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
            String auditTab = trimToEmpty(request.getAuditTab());
            // Pre：pending Tab 再收窄停嘱待校（长期 STOPPED 且未停嘱校对）
            if (InpatientNurseStationConstants.AUDIT_TAB_PENDING.equals(auditTab)) {
                filtered = nurseStationOrderAuditMapper.filterPendingAuditRows(filtered);
            }
            // Pre：passed Tab 含 VERIFIED + 已停嘱校对 STOPPED（同一条不拆两行）
            if (InpatientNurseStationConstants.AUDIT_TAB_PASSED.equals(auditTab)) {
                filtered = nurseStationOrderAuditMapper.filterPassedAuditRows(filtered);
            }
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
     * IF-IP-AUD-02：批量校对通过；ACTIVE 走开嘱校对，STOPPED 走停嘱校对；转科/出院仅开嘱校对后联动。
     *
     * @param request 待校对项
     * @return 处理结果
     * @throws BusinessException EX-IP-AUD-201/202/203
     */
    @Transactional(rollbackFor = Exception.class)
    public VerifyNurseStationOrdersResponse verifyNurseStationOrders(VerifyNurseStationOrdersRequest request) {
        // Gate：请求体非空
        if (request == null) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "请求不能为空");
        }
        // Gate：勾选条数与锚点完整
        this.assertWriteItemsGate(request.getItems());
        // Gate：操作人须为当前 Practitioner
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_AUD_201,
            "未登录或登录已失效，无法校对医嘱");
        Long performerCheckId = operatorId;
        LocalDateTime checkTime = LocalDateTime.now();
        List<NurseStationOrderAuditWriteItemDto> items = request.getItems();
        // Pre：写项所属就诊须在护士站可见范围
        Set<Long> visibleEncounterIds = this.resolveVisibleEncounterIdsForWrite(items, true);
        Map<Long, List<MedicationRequestListQueryModel>> medByEncounter = this.loadMedByEncounterForWrite(visibleEncounterIds);
        // Pre·药嘱写扩行：中药整方 / 西药成组一并扩成逐条校对（开嘱/停嘱校对均适用；Gate 条数按扩行前勾选计）
        List<NurseStationOrderAuditWriteItemDto> expandedItems =
            this.expandAuditWriteItemsByTcmPrescription(items, medByEncounter);
        Map<Long, ServiceRequestListModel> serviceDetailById =
            this.indexServiceRequestListById(visibleEncounterIds);
        // Gate：扩行后逐条校验可见性与开嘱/停嘱校对资格
        for (NurseStationOrderAuditWriteItemDto item : expandedItems) {
            this.assertWriteItemVerifiableAndVisible(item, visibleEncounterIds);
        }
        int processed = 0;
        // Cmd：药品——STOPPED 停嘱校对，ACTIVE 开嘱校对
        for (NurseStationOrderAuditWriteItemDto item : expandedItems) {
            if (!ReadModelOrderTypeConstants.MED_REQUEST.equals(item.getOrderType())) {
                continue;
            }
            MedicationRequest med = medicationRequestQueryService.findById(item.getRequestId());
            if (med == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_AUD_201,
                    "药品申请不存在");
            }
            // 分支：已停嘱长期行走停嘱校对，仅写停嘱校对痕迹
            if (RequestStatus.STOPPED.getCode().equals(med.getStatusEnum())) {
                VerifyStopMedicationOrderCommand stopCommand =
                    nurseStationOrderAuditMapper.toVerifyStopMedicationOrderCommand(
                        item, operatorId, performerCheckId, checkTime);
                medicationRequestDomainService.verifyStopMedicationOrder(stopCommand);
                processed = processed + 1;
                continue;
            }
            // 分支：已发送行走开嘱校对 → VERIFIED
            VerifyMedicationOrderCommand command = nurseStationOrderAuditMapper.toVerifyMedicationOrderCommand(
                item, operatorId, performerCheckId, checkTime);
            medicationRequestDomainService.verifyMedicationOrder(command);
            processed = processed + 1;
        }
        List<Long> verifiedServiceRequestIds = new ArrayList<>();
        // Cmd：诊疗——STOPPED 停嘱校对，ACTIVE 开嘱校对（诊疗不参与成组扩行，用原始勾选）
        for (NurseStationOrderAuditWriteItemDto item : items) {
            if (!ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(item.getOrderType())) {
                continue;
            }
            ServiceRequestListModel svc = serviceRequestQueryService.getListModelById(item.getRequestId());
            if (svc == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_AUD_201,
                    "诊疗申请不存在");
            }
            // 分支：已停嘱长期行走停嘱校对，不触发转科/出院联动
            if (RequestStatus.STOPPED.getCode().equals(svc.getStatusEnum())) {
                VerifyStopServiceRequestCommand stopCommand =
                    nurseStationOrderAuditMapper.toVerifyStopServiceRequestCommand(
                        item, operatorId, performerCheckId, checkTime);
                serviceRequestDomainService.verifyStopServiceRequest(stopCommand);
                processed = processed + 1;
                continue;
            }
            // 分支：已发送行走开嘱校对，收集 id 供转科/出院编排
            VerifyServiceRequestCommand command = nurseStationOrderAuditMapper.toVerifyServiceRequestCommand(
                item, operatorId, performerCheckId, checkTime);
            serviceRequestDomainService.verifyServiceRequest(command);
            verifiedServiceRequestIds.add(item.getRequestId());
            processed = processed + 1;
        }
        // Cmd：仅开嘱校对成功的诊疗触发转科/出院 Encounter 编排（停嘱校对不触发）
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
        // Asm：映射处理条数
        return nurseStationOrderAuditMapper.toVerifyNurseStationOrdersResponse(processed);
    }

    /**
     * IF-IP-AUD-03：批量退回草稿或取消停嘱校对；转科/出院类同步撤销仅开嘱退回路径触发。
     *
     * <p>STOPPED 且已有 {@code stopPerformerCheckId}：取消停嘱校对（清空痕迹、不改状态、不退回草稿）。
     * 其余走原退回草稿（须 **BR-IP-AUD-013**）。</p>
     * <p>出院类（category=34）开嘱退回后同事务软删申请并废离院里程碑（**BR-IP-AUD-016**）：
     * 出院内容不可行内修订，医生须右上角 IF-IP-DOR-21 重开。</p>
     *
     * @param request 待退回项与统一原因
     * @return 处理结果
     * @throws BusinessException EX-IP-AUD-301（退回草稿门控）、EX-IP-AUD-204（取消停嘱校对失败）、
     *         EX-IP-AUD-302（不可见）、EX-IP-AUD-203（逆向编排）
     */
    @Transactional(rollbackFor = Exception.class)
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
        // Pre·药嘱写扩行：退回/取消停嘱校对须覆盖中药整方 / 西药成组全部成员行
        List<NurseStationOrderAuditWriteItemDto> expandedItems =
            this.expandAuditWriteItemsByTcmPrescription(items, medByEncounter);
        // Pre：状态/来源/下游事实门禁（取消停嘱校对仅可见性；ACTIVE/VERIFIED 须过签退门控）
        for (NurseStationOrderAuditWriteItemDto item : expandedItems) {
            this.assertWriteItemRejectableAndVisible(item, visibleEncounterIds);
        }
        int processed = 0;
        // Cmd：药品——已停嘱校对则取消停嘱校对；否则退回草稿
        for (NurseStationOrderAuditWriteItemDto item : expandedItems) {
            if (!ReadModelOrderTypeConstants.MED_REQUEST.equals(item.getOrderType())) {
                continue;
            }
            MedicationRequest med = medicationRequestQueryService.findById(item.getRequestId());
            if (med == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_AUD_301,
                    "药品申请不存在");
            }
            // 分支：STOPPED 且已有停嘱校对痕迹 → 取消停嘱校对（不改状态、不退回草稿）
            if (RequestStatus.STOPPED.getCode().equals(med.getStatusEnum())
                && med.getStopPerformerCheckId() != null) {
                try {
                    CancelVerifyStopMedicationOrderCommand cancelCommand =
                        nurseStationOrderAuditMapper.toCancelVerifyStopMedicationOrderCommand(item, operatorId);
                    medicationRequestDomainService.cancelVerifyStopMedicationOrder(cancelCommand);
                } catch (BusinessException ex) {
                    // 域断言失败统一映射为取消停嘱校对语义码，避免泄漏域错误码
                    throw new BusinessException(
                        InpatientNurseStationConstants.EX_IP_AUD_204,
                        ex.getMessage());
                }
                processed = processed + 1;
                continue;
            }
            // 分支：ACTIVE/VERIFIED → 退回草稿
            RejectMedicationOrderCommand command = nurseStationOrderAuditMapper.toRejectMedicationOrderCommand(
                item, reason, operatorId, performerCheckId, checkTime);
            medicationRequestDomainService.rejectMedicationOrder(command);
            processed = processed + 1;
        }
        List<Long> rejectedServiceRequestIds = new ArrayList<>();
        // Cmd：诊疗——已停嘱校对则取消停嘱校对；否则退回草稿（诊疗不参与成组扩行）
        for (NurseStationOrderAuditWriteItemDto item : items) {
            if (!ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(item.getOrderType())) {
                continue;
            }
            ServiceRequestListModel svc = serviceRequestQueryService.getListModelById(item.getRequestId());
            if (svc == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_AUD_301,
                    "诊疗申请不存在");
            }
            // 分支：STOPPED 且已有停嘱校对痕迹 → 取消停嘱校对，不触发转科/出院撤销
            if (RequestStatus.STOPPED.getCode().equals(svc.getStatusEnum())
                && svc.getStopPerformerCheckId() != null) {
                try {
                    CancelVerifyStopServiceRequestCommand cancelCommand =
                        nurseStationOrderAuditMapper.toCancelVerifyStopServiceRequestCommand(item, operatorId);
                    serviceRequestDomainService.cancelVerifyStopServiceRequest(cancelCommand);
                } catch (BusinessException ex) {
                    // 域断言失败统一映射为取消停嘱校对语义码
                    throw new BusinessException(
                        InpatientNurseStationConstants.EX_IP_AUD_204,
                        ex.getMessage());
                }
                processed = processed + 1;
                continue;
            }
            // 分支：ACTIVE/VERIFIED → 退回草稿，收集 id 供 ON_HOLD 撤销
            RejectServiceRequestToDraftCommand command =
                nurseStationOrderAuditMapper.toRejectServiceRequestToDraftCommand(
                    item, reason, operatorId, performerCheckId, checkTime);
            serviceRequestDomainService.rejectServiceRequestToDraft(command);
            rejectedServiceRequestIds.add(item.getRequestId());
            processed = processed + 1;
        }
        // Cmd：转科/出院类诊疗同步撤销 ON_HOLD 过渡态（GAP-ATD-008；仅开嘱退回）
        for (Long serviceRequestId : rejectedServiceRequestIds) {
            transferDischargeHoldOrchestrationSupport.tryRevertHoldAfterServiceRequestReverted(
                serviceRequestId,
                operatorId,
                checkTime,
                InpatientNurseStationConstants.EX_IP_AUD_203);
        }
        // Cmd（BR-IP-AUD-016）：出院退回后直接软删，医生须右上角 IF-IP-DOR-21 重开（不可行内修订）
        for (Long serviceRequestId : rejectedServiceRequestIds) {
            transferDischargeHoldOrchestrationSupport.softDeleteDischargeAdviceAfterRevertedToDraft(
                serviceRequestId, operatorId);
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
     * 单条校对写项：可见性 + 开嘱校对（ACTIVE）或停嘱校对（长期 STOPPED 且未写停嘱校对痕迹）。
     *
     * @param item              写项
     * @param visibleEncounters 可见就诊集合
     */
    private void assertWriteItemVerifiableAndVisible(
        NurseStationOrderAuditWriteItemDto item,
        Set<Long> visibleEncounters) {
        String notAllowedCode = InpatientNurseStationConstants.EX_IP_AUD_201;
        String notVisibleCode = InpatientNurseStationConstants.EX_IP_AUD_202;
        Long encounterId = this.resolveEncounterIdForWriteItem(item);
        // Gate：就诊须在管理病区可见范围
        if (encounterId == null || !visibleEncounters.contains(encounterId)) {
            throw new BusinessException(notVisibleCode, "就诊不在护士站可见范围");
        }
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(item.getOrderType())) {
            this.assertMedicationRequestVerifiable(item.getRequestId(), notAllowedCode);
            return;
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(item.getOrderType())) {
            this.assertServiceRequestVerifiable(item.getRequestId(), notAllowedCode);
            return;
        }
        throw new BusinessException(notAllowedCode, "不支持的医嘱类型");
    }

    /**
     * 药品校对资格：ACTIVE 可开嘱校对；长期 STOPPED 且未停嘱校对可停嘱校对。
     *
     * @param requestId 药品申请主键
     * @param errorCode 业务语义码
     */
    private void assertMedicationRequestVerifiable(Long requestId, String errorCode) {
        MedicationRequest request = medicationRequestQueryService.findById(requestId);
        if (request == null) {
            throw new BusinessException(errorCode, "药品申请不存在");
        }
        String status = request.getStatusEnum();
        // 分支：已发送 → 开嘱校对
        if (RequestStatus.ACTIVE.getCode().equals(status)) {
            return;
        }
        // 分支：已停嘱 → 仅长期且尚未停嘱校对可走停嘱校对
        if (RequestStatus.STOPPED.getCode().equals(status)) {
            if (!TherapyType.LONG_TERM.getCode().equals(request.getTherapyEnum())) {
                throw new BusinessException(errorCode, "仅长期已停嘱医嘱允许停嘱校对");
            }
            if (request.getStopPerformerCheckId() != null) {
                throw new BusinessException(errorCode, "该医嘱已完成停嘱校对");
            }
            return;
        }
        throw new BusinessException(errorCode, "仅已发送或待停嘱校对医嘱允许校对");
    }

    /**
     * 诊疗校对资格：ACTIVE 可开嘱校对；长期 STOPPED 且未停嘱校对可停嘱校对。
     *
     * @param requestId 诊疗申请主键
     * @param errorCode 业务语义码
     */
    private void assertServiceRequestVerifiable(Long requestId, String errorCode) {
        ServiceRequestListModel detail = serviceRequestQueryService.getListModelById(requestId);
        if (detail == null) {
            throw new BusinessException(errorCode, "诊疗申请不存在");
        }
        String status = detail.getStatusEnum();
        // 分支：已发送 → 开嘱校对
        if (RequestStatus.ACTIVE.getCode().equals(status)) {
            return;
        }
        // 分支：已停嘱 → 仅长期且尚未停嘱校对可走停嘱校对
        if (RequestStatus.STOPPED.getCode().equals(status)) {
            if (!TherapyType.LONG_TERM.getCode().equals(detail.getTherapyEnum())) {
                throw new BusinessException(errorCode, "仅长期已停嘱医嘱允许停嘱校对");
            }
            if (detail.getStopPerformerCheckId() != null) {
                throw new BusinessException(errorCode, "该医嘱已完成停嘱校对");
            }
            return;
        }
        throw new BusinessException(errorCode, "仅已发送或待停嘱校对医嘱允许校对");
    }

    /**
     * 单条退回/取消停嘱校对写项门禁。
     *
     * <p>STOPPED 且已有停嘱校对痕迹：仅校验可见性（取消停嘱校对路径）。
     * STOPPED 但尚未停嘱校对：拒绝（{@code EX-IP-AUD-204}，须先停嘱校对或改从 pending 校对）。
     * 其余须 ACTIVE/VERIFIED、医生开立来源、无下游阻塞事实（BR-IP-AUD-013 / {@code EX-IP-AUD-301}）。</p>
     *
     * @param item              写项
     * @param visibleEncounters 可见就诊集合
     */
    private void assertWriteItemRejectableAndVisible(
        NurseStationOrderAuditWriteItemDto item,
        Set<Long> visibleEncounters) {
        String rejectDraftCode = InpatientNurseStationConstants.EX_IP_AUD_301;
        String cancelStopVerifyCode = InpatientNurseStationConstants.EX_IP_AUD_204;
        String notVisibleCode = InpatientNurseStationConstants.EX_IP_AUD_302;
        Long encounterId = this.resolveEncounterIdForWriteItem(item);
        if (encounterId == null || !visibleEncounters.contains(encounterId)) {
            throw new BusinessException(notVisibleCode, "就诊不在护士站可见范围");
        }
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(item.getOrderType())) {
            MedicationRequest med = medicationRequestQueryService.findById(item.getRequestId());
            if (med == null) {
                throw new BusinessException(rejectDraftCode, "药品申请不存在");
            }
            // 分支：已停嘱校对 → 取消停嘱校对路径，跳过退回草稿门控
            if (RequestStatus.STOPPED.getCode().equals(med.getStatusEnum())
                && med.getStopPerformerCheckId() != null) {
                return;
            }
            // 分支：已停嘱但未停嘱校对 → 不可「退回/取消停嘱校对」，须先做停嘱校对
            if (RequestStatus.STOPPED.getCode().equals(med.getStatusEnum())) {
                throw new BusinessException(cancelStopVerifyCode, "该医嘱尚未停嘱校对，无法取消停嘱校对");
            }
            // 药品：状态白名单 + 来源/派生 + 签退门控
            this.assertMedicationRequestRejectable(item.getRequestId(), encounterId, rejectDraftCode);
            return;
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(item.getOrderType())) {
            ServiceRequestListModel svc = serviceRequestQueryService.getListModelById(item.getRequestId());
            if (svc == null) {
                throw new BusinessException(rejectDraftCode, "诊疗申请不存在");
            }
            // 分支：已停嘱校对 → 取消停嘱校对路径，跳过退回草稿门控
            if (RequestStatus.STOPPED.getCode().equals(svc.getStatusEnum())
                && svc.getStopPerformerCheckId() != null) {
                return;
            }
            // 分支：已停嘱但未停嘱校对 → 须先停嘱校对
            if (RequestStatus.STOPPED.getCode().equals(svc.getStatusEnum())) {
                throw new BusinessException(cancelStopVerifyCode, "该医嘱尚未停嘱校对，无法取消停嘱校对");
            }
            // 诊疗：同上（无发药 Gate，仅执行/费用）
            this.assertServiceRequestRejectable(item.getRequestId(), encounterId, rejectDraftCode);
            return;
        }
        throw new BusinessException(rejectDraftCode, "不支持的医嘱类型");
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
            // 幂等：仅当本条医嘱自身已有对齐里程碑时跳过二次编排（禁止取时间线首条 requestId）
            if (this.hasAlignedTransferOrDischargeMilestone(encounterId, isTransfer, serviceRequestId)) {
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
            // 校对出院：dischargedAt 取计划出院时间 occurrenceStartTime（与办理出院/AS-IP-NUR-002 同口径）；
            // 领域 dischargeInpatientEncounter 不写 end_time，仍须传对以免后续误用开立时刻。
            if (detail.getOccurrenceStartTime() != null) {
                dischargedAt = detail.getOccurrenceStartTime();
            } else if (detail.getReqOpenedTime() != null) {
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
     * 判断就诊是否已有与指定诊疗申请对齐的转科/出院里程碑（校对 ON_HOLD 幂等门禁）。
     *
     * <p>同就诊可多次开立转科/出院，里程碑按条追加；必须 {@code requestId} 精确匹配，
     * 禁止取时间线首条（否则二次校对会误判「无法重复编排」）。</p>
     *
     * @param encounterId      就诊主键
     * @param transfer         为 true 查转科里程碑，否则查出院里程碑
     * @param serviceRequestId 当前正在校对的诊疗申请主键
     * @return 存在对齐里程碑时为 true
     */
    private boolean hasAlignedTransferOrDischargeMilestone(
        Long encounterId,
        boolean transfer,
        Long serviceRequestId) {
        if (encounterId == null || serviceRequestId == null) {
            return false;
        }
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
            if (serviceRequestId.equals(milestone.getRequestId())) {
                return true;
            }
        }
        return false;
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
     * 校对 Tab 白名单：仅未校对 / 已校对（BR-IP-AUD-003）。
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
