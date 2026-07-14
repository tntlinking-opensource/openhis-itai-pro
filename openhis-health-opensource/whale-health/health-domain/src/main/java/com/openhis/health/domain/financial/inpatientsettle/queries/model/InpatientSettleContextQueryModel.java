/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.inpatientsettle.queries.model;

import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 住院结算读路径 L1 上下文：住院就诊展示、登记账户与 charge/payment 汇总（IF-IP-STL-03 共用 batch）。
 */
@Getter
@Setter
public class InpatientSettleContextQueryModel {

    /** 住院就诊上下文（患者/科室/床位等展示列）。 */
    private InpatientEncounterContextQueryModel inpatientContext;

    /** 就诊下有效个人现金登记账户。 */
    private Account registrationAccount;

    /** 待结算费用合计（charge_item 口径）。 */
    private BigDecimal pendingSettlementTotal;

    /** 已结算费用合计（charge_item 口径）。 */
    private BigDecimal settledTotal;

    /** 是否处于反结待重结（payment 流水判定）。 */
    private boolean reversedPendingResettle;
}
