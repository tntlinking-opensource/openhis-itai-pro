/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.bridge.recover.support;

import com.openhis.whale.einvoice.boss.domain.recover.access.BossRecoverOutboundAccess;
import com.openhis.whale.einvoice.boss.domain.invoke.access.BossInvokeLogAccess;
import com.openhis.whale.einvoice.boss.bridge.recover.invoker.EinvRecoverInvoker;
import com.openhis.whale.einvoice.boss.domain.share.BossServiceIds;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.gateway.scheme.issue.parser.IssueOutputParser;
import com.openhis.whale.einvoice.boss.gateway.scheme.recover.assembler.RecoverPayloadAssembler;
import com.openhis.whale.einvoice.boss.domain.invoke.result.BossInvokeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 查票出站支撑：{@link RecoverPayloadAssembler} → {@link EinvRecoverInvoker} → {@link IssueOutputParser}。
 *
 * <p>查票失败统一返回 {@link Optional#empty()}，由 {@link com.openhis.whale.einvoice.boss.application.internal.issue.BossRecoverService}
 * 决定是否更新 document。</p>
 */
@Component
@RequiredArgsConstructor
public class BossRecoverOutboundSupport implements BossRecoverOutboundAccess {

    private final EinvRecoverInvoker recoverInvoker;
    private final RecoverPayloadAssembler recoverPayloadAssembler;
    private final IssueOutputParser issueOutputParser;
    private final BossInvokeLogAccess invokeLogWriter;

    /**
     * 按 health 快照中的 busNo 查票（Worker 开具前补偿、按 invoiceId 查票）。
     *
     * @param snapshot 含 busNo、totalAmt 的快照
     * @param document 可选单据，存在时写 invoke 日志
     * @return 成功时的票面；失败 empty
     */
    @Override
    public Optional<BossInvoiceIssueResult> invokeBySnapshot(
            BossIssueSnapshot snapshot, Optional<EinvBossDocument> document) {
        String requestJson = recoverPayloadAssembler.assemble(snapshot);
        return invokeAndParse(
                requestJson,
                snapshot.getBusNo(),
                snapshot.getTotalAmt(),
                document);
    }

    /**
     * 仅按 busNo 查票（Orchestrator、补偿 API）。
     *
     * @param busNo 业务流水号
     * @param busDateTime 业务发生时间，可 null
     * @param document 可选单据（取 totalAmt、tenantId 写日志）
     * @return 查票结果
     */
    @Override
    public Optional<BossInvoiceIssueResult> invokeByBusNo(
            String busNo, LocalDateTime busDateTime, Optional<EinvBossDocument> document) {
        String requestJson = recoverPayloadAssembler.assemble(busNo, busDateTime);
        BigDecimal total = document.map(EinvBossDocument::getTotalAmt).orElse(null);
        return invokeAndParse(requestJson, busNo, total, document);
    }

    /**
     * 查票出站公共路径：invoker → 日志 → 成功时 parser。
     *
     * @param requestJson 已组装请求
     * @param busNo 业务流水号
     * @param totalAmt 票面金额上下文
     * @param document 有则写 {@code einv_boss_invoke_log}
     * @return 博思有票且 S0000 时 Optional.of(结果)
     */
    private Optional<BossInvoiceIssueResult> invokeAndParse(
            String requestJson,
            String busNo,
            BigDecimal totalAmt,
            Optional<EinvBossDocument> document) {
        long started = System.currentTimeMillis();
        BossInvokeResult invokeResult = recoverInvoker.invoke(requestJson);
        int durationMs = (int) (System.currentTimeMillis() - started);
        document.ifPresent(doc -> invokeLogWriter.writeWithoutTask(
                doc.getTenantId(),
                BossServiceIds.GET_E_BILL_BY_BUS_NO,
                busNo,
                invokeResult,
                durationMs,
                requestJson,
                invokeResult.getRawMessage()));
        if (!invokeResult.isSuccess()) {
            return Optional.empty();
        }
        return Optional.of(issueOutputParser.toIssueResult(invokeResult, busNo, totalAmt));
    }
}
