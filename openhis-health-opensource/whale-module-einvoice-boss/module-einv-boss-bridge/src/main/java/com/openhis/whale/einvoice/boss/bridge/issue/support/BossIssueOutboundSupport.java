/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.bridge.issue.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.einvoice.boss.domain.issue.access.BossIssueOutboundAccess;
import com.openhis.whale.einvoice.boss.domain.issue.result.IssueInvokeOutcome;
import com.openhis.whale.einvoice.boss.domain.issue.result.IssuePartOutcome;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 开具出站支撑：payload 组装 → {@link EinvIssueInvoker} → {@link IssueOutputParser}。
 *
 * <p>由 {@link com.openhis.whale.einvoice.boss.application.internal.issue.BossIssueWorkerService} 调用；
 * 日志写入失败不阻断主流程。明细超过 {@link BossPayloadSupport#MAX_LINES_PER_BILL} 时按
 * {@code eBillRelateNo} 拆多张开具；返回结果含全部分片，由 Worker 各落一条单据。</p>
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
     * 执行博思开具出站并映射为领域结果（超行数时拆多张）。
     *
     * @param task 关联任务（写 invoke 日志）
     * @param document boss 主单据（拆票时仅临时改 busNo/totalAmt，出站后还原）
     * @param lines 明细行（已按 lineNo 排序）
     * @param snapshot health 读模型
     * @return 全部分片结果（首张供 health 回写；Worker 按分片各落库）
     * @throws BusinessException 博思拒收，错误码 {@link EinvBossErrorCodes#EINV_BOSS_REJECT}
     */
    @Override
    public IssueInvokeOutcome invokeIssue(
            EinvBossIssueTask task,
            EinvBossDocument document,
            List<EinvBossLine> lines,
            BossIssueSnapshot snapshot) {
        enrichLinesFromSnapshot(lines, snapshot);
        normalizeLineUnits(lines);
        List<List<EinvBossLine>> chunks =
                BossPayloadSupport.partitionLines(lines, BossPayloadSupport.MAX_LINES_PER_BILL);
        if (chunks.isEmpty()) {
            chunks = List.of(List.of());
        }
        if (chunks.size() == 1) {
            return new IssueInvokeOutcome(
                    List.of(invokeSingle(task, document, chunks.get(0), snapshot, null, 0)));
        }

        String relateNo = document.getBusNo();
        BigDecimal fullTotal = BossPayloadSupport.scale2(document.getTotalAmt());
        BigDecimal allocated = BigDecimal.ZERO.setScale(2);
        List<IssuePartOutcome> parts = new ArrayList<>();
        List<String> issuedBillNos = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            List<EinvBossLine> chunk = chunks.get(i);
            BigDecimal chunkTotal;
            if (i == chunks.size() - 1) {
                chunkTotal = BossPayloadSupport.scale2(fullTotal.subtract(allocated));
            } else {
                chunkTotal = BossPayloadSupport.sumLineAmounts(chunk);
                allocated = allocated.add(chunkTotal);
            }
            String chunkBusNo;
            if (i == 0) {
                chunkBusNo = relateNo;
            } else {
                chunkBusNo = relateNo + "-" + (i + 1);
            }
            BossIssueSnapshot chunkSnapshot =
                    BossPayloadSupport.copyWithScaledSettlement(snapshot, chunkTotal, fullTotal);

            IssuePartOutcome part =
                    invokeChunk(task, document, chunk, chunkSnapshot, chunkBusNo, chunkTotal, relateNo, i);
            parts.add(part);
            issuedBillNos.add(part.issueResult().getBillNo());
        }
        log.info(
                "Split einvoice issued, relateNo={}, parts={}, billNos={}",
                relateNo,
                chunks.size(),
                issuedBillNos);
        return new IssueInvokeOutcome(parts);
    }

    /**
     * 单张（或拆票中的一片）开具。
     *
     * @param partIndex 分片序号
     */
    private IssuePartOutcome invokeSingle(
            EinvBossIssueTask task,
            EinvBossDocument document,
            List<EinvBossLine> lines,
            BossIssueSnapshot snapshot,
            String relateNo,
            int partIndex) {
        String serviceId = payloadBuilder.resolveServiceId(document.getIssueKind());
        String payload = payloadBuilder.build(document, lines, snapshot);
        if (StringUtils.isNotBlank(relateNo)) {
            payload = withRelateNo(payload, relateNo);
        }
        return invokeAndParse(
                task, document.getBusNo(), document.getTotalAmt(), serviceId, payload, lines, partIndex);
    }

    private IssuePartOutcome invokeChunk(
            EinvBossIssueTask task,
            EinvBossDocument document,
            List<EinvBossLine> chunkLines,
            BossIssueSnapshot chunkSnapshot,
            String chunkBusNo,
            BigDecimal chunkTotal,
            String relateNo,
            int partIndex) {
        String originalBusNo = document.getBusNo();
        BigDecimal originalTotal = document.getTotalAmt();
        document.setBusNo(chunkBusNo);
        document.setTotalAmt(chunkTotal);
        try {
            return invokeSingle(task, document, chunkLines, chunkSnapshot, relateNo, partIndex);
        } finally {
            document.setBusNo(originalBusNo);
            document.setTotalAmt(originalTotal);
        }
    }

    private IssuePartOutcome invokeAndParse(
            EinvBossIssueTask task,
            String busNo,
            BigDecimal totalAmt,
            String serviceId,
            String payload,
            List<EinvBossLine> chunkLines,
            int partIndex) {
        long started = System.currentTimeMillis();
        BossInvokeResult invokeResult = issueInvoker.invoke(serviceId, payload);
        int durationMs = (int) (System.currentTimeMillis() - started);
        try {
            invokeLogWriter.write(
                    task, serviceId, busNo, invokeResult, durationMs, payload, invokeResult.getRawMessage());
        } catch (Exception logEx) {
            log.warn("Persist boss invoke log failed, taskId={}", task.getId(), logEx);
        }
        if (!invokeResult.isSuccess()) {
            String rejectDetail = StringUtils.defaultIfBlank(invokeResult.getRawMessage(), invokeResult.getResultCode());
            throw new BusinessException(EinvBossErrorCodes.EINV_BOSS_REJECT, "博思返回: " + rejectDetail);
        }
        BossInvoiceIssueResult issueResult =
                issueOutputParser.toIssueResult(invokeResult, busNo, totalAmt);
        List<EinvBossLine> partLines;
        if (chunkLines == null) {
            partLines = List.of();
        } else {
            partLines = List.copyOf(chunkLines);
        }
        return new IssuePartOutcome(partIndex, busNo, totalAmt, partLines, issueResult, payload);
    }

    private static String withRelateNo(String payload, String relateNo) {
        JSONObject body = JSON.parseObject(payload);
        body.put("eBillRelateNo", relateNo);
        return body.toJSONString();
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
                // 快照单位若已是中文，优先生效（随后 normalizeLineUnits 幂等）
                line.setUnit(snap.getUnit().trim());
            }
        }
    }
}
