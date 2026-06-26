/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.querysupport.financial;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 住院结算摘要读结果（IF-IP-STL-03）。
 */
@Data
@Accessors(chain = true)
public class InpatientSettleSummaryResult {

    private Long encounterId;
    private Long patientId;
    private String encounterBusNo;
    private String patientName;
    private String organizationName;
    private String wardName;
    private String bedName;
    private String statusEnum;
    private String statusEnumText;
    private BigDecimal pendingSettlementTotal;
    private BigDecimal prepayNetBalance;
    private BigDecimal selectedTotal;
    private BigDecimal balanceHint;
    private BigDecimal displayAccountBalance;
    private String accountBalanceDisplayMode;
    private BigDecimal availableBalance;
    private String accountBalanceSummaryText;
    private String accountBalanceSubText;
    private Boolean lowBalanceWarning;
}
