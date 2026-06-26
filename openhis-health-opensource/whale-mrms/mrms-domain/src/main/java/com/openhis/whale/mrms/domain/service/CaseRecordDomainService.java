/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.service;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.mrms.domain.command.RefreshAutoFillCommand;
import com.openhis.whale.mrms.domain.command.SubmitCaseRecordCommand;
import com.openhis.whale.mrms.domain.command.TransitionCaseRecordCommand;
import com.openhis.whale.mrms.domain.entity.CaseRecord;
import com.openhis.whale.mrms.domain.entity.CaseRecordHomepage;
import com.openhis.whale.mrms.domain.entity.HomepageDiagnosis;
import com.openhis.whale.mrms.domain.enums.CaseRecordTransitionAction;
import com.openhis.whale.mrms.domain.enums.HomepageDiagType;
import com.openhis.whale.mrms.domain.enums.WorkflowRole;
import com.openhis.whale.mrms.domain.model.HomepageSaveCommand;
import com.openhis.whale.mrms.domain.repository.CaseRecordHomepageRepository;
import com.openhis.whale.mrms.domain.repository.CaseRecordRepository;
import com.openhis.whale.mrms.domain.support.HomepageChildTableSupport;
import com.openhis.whale.mrms.domain.support.MrmsErrorCodes;
import com.openhis.whale.mrms.event.CaseRecordArchivedEvent;
import com.openhis.whale.mrms.event.CaseRecordCreatedEvent;
import com.openhis.whale.mrms.event.CaseRecordReceivedEvent;
import com.openhis.whale.mrms.event.CaseRecordReturnedEvent;
import com.openhis.whale.mrms.event.CaseRecordSubmittedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案主档聚合写侧领域服务：建档、状态迁移、摘要冗余刷新、提交。
 *
 * <p>不负责：应用层参数门禁、列表分页查询、展示 DTO 组装。</p>
 */
@Service
@RequiredArgsConstructor
public class CaseRecordDomainService {

    private final CaseRecordRepository caseRecordRepository;
    private final CaseRecordHomepageRepository homepageRepository;
    private final CaseRecordHomepageDomainService caseRecordHomepageDomainService;
    private final HomepageAutoFillMerger homepageAutoFillMerger;
    private final HomepageChildTableSupport childTableSupport;
    private final MedcasnoGenerator medcasnoGenerator;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 幂等创建病案主档（同一 encounter 仅一条有效记录）。
     *
     * @param tenantId 租户
     * @param patientId 患者
     * @param encounterId 就诊
     * @param operatorId 操作人
     * @return 主档（新建或已存在）
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecord createIfAbsent(Long tenantId, Long patientId, Long encounterId, Long operatorId) {
        Optional<CaseRecord> existing = caseRecordRepository
                .findByTenantIdAndEncounterIdAndIsDeletedFalse(tenantId, encounterId);
        if (existing.isPresent()) {
            return existing.get();
        }
        CaseRecord record = new CaseRecord();
        long iptCnt = caseRecordRepository.countByTenantIdAndPatientIdAndIsDeletedFalse(tenantId, patientId);
        record.initNewRecord(
                tenantId,
                patientId,
                encounterId,
                medcasnoGenerator.nextMedcasno(tenantId),
                (int) iptCnt + 1,
                operatorId);
        CaseRecord saved = caseRecordRepository.save(record);
        domainEventPublisher.publish(new CaseRecordCreatedEvent(
                saved.getId(),
                saved.getTenantId(),
                operatorId,
                saved.getPatientId(),
                saved.getEncounterId(),
                saved.getMedcasno()));
        return saved;
    }

    /**
     * 按主键加载有效主档。
     *
     * @param caseRecordId 主档主键
     * @return 主档
     */
    public CaseRecord requireById(Long caseRecordId) {
        return caseRecordRepository.findByIdAndIsDeletedFalse(caseRecordId)
                .orElseThrow(() -> new EntityNotFoundException("病案主档", caseRecordId));
    }

    /**
     * 按就诊查有效主档。
     *
     * @param tenantId 租户
     * @param encounterId 就诊
     * @return 主档
     */
    public Optional<CaseRecord> findByEncounterId(Long tenantId, Long encounterId) {
        return caseRecordRepository.findByTenantIdAndEncounterIdAndIsDeletedFalse(tenantId, encounterId);
    }

    /**
     * 医生保存首页并刷新主档列表冗余。
     *
     * @param command 保存命令
     * @return 保存后首页
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordHomepage saveHomepage(HomepageSaveCommand command) {
        CaseRecord record = requireById(command.getCaseRecordId());
        record.assertEditableByDoctor();
        CaseRecordHomepage homepage = caseRecordHomepageDomainService.save(command);
        refreshSummary(record, homepage);
        return homepage;
    }

    /**
     * 按段刷新 HIS 自动取值并刷新主档列表冗余。
     *
     * @param command 刷新命令
     * @return 刷新后首页
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordHomepage refreshAutoFill(RefreshAutoFillCommand command) {
        CaseRecord record = requireById(command.getCaseRecordId());
        record.assertEditableByDoctor();
        CaseRecordHomepage homepage = requireHomepage(command.getCaseRecordId());
        homepage.assertEditable();
        CaseRecordHomepage merged = homepageAutoFillMerger.merge(
                record,
                command.getSections(),
                command.isForceOverwrite(),
                command.getOperatorId());
        refreshSummary(record, merged);
        return merged;
    }

    /**
     * 医生提交首页至病案室（含提交前领域校验）。
     *
     * @param command 提交命令
     * @return 更新后主档
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecord submit(SubmitCaseRecordCommand command) {
        CaseRecord record = requireById(command.getCaseRecordId());
        record.assertEditableByDoctor();
        CaseRecordHomepage homepage = requireHomepage(command.getCaseRecordId());
        List<HomepageDiagnosis> diagnoses = childTableSupport.listDiagnoses(command.getCaseRecordId());
        homepage.assertReadyToSubmit(diagnoses);
        record.applyTransition(CaseRecordTransitionAction.SUBMIT, WorkflowRole.DOCTOR, null, command.getOperatorId());
        CaseRecord saved = caseRecordRepository.save(record);
        domainEventPublisher.publish(new CaseRecordSubmittedEvent(
                saved.getId(),
                saved.getTenantId(),
                command.getOperatorId(),
                saved.getEncounterId(),
                saved.getMedcasno()));
        return saved;
    }

    /**
     * 执行状态迁移。
     *
     * @param command 迁移命令
     * @return 更新后主档
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecord transition(TransitionCaseRecordCommand command) {
        CaseRecord record = requireById(command.getCaseRecordId());
        record.applyTransition(
                command.getAction(),
                command.getRole(),
                command.getReturnReason(),
                command.getOperatorId());
        if (command.getAction() == CaseRecordTransitionAction.ARCHIVE) {
            record.applyArchiveMetadata(command.getOperatorId(), command.getQualityGradeCode());
            caseRecordHomepageDomainService.freeze(command.getCaseRecordId(), command.getOperatorId());
        }
        CaseRecord saved = caseRecordRepository.save(record);
        publishTransitionEvent(saved, command);
        return saved;
    }

    /**
     * 从首页刷新列表冗余摘要字段。
     *
     * @param record 主档
     * @param homepage 首页
     * @return 更新后主档
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecord refreshSummary(CaseRecord record, CaseRecordHomepage homepage) {
        record.refreshSummaryFrom(homepage, resolveMainDiagName(record.getId()));
        return caseRecordRepository.save(record);
    }

    private void publishTransitionEvent(CaseRecord saved, TransitionCaseRecordCommand command) {
        if (command.getAction() == CaseRecordTransitionAction.RECEIVE) {
            domainEventPublisher.publish(new CaseRecordReceivedEvent(
                    saved.getId(),
                    saved.getTenantId(),
                    command.getOperatorId(),
                    saved.getEncounterId()));
            return;
        }
        if (command.getAction() == CaseRecordTransitionAction.RETURN) {
            domainEventPublisher.publish(new CaseRecordReturnedEvent(
                    saved.getId(),
                    saved.getTenantId(),
                    command.getOperatorId(),
                    saved.getEncounterId(),
                    saved.getReturnReason()));
            return;
        }
        if (command.getAction() == CaseRecordTransitionAction.ARCHIVE) {
            domainEventPublisher.publish(new CaseRecordArchivedEvent(
                    saved.getId(),
                    saved.getTenantId(),
                    command.getOperatorId(),
                    saved.getEncounterId(),
                    saved.getPatientId(),
                    saved.getArchivedAt()));
        }
    }

    private CaseRecordHomepage requireHomepage(Long caseRecordId) {
        return homepageRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId)
                .orElseThrow(() -> new BusinessException(MrmsErrorCodes.CASE_RECORD_NOT_FOUND, "病案首页不存在"));
    }

    private String resolveMainDiagName(Long caseRecordId) {
        for (HomepageDiagnosis row : childTableSupport.listDiagnoses(caseRecordId)) {
            if (HomepageDiagType.DISCHARGE_MAIN.getCode().equals(row.getDiagTypeCode())) {
                return trimToNull(row.getDiagName());
            }
        }
        return null;
    }
}
