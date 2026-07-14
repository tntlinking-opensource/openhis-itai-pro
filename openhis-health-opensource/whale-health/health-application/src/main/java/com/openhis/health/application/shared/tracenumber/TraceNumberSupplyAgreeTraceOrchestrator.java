/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.tracenumber;

import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 供应单同意路径追溯写编排 Support（AS-CUS-TRC-001 · IF-TRC-05～13；盘点/损益见 {@link TraceNumberStocktakeGainLossTraceOrchestrator}）。
 *
 * <p>封装单端 HOSPITAL_IO 与成对 INTERNAL_TRANSFER 写入，供库房/药房/科室库存 AppService 调用。</p>
 */
@Component
@RequiredArgsConstructor
public class TraceNumberSupplyAgreeTraceOrchestrator {

    /** 追溯写侧编排（含逗号串展开）。 */
    private final TraceNumberRecordOrchestrator traceNumberRecordOrchestrator;

    /**
     * 单端供应行写追溯（进货 IN、退供应商 OUT、科室领用 OUT、退领 IN 等）。
     *
     * @param cmd 供应写编排命令；traceNos 空白时跳过
     */
    public void recordSingleEndLine(RecordTraceFromSupplyLineSpec cmd) {
        if (cmd == null || trimToNull(cmd.getTraceNos()) == null) {
            return;
        }
        traceNumberRecordOrchestrator.recordFromSupplyLine(cmd);
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
     * 调拨/出库/退库成对 OUT+IN 写追溯（IF-TRC-07～11）。
     *
     * @param cmd 成对写编排命令
     */
    public void recordTransferPair(RecordTraceTransferPairSpec cmd) {
        if (cmd == null || cmd.getLines() == null || cmd.getLines().isEmpty()) {
            return;
        }
        traceNumberRecordOrchestrator.recordTransferPair(cmd);
    }

    /**
     * 从逗号串构建调拨成对明细（每码一行，数量均分）。
     *
     * @param traceNoRaw           逗号串
     * @param itemTable            品项表
     * @param itemId               品项 id
     * @param lotNumber            批号
     * @param splitQuantity        拆零数量
     * @param splitUnitCode        拆零单位
     * @param unitConversionSource 换算源
     * @return 成对明细行
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
        List<String> codes = traceNumberRecordOrchestrator.splitTraceNos(traceNoRaw);
        BigDecimal perCodeQty = splitQuantity;
        if (perCodeQty != null && codes.size() > 1) {
            perCodeQty = perCodeQty.divide(
                BigDecimal.valueOf(codes.size()), 6, java.math.RoundingMode.HALF_UP);
        }
        for (String code : codes) {
            RecordTraceTransferPairSpec.TransferLine line = new RecordTraceTransferPairSpec.TransferLine();
            line.setTraceNo(code);
            line.setItemTable(itemTable);
            line.setItemId(itemId);
            line.setLotNumber(lotNumber);
            line.setQuantity(perCodeQty);
            line.setUnitCode(splitUnitCode);
            line.setUnitConversionSource(unitConversionSource);
            lines.add(line);
        }
        return lines;
    }
}
