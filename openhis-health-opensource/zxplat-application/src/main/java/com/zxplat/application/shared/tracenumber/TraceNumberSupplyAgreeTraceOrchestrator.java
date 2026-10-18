/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.tracenumber;

import com.zxplat.domain.custom.tracenumber.queries.model.TraceNumberRecordQueryModel;
import com.zxplat.domain.custom.tracenumber.service.TraceNumberQueryService;
import com.zxplat.domain.share.enums.SupplyType;
import com.zxplat.domain.share.enums.TraceDirection;
import com.zxplat.domain.share.product.MedicationProductUnitConversionSource;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 供应单同意路径追溯写编排 Support（AS-CUS-TRC-001 · IF-TRC-05～13；盘点/损益见 {@link TraceNumberStocktakeGainLossTraceOrchestrator}）。
 *
 * <p>封装单端 HOSPITAL_IO 与成对 INTERNAL_TRANSFER 写入，供库房/药房/科室库存 AppService 调用。
 * 消耗/流转侧扫码可选：盖不住则跳过写主表，不挡同意主流程；进货 IN 硬 Gate 不在本类软化。</p>
 */
@Component
@RequiredArgsConstructor
public class TraceNumberSupplyAgreeTraceOrchestrator {

    /** 追溯写侧编排（含逗号串展开）。 */
    private final TraceNumberRecordOrchestrator traceNumberRecordOrchestrator;

    /** 品批是否有主档 / 覆盖判定 / OUT 流水镜像。 */
    private final TraceNumberQueryService traceNumberQueryService;

    /**
     * 单端供应行写追溯（进货 IN、退供应商 OUT、科室领用 OUT、退领 IN 等）。
     *
     * @param cmd 供应写编排命令；traceNos 空白时跳过
     */
    public void recordSingleEndLine(RecordTraceFromSupplyLineSpec cmd) {
        // 命令为空：无供应行可写
        if (cmd == null) {
            return;
        }
        // 退领镜像：显式每码可无 traceNos 字符串
        boolean hasExplicit =
            cmd.getExplicitPerCodeSplits() != null && !cmd.getExplicitPerCodeSplits().isEmpty();
        // 既无显式每码、又无扫码串：本行跳过写追溯
        if (!hasExplicit && trimToNull(cmd.getTraceNos()) == null) {
            return;
        }
        // OUT 且无码批：业务行可有码，不写主表流水（与发药 Option A 一致）
        if (TraceDirection.OUT.getCode().equals(cmd.getRecordDirectionEnum())
            && !this.traceNumberQueryService.existsMasterByItemLot(
                cmd.getItemTable(), cmd.getItemId(), cmd.getLotNumber())) {
            return;
        }
        // 领用等消耗 OUT：盖不住则跳过写追溯，不挡同意
        if (this.isConsumeOutSoftSkipCandidate(cmd) && !hasExplicit) {
            List<String> codes = this.traceNumberRecordOrchestrator.splitTraceNos(cmd.getTraceNos());
            // 已扫集合剩余合计盖不住本次拆零：软跳过
            if (!this.traceNumberQueryService.isRemainingSplitSufficientForDispenseOut(
                codes, cmd.getQuantity(), cmd.getUnitCode(), cmd.getUnitConversionSource())) {
                return;
            }
        }
        this.traceNumberRecordOrchestrator.recordFromSupplyLine(cmd);
    }

    /**
     * 批量单端写追溯。
     *
     * @param commands 命令列表
     */
    public void recordSingleEndLines(List<RecordTraceFromSupplyLineSpec> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (RecordTraceFromSupplyLineSpec cmd : commands) {
            this.recordSingleEndLine(cmd);
        }
    }

    /**
     * 科室退领 IN：按源领用 {@code originalBusNo} 的 OUT 每码实扣镜像加回（DD §5）。
     *
     * <p>无源 OUT（原领用未扫）→ 跳过。整行退量 ≥ OUT 合计则全额镜像；否则 OUT 逆序耗尽。</p>
     *
     * @param cmd            退领写命令（含本单 busNo、库位、换算源等）
     * @param originalBusNo  源领用单号
     * @param returnSplitQty 本次退领拆零量
     */
    public void recordDeptIssueReturnMirroredIn(
        RecordTraceFromSupplyLineSpec cmd,
        String originalBusNo,
        BigDecimal returnSplitQty
    ) {
        // 命令为空或无源领用单号：无法按 OUT 镜像加回
        if (cmd == null || trimToNull(originalBusNo) == null) {
            return;
        }
        // 退领拆零为空或非正：无可加回量
        if (returnSplitQty == null || returnSplitQty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        // 无码批：不写主表
        if (!this.traceNumberQueryService.existsMasterByItemLot(
            cmd.getItemTable(), cmd.getItemId(), cmd.getLotNumber())) {
            return;
        }
        List<TraceNumberRecordQueryModel> outRecords =
            this.traceNumberQueryService.listHospitalIoOutRecordsByBusNoAndOperationTypes(
                originalBusNo.trim(),
                List.of(SupplyType.DEPT_ISSUE.getCode())
            );
        // 仅保留本品批 OUT；再与退领行码串求交（有码串时）
        List<TraceNumberRecordQueryModel> matched = this.filterOutRecordsForReturnLine(outRecords, cmd);
        // 源领用未扫或本品批无 OUT：跳过退领 IN
        if (matched.isEmpty()) {
            return;
        }
        List<TraceNumberRecordOrchestrator.ExplicitPerCodeSplit> mirrored =
            this.buildMirrorSplits(matched, returnSplitQty);
        // 镜像拆零为空（OUT 量无效）：不写 IN
        if (mirrored.isEmpty()) {
            return;
        }
        cmd.setExplicitPerCodeSplits(mirrored);
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < mirrored.size(); i++) {
            // 多码用逗号拼接可读冗余串
            if (i > 0) {
                joined.append(',');
            }
            joined.append(mirrored.get(i).getTraceNo());
        }
        cmd.setTraceNos(joined.toString());
        cmd.setSupplyTypeCode(SupplyType.DEPT_ISSUE_RETURN.getCode());
        cmd.setRecordDirectionEnum(TraceDirection.IN.getCode());
        this.traceNumberRecordOrchestrator.recordFromSupplyLine(cmd);
    }

    /**
     * 调拨/出库/退库成对 OUT+IN 写追溯（IF-TRC-07～11）。
     *
     * <p>仅保留主表已有该品批码的行；无码批只落供应单业务 {@code trace_no}。</p>
     *
     * @param cmd 成对写编排命令
     */
    public void recordTransferPair(RecordTraceTransferPairSpec cmd) {
        if (cmd == null || cmd.getLines() == null || cmd.getLines().isEmpty()) {
            return;
        }
        List<RecordTraceTransferPairSpec.TransferLine> hardLines = new ArrayList<>();
        for (RecordTraceTransferPairSpec.TransferLine line : cmd.getLines()) {
            if (line == null) {
                continue;
            }
            // 无码批跳过主表成对写
            if (!this.traceNumberQueryService.existsMasterByItemLot(
                line.getItemTable(), line.getItemId(), line.getLotNumber())) {
                continue;
            }
            hardLines.add(line);
        }
        if (hardLines.isEmpty()) {
            return;
        }
        cmd.setLines(hardLines);
        this.traceNumberRecordOrchestrator.recordTransferPair(cmd);
    }

    /**
     * 从逗号串构建调拨成对明细：已扫集合内剩余先出（DD §3.2 P1）；盖不住返回空（调用方跳过写）。
     *
     * @param traceNoRaw           逗号串
     * @param itemTable            品项表
     * @param itemId               品项 id
     * @param lotNumber            批号
     * @param splitQuantity        拆零数量
     * @param splitUnitCode        拆零单位
     * @param unitConversionSource 换算源
     * @return 成对明细行；盖不住为空列表
     */
    public List<RecordTraceTransferPairSpec.TransferLine> buildTransferLinesFromTraceNoString(
        String traceNoRaw,
        String itemTable,
        Long itemId,
        String lotNumber,
        BigDecimal splitQuantity,
        String splitUnitCode,
        MedicationProductUnitConversionSource unitConversionSource
    ) {
        List<RecordTraceTransferPairSpec.TransferLine> lines = new ArrayList<>();
        if (StringUtils.isEmpty(traceNoRaw)) {
            return lines;
        }
        List<String> codes = this.traceNumberRecordOrchestrator.splitTraceNos(traceNoRaw);
        if (codes.isEmpty()) {
            return lines;
        }
        // 无码批：不写主表成对（调用方仍可落业务码）
        if (!this.traceNumberQueryService.existsMasterByItemLot(itemTable, itemId, lotNumber)) {
            return lines;
        }
        Optional<List<TraceNumberQueryService.DispenseOutPerCodeSplit>> allocated =
            this.traceNumberQueryService.allocateRemainingFirstSplitForDispenseOut(
                codes, splitQuantity, splitUnitCode, unitConversionSource);
        // 盖不住：软跳过，不挡调拨同意
        if (allocated.isEmpty()) {
            return lines;
        }
        for (TraceNumberQueryService.DispenseOutPerCodeSplit row : allocated.get()) {
            RecordTraceTransferPairSpec.TransferLine line = new RecordTraceTransferPairSpec.TransferLine();
            line.setTraceNo(row.getTraceNo());
            line.setItemTable(itemTable);
            line.setItemId(itemId);
            line.setLotNumber(lotNumber);
            line.setQuantity(row.getSplitQuantity());
            line.setUnitCode(splitUnitCode);
            line.setUnitConversionSource(unitConversionSource);
            lines.add(line);
        }
        return lines;
    }

    /**
     * 领用 OUT 是否走消耗侧软跳过。
     *
     * @param cmd 供应写命令
     * @return 领用 OUT 为 true
     */
    private boolean isConsumeOutSoftSkipCandidate(RecordTraceFromSupplyLineSpec cmd) {
        // 命令为空：非消耗软跳过候选
        if (cmd == null) {
            return false;
        }
        // 非 OUT 方向：进货 IN 等硬 Gate，不走消耗软跳过
        if (!TraceDirection.OUT.getCode().equals(cmd.getRecordDirectionEnum())) {
            return false;
        }
        // 仅科室领用 OUT 与发药同属 P0 消耗可选扫
        return SupplyType.DEPT_ISSUE.getCode().equals(cmd.getSupplyTypeCode());
    }

    /**
     * 退领行过滤源 OUT：本品批；若退领行有码串则只保留这些码。
     *
     * @param outRecords 源领用 OUT
     * @param cmd        退领命令
     * @return 过滤后 OUT
     */
    private List<TraceNumberRecordQueryModel> filterOutRecordsForReturnLine(
        List<TraceNumberRecordQueryModel> outRecords,
        RecordTraceFromSupplyLineSpec cmd
    ) {
        List<TraceNumberRecordQueryModel> matched = new ArrayList<>();
        Set<String> codeFilter = new HashSet<>();
        // 退领行带码串：只镜像这些码对应的源 OUT
        if (StringUtils.isNotEmpty(cmd.getTraceNos())) {
            for (String code : this.traceNumberRecordOrchestrator.splitTraceNos(cmd.getTraceNos())) {
                codeFilter.add(code);
            }
        }
        String wantLot;
        // 退领行批号空：按空串与 OUT 批号对齐
        if (cmd.getLotNumber() == null) {
            wantLot = "";
        } else {
            wantLot = cmd.getLotNumber().trim();
        }
        for (TraceNumberRecordQueryModel out : outRecords) {
            // 空 OUT 或无码：不参与退领镜像
            if (out == null || StringUtils.isEmpty(out.getTraceNo())) {
                continue;
            }
            String outLot;
            // OUT 批号空：按空串参与本品批匹配
            if (out.getLotNumber() == null) {
                outLot = "";
            } else {
                outLot = out.getLotNumber().trim();
            }
            // 批号不一致：非本退领行对应的 OUT
            if (!wantLot.equals(outLot)) {
                continue;
            }
            // 有码串过滤时：不在退领码集合内则跳过
            if (!codeFilter.isEmpty() && !codeFilter.contains(out.getTraceNo().trim())) {
                continue;
            }
            matched.add(out);
        }
        return matched;
    }

    /**
     * 按 OUT 流水构建镜像加回量（整单全额 / 部分逆序耗尽）。
     *
     * @param outRecords     OUT 读模型（id 升序）
     * @param returnSplitQty 退领拆零量
     * @return 显式每码拆零
     */
    private List<TraceNumberRecordOrchestrator.ExplicitPerCodeSplit> buildMirrorSplits(
        List<TraceNumberRecordQueryModel> outRecords,
        BigDecimal returnSplitQty
    ) {
        BigDecimal outTotal = BigDecimal.ZERO;
        for (TraceNumberRecordQueryModel out : outRecords) {
            // 空行或拆零缺失：不计入 OUT 合计
            if (out == null || out.getSplitQuantity() == null) {
                continue;
            }
            outTotal = outTotal.add(out.getSplitQuantity());
        }
        // 整行退：全额镜像
        if (returnSplitQty.compareTo(outTotal) >= 0) {
            List<TraceNumberRecordOrchestrator.ExplicitPerCodeSplit> full = new ArrayList<>();
            for (TraceNumberRecordQueryModel out : outRecords) {
                // 拆零为空或非正：不生成加回行
                if (out == null || out.getSplitQuantity() == null
                    || out.getSplitQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                TraceNumberRecordOrchestrator.ExplicitPerCodeSplit row =
                    new TraceNumberRecordOrchestrator.ExplicitPerCodeSplit();
                row.setTraceNo(out.getTraceNo());
                row.setSplitQuantity(out.getSplitQuantity());
                full.add(row);
            }
            return full;
        }
        // 部分退：OUT 逆序耗尽
        List<TraceNumberRecordQueryModel> reverse = new ArrayList<>(outRecords);
        Collections.reverse(reverse);
        BigDecimal remain = returnSplitQty;
        List<TraceNumberRecordOrchestrator.ExplicitPerCodeSplit> partial = new ArrayList<>();
        for (TraceNumberRecordQueryModel out : reverse) {
            // 退量已耗尽：停止逆序加回
            if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            // 拆零为空或非正：跳过该 OUT
            if (out == null || out.getSplitQuantity() == null
                || out.getSplitQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal take = out.getSplitQuantity();
            // 本码 OUT 超过剩余退量：只加回剩余部分
            if (take.compareTo(remain) > 0) {
                take = remain;
            }
            TraceNumberRecordOrchestrator.ExplicitPerCodeSplit row =
                new TraceNumberRecordOrchestrator.ExplicitPerCodeSplit();
            row.setTraceNo(out.getTraceNo());
            row.setSplitQuantity(take);
            partial.add(row);
            remain = remain.subtract(take);
        }
        return partial;
    }
}
