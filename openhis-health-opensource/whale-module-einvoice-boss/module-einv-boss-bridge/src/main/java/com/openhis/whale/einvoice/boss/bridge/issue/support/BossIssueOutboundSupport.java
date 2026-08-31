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
import com.openhis.whale.einvoice.boss.domain.issue.result.BossChargeLineSnapshot;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.openhis.whale.einvoice.boss.gateway.scheme.issue.parser.IssueOutputParser;
import com.openhis.whale.einvoice.boss.gateway.scheme.issue.assembler.BossPayloadBuilder;
import com.openhis.whale.einvoice.boss.gateway.scheme.issue.assembler.BossPayloadSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import com.openhis.whale.einvoice.boss.domain.invoke.result.BossInvokeResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 开具出站支撑：payload 组装 → {@link EinvIssueInvoker} → {@link IssueOutputParser}。
 *
 * <p>由 {@link com.openhis.whale.einvoice.boss.application.internal.issue.BossIssueWorkerService} 调用；
 * 日志写入失败不阻断主流程。医院要求一结算一票：{@code listDetail} 全量出站，
 * {@code chargeDetail} 按统计大类聚合；大类超院点上限时失败，不拆多张。</p>
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
     * 执行博思开具出站并映射为领域结果（单票，不拆）。
     *
     * @param task 关联任务（写 invoke 日志）
     * @param document boss 单据
     * @param lines 明细行（已按 lineNo 排序）
     * @param snapshot health 读模型
     * @return 单张开具结果
     * @throws BusinessException 大类超限或博思拒收
     */
    @Override
    public IssueInvokeOutcome invokeIssue(
            EinvBossIssueTask task,
            EinvBossDocument document,
            List<EinvBossLine> lines,
            BossIssueSnapshot snapshot) {
        enrichLinesFromSnapshot(lines, snapshot);
        normalizeLineUnits(lines);
        assertChargeDetailWithinLimit(lines);
        String serviceId = payloadBuilder.resolveServiceId(document.getIssueKind());
        String payload = payloadBuilder.build(document, lines, snapshot);
        long started = System.currentTimeMillis();
        BossInvokeResult invokeResult = issueInvoker.invoke(serviceId, payload);
        int durationMs = (int) (System.currentTimeMillis() - started);
        try {
            invokeLogWriter.write(
                    task,
                    serviceId,
                    document.getBusNo(),
                    invokeResult,
                    durationMs,
                    payload,
                    invokeResult.getRawMessage());
        } catch (Exception logEx) {
            log.warn("Persist boss invoke log failed, taskId={}", task.getId(), logEx);
        }
        if (!invokeResult.isSuccess()) {
            String rejectDetail = StringUtils.defaultIfBlank(invokeResult.getRawMessage(), invokeResult.getResultCode());
            throw new BusinessException(EinvBossErrorCodes.EINV_BOSS_REJECT, "博思返回: " + rejectDetail);
        }
        BossInvoiceIssueResult issueResult =
                issueOutputParser.toIssueResult(invokeResult, document.getBusNo(), document.getTotalAmt());
        return IssueInvokeOutcome.ofSingle(issueResult, payload, lines);
    }

    /**
     * 院点票据代码限定收费项目（chargeDetail）行数；清单 listDetail 不限。超限禁止拆票，直接失败。
     */
    private static void assertChargeDetailWithinLimit(List<EinvBossLine> lines) {
        int chargeRows = BossPayloadSupport.aggregateByChargeCode(lines).size();
        if (chargeRows > BossPayloadSupport.MAX_CHARGE_DETAIL_ROWS) {
            throw new BusinessException(
                    EinvBossErrorCodes.EINV_CHARGE_DETAIL_LIMIT,
                    "收费项目大类行数 "
                            + chargeRows
                            + " 超过院点上限 "
                            + BossPayloadSupport.MAX_CHARGE_DETAIL_ROWS
                            + "，请检查收费对照后重开（医院不允许拆票）");
        }
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

    /**
     * 用最新 health 快照补全落库明细缺失的项目名/医保等级（不落库；仅出站）。
     *
     * <p>历史单据若初始化时未展开产品，库内 {@code item_name}/{@code list_medical_care_type} 为空，
     * 出站前按 {@code sourceChargeItemId} 对齐快照行回填，避免票面只有统计大类、无甲乙丙。</p>
     */
    private void enrichLinesFromSnapshot(List<EinvBossLine> lines, BossIssueSnapshot snapshot) {
        if (lines == null || lines.isEmpty() || snapshot == null || snapshot.getChargeLines() == null) {
            return;
        }
        Map<Long, BossChargeLineSnapshot> byChargeItemId = new HashMap<>();
        for (BossChargeLineSnapshot chargeLine : snapshot.getChargeLines()) {
            if (chargeLine != null && chargeLine.getChargeItemId() != null) {
                byChargeItemId.putIfAbsent(chargeLine.getChargeItemId(), chargeLine);
            }
        }
        if (byChargeItemId.isEmpty()) {
            return;
        }
        for (EinvBossLine line : lines) {
            if (line == null || line.getSourceChargeItemId() == null) {
                continue;
            }
            BossChargeLineSnapshot snap = byChargeItemId.get(line.getSourceChargeItemId());
            if (snap == null) {
                continue;
            }
            if (StringUtils.isBlank(line.getItemName()) && StringUtils.isNotBlank(snap.getItemName())) {
                line.setItemName(snap.getItemName().trim());
            }
            if (StringUtils.isBlank(line.getItemCode()) && StringUtils.isNotBlank(snap.getItemCode())) {
                line.setItemCode(snap.getItemCode().trim());
            }
            if (StringUtils.isBlank(line.getListMedicalCareType())
                    && StringUtils.isNotBlank(snap.getListMedicalCareType())) {
                line.setListMedicalCareType(snap.getListMedicalCareType().trim());
            }
            if (StringUtils.isBlank(line.getListRemark()) && StringUtils.isNotBlank(snap.getListRemark())) {
                line.setListRemark(snap.getListRemark().trim());
            }
            if (StringUtils.isNotBlank(snap.getUnit())) {
                line.setUnit(snap.getUnit().trim());
            }
        }
    }
}
