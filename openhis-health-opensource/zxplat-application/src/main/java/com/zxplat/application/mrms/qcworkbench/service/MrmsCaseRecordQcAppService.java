/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.qcworkbench.service;

import com.zxplat.application.mrms.caserecord.homepage.dto.CaseRecordDetailDto;
import com.zxplat.application.mrms.caserecord.homepage.mapper.CaseRecordDetailMapper;
import com.zxplat.application.mrms.qcworkbench.dto.QcDefectDto;
import com.zxplat.application.mrms.qcworkbench.dto.QcRunResultDto;
import com.zxplat.application.mrms.qcworkbench.dto.QcRunSummaryDto;
import com.zxplat.application.mrms.qcworkbench.mapper.QcMapper;
import com.zxplat.application.mrms.qcworkbench.support.CaseRecordOverdueScanOrchestrator;
import com.zxplat.application.mrms.qcworkbench.request.AddManualDefectRequest;
import com.zxplat.application.mrms.qcworkbench.request.ArchiveCaseRecordRequest;
import com.zxplat.application.mrms.qcworkbench.request.DefectActionRequest;
import com.zxplat.application.mrms.qcworkbench.request.ListDefectsRequest;
import com.zxplat.application.mrms.qcworkbench.request.ListQcRunsRequest;
import com.zxplat.application.mrms.qcworkbench.request.RerunQcRequest;
import com.zxplat.application.mrms.qcworkbench.request.StartQcRequest;
import com.zxplat.application.mrms.qcworkbench.request.UnarchiveCaseRecordRequest;
import com.zxplat.domain.mrms.caserecord.command.TransitionCaseRecordCommand;
import com.zxplat.domain.mrms.caserecord.entity.CaseRecord;
import com.zxplat.domain.mrms.caserecord.service.CaseRecordDomainService;
import com.zxplat.domain.mrms.qc.entity.QcDefect;
import com.zxplat.domain.mrms.qc.entity.QcRun;
import com.zxplat.domain.mrms.qc.executor.QcRuleExecutor;
import com.zxplat.domain.mrms.qc.model.QcRunResult;
import com.zxplat.domain.mrms.qc.service.QcDefectDomainService;
import com.zxplat.domain.share.enums.CaseRecordTransitionAction;
import com.zxplat.domain.share.enums.QcTrigger;
import com.zxplat.domain.share.enums.WorkflowRole;
import com.zxplat.domain.infrastructure.integration.emr.EmrDocumentExtension;
import com.zxplat.domain.infrastructure.integration.noop.IntegrationAfterCommitSupport;
import com.zxplat.application.shared.validator.MrmsOperatorResolver;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.infrastructure.job.SchedulableJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 病案质控与归档应用服务（IF-MR-Qc）。
 *
 * <p>路由前缀 {@code POST /api/app/mrmsCaseRecordQc/*}。负责质控启动/重跑、缺陷处置、
 * 归档/解档、归档后 EMR 文书锁定及逾期扫描定时任务。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MrmsCaseRecordQcAppService {

    private final CaseRecordDomainService caseRecordDomainService;
    private final QcRuleExecutor qcRuleExecutor;
    private final QcDefectDomainService qcDefectDomainService;
    private final CaseRecordDetailMapper caseRecordDetailMapper;
    private final QcMapper qcMapper;
    private final MrmsOperatorResolver operatorResolver;
    private final EmrDocumentExtension emrDocumentExtension;
    private final CaseRecordOverdueScanOrchestrator caseRecordOverdueScanOrchestrator;

    /**
     * 开始质控（本期未开放：质控已从主档归档状态拆出）。
     *
     * @param request 含病案主键
     * @return 不返回；始终拒绝
     */
    @Transactional(rollbackFor = Exception.class)
    public QcRunResultDto startQc(StartQcRequest request) {
        throw new ValidationException("质控尚未开放：主档 status 仅承载归档三态，质控以后另开");
    }

    /**
     * 手动重跑质控规则，不迁移主档状态。
     *
     * @param request 含病案主键
     * @return 质控批次结果
     */
    @Transactional(rollbackFor = Exception.class)
    public QcRunResultDto rerunQc(RerunQcRequest request) {
        // Gate：病案须存在
        Long caseRecordId = requireCaseRecordId(request.getCaseRecordId());
        Long operatorId = operatorResolver.requireOperatorId();
        caseRecordDomainService.requireById(caseRecordId);
        // Cmd：MANUAL 触发规则执行
        QcRunResult result = qcRuleExecutor.execute(caseRecordId, QcTrigger.MANUAL, operatorId);
        // Asm：映射结果 DTO
        CaseRecord record = caseRecordDomainService.requireById(caseRecordId);
        return qcMapper.toRunResultDto(result, record, caseRecordDetailMapper);
    }

    /**
     * 查询病案质控执行批次历史。
     *
     * @param request 含病案主键
     * @return 批次摘要列表
     */
    public List<QcRunSummaryDto> listQcRuns(ListQcRunsRequest request) {
        // Gate：病案须存在
        Long caseRecordId = requireCaseRecordId(request.getCaseRecordId());
        caseRecordDomainService.requireById(caseRecordId);
        // Pre：读缺陷域批次列表
        List<QcRun> runs = qcDefectDomainService.listRuns(caseRecordId);
        // Asm：映射摘要 DTO
        return qcMapper.toRunSummaryDtos(runs);
    }

    /**
     * 查询病案质控缺陷列表，可按状态/严重级别过滤。
     *
     * @param request 过滤条件
     * @return 缺陷 DTO 列表
     */
    public List<QcDefectDto> listDefects(ListDefectsRequest request) {
        // Gate：病案主键必填
        Long caseRecordId = requireCaseRecordId(request.getCaseRecordId());
        // Pre：读开放/历史缺陷
        List<QcDefect> defects = qcDefectDomainService.listDefects(
                caseRecordId, request.getStatusCode(), request.getSeverityCode());
        // Asm：映射缺陷 DTO
        return qcMapper.toDefectDtos(defects);
    }

    /**
     * 临床侧标记缺陷已整改。
     *
     * @param request 含缺陷主键
     * @return 更新后缺陷 DTO
     */
    @Transactional(rollbackFor = Exception.class)
    public QcDefectDto markRectified(DefectActionRequest request) {
        // Gate：缺陷主键必填
        if (request.getDefectId() == null) {
            throw new ValidationException("缺陷主键不能为空");
        }
        // Cmd：领域标记整改
        QcDefect defect = qcDefectDomainService.markRectified(
                request.getDefectId(), operatorResolver.requireOperatorId());
        // Asm：映射 DTO
        return qcMapper.toDefectDto(defect);
    }

    /**
     * 病案室关闭缺陷（整改验收通过）。
     *
     * @param request 含缺陷主键
     * @return 更新后缺陷 DTO
     */
    @Transactional(rollbackFor = Exception.class)
    public QcDefectDto closeDefect(DefectActionRequest request) {
        // Gate：缺陷主键必填
        if (request.getDefectId() == null) {
            throw new ValidationException("缺陷主键不能为空");
        }
        // Cmd：领域关闭缺陷
        QcDefect defect = qcDefectDomainService.closeDefect(
                request.getDefectId(), operatorResolver.requireOperatorId());
        // Asm：映射 DTO
        return qcMapper.toDefectDto(defect);
    }

    /**
     * 病案室登记手工缺陷（无自动规则码）。
     *
     * @param request 缺陷描述与严重级别
     * @return 新建缺陷 DTO
     */
    @Transactional(rollbackFor = Exception.class)
    public QcDefectDto addManualDefect(AddManualDefectRequest request) {
        // Gate：病案、描述、严重级别必填
        Long caseRecordId = requireCaseRecordId(request.getCaseRecordId());
        if (trimToNull(request.getDefectMessage()) == null) {
            throw new ValidationException("缺陷描述不能为空");
        }
        if (trimToNull(request.getSeverityCode()) == null) {
            throw new ValidationException("严重级别不能为空");
        }
        CaseRecord record = caseRecordDomainService.requireById(caseRecordId);
        // Cmd：领域写入手工缺陷
        QcDefect defect = qcDefectDomainService.addManualDefect(
                caseRecordId,
                record.getTenantId(),
                request.getSeverityCode(),
                request.getDefectMessage(),
                operatorResolver.requireOperatorId());
        // Asm：映射 DTO
        return qcMapper.toDefectDto(defect);
    }

    /**
     * 病案室归档入口：仅允许 {@code PENDING_ARCHIVE} → {@code ARCHIVED}（质控前置已拆出）。
     *
     * @param request 含病案主键与质控等级
     * @return 归档后主档详情
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordDetailDto archive(ArchiveCaseRecordRequest request) {
        // Gate：病案主键与操作人
        Long caseRecordId = requireCaseRecordId(request.getCaseRecordId());
        Long operatorId = operatorResolver.requireOperatorId();
        // Cmd：走归档三态同意（不再要求 QCING）
        CaseRecord record = caseRecordDomainService.approveArchive(
                caseRecordId, operatorId, trimToNull(request.getQualityGradeCode()));
        // 提交后锁定 EMR，避免事务内调外部模块
        scheduleEmrLockAfterCommit(record);
        // Asm：映射主档详情
        return caseRecordDetailMapper.toDetail(record);
    }

    /**
     * 归档成功后于事务提交后锁定就诊下全部 EMR 文书。
     *
     * @param record 已归档病案
     */
    private void scheduleEmrLockAfterCommit(CaseRecord record) {
        // EMR 未启用或无就诊时不调度锁定
        if (!emrDocumentExtension.isEnabled() || record.getEncounterId() == null) {
            return;
        }
        Long caseRecordId = record.getId();
        Long encounterId = record.getEncounterId();
        IntegrationAfterCommitSupport.runAfterCommit(() -> {
            try {
                emrDocumentExtension.lockAllByEncounter(encounterId);
            } catch (Exception ex) {
                log.error("病案归档后锁定 EMR 文书失败 caseRecordId={} encounterId={}",
                        caseRecordId, encounterId, ex);
            }
        });
    }

    /**
     * 解档（已禁止）。
     *
     * @param request 含解档原因
     * @return 不返回
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordDetailDto unarchive(UnarchiveCaseRecordRequest request) {
        throw new ValidationException("已禁止解档；归档后修改请走编辑申请");
    }

    /**
     * 病案室角色下的状态迁移封装。
     *
     * @param caseRecordId     病案主键
     * @param action           迁移动作
     * @param operatorId       操作人
     * @param qualityGradeCode 归档质控等级，可为 null
     * @return 迁移后主档
     */
    private CaseRecord transition(
            Long caseRecordId,
            CaseRecordTransitionAction action,
            Long operatorId,
            String qualityGradeCode) {
        TransitionCaseRecordCommand command = new TransitionCaseRecordCommand();
        command.setCaseRecordId(caseRecordId);
        command.setAction(action);
        command.setRole(WorkflowRole.RECORD_ROOM);
        command.setQualityGradeCode(qualityGradeCode);
        command.setOperatorId(operatorId);
        return caseRecordDomainService.transition(command);
    }

    /**
     * 校验病案主键非空。
     *
     * @param caseRecordId 病案主档主键
     * @return 非 null 主键
     */
    private Long requireCaseRecordId(Long caseRecordId) {
        if (caseRecordId == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        return caseRecordId;
    }

    /**
     * 病案提交/归档逾期扫描（系统调度，无 UI 按钮）。
     *
     * <p>处理器 key {@code mrmsCaseRecordQc/scanOverdueCaseRecords}；
     * cron 与启停由 zxplat-job 管理界面配置，默认建议 {@code 0 0 8 * * ?}。</p>
     */
    @SchedulableJob("病案逾期扫描")
    public void scanOverdueCaseRecords() {
        caseRecordOverdueScanOrchestrator.scanAll();
    }
}
