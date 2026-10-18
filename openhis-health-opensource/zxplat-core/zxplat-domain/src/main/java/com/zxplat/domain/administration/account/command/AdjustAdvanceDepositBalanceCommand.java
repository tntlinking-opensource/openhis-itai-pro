/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.account.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 住院预交金结算成功后调整就诊个人现金户余额（DM-FIN-002 §9.1、BR-IP-PREP-005）。
 */
@Getter
@Setter
public class AdjustAdvanceDepositBalanceCommand {

    /** 患者主键。 */
    private Long patientId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 余额增量：收取为正、退费为负。 */
    private BigDecimal balanceDelta;
    /** 操作人主键，可空。 */
    private Long operatorId;
}
