/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.audit.mapper;

import com.zxplat.plugin.chs.domain.audit.result.AuditAnalyzeResult;
import com.zxplat.plugin.chs.domain.audit.result.AuditViolationDetailResult;
import com.zxplat.plugin.chs.domain.audit.result.AuditViolationResult;
import com.zxplat.plugin.chs.gateway.scheme.audit.model.Audit3101DetailRow;
import com.zxplat.plugin.chs.gateway.scheme.audit.model.Audit3101Output;
import com.zxplat.plugin.chs.gateway.scheme.audit.model.Audit3101ViolationRow;

import java.util.ArrayList;
import java.util.List;

/**
 * 【3101】scheme Output → domain Result。
 */
public final class Audit3101ResultMapper {

    private Audit3101ResultMapper() {
    }

    /**
     * 映射平台解析结果为领域结果。
     *
     * @param output        解析后的 output
     * @param taskId        任务 ID 回显
     * @param trigScen      触发场景回显
     * @param contractBusNo 契约回显
     * @return domain Result
     */
    public static AuditAnalyzeResult toResult(
            Audit3101Output output,
            String taskId,
            String trigScen,
            String contractBusNo) {
        AuditAnalyzeResult result = new AuditAnalyzeResult();
        result.setTaskId(taskId);
        result.setTrigScen(trigScen);
        result.setContractBusNo(contractBusNo);
        result.setViolations(new ArrayList<>());
        if (output == null || output.getResult() == null || output.getResult().isEmpty()) {
            return result;
        }
        for (Audit3101ViolationRow row : output.getResult()) {
            if (row == null) {
                continue;
            }
            AuditViolationResult violation = new AuditViolationResult();
            violation.setJrId(row.getJrId());
            violation.setRuleId(row.getRuleId());
            violation.setRuleName(row.getRuleName());
            violation.setVolaCont(row.getVolaCont());
            violation.setPatnId(row.getPatnId());
            violation.setMdtrtId(row.getMdtrtId());
            violation.setVolaAmt(row.getVolaAmt());
            violation.setVolaAmtStas(row.getVolaAmtStas());
            violation.setSevDeg(row.getSevDeg());
            violation.setVolaEvid(row.getVolaEvid());
            violation.setVolaBhvrType(row.getVolaBhvrType());
            violation.setDetails(mapDetails(row.getJudgeResultDetailDtos()));
            result.getViolations().add(violation);
        }
        return result;
    }

    /**
     * 平台 judge_result_detail_dtos → 领域明细（含 jrdId，供 3103 warn_rslt_id）。
     *
     * @param rows 平台明细行
     * @return 领域明细
     */
    private static List<AuditViolationDetailResult> mapDetails(List<Audit3101DetailRow> rows) {
        List<AuditViolationDetailResult> list = new ArrayList<>();
        if (rows == null) {
            return list;
        }
        for (Audit3101DetailRow row : rows) {
            if (row == null) {
                continue;
            }
            AuditViolationDetailResult detail = new AuditViolationDetailResult();
            detail.setJrdId(row.getJrdId());
            detail.setPatnId(row.getPatnId());
            detail.setMdtrtId(row.getMdtrtId());
            detail.setRxId(row.getRxId());
            detail.setVolaItemType(row.getVolaItemType());
            detail.setVolaAmt(row.getVolaAmt());
            list.add(detail);
        }
        return list;
    }
}
