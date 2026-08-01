/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.support;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.openhis.chs.domain.reconciliation.result.ReconDetailRowResult;
import com.openhis.chs.domain.settlement.constants.ChsSettleEncounterClass;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.entity.YbSettlementReversal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 3202 明细行从本地结算快照 / 撤销凭证转换。
 * <p>退费场景须同时输出正向（{@code refd_setl_flag=0}）与反向（{@code =1}）两行。</p>
 */
public final class ReconDetailRowSupport {

    /** 正常结算（非退费） */
    private static final String REFD_SETL_FLAG_NORMAL = "0";

    /** 退费/撤销反向结算 */
    private static final String REFD_SETL_FLAG_REFUND = "1";

    /** 工具类禁止实例化 */
    private ReconDetailRowSupport() {
    }

    /**
     * 门诊结算快照转为 3202 输入文件行（正向，{@code refd_setl_flag=0}）。
     *
     * @param rows 门诊正式结算记录
     * @return 有效结算行（setl_id / mdtrt_id 非空）
     */
    public static List<ReconDetailRowResult> fromOutpatientRows(List<YbSettlementRecord> rows) {
        List<ReconDetailRowResult> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (YbSettlementRecord row : rows) {
            if (isEmpty(row.getSetlId()) || isEmpty(row.getMdtrtId())) {
                continue;
            }
            result.add(new ReconDetailRowResult()
                .setSetlId(row.getSetlId().trim())
                .setMdtrtId(row.getMdtrtId().trim())
                .setPsnNo(row.getPsnNo())
                .setMedfeeSumamt(row.getMedfeeSumamt())
                .setFundPaySumamt(row.getFundPaySumamt())
                .setAcctPay(row.getAcctPay())
                .setRefdSetlFlag(REFD_SETL_FLAG_NORMAL)
                .setSource("OP"));
        }
        return result;
    }

    /**
     * 住院结算快照转为 3202 输入文件行（正向，{@code refd_setl_flag=0}）。
     *
     * @param rows 住院已结算预结算记录
     * @return 有效结算行（setl_id / mdtrt_id 非空）
     */
    public static List<ReconDetailRowResult> fromInpatientRows(List<YbInpatientPreSettle> rows) {
        List<ReconDetailRowResult> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (YbInpatientPreSettle row : rows) {
            if (isEmpty(row.getSetlId()) || isEmpty(row.getMdtrtId())) {
                continue;
            }
            result.add(new ReconDetailRowResult()
                .setSetlId(row.getSetlId().trim())
                .setMdtrtId(row.getMdtrtId().trim())
                .setPsnNo(row.getPsnNo())
                .setMedfeeSumamt(row.getMedfeeSumamt())
                .setFundPaySumamt(row.getFundPaySumamt())
                .setAcctPay(row.getAcctPay())
                .setRefdSetlFlag(REFD_SETL_FLAG_NORMAL)
                .setSource("IP"));
        }
        return result;
    }

    /**
     * 撤销凭证转为 3202 反向行（{@code refd_setl_flag=1}）。
     * <p>结算 ID 优先用撤销回执 {@code revoke_setl_id}，否则回落原结算 {@code setl_id}；
     * 金额取撤销落库值（一般为正数绝对值）。</p>
     *
     * @param reversals   撤销时间窗内凭证
     * @param settlements 同窗或已装载的原结算（用于补全 setl/mdtrt；可为 null）
     * @return 有效反向行
     */
    public static List<ReconDetailRowResult> fromReversalRows(
            List<YbSettlementReversal> reversals, List<YbSettlementRecord> settlements) {
        List<ReconDetailRowResult> result = new ArrayList<>();
        if (reversals == null || reversals.isEmpty()) {
            return result;
        }
        Map<Long, YbSettlementRecord> byId = indexSettlements(settlements);
        for (YbSettlementReversal reversal : reversals) {
            YbSettlementRecord origin = byId.get(reversal.getSettlementRecordId());
            String originSetlId = null;
            String originMdtrtId = null;
            String originPsnNo = null;
            if (origin != null) {
                originSetlId = origin.getSetlId();
                originMdtrtId = origin.getMdtrtId();
                originPsnNo = origin.getPsnNo();
            }
            String setlId = firstNonEmpty(reversal.getRevokeSetlId(), originSetlId, null);
            String mdtrtId = firstNonEmpty(reversal.getMdtrtId(), originMdtrtId, null);
            if (isEmpty(setlId) || isEmpty(mdtrtId)) {
                continue;
            }
            String psnNo = firstNonEmpty(reversal.getPsnNo(), originPsnNo, null);
            String source = resolveReversalSource(reversal, origin);
            result.add(new ReconDetailRowResult()
                .setSetlId(setlId.trim())
                .setMdtrtId(mdtrtId.trim())
                .setPsnNo(psnNo)
                .setMedfeeSumamt(reversal.getMedfeeSumamt())
                .setFundPaySumamt(reversal.getFundPaySumamt())
                .setAcctPay(reversal.getAcctPay())
                .setRefdSetlFlag(REFD_SETL_FLAG_REFUND)
                .setSource(source));
        }
        return result;
    }

    /**
     * 将结算列表建为 id 索引，便于撤销行回查。
     *
     * @param settlements 结算回执；null 视为空
     * @return id → 回执
     */
    private static Map<Long, YbSettlementRecord> indexSettlements(List<YbSettlementRecord> settlements) {
        Map<Long, YbSettlementRecord> byId = new HashMap<>();
        if (settlements == null) {
            return byId;
        }
        for (YbSettlementRecord row : settlements) {
            if (row == null || row.getId() == null) {
                continue;
            }
            byId.put(row.getId(), row);
        }
        return byId;
    }

    /**
     * 解析撤销行来源标记：优先撤销表就诊类别，其次原结算，默认 OP。
     *
     * @param reversal 撤销凭证
     * @param origin   原结算；可为 null
     * @return {@code OP} / {@code IP}
     */
    private static String resolveReversalSource(YbSettlementReversal reversal, YbSettlementRecord origin) {
        String encounterClass = reversal.getEncounterClass();
        if (isEmpty(encounterClass) && origin != null) {
            encounterClass = origin.getEncounterClass();
        }
        if (isNotEmpty(encounterClass) && ChsSettleEncounterClass.IMP.equals(encounterClass.trim())) {
            return "IP";
        }
        return "OP";
    }

    /**
     * 按优先级取第一个非空字符串。
     *
     * @param first  首选
     * @param second 次选
     * @param third  末选
     * @return 首个非空；皆空则 null
     */
    private static String firstNonEmpty(String first, String second, String third) {
        if (isNotEmpty(first)) {
            return first;
        }
        if (isNotEmpty(second)) {
            return second;
        }
        if (isNotEmpty(third)) {
            return third;
        }
        return null;
    }
}
