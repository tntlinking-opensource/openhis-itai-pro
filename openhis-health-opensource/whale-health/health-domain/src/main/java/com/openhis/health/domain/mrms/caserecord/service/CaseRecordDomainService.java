/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.service;

import com.openhis.health.domain.mrms.caserecord.command.RefreshAutoFillCommand;
import com.openhis.health.domain.mrms.caserecord.command.SubmitCaseRecordCommand;
import com.openhis.health.domain.mrms.caserecord.command.TransitionCaseRecordCommand;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecordHomepage;
import com.openhis.health.domain.mrms.caserecord.entity.HomepageDiagnosis;
import com.openhis.health.domain.mrms.caserecord.command.HomepageSaveCommand;
import com.openhis.health.domain.mrms.caserecord.repository.CaseRecordHomepageRepository;
import com.openhis.health.domain.mrms.caserecord.repository.CaseRecordRepository;
import com.openhis.health.domain.mrms.caserecord.support.MedcasnoGenerator;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.health.domain.share.enums.CaseRecordStatus;
import com.openhis.health.domain.share.enums.CaseRecordTransitionAction;
import com.openhis.health.domain.share.enums.HomepageDiagType;
import com.openhis.health.domain.share.enums.WorkflowRole;
import com.openhis.health.domain.share.events.mrms.caserecord.CaseRecordArchivedEvent;
import com.openhis.health.domain.share.events.mrms.caserecord.CaseRecordCreatedEvent;
import com.openhis.health.domain.share.events.mrms.caserecord.CaseRecordReceivedEvent;
import com.openhis.health.domain.share.events.mrms.caserecord.CaseRecordReturnedEvent;
import com.openhis.health.domain.share.events.mrms.caserecord.CaseRecordSubmittedEvent;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案主档（{@code mrms_case_record}）聚合写侧领域服务。
 *
 * <p>职责：幂等建档、入院登记 bootstrap、首页保存/自动取值、医生提交、工作流状态迁移与领域事件发布。</p>
 * <p>不变量：状态迁移委托聚合根 {@link CaseRecord}；持久化先于 {@code publish} 领域事件。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseRecordDomainService {

    private final CaseRecordRepository caseRecordRepository;
    private final CaseRecordHomepageRepository homepageRepository;
    private final CaseRecordHomepageDomainService caseRecordHomepageDomainService;
    private final MedcasnoGenerator medcasnoGenerator;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 幂等建档：同租户同就诊已存在则直接返回。
     *
     * @param tenantId 租户
     * @param patientId 患者
     * @param encounterId 就诊
     * @param operatorId 操作人
     * @return 新建或已存在的病案主档
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecord createIfAbsent(Long tenantId, Long patientId, Long encounterId, Long operatorId) {
        // Pre：同就诊已建档则幂等返回
        Optional<CaseRecord> existing = caseRecordRepository
                .findByTenantIdAndEncounterIdAndIsDeletedFalse(tenantId, encounterId);
        if (existing.isPresent()) {
            return existing.get();
        }
        // Cmd：初始化聚合根并生成病案号与住院次
        CaseRecord record = new CaseRecord();
        long iptCnt = caseRecordRepository.countByTenantIdAndPatientIdAndIsDeletedFalse(tenantId, patientId);
        record.initNewRecord(
                tenantId,
                patientId,
                encounterId,
                medcasnoGenerator.nextMedcasno(tenantId),
                (int) iptCnt + 1,
                operatorId);
        // 持久化先于事件发布
        CaseRecord saved = caseRecordRepository.save(record);
        // 发布建档事件供 bootstrap/质控订阅
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
     * 入院登记 bootstrap：幂等建档 + 懒创建首页；新建时全量 HIS 自动取值并刷新列表冗余。
     *
     * <p>主触发：住院登记提交 afterCommit；出院医嘱/首页打开仅作历史就诊幂等兜底。</p>
     *
     * @param tenantId 租户
     * @param patientId 患者
     * @param encounterId 就诊
     * @param operatorId 操作人
     * @return 病案主档主键
     */
    @Transactional(rollbackFor = Exception.class)
    public Long bootstrapIfAbsent(Long tenantId, Long patientId, Long encounterId, Long operatorId) {
        // Pre：判断是否新建（createIfAbsent 幂等返回后无法再区分）
        boolean isNew = findByEncounterId(tenantId, encounterId).isEmpty();
        CaseRecord record = createIfAbsent(tenantId, patientId, encounterId, operatorId);
        // Cmd：懒创建首页
        caseRecordHomepageDomainService.ensureHomepage(record.getId(), record.getTenantId(), operatorId);
        // 新建病案时全量自动取值（失败不阻断 bootstrap）
        if (isNew) {
            runBootstrapAutoFill(record, operatorId);
        }
        return record.getId();
    }

    /**
     * 按主键加载病案主档，不存在时抛 {@link EntityNotFoundException}。
     *
     * @param caseRecordId 病案主档主键
     * @return 未软删的病案主档
     */
    public CaseRecord requireById(Long caseRecordId) {
        return caseRecordRepository.findByIdAndIsDeletedFalse(caseRecordId)
                .orElseThrow(() -> new EntityNotFoundException("病案主档", caseRecordId));
    }

    /**
     * 按租户与就诊查找病案主档（幂等建档判重）。
     *
     * @param tenantId 租户
     * @param encounterId 就诊
     * @return 存在则 Optional 含实体，否则 empty
     */
    public Optional<CaseRecord> findByEncounterId(Long tenantId, Long encounterId) {
        return caseRecordRepository.findByTenantIdAndEncounterIdAndIsDeletedFalse(tenantId, encounterId);
    }

    /**
     * 医生保存首页：校验可编辑后委托首页领域服务并刷新主档列表冗余。
     *
     * @param command 首页保存命令
     * @return 保存后的首页
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordHomepage saveHomepage(HomepageSaveCommand command) {
        // Pre：加载主档并断言医生可编辑（draft/returned；归档冻结后不可改）
        CaseRecord record = requireById(command.getCaseRecordId());
        record.assertEditableByDoctor();
        // Cmd：首页写侧保存
        CaseRecordHomepage homepage = caseRecordHomepageDomainService.save(command);
        // 持久化主档冗余摘要
        refreshSummary(record, homepage);
        return homepage;
    }

    /**
     * 从 HIS 快照刷新首页指定分区并同步主档冗余。
     *
     * @param command 含 caseRecordId、sections、forceOverwrite
     * @return 合并后的首页
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordHomepage refreshAutoFill(RefreshAutoFillCommand command) {
        // Pre：主档与首页须处于医生可编辑态（未归档冻结）
        CaseRecord record = requireById(command.getCaseRecordId());
        record.assertEditableByDoctor();
        CaseRecordHomepage homepage = requireHomepage(command.getCaseRecordId());
        homepage.assertEditable();
        // Cmd：跨聚合读 HIS 并合并到首页
        CaseRecordHomepage merged = caseRecordHomepageDomainService.mergeAutoFillFromHis(
                record,
                command.getSections(),
                command.isForceOverwrite(),
                command.getOperatorId());
        refreshSummary(record, merged);
        return merged;
    }

    /**
     * 医生提交病案：校验首页完备后 draft/returned → submitted。
     *
     * @param command 含 caseRecordId 与 operatorId
     * @return 提交后的主档
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecord submit(SubmitCaseRecordCommand command) {
        // Pre：主档可编辑且首页满足提交校验
        CaseRecord record = requireById(command.getCaseRecordId());
        record.assertEditableByDoctor();
        CaseRecordHomepage homepage = requireHomepage(command.getCaseRecordId());
        List<HomepageDiagnosis> diagnoses = caseRecordHomepageDomainService.listDiagnoses(command.getCaseRecordId());
        homepage.assertReadyToSubmit(diagnoses);
        // Cmd：聚合根状态迁移
        record.applyTransition(CaseRecordTransitionAction.SUBMIT, WorkflowRole.DOCTOR, null, command.getOperatorId());
        // 持久化先于事件发布
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
     * 病案室工作流状态迁移（接收/退回/质控/归档/解档）。
     *
     * @param command 迁移动作、角色、原因与操作人
     * @return 迁移后的主档
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecord transition(TransitionCaseRecordCommand command) {
        // Pre：加载主档
        CaseRecord record = requireById(command.getCaseRecordId());
        // 解档动作使用 unarchiveReason，其余使用 returnReason
        String reason = command.getReturnReason();
        if (command.getAction() == CaseRecordTransitionAction.UNARCHIVE) {
            reason = command.getUnarchiveReason();
        }
        record.applyTransition(
                command.getAction(),
                command.getRole(),
                reason,
                command.getOperatorId());
        // 归档：写入归档元数据并冻结首页
        if (command.getAction() == CaseRecordTransitionAction.ARCHIVE) {
            record.applyArchiveMetadata(command.getOperatorId(), command.getQualityGradeCode());
            caseRecordHomepageDomainService.freeze(command.getCaseRecordId(), command.getOperatorId());
        }
        // 解档：清除归档元数据并解冻首页
        if (command.getAction() == CaseRecordTransitionAction.UNARCHIVE) {
            record.applyUnarchiveMetadata(command.getUnarchiveReason(), command.getOperatorId());
            caseRecordHomepageDomainService.unfreeze(command.getCaseRecordId(), command.getOperatorId());
        }
        // 持久化先于事件发布
        CaseRecord saved = caseRecordRepository.save(record);
        publishTransitionEvent(saved, command);
        return saved;
    }

    /**
     * 从首页刷新主档列表冗余字段并持久化。
     *
     * @param record 病案主档
     * @param homepage 首页
     * @return 保存后的主档
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecord refreshSummary(CaseRecord record, CaseRecordHomepage homepage) {
        // Cmd：从首页冗余字段回写主档列表摘要（主诊断名由读侧解析）
        record.refreshSummaryFrom(homepage, resolveMainDiagName(record.getId()));
        // 持久化主档冗余列
        return caseRecordRepository.save(record);
    }

    /**
     * 按迁移动作发布对应领域事件（接收/退回/归档）。
     *
     * @param saved 已持久化的主档
     * @param command 迁移动作与操作人
     */
    private void publishTransitionEvent(CaseRecord saved, TransitionCaseRecordCommand command) {
        // 接收：通知下游开始病案室流程
        if (command.getAction() == CaseRecordTransitionAction.RECEIVE) {
            domainEventPublisher.publish(new CaseRecordReceivedEvent(
                    saved.getId(),
                    saved.getTenantId(),
                    command.getOperatorId(),
                    saved.getEncounterId()));
            return;
        }
        // 退回：携带退回原因供医生端展示
        if (command.getAction() == CaseRecordTransitionAction.RETURN) {
            domainEventPublisher.publish(new CaseRecordReturnedEvent(
                    saved.getId(),
                    saved.getTenantId(),
                    command.getOperatorId(),
                    saved.getEncounterId(),
                    saved.getReturnReason()));
            return;
        }
        // 归档：携带患者与归档时间供 4401/统计订阅
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

    /**
     * 4401 成功后隐性推进：将病案推到已归档并冻结首页（自动管理病案，用户无感）。
     *
     * <p>按状态机逐步 SUBMIT→RECEIVE→START_QC→ARCHIVE；已归档仅补冻结。任一步失败抛业务异常。</p>
     *
     * @param caseRecordId 病案主档主键
     * @param operatorId 操作人（与现有迁移口径一致）
     * @return 推进后的主档
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecord ensureArchivedAndFrozenForUpload(Long caseRecordId, Long operatorId) {
        CaseRecord record = requireById(caseRecordId);
        // 已归档：仅确保首页冻结
        if (record.getStatusEnum() == CaseRecordStatus.ARCHIVED) {
            caseRecordHomepageDomainService.freeze(caseRecordId, operatorId);
            return requireById(caseRecordId);
        }
        // draft/returned → 医生提交
        if (record.getStatusEnum() == CaseRecordStatus.DRAFT
                || record.getStatusEnum() == CaseRecordStatus.RETURNED) {
            SubmitCaseRecordCommand submit = new SubmitCaseRecordCommand();
            submit.setCaseRecordId(caseRecordId);
            submit.setOperatorId(operatorId);
            submit(submit);
            record = requireById(caseRecordId);
        }
        // submitted → 病案室接收
        if (record.getStatusEnum() == CaseRecordStatus.SUBMITTED) {
            runRoomTransition(caseRecordId, CaseRecordTransitionAction.RECEIVE, operatorId);
            record = requireById(caseRecordId);
        }
        // received → 开始质控
        if (record.getStatusEnum() == CaseRecordStatus.RECEIVED) {
            runRoomTransition(caseRecordId, CaseRecordTransitionAction.START_QC, operatorId);
            record = requireById(caseRecordId);
        }
        // qcing → 归档（同步冻结）
        if (record.getStatusEnum() == CaseRecordStatus.QCING) {
            runRoomTransition(caseRecordId, CaseRecordTransitionAction.ARCHIVE, operatorId);
            record = requireById(caseRecordId);
        }
        // 终态仍非归档则阻断（避免半吊子）
        if (record.getStatusEnum() != CaseRecordStatus.ARCHIVED) {
            throw new BusinessException(
                    MrmsErrorCodes.UPLOAD_PRECONDITION,
                    "隐性推进后病案仍未归档，当前状态=" + record.getStatusCode());
        }
        return record;
    }

    /**
     * 以病案室角色执行单步状态迁移。
     *
     * @param caseRecordId 病案主键
     * @param action 迁移动作
     * @param operatorId 操作人
     */
    private void runRoomTransition(Long caseRecordId, CaseRecordTransitionAction action, Long operatorId) {
        TransitionCaseRecordCommand command = new TransitionCaseRecordCommand();
        command.setCaseRecordId(caseRecordId);
        command.setAction(action);
        command.setRole(WorkflowRole.RECORD_ROOM);
        command.setOperatorId(operatorId);
        transition(command);
    }

    /**
     * 加载首页，不存在时抛业务异常。
     *
     * @param caseRecordId 病案主档主键
     * @return 首页实体
     */
    private CaseRecordHomepage requireHomepage(Long caseRecordId) {
        return homepageRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId)
                .orElseThrow(() -> new BusinessException(MrmsErrorCodes.CASE_RECORD_NOT_FOUND, "病案首页不存在"));
    }

    /** 解析出院主诊断名称，供主档冗余字段 {@code main_diag_name}。 */
    private String resolveMainDiagName(Long caseRecordId) {
        // 遍历诊断子表，取 discharge_main 类型首条
        for (HomepageDiagnosis row : caseRecordHomepageDomainService.listDiagnoses(caseRecordId)) {
            if (HomepageDiagType.DISCHARGE_MAIN.getCode().equals(row.getDiagTypeCode())) {
                return trimToNull(row.getDiagName());
            }
        }
        return null;
    }

    /**
     * 建档后全量自动取值；失败仅打日志不阻断建档。
     *
     * @param record     刚建或已存在的主档
     * @param operatorId 操作人
     */
    private void runBootstrapAutoFill(CaseRecord record, Long operatorId) {
        try {
            RefreshAutoFillCommand command = new RefreshAutoFillCommand();
            command.setCaseRecordId(record.getId());
            command.setSections(List.of("all"));
            command.setForceOverwrite(false);
            command.setOperatorId(operatorId);
            refreshAutoFill(command);
        } catch (Exception ex) {
            log.warn("建档自动取值失败 caseRecordId={} encounterId={}",
                    record.getId(), record.getEncounterId(), ex);
        }
    }
}
