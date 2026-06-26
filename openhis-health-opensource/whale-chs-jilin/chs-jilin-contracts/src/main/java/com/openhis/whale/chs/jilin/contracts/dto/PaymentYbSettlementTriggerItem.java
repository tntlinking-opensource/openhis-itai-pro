/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.dto;

import lombok.Getter;
import lombok.Setter;

/** 收银结算挂接触发载荷中的单条医保凭证（对应一组 chrg_bchno）。 */
@Getter
@Setter
public class PaymentYbSettlementTriggerItem {

    /** 平台结算 ID setl_id。 */
    private String setlId;
    /** 收费批次号 chrg_bchno。 */
    private String chrgBchno;
    /** 平台就诊 ID mdtrt_id。 */
    private String mdtrtId;
    /** 本地结算回执 {@code yb_settlement_record.id}。 */
    private Long settlementRecordId;
    /** 费用账户 id（慢病多账户场景）。 */
    private Long accountId;
    /** 结算状态（ACTIVE / REVOKED 等）。 */
    private String status;
}
