/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.invoke.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * submitInvokeResult 响应。
 *
 * <p>单步成功且 phase 未完成时 {@code nextAction=PREPARE_NEXT}；整链完成时 {@code COMPLETED}。</p>
 */
@Data
@Accessors(chain = true)
public class SubmitWorkInjuryInvokeResultResponse {

    /** 会话 id。 */
    private String sessionId;
    /** 下一步动作码。 */
    private String nextAction;
    /** submit 失败或 Win 报错摘要。 */
    private String errMsg;
    /** 可读摘要（9102 行数、2206 金额等）。 */
    private String summary;

    /** 2207 落账后本地回执 id（OP_SETTLE 完成时）。 */
    private Long settlementRecordId;
    /** 平台 setl_id（2207）。 */
    private String setlId;
    /** 收费批次号 chrg_bchno。 */
    private String chrgBchno;
    /** 医疗费总额。 */
    private BigDecimal medfeeSumamt;
    /** 基金支付总额。 */
    private BigDecimal fundPaySumamt;
    /** 个人现金支出。 */
    private BigDecimal psnCashPay;
}
