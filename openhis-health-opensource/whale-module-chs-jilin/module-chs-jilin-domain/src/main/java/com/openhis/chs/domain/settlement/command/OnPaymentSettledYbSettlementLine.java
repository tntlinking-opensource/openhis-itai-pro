/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement.command;

import lombok.Getter;
import lombok.Setter;

/** 收银落账挂接命令中的单条医保结算凭证行 */
@Getter
@Setter
public class OnPaymentSettledYbSettlementLine {

    /** 平台结算 ID */
    private String setlId;
    /** 收费批次号 */
    private String chrgBchno;
    /** 平台就诊 ID */
    private String mdtrtId;
    /** 本地结算回执 ID */
    private Long settlementRecordId;
    /** HIS 账户 ID */
    private Long accountId;
    /** 凭证状态（与 health 侧约定字符串） */
    private String status;
}
