/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** HIS 收银结算完成后的医保挂接触发载荷（PaymentSettled 事件 → Listener）。 */
@Getter
@Setter
public class PaymentSettlementTrigger {

    /** 收银 payment 主键。 */
    private Long paymentId;
    /** 就诊 id。 */
    private Long encounterId;
    /** 本次结算涉及的 chargeItem id 列表（逗号分隔字符串）。 */
    private String chargeItemIds;
    /** 收银展示金额。 */
    private BigDecimal displayAmount;
    /** 账单日期。 */
    private LocalDateTime billDate;
    /** 录入人 id。 */
    private Long entererId;
    /** true 住院 / false 门诊。 */
    private boolean inpatient;
    /** 本次收银关联的医保结算凭证行（可多组混合结算）。 */
    private List<PaymentYbSettlementTriggerItem> ybSettlements;
}
