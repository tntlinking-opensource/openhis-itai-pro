/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.orchestration;

import com.openhis.health.application.peis.history.support.PeisPackageSnapshotSupport;
import com.openhis.health.application.peis.orchestration.command.AdjustPeServiceRequestsCommand;
import com.openhis.health.application.peis.orchestration.command.RegisterGroupAfterPaidCommand;
import com.openhis.health.application.peis.orchestration.command.RegisterPersonalCheckInCommand;
import com.openhis.health.application.peis.orchestration.result.RegisterPersonalCheckInResult;
import com.openhis.health.domain.administration.encounter.command.RegisterPhysicalExamEncounterCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.peis.encounterext.command.CreatePeEncounterExtCommand;
import com.openhis.health.domain.peis.encounterext.command.UpdatePePackageSnapshotCommand;
import com.openhis.health.domain.peis.encounterext.queries.model.PeisEncounterExtModel;
import com.openhis.health.domain.peis.encounterext.service.PeisEncounterExtDomainService;
import com.openhis.health.domain.peis.encounterext.service.PeisEncounterExtQueryService;
import com.openhis.health.domain.peis.servicerequestext.command.InitPeisSrExtCommand;
import com.openhis.health.domain.peis.servicerequestext.queries.model.PeisServiceRequestExtModel;
import com.openhis.health.domain.peis.servicerequestext.service.PeisServiceRequestExtDomainService;
import com.openhis.health.domain.peis.servicerequestext.service.PeisServiceRequestExtQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.ObservationDefinitionConstants;
import com.openhis.health.domain.share.constants.PeisCatalogConstants;
import com.openhis.health.domain.share.constants.PeisExamKindConstants;
import com.openhis.health.domain.share.constants.PeisExecStatusConstants;
import com.openhis.health.domain.share.constants.ServiceRequestCategoryConstants;
import com.openhis.health.domain.share.extension.campus.PeisCampusIdentityPort;
import com.openhis.health.domain.share.extension.campus.PeisCampusIdentityView;
import com.openhis.health.domain.share.enums.ActivityType;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.events.custom.peisregistration.PeisEncounterRegisteredEvent;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionBaseQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionDetailQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.servicerequest.command.CancelServiceRequestCommand;
import com.openhis.health.domain.workflow.servicerequest.command.CreateServiceRequestDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestQueryModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 体检登记跨聚合编排（AS-PEIS-REG-ORCH）。
 *
 * <p>个检登记与加/减项；不声明事务，由 AppService 入口 UoW 管理。</p>
 */
@Component
@RequiredArgsConstructor
public class PeisRegistrationOrchestrator {

    private final EncounterDomainService encounterDomainService;
    private final EncounterQueryService encounterQueryService;
    private final PatientQueryService patientQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final PeisEncounterExtDomainService peisEncounterExtDomainService;
    private final PeisEncounterExtQueryService peisEncounterExtQueryService;
    private final ServiceRequestDomainService serviceRequestDomainService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final PeisServiceRequestExtDomainService peisServiceRequestExtDomainService;
    private final PeisServiceRequestExtQueryService peisServiceRequestExtQueryService;
    private final IDomainEventPublisher domainEventPublisher;
    private final ObjectProvider<PeisCampusIdentityPort> peisCampusIdentityPort;

    /**
     * 个检登记：创建 PE Encounter、扩展、套餐展开 SR 与执行扩展。
     *
     * @param cmd 登记命令
     * @return 登记结果
     */
    public RegisterPersonalCheckInResult registerPersonalCheckIn(RegisterPersonalCheckInCommand cmd) {
        // Gate：必填
        if (cmd == null || cmd.getPatientId() == null || cmd.getPackageActivityId() == null
            || cmd.getOrgId() == null || cmd.getOperatorId() == null) {
            throw new ValidationException("个检登记失败：患者、套餐、机构与操作人不能为空");
        }

        // Pre：患者存在
        if (patientQueryService.findOptionalPatientEntityById(cmd.getPatientId()).isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PEIS_REG_PATIENT_NOT_FOUND, "患者不存在");
        }

        // Pre：套餐有效且含子项
        ActivityDefinitionDetailQueryModel packageDetail =
            activityDefinitionQueryService.getServiceCatalog(cmd.getPackageActivityId());
        ActivityDefinitionBaseQueryModel base = packageDetail == null ? null : packageDetail.getBaseInfo();
        if (base == null) {
            throw new BusinessException(ErrorCodeConstants.PEIS_REG_PACKAGE_INACTIVE, "套餐不存在或未启用");
        }
        if (!isActivePublication(base.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.PEIS_REG_PACKAGE_INACTIVE, "套餐未启用");
        }
        if (!PeisCatalogConstants.ACTIVITY_CATEGORY_PEIS.equals(base.getCategoryCode())) {
            throw new BusinessException(ErrorCodeConstants.PEIS_REG_PACKAGE_INACTIVE, "套餐类目非法");
        }
        List<ActivityDefinitionChildQueryModel> children = packageDetail.getChildren();
        if (children == null) {
            children = List.of();
        }
        boolean hasChildren = Boolean.TRUE.equals(base.getChildrenFlag()) || !children.isEmpty();
        if (!hasChildren || children.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PEIS_REG_PACKAGE_INACTIVE, "套餐无有效子项");
        }

        // Pre：防重复在检
        if (encounterQueryService.existsActivePeEncounter(cmd.getPatientId())) {
            throw new BusinessException(ErrorCodeConstants.PEIS_REG_ENCOUNTER_ALREADY_ACTIVE, "已有在检记录");
        }
        // BR-PE-ANNUAL-01
        if (encounterQueryService.existsPeEncounterInCalendarYear(cmd.getPatientId(), java.time.Year.now().getValue())) {
            throw new BusinessException("PEIS_ANNUAL_DUPLICATE", "该学生本年度已有有效体检");
        }

        // Cmd：创建 PE 就诊
        RegisterPhysicalExamEncounterCommand registerCmd = new RegisterPhysicalExamEncounterCommand();
        registerCmd.setPatientId(cmd.getPatientId());
        registerCmd.setOrganizationId(cmd.getOrgId());
        registerCmd.setRegistrarId(cmd.getOperatorId());
        Long encounterId = encounterDomainService.registerPhysicalExamEncounter(registerCmd);
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounter(encounterId);

        // Asm：套餐快照
        List<Long> childActivityIds = new ArrayList<>();
        for (ActivityDefinitionChildQueryModel child : children) {
            if (child != null && child.getChildrenId() != null) {
                childActivityIds.add(child.getChildrenId());
            }
        }
        String examKind = resolveExamKind(cmd.getExamKind(), cmd.getPatientId());
        String snapshotJson = PeisPackageSnapshotSupport.build(
            cmd.getPackageActivityId(), childActivityIds, base.getName(), examKind);

        // Cmd：就诊扩展
        CreatePeEncounterExtCommand extCmd = new CreatePeEncounterExtCommand();
        extCmd.setEncounterId(encounterId);
        extCmd.setPackageActivityId(cmd.getPackageActivityId());
        extCmd.setPackageSnapshotJson(snapshotJson);
        extCmd.setGroupFlag(Boolean.FALSE);
        extCmd.setBookingAppointmentId(cmd.getBookingAppointmentId());
        extCmd.setBusNo(encounter.getBusNo());
        extCmd.setTenantId(TenantUtils.getCurrentTenantId());
        extCmd.setOrgId(cmd.getOrgId());
        peisEncounterExtDomainService.createForRegistration(extCmd);

        // Cmd：展开 SR + 初始化执行扩展
        List<Long> serviceRequestIds = new ArrayList<>();
        for (Long childActivityId : childActivityIds) {
            Long srId = createPeServiceRequestDraft(
                encounterId, cmd.getPatientId(), childActivityId, cmd.getOrgId(), cmd.getOperatorId());
            serviceRequestIds.add(srId);
        }
        if (!serviceRequestIds.isEmpty()) {
            InitPeisSrExtCommand initCmd = new InitPeisSrExtCommand();
            initCmd.setServiceRequestIds(serviceRequestIds);
            initCmd.setTenantId(TenantUtils.getCurrentTenantId());
            initCmd.setOrgId(cmd.getOrgId());
            peisServiceRequestExtDomainService.initForServiceRequests(initCmd);
        }

        // Post：登记成功事件
        domainEventPublisher.publish(new PeisEncounterRegisteredEvent(
            encounterId, cmd.getPatientId(), cmd.getPackageActivityId(), encounter.getBusNo()));

        RegisterPersonalCheckInResult result = new RegisterPersonalCheckInResult();
        result.setEncounterId(encounterId);
        result.setBusNo(encounter.getBusNo());
        result.setServiceRequestCount(serviceRequestIds.size());
        return result;
    }

    /**
     * 团检收费成功后建单：Encounter(PE)+ext.group_flag + 套餐 SR（进度待检）。
     *
     * @param cmd 团检建单命令
     * @return 建单结果
     */
    public RegisterPersonalCheckInResult registerGroupAfterPaid(RegisterGroupAfterPaidCommand cmd) {
        if (cmd == null || cmd.getPatientId() == null || cmd.getPackageActivityId() == null
            || cmd.getOrgId() == null || cmd.getOperatorId() == null || cmd.getCompanyId() == null) {
            throw new ValidationException("团检建单失败：患者、套餐、机构、单位与操作人不能为空");
        }
        if (patientQueryService.findOptionalPatientEntityById(cmd.getPatientId()).isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PEIS_REG_PATIENT_NOT_FOUND, "患者不存在");
        }
        if (encounterQueryService.existsActivePeEncounter(cmd.getPatientId())) {
            throw new BusinessException(ErrorCodeConstants.PEIS_REG_ENCOUNTER_ALREADY_ACTIVE, "已有在检记录");
        }
        if (encounterQueryService.existsPeEncounterInCalendarYear(cmd.getPatientId(), java.time.Year.now().getValue())) {
            throw new BusinessException("PEIS_ANNUAL_DUPLICATE", "该学生本年度已有有效体检");
        }

        ActivityDefinitionDetailQueryModel packageDetail =
            activityDefinitionQueryService.getServiceCatalog(cmd.getPackageActivityId());
        ActivityDefinitionBaseQueryModel base = packageDetail == null ? null : packageDetail.getBaseInfo();
        if (base == null || !isActivePublication(base.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.PEIS_REG_PACKAGE_INACTIVE, "套餐不存在或未启用");
        }
        List<ActivityDefinitionChildQueryModel> children =
            packageDetail.getChildren() == null ? List.of() : packageDetail.getChildren();
        if (children.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PEIS_REG_PACKAGE_INACTIVE, "套餐无有效子项");
        }

        RegisterPhysicalExamEncounterCommand registerCmd = new RegisterPhysicalExamEncounterCommand();
        registerCmd.setPatientId(cmd.getPatientId());
        registerCmd.setOrganizationId(cmd.getOrgId());
        registerCmd.setRegistrarId(cmd.getOperatorId());
        Long encounterId = encounterDomainService.registerPhysicalExamEncounter(registerCmd);
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounter(encounterId);

        List<Long> childActivityIds = new ArrayList<>();
        for (ActivityDefinitionChildQueryModel child : children) {
            if (child != null && child.getChildrenId() != null) {
                childActivityIds.add(child.getChildrenId());
            }
        }
        String examKind = resolveExamKind(cmd.getExamKind(), cmd.getPatientId());
        String snapshotJson = PeisPackageSnapshotSupport.build(
            cmd.getPackageActivityId(), childActivityIds, base.getName(), examKind);

        CreatePeEncounterExtCommand extCmd = new CreatePeEncounterExtCommand();
        extCmd.setEncounterId(encounterId);
        extCmd.setPackageActivityId(cmd.getPackageActivityId());
        extCmd.setPackageSnapshotJson(snapshotJson);
        extCmd.setGroupFlag(Boolean.TRUE);
        extCmd.setCompanyId(cmd.getCompanyId());
        extCmd.setContractId(cmd.getContractId());
        extCmd.setGroupBatchId(cmd.getGroupBatchId());
        extCmd.setBusNo(encounter.getBusNo());
        extCmd.setTenantId(TenantUtils.getCurrentTenantId());
        extCmd.setOrgId(cmd.getOrgId());
        peisEncounterExtDomainService.createForRegistration(extCmd);

        List<Long> serviceRequestIds = new ArrayList<>();
        for (Long childActivityId : childActivityIds) {
            serviceRequestIds.add(createPeServiceRequestDraft(
                encounterId, cmd.getPatientId(), childActivityId, cmd.getOrgId(), cmd.getOperatorId()));
        }
        if (!serviceRequestIds.isEmpty()) {
            InitPeisSrExtCommand initCmd = new InitPeisSrExtCommand();
            initCmd.setServiceRequestIds(serviceRequestIds);
            initCmd.setTenantId(TenantUtils.getCurrentTenantId());
            initCmd.setOrgId(cmd.getOrgId());
            peisServiceRequestExtDomainService.initForServiceRequests(initCmd);
        }

        domainEventPublisher.publish(new PeisEncounterRegisteredEvent(
            encounterId, cmd.getPatientId(), cmd.getPackageActivityId(), encounter.getBusNo()));

        RegisterPersonalCheckInResult result = new RegisterPersonalCheckInResult();
        result.setEncounterId(encounterId);
        result.setBusNo(encounter.getBusNo());
        result.setServiceRequestCount(serviceRequestIds.size());
        return result;
    }

    /**
     * 登记后加/减项并刷新套餐快照。
     *
     * @param cmd 调整命令
     */
    public void adjustServiceRequests(AdjustPeServiceRequestsCommand cmd) {
        // Gate：必填
        if (cmd == null || cmd.getEncounterId() == null || cmd.getOperatorId() == null) {
            throw new ValidationException("项目调整失败：就诊与操作人不能为空");
        }
        boolean hasAdd = cmd.getAddActivityIds() != null && !cmd.getAddActivityIds().isEmpty();
        boolean hasRemove =
            cmd.getRemoveServiceRequestIds() != null && !cmd.getRemoveServiceRequestIds().isEmpty();
        if (!hasAdd && !hasRemove) {
            throw new ValidationException("项目调整失败：加项或减项至少一项");
        }

        // Pre：体检就诊且在检
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            cmd.getEncounterId(),
            EncounterClass.PE.getCode(),
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT,
            "非体检就诊不可调整项目");
        if (!EncounterStatus.IN_PROGRESS.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_REG_ENCOUNTER_STATUS_INVALID, "就诊状态不允许调整项目");
        }

        PeisEncounterExtModel ext = peisEncounterExtQueryService.getByEncounterId(cmd.getEncounterId());
        Set<Long> removedSrIds = new HashSet<>();
        if (hasRemove) {
            for (Long srId : cmd.getRemoveServiceRequestIds()) {
                if (srId == null) {
                    continue;
                }
                PeisServiceRequestExtModel srExt =
                    peisServiceRequestExtQueryService.findByServiceRequestId(srId);
                if (srExt == null) {
                    throw new BusinessException(
                        ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "服务申请扩展不存在");
                }
                if (!PeisExecStatusConstants.PENDING.equals(srExt.getExecStatusEnum())) {
                    throw new BusinessException(
                        ErrorCodeConstants.PEIS_REG_SR_ALREADY_EXECUTED, "项目已执行不可减项");
                }
                CancelServiceRequestCommand cancelCmd = new CancelServiceRequestCommand();
                cancelCmd.setOperatorId(cmd.getOperatorId());
                CancelServiceRequestCommand.CancelServiceRequestItemCommand item =
                    new CancelServiceRequestCommand.CancelServiceRequestItemCommand();
                item.setId(srId);
                item.setCancelReason("体检减项");
                cancelCmd.setItem(item);
                serviceRequestDomainService.cancelServiceRequest(cancelCmd);
                removedSrIds.add(srId);
            }
        }

        List<Long> addedSrIds = new ArrayList<>();
        if (hasAdd) {
            for (Long activityId : cmd.getAddActivityIds()) {
                if (activityId == null) {
                    continue;
                }
                Long srId = createPeServiceRequestDraft(
                    cmd.getEncounterId(),
                    encounter.getPatientId(),
                    activityId,
                    encounter.getOrganizationId(),
                    cmd.getOperatorId());
                addedSrIds.add(srId);
            }
            if (!addedSrIds.isEmpty()) {
                InitPeisSrExtCommand initCmd = new InitPeisSrExtCommand();
                initCmd.setServiceRequestIds(addedSrIds);
                initCmd.setTenantId(TenantUtils.getCurrentTenantId());
                initCmd.setOrgId(encounter.getOrganizationId());
                peisServiceRequestExtDomainService.initForServiceRequests(initCmd);
            }
        }

        // Cmd：按剩余未取消 SR 刷新快照
        List<Long> remainingChildIds = new ArrayList<>();
        List<PeisServiceRequestExtModel> lines =
            peisServiceRequestExtQueryService.listByEncounterId(cmd.getEncounterId());
        for (PeisServiceRequestExtModel line : lines) {
            if (line == null || line.getServiceRequestId() == null || line.getActivityId() == null) {
                continue;
            }
            if (removedSrIds.contains(line.getServiceRequestId())) {
                continue;
            }
            ServiceRequestQueryModel sr =
                serviceRequestQueryService.mustGetServiceRequest(line.getServiceRequestId());
            if (sr != null && RequestStatus.CANCELLED.getCode().equals(sr.getStatusEnum())) {
                continue;
            }
            remainingChildIds.add(line.getActivityId());
        }

        String packageName = null;
        if (ext.getPackageActivityId() != null) {
            ActivityDefinitionDetailQueryModel pkg =
                activityDefinitionQueryService.getServiceCatalog(ext.getPackageActivityId());
            if (pkg != null && pkg.getBaseInfo() != null) {
                packageName = pkg.getBaseInfo().getName();
            }
        }
        String examKind = PeisPackageSnapshotSupport.extractExamKind(ext.getPackageSnapshotJson());
        UpdatePePackageSnapshotCommand snapCmd = new UpdatePePackageSnapshotCommand();
        snapCmd.setEncounterId(cmd.getEncounterId());
        snapCmd.setPackageActivityId(ext.getPackageActivityId());
        snapCmd.setPackageSnapshotJson(PeisPackageSnapshotSupport.build(
            ext.getPackageActivityId(), remainingChildIds, packageName, examKind));
        peisEncounterExtDomainService.updatePackageSnapshot(snapCmd);
    }

    /**
     * 复查项目确认：为原体检就诊追加复查 SR 与执行扩展（AS-PEIS-REC-001 REC-02）。
     *
     * <p>复用登记 createPeServiceRequestDraft + initForServiceRequests 口径；复查 SR 独立计费走 health Client。</p>
     *
     * @param encounterId  原体检就诊标识
     * @param activityIds  复查项目 AD 主键列表
     * @param operatorId   操作人标识
     * @return 新建服务申请主键列表
     */
    public List<Long> addRecheckServiceRequests(Long encounterId, List<Long> activityIds, Long operatorId) {
        if (encounterId == null || operatorId == null) {
            throw new ValidationException("复查加项失败：就诊与操作人不能为空");
        }
        if (activityIds == null || activityIds.isEmpty()) {
            throw new ValidationException("复查加项失败：请选择复查项目");
        }
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            encounterId,
            EncounterClass.PE.getCode(),
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT,
            "非体检就诊不可追加复查项目");
        List<Long> createdSrIds = new ArrayList<>();
        for (Long activityId : activityIds) {
            if (activityId == null) {
                continue;
            }
            Long srId = createPeServiceRequestDraft(
                encounterId,
                encounter.getPatientId(),
                activityId,
                encounter.getOrganizationId(),
                operatorId);
            createdSrIds.add(srId);
        }
        if (!createdSrIds.isEmpty()) {
            InitPeisSrExtCommand initCmd = new InitPeisSrExtCommand();
            initCmd.setServiceRequestIds(createdSrIds);
            initCmd.setTenantId(TenantUtils.getCurrentTenantId());
            initCmd.setOrgId(encounter.getOrganizationId());
            peisServiceRequestExtDomainService.initForServiceRequests(initCmd);
        }
        return createdSrIds;
    }

    private Long createPeServiceRequestDraft(
            Long encounterId, Long patientId, Long activityId, Long orgId, Long operatorId) {
        ActivityDefinitionDetailQueryModel detail =
            activityDefinitionQueryService.getServiceCatalog(activityId);
        ActivityDefinitionBaseQueryModel childBase = detail == null ? null : detail.getBaseInfo();
        String unitCode = "1";
        String typeEnum = null;
        Long performOrgId = orgId;
        if (childBase != null) {
            if (StringUtils.isNotEmpty(childBase.getPackageUnitCode())) {
                unitCode = childBase.getPackageUnitCode();
            } else if (StringUtils.isNotEmpty(childBase.getDefEncounterUnitCode())) {
                unitCode = childBase.getDefEncounterUnitCode();
            }
            typeEnum = childBase.getTypeEnum();
            if (childBase.getOrgId() != null) {
                performOrgId = childBase.getOrgId();
            }
        }

        CreateServiceRequestDraftCommand draftCmd = new CreateServiceRequestDraftCommand();
        draftCmd.setOperatorId(operatorId);
        CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand item =
            new CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand();
        item.setEncounterId(encounterId);
        item.setPatientId(patientId);
        item.setActivityId(activityId);
        item.setQuantity(BigDecimal.ONE);
        item.setUnitCode(unitCode);
        item.setExecutionCount(1);
        item.setTherapyEnum(TherapyType.TEMPORARY.getCode());
        item.setCategoryEnum(mapCategoryFromActivityType(typeEnum));
        item.setGenerateSourceEnum(GenerateSource.REGISTRATION_REGISTER.getCode());
        item.setOrgId(performOrgId);
        item.setRequesterId(operatorId);
        draftCmd.setItem(item);
        return serviceRequestDomainService.createServiceRequestDraft(draftCmd);
    }

    private static String mapCategoryFromActivityType(String typeEnum) {
        if (ActivityType.PROOF.getCode().equals(typeEnum)) {
            return ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROOF;
        }
        if (ActivityType.TEST.getCode().equals(typeEnum)) {
            return ServiceRequestCategoryConstants.SERVICE_CATEGORY_TEST;
        }
        return ServiceRequestCategoryConstants.SERVICE_CATEGORY_TREATMENT;
    }

    private static boolean isActivePublication(String statusEnum) {
        if (StringUtils.isEmpty(statusEnum)) {
            return false;
        }
        String normalized = statusEnum.trim();
        return PublicationStatus.ACTIVE.getCode().equalsIgnoreCase(normalized)
            || ObservationDefinitionConstants.STATUS_ACTIVE.equalsIgnoreCase(normalized);
    }

    /**
     * 解析体检类型：显式优先；否则 Campus 教职工→staff，学生首检→freshman，否则 annual；未知→other。
     */
    private String resolveExamKind(String requested, Long patientId) {
        String normalized = PeisExamKindConstants.normalizeOrNull(requested);
        if (normalized != null && !PeisExamKindConstants.OTHER.equals(normalized)) {
            return normalized;
        }
        PeisCampusIdentityPort port = peisCampusIdentityPort.getIfAvailable();
        if (port != null && patientId != null) {
            java.util.Optional<PeisCampusIdentityView> identity =
                port.resolveByPatientId(TenantUtils.getCurrentTenantId(), patientId);
            if (identity.isPresent()) {
                if ("STAFF".equals(identity.get().getSubjectType())) {
                    return PeisExamKindConstants.STAFF;
                }
                if ("STUDENT".equals(identity.get().getSubjectType())) {
                    return encounterQueryService.existsAnyNonCancelledPeEncounter(patientId)
                        ? PeisExamKindConstants.ANNUAL
                        : PeisExamKindConstants.FRESHMAN;
                }
            }
        }
        if (normalized != null) {
            return normalized;
        }
        return PeisExamKindConstants.OTHER;
    }
}
