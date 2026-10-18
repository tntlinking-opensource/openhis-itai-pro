/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.bridge.writeoff.support;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.plugin.einvoice.boss.domain.writeoff.access.BossWriteOffOutboundAccess;
import com.zxplat.plugin.einvoice.boss.domain.writeoff.result.WriteOffInvokeOutcome;
import com.zxplat.plugin.einvoice.boss.domain.invoke.access.BossInvokeLogAccess;
import com.zxplat.plugin.einvoice.boss.bridge.writeoff.invoker.EinvWriteOffInvoker;
import com.zxplat.plugin.einvoice.boss.domain.share.BossServiceIds;
import com.zxplat.plugin.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.zxplat.plugin.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.zxplat.plugin.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;
import com.zxplat.plugin.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.zxplat.plugin.einvoice.boss.gateway.scheme.issue.parser.IssueOutputParser;
import com.zxplat.plugin.einvoice.boss.domain.invoke.result.BossInvokeResult;
import com.zxplat.plugin.einvoice.boss.gateway.scheme.writeoff.assembler.WriteOffPayloadAssembler;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 冲红出站支撑：{@link WriteOffPayloadAssembler} → {@link EinvWriteOffInvoker} → {@link IssueOutputParser}。
 */
@Component
@RequiredArgsConstructor
public class BossWriteOffOutboundSupport implements BossWriteOffOutboundAccess {

    private final EinvWriteOffInvoker writeOffInvoker;
    private final WriteOffPayloadAssembler writeOffPayloadAssembler;
    private final IssueOutputParser issueOutputParser;
    private final BossInvokeLogAccess invokeLogWriter;

    /**
     * 调用博思 writeOffEBill 并解析通用票面字段。
     *
     * @param document boss 单据（含蓝票号）
     * @param snapshot health 快照（患者、操作人等）
     * @param reason 冲红原因
     * @return 原始 invoke 结果与解析后的 DTO；红票 eScarlet 字段在 {@code invokeResult} 中
     * @throws BusinessException 博思拒收
     */
    @Override
    public WriteOffInvokeOutcome invokeWriteOff(
            EinvBossDocument document, BossIssueSnapshot snapshot, String reason) {
        String requestJson = writeOffPayloadAssembler.assemble(
                document, snapshot, reason, LocalDateTime.now());
        long started = System.currentTimeMillis();
        BossInvokeResult invokeResult = writeOffInvoker.invoke(requestJson);
        int durationMs = (int) (System.currentTimeMillis() - started);
        invokeLogWriter.writeWithoutTask(
                document.getTenantId(),
                BossServiceIds.WRITE_OFF_E_BILL,
                document.getBusNo(),
                invokeResult,
                durationMs,
                requestJson,
                invokeResult.getRawMessage());
        if (!invokeResult.isSuccess()) {
            String detail = StringUtils.defaultIfBlank(invokeResult.getRawMessage(), invokeResult.getResultCode());
            throw new BusinessException(EinvBossErrorCodes.EINV_BOSS_REJECT, "博思冲红拒收: " + detail);
        }
        BossInvoiceIssueResult issueResult =
                issueOutputParser.toIssueResult(invokeResult, document.getBusNo(), document.getTotalAmt());
        return new WriteOffInvokeOutcome(invokeResult, issueResult);
    }
}
