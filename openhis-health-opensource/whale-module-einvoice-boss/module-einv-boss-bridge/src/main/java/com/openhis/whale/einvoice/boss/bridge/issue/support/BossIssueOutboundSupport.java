/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.bridge.issue.support;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.einvoice.boss.domain.issue.access.BossIssueOutboundAccess;
import com.openhis.whale.einvoice.boss.domain.issue.result.IssueInvokeOutcome;
import com.openhis.whale.einvoice.boss.domain.invoke.access.BossInvokeLogAccess;
import com.openhis.whale.einvoice.boss.bridge.issue.invoker.EinvIssueInvoker;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossLine;
import com.openhis.whale.einvoice.boss.domain.issue.entity.EinvBossIssueTask;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.openhis.whale.einvoice.boss.gateway.scheme.issue.parser.IssueOutputParser;
import com.openhis.whale.einvoice.boss.gateway.scheme.issue.assembler.BossPayloadBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import com.openhis.whale.einvoice.boss.domain.invoke.result.BossInvokeResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 开具出站支撑：payload 组装 → {@link EinvIssueInvoker} → {@link IssueOutputParser}。
 *
 * <p>由 {@link com.openhis.whale.einvoice.boss.application.internal.issue.BossIssueWorkerService} 调用；
 * 日志写入失败不阻断主流程。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BossIssueOutboundSupport implements BossIssueOutboundAccess {

    private final BossPayloadBuilder payloadBuilder;
    private final EinvIssueInvoker issueInvoker;
    private final IssueOutputParser issueOutputParser;
    private final BossInvokeLogAccess invokeLogWriter;
    private final BossQuantityUnitDisplayResolver quantityUnitDisplayResolver;

    /**
     * 执行一次博思开具出站并映射为领域结果。
     *
     * @param task 关联任务（写 invoke 日志）
     * @param document boss 单据
     * @param lines 明细行（已按 lineNo 排序）
     * @param snapshot health 读模型
     * @return 票面结果与请求 JSON（供 document.payloadJson 存档）
     * @throws BusinessException 博思拒收，错误码 {@link EinvBossErrorCodes#EINV_BOSS_REJECT}
     */
    @Override
    public IssueInvokeOutcome invokeIssue(
            EinvBossIssueTask task,
            EinvBossDocument document,
            List<EinvBossLine> lines,
            BossIssueSnapshot snapshot) {
        // 出站前再兜底：调额保留行或历史落库的英文/数字 code 转为中文
        normalizeLineUnits(lines);
        String serviceId = payloadBuilder.resolveServiceId(document.getIssueKind());
        String payload = payloadBuilder.build(document, lines, snapshot);
        long started = System.currentTimeMillis();
        BossInvokeResult invokeResult = issueInvoker.invoke(serviceId, payload);
        int durationMs = (int) (System.currentTimeMillis() - started);
        try {
            invokeLogWriter.write(
                    task, serviceId, document.getBusNo(), invokeResult, durationMs, payload, invokeResult.getRawMessage());
        } catch (Exception logEx) {
            log.warn("Persist boss invoke log failed, taskId={}", task.getId(), logEx);
        }
        if (!invokeResult.isSuccess()) {
            String rejectDetail = StringUtils.defaultIfBlank(invokeResult.getRawMessage(), invokeResult.getResultCode());
            throw new BusinessException(EinvBossErrorCodes.EINV_BOSS_REJECT, "博思返回: " + rejectDetail);
        }
        BossInvoiceIssueResult issueResult =
                issueOutputParser.toIssueResult(invokeResult, document.getBusNo(), document.getTotalAmt());
        return new IssueInvokeOutcome(issueResult, payload);
    }

    /**
     * 将明细 unit 术语码就地转为中文 display（不落库；仅影响本次出站报文）。
     *
     * @param lines 待开具明细
     */
    private void normalizeLineUnits(List<EinvBossLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        Map<String, String> unitLabelByCode = quantityUnitDisplayResolver.warmLabelMap();
        for (EinvBossLine line : lines) {
            line.setUnit(quantityUnitDisplayResolver.toDisplay(line.getUnit(), unitLabelByCode));
        }
    }
}
