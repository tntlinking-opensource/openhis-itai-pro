/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.qc.service;

import com.openhis.health.domain.mrms.qc.entity.QcDefect;
import com.openhis.health.domain.mrms.qc.entity.QcRun;
import com.openhis.health.domain.mrms.qc.repository.QcDefectRepository;
import com.openhis.health.domain.mrms.qc.repository.QcRunRepository;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.health.domain.share.enums.QcDefectSeverity;
import com.openhis.health.domain.share.enums.QcDefectStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 质控缺陷与执行批次（{@code mrms_qc_defect} / {@code mrms_qc_run}）写侧领域服务。
 *
 * <p>职责：缺陷登记/整改/关闭、规则命中 upsert、归档前阻断级断言及质控批次汇总。</p>
 * <p>不变量：归档前不得存在 open/rectified 阻断级缺陷（BR-QC-ARCHIVE）；持久化先于下游订阅。</p>
 * <p>仅注入本聚合 {@link QcDefectRepository}、{@link QcRunRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class QcDefectDomainService {

    private final QcDefectRepository qcDefectRepository;
    private final QcRunRepository qcRunRepository;

    /**
     * 按病案列出缺陷，可按状态/严重级别内存过滤（调用量受单病案缺陷数限制）。
     *
     * @param caseRecordId 病案主档主键
     * @param statusCode 缺陷状态 code，空则不过滤
     * @param severityCode 严重级别 code，空则不过滤
     * @return 缺陷列表，按创建时间倒序
     */
    public List<QcDefect> listDefects(Long caseRecordId, String statusCode, String severityCode) {
        List<QcDefect> rows = qcDefectRepository.findByCaseRecordIdAndIsDeletedFalseOrderByCreatedAtDesc(caseRecordId);
        List<QcDefect> filtered = new ArrayList<>();
        for (QcDefect row : rows) {
            // 入参带状态时才做内存过滤，不匹配则跳过
            if (trimToNull(statusCode) != null && !statusCode.equals(row.getStatusCode())) {
                continue;
            }
            // 入参带严重级别时才做内存过滤，不匹配则跳过
            if (trimToNull(severityCode) != null && !severityCode.equals(row.getSeverityCode())) {
                continue;
            }
            filtered.add(row);
        }
        return filtered;
    }

    /**
     * 列出病案历次质控执行记录。
     *
     * @param caseRecordId 病案主档主键
     * @return 执行记录，按完成时间倒序
     */
    public List<QcRun> listRuns(Long caseRecordId) {
        return qcRunRepository.findByCaseRecordIdAndIsDeletedFalseOrderByFinishedAtDesc(caseRecordId);
    }

    /**
     * 归档前断言：不存在 open/rectified 的阻断级缺陷（BR-QC-ARCHIVE）。
     *
     * @param caseRecordId 病案主档主键
     * @throws BusinessException 存在阻断级开放缺陷时
     */
    public void assertNoOpenBlockDefects(Long caseRecordId) {
        long count = qcDefectRepository.countByCaseRecordIdAndStatusCodeInAndSeverityCodeAndIsDeletedFalse(
                caseRecordId,
                List.of(QcDefectStatus.OPEN.getCode(), QcDefectStatus.RECTIFIED.getCode()),
                QcDefectSeverity.BLOCK.getCode());
        // 存在 open/rectified 阻断级缺陷则拒绝归档（BR-QC-ARCHIVE）
        if (count > 0) {
            throw new BusinessException(MrmsErrorCodes.QC_ARCHIVE_BLOCKED, "存在阻断级开放缺陷，不可归档");
        }
    }

    /**
     * 手工登记质控缺陷（病案室补录）。
     *
     * @param caseRecordId 病案主档主键
     * @param tenantId 租户主键
     * @param severityCode 严重级别 code
     * @param message 缺陷描述
     * @param operatorId 操作人
     * @return 新建的缺陷
     */
    @Transactional(rollbackFor = Exception.class)
    public QcDefect addManualDefect(
            Long caseRecordId,
            Long tenantId,
            String severityCode,
            String message,
            Long operatorId) {
        // 加载/组装：手工补录 open 缺陷
        QcDefect defect = new QcDefect();
        defect.setTenantId(tenantId);
        defect.setCaseRecordId(caseRecordId);
        defect.setDefectMessage(trimToNull(message));
        defect.setSeverityCode(trimToNull(severityCode));
        defect.setStatusCode(QcDefectStatus.OPEN.getCode());
        defect.setCreatedAt(LocalDateTime.now());
        defect.setCreatedBy(operatorId);
        defect.setIsDeleted(false);
        // 持久化
        return qcDefectRepository.save(defect);
    }

    /**
     * 标记缺陷已整改，待复核。
     *
     * @param defectId 缺陷主键
     * @param operatorId 操作人
     * @return 更新后的缺陷
     */
    @Transactional(rollbackFor = Exception.class)
    public QcDefect markRectified(Long defectId, Long operatorId) {
        // 加载/组装：加载缺陷
        QcDefect defect = requireDefect(defectId);
        // 前置校验：仅 open 可转 rectified
        if (!QcDefectStatus.OPEN.getCode().equals(defect.getStatusCode())) {
            throw new BusinessException(MrmsErrorCodes.QC_DEFECT_INVALID_STATUS, "仅待整改缺陷可标记已整改");
        }
        defect.setStatusCode(QcDefectStatus.RECTIFIED.getCode());
        defect.setRectifiedAt(LocalDateTime.now());
        defect.setUpdatedAt(LocalDateTime.now());
        defect.setUpdatedBy(operatorId);
        // 持久化
        return qcDefectRepository.save(defect);
    }

    /**
     * 关闭缺陷（open 或 rectified 均可收口为 closed）。
     *
     * @param defectId 缺陷主键
     * @param operatorId 操作人
     * @return 关闭后的缺陷
     */
    @Transactional(rollbackFor = Exception.class)
    public QcDefect closeDefect(Long defectId, Long operatorId) {
        // 加载/组装聚合
        QcDefect defect = requireDefect(defectId);
        // 已关闭则幂等返回
        if (QcDefectStatus.CLOSED.getCode().equals(defect.getStatusCode())) {
            return defect;
        }
        // 前置校验：非 open/rectified 状态不可关闭
        if (!QcDefectStatus.OPEN.getCode().equals(defect.getStatusCode())
                && !QcDefectStatus.RECTIFIED.getCode().equals(defect.getStatusCode())) {
            throw new BusinessException(MrmsErrorCodes.QC_DEFECT_INVALID_STATUS, "当前缺陷状态不可关闭");
        }
        defect.setStatusCode(QcDefectStatus.CLOSED.getCode());
        defect.setClosedAt(LocalDateTime.now());
        defect.setUpdatedAt(LocalDateTime.now());
        defect.setUpdatedBy(operatorId);
        // 持久化
        return qcDefectRepository.save(defect);
    }

    /**
     * 规则命中时 upsert 开放缺陷：同病案+规则码且 open 则更新描述，否则新建。
     *
     * @param tenantId 租户主键
     * @param caseRecordId 病案主档主键
     * @param ruleCode 规则 code
     * @param severityCode 严重级别 code
     * @param message 缺陷描述
     * @param operatorId 操作人
     * @param now 操作时间
     */
    @Transactional(rollbackFor = Exception.class)
    public void upsertOpenDefectFromRule(
            Long tenantId,
            Long caseRecordId,
            String ruleCode,
            String severityCode,
            String message,
            Long operatorId,
            LocalDateTime now) {
        Optional<QcDefect> existing = qcDefectRepository.findByCaseRecordIdAndRuleCodeAndStatusCodeAndIsDeletedFalse(
                caseRecordId, ruleCode, QcDefectStatus.OPEN.getCode());
        // 同规则已有 open 缺陷：更新描述与级别
        if (existing.isPresent()) {
            QcDefect defect = existing.get();
            defect.setDefectMessage(message);
            defect.setSeverityCode(severityCode);
            defect.setUpdatedAt(now);
            defect.setUpdatedBy(operatorId);
            // 持久化
            qcDefectRepository.save(defect);
            return;
        }
        // 加载/组装聚合：首次命中新建 open 缺陷
        QcDefect defect = new QcDefect();
        defect.setTenantId(tenantId);
        defect.setCaseRecordId(caseRecordId);
        defect.setRuleCode(ruleCode);
        defect.setDefectMessage(message);
        defect.setSeverityCode(severityCode);
        defect.setStatusCode(QcDefectStatus.OPEN.getCode());
        defect.setCreatedAt(now);
        defect.setCreatedBy(operatorId);
        defect.setIsDeleted(false);
        // 持久化
        qcDefectRepository.save(defect);
    }

    /**
     * 规则未命中时自动关闭同规则码的 open 缺陷（规则已满足）。
     *
     * @param caseRecordId 病案主档主键
     * @param ruleCode 规则 code
     * @param operatorId 操作人
     * @param now 关闭时间
     */
    @Transactional(rollbackFor = Exception.class)
    public void closeOpenDefectByRuleIfPresent(
            Long caseRecordId,
            String ruleCode,
            Long operatorId,
            LocalDateTime now) {
        Optional<QcDefect> existing = qcDefectRepository.findByCaseRecordIdAndRuleCodeAndStatusCodeAndIsDeletedFalse(
                caseRecordId, ruleCode, QcDefectStatus.OPEN.getCode());
        // 无 open 缺陷则无需关闭（规则已满足或从未命中）
        if (existing.isEmpty()) {
            return;
        }
        QcDefect defect = existing.get();
        defect.setStatusCode(QcDefectStatus.CLOSED.getCode());
        defect.setClosedAt(now);
        defect.setUpdatedAt(now);
        defect.setUpdatedBy(operatorId);
        // 持久化关闭
        qcDefectRepository.save(defect);
    }

    /**
     * 统计 open/rectified 状态下指定严重级别的缺陷数。
     *
     * @param caseRecordId 病案主档主键
     * @param severity 严重级别
     * @return 缺陷条数
     */
    public int countOpenDefectsBySeverity(Long caseRecordId, QcDefectSeverity severity) {
        return (int) qcDefectRepository.countByCaseRecordIdAndStatusCodeInAndSeverityCodeAndIsDeletedFalse(
                caseRecordId,
                List.of(QcDefectStatus.OPEN.getCode(), QcDefectStatus.RECTIFIED.getCode()),
                severity.getCode());
    }

    /**
     * 列出仍待处理的缺陷（open 或 rectified）。
     *
     * @param caseRecordId 病案主档主键
     * @return 活跃缺陷列表
     */
    public List<QcDefect> listActiveDefects(Long caseRecordId) {
        return qcDefectRepository.findByCaseRecordIdAndStatusCodeInAndIsDeletedFalse(
                caseRecordId,
                List.of(QcDefectStatus.OPEN.getCode(), QcDefectStatus.RECTIFIED.getCode()));
    }

    /**
     * 写入本轮质控执行批次汇总（{@code mrms_qc_run}）。
     *
     * @param tenantId 租户主键
     * @param caseRecordId 病案主档主键
     * @param triggerCode 触发来源 code
     * @param openBlockCount 开放阻断级缺陷数
     * @param openWarnCount 开放警告级缺陷数
     * @param passed 是否通过（无 open 阻断级）
     * @param operatorId 操作人
     * @param finishedAt 完成时间
     * @return 持久化后的批次
     */
    @Transactional(rollbackFor = Exception.class)
    public QcRun appendQcRun(
            Long tenantId,
            Long caseRecordId,
            String triggerCode,
            int openBlockCount,
            int openWarnCount,
            boolean passed,
            Long operatorId,
            LocalDateTime finishedAt) {
        // Cmd：组装质控执行批次快照（开放阻断/警告计数与通过标志）
        QcRun run = new QcRun();
        run.setTenantId(tenantId);
        run.setCaseRecordId(caseRecordId);
        run.setTriggerCode(triggerCode);
        run.setDefectOpenCount(openBlockCount);
        run.setDefectWarnCount(openWarnCount);
        run.setPassed(passed);
        run.setFinishedAt(finishedAt);
        run.setCreatedAt(finishedAt);
        run.setCreatedBy(operatorId);
        run.setIsDeleted(false);
        // 持久化批次留痕
        return qcRunRepository.save(run);
    }

    /** 加载缺陷，不存在时抛 {@link EntityNotFoundException}。
     *
     * @param defectId 缺陷主键
     * @return 未软删的缺陷实体
     */
    private QcDefect requireDefect(Long defectId) {
        return qcDefectRepository.findByIdAndIsDeletedFalse(defectId)
                .orElseThrow(() -> new EntityNotFoundException("质控缺陷", defectId));
    }
}
