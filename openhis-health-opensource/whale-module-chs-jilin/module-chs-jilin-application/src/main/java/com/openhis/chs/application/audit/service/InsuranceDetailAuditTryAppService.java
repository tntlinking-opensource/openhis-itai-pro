/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.audit.service;

import com.openhis.chs.application.audit.request.DetailAuditAnalyzeFromEncounterRequest;
import com.openhis.chs.domain.audit.access.Audit3101Invoker;
import com.openhis.chs.domain.audit.access.Audit3103Invoker;
import com.openhis.chs.domain.audit.command.AuditAnalyze3101Command;
import com.openhis.chs.domain.audit.command.AuditFeedback3103Command;
import com.openhis.chs.domain.audit.result.AuditAnalyzeResult;
import com.openhis.chs.domain.audit.snapshot.AuditFeeSnapshot;
import com.openhis.chs.domain.audit.snapshot.AuditFeeSnapshotProjector;
import com.openhis.chs.domain.audit.support.AuditViolationItemNameSupport;
import com.openhis.chs.domain.catalog.access.ChsContractPropertiesAccess;
import com.openhis.chs.domain.outpatient.command.OpPreSettleCommand;
import com.openhis.chs.domain.outpatient.loader.OpPreSettleContextLoader;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 明细审核试调应用服务（{@code POST /api/app/insuranceDetailAuditTry/*}）。
 *
 * <p>对应路由 {@code /chs/detail-audit-try}：手工 Command 或从门诊就诊 Snapshot 组装后调 3101/3103。
 * 不进入收费资金链。</p>
 */
@Service
@RequiredArgsConstructor
public class InsuranceDetailAuditTryAppService {

    private final Audit3101Invoker audit3101Invoker;
    private final Audit3103Invoker audit3103Invoker;
    private final OpPreSettleContextLoader opPreSettleContextLoader;
    private final ChsContractPropertiesAccess contractPropertiesAccess;

    /**
     * 【3101】手工/粘贴 Command 试调。
     *
     * @param request 完整分析命令
     * @return 违规结果
     */
    public AuditAnalyzeResult analyze3101(AuditAnalyze3101Command request) {
        // Gate：请求体非空
        if (request == null) {
            throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "3101 请求不能为空");
        }
        // Pre：无额外装载；syscode/契约 Gate 在 Invoker
        // Cmd：直调 domain 出站端口（落库在 Invoker）
        AuditAnalyzeResult result = audit3101Invoker.invoke(request);
        // Asm：Invoker 已返回领域 Result，直接出参
        return result;
    }

    /**
     * 【3101】从门诊就诊自动组装后试调。
     *
     * @param request 就诊与场景参数
     * @return 违规结果
     */
    public AuditAnalyzeResult analyze3101FromEncounter(DetailAuditAnalyzeFromEncounterRequest request) {
        // Gate：就诊与场景、mdtrt、机构类型等级必填
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "encounterId 不能为空");
        }
        if (StringUtils.isEmpty(request.getTrigScen())) {
            throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "trigScen 不能为空");
        }
        if (StringUtils.isEmpty(request.getMdtrtId())) {
            throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "mdtrtId 不能为空");
        }
        if (StringUtils.isEmpty(request.getMedinsType()) || StringUtils.isEmpty(request.getMedinsLv())) {
            throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "medinsType/medinsLv 不能为空");
        }
        // Pre：一次装载门诊预结算上下文（与 2204 同源，禁止平行扫库）
        OpPreSettleCommand partial = new OpPreSettleCommand();
        partial.setContractBusNo(request.getContractBusNo());
        partial.setContractId(request.getContractId());
        partial.setEncounterId(request.getEncounterId());
        partial.setChargeItemIds(request.getChargeItemIds());
        OpPreSettleCommand enriched = opPreSettleContextLoader.enrichPreSettle(partial);
        AuditFeeSnapshot snap = AuditFeeSnapshotProjector.fromOpPreSettle(enriched);
        snap.setMdtrtId(request.getMdtrtId());
        snap.setMedinsType(request.getMedinsType());
        snap.setMedinsLv(request.getMedinsLv());
        snap.setBrdy(request.getBrdy());
        // Pre：机构编码/名称/区划从契约配置补齐（3101 medins_name 必填 1～200）
        String busNo = enriched.getContractBusNo();
        if (StringUtils.isEmpty(busNo)) {
            busNo = request.getContractBusNo();
        }
        contractPropertiesAccess.findFixmedinsCode(busNo).ifPresent(snap::setMedinsId);
        contractPropertiesAccess.findFixmedinsName(busNo).ifPresent(snap::setMedinsName);
        contractPropertiesAccess.findAdmVs(busNo).ifPresent(snap::setMedinsAdmdvs);
        if (StringUtils.isEmpty(snap.getMedinsAdmdvs())) {
            snap.setMedinsAdmdvs(enriched.getInsuplcAdmdvs());
        }
        // Asm：Snapshot → 3101 Command
        AuditAnalyze3101Command command = AuditFeeSnapshotProjector.toAnalyzeCommand(
            snap, request.getTrigScen(), request.getSyscode());
        command.setContractBusNo(busNo);
        command.setContractId(request.getContractId());
        // Cmd：出站 3101
        AuditAnalyzeResult result = audit3101Invoker.invoke(command);
        AuditViolationItemNameSupport.enrichItemNames(result, snap);
        return result;
    }

    /**
     * 【3103】反馈试调。
     *
     * @param request 反馈命令（warnRsltId=jrdId）
     */
    public void feedback3103(AuditFeedback3103Command request) {
        // Gate：请求体非空
        if (request == null) {
            throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "3103 请求不能为空");
        }
        // Pre：无额外装载；warn 校验在 Invoker
        // Cmd：直调 domain 出站端口
        audit3103Invoker.invoke(request);
        // Asm：3103 无业务输出
    }
}
