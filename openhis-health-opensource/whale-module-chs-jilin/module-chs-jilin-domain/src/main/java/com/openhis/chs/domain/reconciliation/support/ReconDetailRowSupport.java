/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.support;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

import com.openhis.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.openhis.chs.domain.reconciliation.result.ReconDetailRowResult;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;

import java.util.ArrayList;
import java.util.List;

/** 3202 明细行从本地结算快照转换 */
public final class ReconDetailRowSupport {

    private static final String REFD_SETL_FLAG_NORMAL = "0";

    /** 工具类禁止实例化 */
    private ReconDetailRowSupport() {
    }

    /**
     * 门诊结算快照转为 3202 输入文件行。
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
     * 住院结算快照转为 3202 输入文件行。
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
}
