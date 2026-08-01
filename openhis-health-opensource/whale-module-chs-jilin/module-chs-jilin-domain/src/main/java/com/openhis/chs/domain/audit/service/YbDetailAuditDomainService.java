/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.audit.service;

import com.openhis.chs.domain.audit.command.AuditFeedback3103Command;
import com.openhis.chs.domain.audit.command.AuditFeedbackWarnCommand;
import com.openhis.chs.domain.audit.entity.YbDetailAuditFeedback;
import com.openhis.chs.domain.audit.entity.YbDetailAuditHit;
import com.openhis.chs.domain.audit.entity.YbDetailAuditHitLine;
import com.openhis.chs.domain.audit.repository.YbDetailAuditFeedbackRepository;
import com.openhis.chs.domain.audit.repository.YbDetailAuditHitLineRepository;
import com.openhis.chs.domain.audit.repository.YbDetailAuditHitRepository;
import com.openhis.chs.domain.audit.result.AuditAnalyzeResult;
import com.openhis.chs.domain.audit.result.AuditViolationDetailResult;
import com.openhis.chs.domain.audit.result.AuditViolationResult;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 明细审核落库领域服务（仅操作本聚合三表）。
 *
 * <p>不变量：3101 按 jr_id/jrd_id 幂等 upsert；3103 每次追加反馈行。</p>
 */
@Service
@RequiredArgsConstructor
public class YbDetailAuditDomainService {

    private final YbDetailAuditHitRepository hitRepository;
    private final YbDetailAuditHitLineRepository hitLineRepository;
    private final YbDetailAuditFeedbackRepository feedbackRepository;

    /**
     * 持久化 3101 违规头与明细。
     *
     * @param result      平台解析结果
     * @param tenantId    租户
     * @param operatorId  操作员
     * @param drCodg      责任医师编码（来自 Snapshot/请求）
     * @param drName      责任医师姓名
     * @param deptCodg    科室编码
     * @param deptName    科室名称
     */
    @Transactional
    public void persistAnalyzeResult(
            AuditAnalyzeResult result,
            Long tenantId,
            Long operatorId,
            String drCodg,
            String drName,
            String deptCodg,
            String deptName) {
        // 前置校验：无违规无需落库
        if (result == null || result.getViolations() == null || result.getViolations().isEmpty()) {
            return;
        }
        // 加载/组装聚合：逐条违规头幂等写入
        for (AuditViolationResult violation : result.getViolations()) {
            if (violation == null || StringUtils.isEmpty(violation.getJrId())) {
                continue;
            }
            YbDetailAuditHit hit = hitRepository
                .findByTenantIdAndJrIdAndIsDeletedFalse(tenantId, violation.getJrId())
                .orElseGet(YbDetailAuditHit::new);
            hit.setJrId(violation.getJrId());
            hit.setTaskId(result.getTaskId());
            hit.setTrigScen(result.getTrigScen());
            hit.setContractBusNo(result.getContractBusNo());
            hit.setMdtrtId(violation.getMdtrtId());
            hit.setPatnId(violation.getPatnId());
            hit.setRuleId(violation.getRuleId());
            hit.setRuleName(violation.getRuleName());
            hit.setVolaCont(violation.getVolaCont());
            hit.setVolaAmt(violation.getVolaAmt());
            hit.setSevDeg(violation.getSevDeg());
            hit.setVolaBhvrType(violation.getVolaBhvrType());
            hit.setDrCodg(drCodg);
            hit.setDrName(drName);
            hit.setDeptCodg(deptCodg);
            hit.setDeptName(deptName);
            hit.setOperatorId(operatorId);
            if (hit.getTenantId() == null) {
                hit.setTenantId(tenantId);
            }
            // 持久化：先写头
            hitRepository.save(hit);
            persistDetails(violation, tenantId);
        }
    }

    /**
     * 持久化 3103 反馈行。
     *
     * @param command    反馈命令
     * @param tenantId   租户
     * @param busNo      契约 bus_no
     * @param operatorId 操作员
     */
    @Transactional
    public void persistFeedback(
            AuditFeedback3103Command command,
            Long tenantId,
            String busNo,
            Long operatorId) {
        // 前置校验：无 warns 不写
        if (command == null || command.getWarns() == null || command.getWarns().isEmpty()) {
            return;
        }
        // 组装/持久化：每条 warn 追加一行
        for (AuditFeedbackWarnCommand warn : command.getWarns()) {
            if (warn == null || StringUtils.isEmpty(warn.getWarnRsltId())) {
                continue;
            }
            YbDetailAuditFeedback row = new YbDetailAuditFeedback();
            row.setWarnRsltId(warn.getWarnRsltId());
            row.setDspoWay(warn.getDspoWay());
            row.setDspoWayRea(warn.getDspoWayRea());
            row.setWarnType(command.getWarnType());
            row.setJrId(warn.getJrId());
            row.setContractBusNo(busNo);
            row.setOperatorId(operatorId);
            row.setTenantId(tenantId);
            feedbackRepository.save(row);
        }
    }

    /**
     * 持久化单条违规头下的明细行（按 jrd_id 幂等 upsert）。
     *
     * @param violation 违规头（含 details）
     * @param tenantId  租户
     */
    private void persistDetails(AuditViolationResult violation, Long tenantId) {
        // 前置：无明细则跳过
        if (violation.getDetails() == null || violation.getDetails().isEmpty()) {
            return;
        }
        // 组装/持久化：逐条 jrd_id 幂等写入，供 3103 warn_rslt_id 回查
        for (AuditViolationDetailResult detail : violation.getDetails()) {
            if (detail == null || StringUtils.isEmpty(detail.getJrdId())) {
                continue;
            }
            YbDetailAuditHitLine line = hitLineRepository
                .findByTenantIdAndJrdIdAndIsDeletedFalse(tenantId, detail.getJrdId())
                .orElseGet(YbDetailAuditHitLine::new);
            line.setJrdId(detail.getJrdId());
            line.setJrId(violation.getJrId());
            line.setRxId(detail.getRxId());
            line.setVolaItemType(detail.getVolaItemType());
            line.setVolaAmt(detail.getVolaAmt());
            line.setMdtrtId(detail.getMdtrtId());
            if (line.getTenantId() == null) {
                line.setTenantId(tenantId);
            }
            hitLineRepository.save(line);
        }
    }
}
