/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.qc.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecordHomepage;
import com.openhis.health.domain.mrms.caserecord.entity.HomepageDiagnosis;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordDomainService;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordHomepageDomainService;
import com.openhis.health.domain.mrms.qc.entity.QcRule;
import com.openhis.health.domain.mrms.qc.entity.QcRun;
import com.openhis.health.domain.mrms.qc.entity.RequiredDocType;
import com.openhis.health.domain.mrms.qc.executor.QcRuleExecutor;
import com.openhis.health.domain.mrms.qc.model.QcRunResult;
import com.openhis.health.domain.mrms.qc.service.QcDefectDomainService;
import com.openhis.health.domain.mrms.qc.service.QcRuleQueryService;
import com.openhis.health.domain.share.constants.MrmsQcRuleCodes;
import com.openhis.health.domain.share.enums.CaseRecordStatus;
import com.openhis.health.domain.share.enums.QcDefectSeverity;
import com.openhis.health.domain.share.enums.QcTrigger;
import com.openhis.health.infrastructure.integration.emr.EmrDocumentExtension;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 质控规则执行器默认实现（qc IF {@code support}，实现 {@link QcRuleExecutor}）。
 *
 * <p>读：病案/首页经 DomainService，规则/必写文书经 {@link QcRuleQueryService}，就诊经
 * {@link EncounterQueryService}（B.6.4），EMR 经 Extension。
 * 写：缺陷 upsert/关闭与 {@code qc_run} 经 {@link QcDefectDomainService}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultQcRuleExecutor implements QcRuleExecutor {

    private final QcRuleQueryService qcRuleQueryService;
    private final QcDefectDomainService qcDefectDomainService;
    private final CaseRecordDomainService caseRecordDomainService;
    private final CaseRecordHomepageDomainService caseRecordHomepageDomainService;
    private final EncounterQueryService encounterQueryService;
    private final ObjectMapper objectMapper;
    private final EmrDocumentExtension emrDocumentExtension;

    /**
     * 对指定病案执行已启用规则，upsert/关闭缺陷并写入 {@code mrms_qc_run}。
     *
     * @param caseRecordId 病案主档主键
     * @param trigger 触发来源
     * @param operatorId 操作人
     * @return 本轮批次 id、是否通过及仍开放缺陷列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public QcRunResult execute(Long caseRecordId, QcTrigger trigger, Long operatorId) {
        // Pre：加载病案、首页与子表（B.6.4 DomainService）
        CaseRecord record = caseRecordDomainService.requireById(caseRecordId);
        CaseRecordHomepage homepage = caseRecordHomepageDomainService
                .findOptionalByCaseRecordId(caseRecordId)
                .orElse(null);
        List<HomepageDiagnosis> diagnoses = caseRecordHomepageDomainService.listDiagnoses(caseRecordId);

        // Pre：租户已启用规则（种子 + 覆盖）
        List<QcRule> rules = qcRuleQueryService.listEnabledRules(record.getTenantId());
        LocalDateTime now = LocalDateTime.now();
        // Cmd：逐条评估并同步缺陷 open/close
        for (QcRule rule : rules) {
            Optional<String> hitMessage = evaluateRule(rule, record, homepage, diagnoses);
            // 命中则 upsert 开放缺陷
            if (hitMessage.isPresent()) {
                qcDefectDomainService.upsertOpenDefectFromRule(
                        record.getTenantId(),
                        record.getId(),
                        rule.getRuleCode(),
                        rule.getSeverityCode(),
                        hitMessage.get(),
                        operatorId,
                        now);
            } else {
                // 未命中则关闭该规则开放缺陷
                qcDefectDomainService.closeOpenDefectByRuleIfPresent(
                        record.getId(), rule.getRuleCode(), operatorId, now);
            }
        }

        int openBlockCount = qcDefectDomainService.countOpenDefectsBySeverity(
                caseRecordId, QcDefectSeverity.BLOCK);
        int openWarnCount = qcDefectDomainService.countOpenDefectsBySeverity(
                caseRecordId, QcDefectSeverity.WARN);
        boolean passed = openBlockCount == 0;

        // Cmd：持久化本轮 qc_run 汇总
        QcRun savedRun = qcDefectDomainService.appendQcRun(
                record.getTenantId(),
                caseRecordId,
                trigger.getCode(),
                openBlockCount,
                openWarnCount,
                passed,
                operatorId,
                now);

        // Asm：组装领域结果（含仍开放缺陷）
        QcRunResult result = new QcRunResult();
        result.setQcRunId(savedRun.getId());
        result.setPassed(passed);
        result.setOpenBlockCount(openBlockCount);
        result.setOpenWarnCount(openWarnCount);
        result.setDefects(qcDefectDomainService.listActiveDefects(caseRecordId));
        return result;
    }

    /**
     * 按 {@code rule_code} 分支评估单条规则是否命中。
     *
     * @param rule 规则元数据
     * @param record 病案主档
     * @param homepage 首页，可为 null
     * @param diagnoses 诊断子表
     * @return 命中时返回缺陷描述；未命中 empty
     */
    private Optional<String> evaluateRule(
            QcRule rule,
            CaseRecord record,
            CaseRecordHomepage homepage,
            List<HomepageDiagnosis> diagnoses) {
        String code = rule.getRuleCode();
        if (MrmsQcRuleCodes.HP_SUBMIT_REQUIRED.equals(code)) {
            // 首页不存在则阻断提交类规则直接命中
            if (homepage == null) {
                return Optional.of("病案首页不存在");
            }
            List<String> errors = homepage.listSubmitValidationErrors(diagnoses);
            if (errors.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(String.join("；", errors));
        }
        if (MrmsQcRuleCodes.HP_DATE_LOGIC.equals(code)) {
            // 日期不全时不判逻辑冲突，避免误报
            if (homepage == null || homepage.getAdmTime() == null || homepage.getDscgTime() == null) {
                return Optional.empty();
            }
            // 出院早于入院则命中
            if (homepage.getDscgTime().isBefore(homepage.getAdmTime())) {
                return Optional.of("出院时间不能早于入院时间");
            }
            return Optional.empty();
        }
        if (MrmsQcRuleCodes.HP_DISCHARGE_MAIN_DIAG.equals(code)) {
            // 首页不存在则阻断
            if (homepage == null) {
                return Optional.of("病案首页不存在");
            }
            List<String> errors = new ArrayList<>();
            // 仅收集含「出院主诊断」的校验错误
            for (String msg : homepage.listSubmitValidationErrors(diagnoses)) {
                if (msg.contains("出院主诊断")) {
                    errors.add(msg);
                }
            }
            if (errors.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(String.join("；", errors));
        }
        if (MrmsQcRuleCodes.SUBMIT_OVERDUE.equals(code)) {
            return evaluateSubmitOverdue(rule, record, homepage);
        }
        if (MrmsQcRuleCodes.ARCHIVE_OVERDUE.equals(code)) {
            return evaluateArchiveOverdue(rule, record, homepage);
        }
        if (MrmsQcRuleCodes.DOC_REQUIRED.equals(code)) {
            // 必写文书规则：对照 EMR 已提交类型集合
            return evaluateDocRequired(record);
        }
        // 未实现分支的规则跳过，避免误关缺陷
        return Optional.empty();
    }

    /**
     * 评估必写文书是否齐全（{@link MrmsQcRuleCodes#DOC_REQUIRED}）。
     *
     * @param record 病案主档
     * @return 缺失文书描述或 empty
     */
    private Optional<String> evaluateDocRequired(CaseRecord record) {
        // EMR 未启用时不做文书质控
        if (!emrDocumentExtension.isEnabled()) {
            return Optional.empty();
        }
        String encounterClass = resolveEncounterClassCode(record.getEncounterId());
        List<RequiredDocType> requiredTypes = qcRuleQueryService.listRequiredDocTypes(
                record.getTenantId(), encounterClass);
        if (requiredTypes.isEmpty()) {
            return Optional.empty();
        }
        Set<String> presentTypes = emrDocumentExtension.listSubmittedDocumentTypeCodes(
                record.getPatientId(), record.getEncounterId());
        List<String> missing = new ArrayList<>();
        for (RequiredDocType row : requiredTypes) {
            // 已提交类型中不存在则记入缺失列表
            if (!presentTypes.contains(row.getDocumentTypeCode())) {
                missing.add(row.getDocumentTypeCode());
            }
        }
        if (missing.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("缺少必写文书：" + String.join("、", missing));
    }

    /**
     * 读就诊类别 code（B.6.4 Encounter QS）。
     *
     * @param encounterId 就诊主键
     * @return 就诊类别 code，无就诊时 null
     */
    private String resolveEncounterClassCode(Long encounterId) {
        if (encounterId == null) {
            return null;
        }
        return encounterQueryService.findOptionalEncounterEntityById(encounterId)
                .map(Encounter::getClassEnum)
                .orElse(null);
    }

    /**
     * 评估首页提交逾期（{@link MrmsQcRuleCodes#SUBMIT_OVERDUE}，draft/returned 态）。
     *
     * @param rule 规则元数据
     * @param record 病案主档
     * @param homepage 首页，可为 null
     * @return 命中时逾期描述；未命中 empty
     */
    private Optional<String> evaluateSubmitOverdue(QcRule rule, CaseRecord record, CaseRecordHomepage homepage) {
        // 首页/出院时间/状态缺失时不计逾期
        if (homepage == null || homepage.getDscgTime() == null || record.getStatusEnum() == null) {
            return Optional.empty();
        }
        CaseRecordStatus status = record.getStatusEnum();
        // 已提交或进入病案室流程后不再计提交逾期
        if (status != CaseRecordStatus.DRAFT && status != CaseRecordStatus.RETURNED) {
            return Optional.empty();
        }
        int maxDays = readMaxDays(rule, MrmsQcRuleCodes.DEFAULT_SUBMIT_OVERDUE_DAYS);
        LocalDateTime deadline = homepage.getDscgTime().plusDays(maxDays);
        if (LocalDateTime.now().isAfter(deadline)) {
            return Optional.of("出院后超过 " + maxDays + " 天仍未提交病案首页");
        }
        return Optional.empty();
    }

    /**
     * 评估归档逾期（{@link MrmsQcRuleCodes#ARCHIVE_OVERDUE}）。
     *
     * @param rule 规则元数据
     * @param record 病案主档
     * @param homepage 首页，可为 null
     * @return 命中时逾期描述；未命中 empty
     */
    private Optional<String> evaluateArchiveOverdue(QcRule rule, CaseRecord record, CaseRecordHomepage homepage) {
        if (homepage == null || homepage.getDscgTime() == null) {
            return Optional.empty();
        }
        // 已归档不再计归档逾期
        if (record.getStatusEnum() == CaseRecordStatus.ARCHIVED) {
            return Optional.empty();
        }
        int maxDays = readMaxDays(rule, MrmsQcRuleCodes.DEFAULT_ARCHIVE_OVERDUE_DAYS);
        LocalDateTime deadline = homepage.getDscgTime().plusDays(maxDays);
        if (LocalDateTime.now().isAfter(deadline)) {
            return Optional.of("出院后超过 " + maxDays + " 天仍未归档");
        }
        return Optional.empty();
    }

    /**
     * 从规则 {@code expression_json} 读取 {@code maxDaysAfterDischarge}，缺省用 defaultDays。
     *
     * @param rule 规则元数据
     * @param defaultDays 种子未配置时的回退天数
     * @return 逾期阈值天数
     */
    private int readMaxDays(QcRule rule, int defaultDays) {
        // expression_json 为空时使用种子默认天数
        if (rule.getExpressionJson() == null || rule.getExpressionJson().isBlank()) {
            return defaultDays;
        }
        try {
            JsonNode node = objectMapper.readTree(rule.getExpressionJson());
            if (node.has(MrmsQcRuleCodes.EXPRESSION_MAX_DAYS_AFTER_DISCHARGE)) {
                return node.get(MrmsQcRuleCodes.EXPRESSION_MAX_DAYS_AFTER_DISCHARGE).asInt(defaultDays);
            }
        } catch (Exception ignored) {
            // JSON 解析失败时回落默认天数
            return defaultDays;
        }
        // 未配置 maxDaysAfterDischarge 键时使用默认
        return defaultDays;
    }
}
