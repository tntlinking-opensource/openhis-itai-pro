/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.support;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 住院结算摘要读结果（IF-IP-STL-03）。
 */
@Data
@Accessors(chain = true)
public class InpatientSettleSummaryResult {

    /** 住院就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 住院号/就诊业务号。 */
    private String encounterBusNo;

    /** 患者姓名。 */
    private String patientName;

    /** 入院科室展示名。 */
    private String organizationName;

    /** 当前病区展示名。 */
    private String wardName;

    /** 当前床位展示名。 */
    private String bedName;

    /** 就诊状态 code。 */
    private String statusEnum;

    /** 就诊状态展示（含 ON_HOLD 转科/出院分流）。 */
    private String statusEnumText;

    /** 本就诊待结算 chargeItem 总额。 */
    private BigDecimal pendingSettlementTotal;

    /** 预交金账户净余额（原始账户余额，非展示口径）。 */
    private BigDecimal prepayNetBalance;

    /** 当前勾选待结算合计；未传勾选 id 时等于 pendingSettlementTotal。 */
    private BigDecimal selectedTotal;

    /** 余额提示：账户余额减 selectedTotal（6 位计算刻度）。 */
    private BigDecimal balanceHint;

    /** 账户可用余额展示值（经 InpatientAccountBalanceDisplayAssembler 口径）。 */
    private BigDecimal displayAccountBalance;

    /** 账户余额展示模式 code（供前端切换文案/样式）。 */
    private String accountBalanceDisplayMode;

    /** 可用余额（与 displayAccountBalance 同源，结构化字段）。 */
    private BigDecimal availableBalance;

    /** 账户余额主文案。 */
    private String accountBalanceSummaryText;

    /** 账户余额副文案（低余额/反结待重结等补充说明）。 */
    private String accountBalanceSubText;

    /** 是否触发低余额预警。 */
    private Boolean lowBalanceWarning;
}
