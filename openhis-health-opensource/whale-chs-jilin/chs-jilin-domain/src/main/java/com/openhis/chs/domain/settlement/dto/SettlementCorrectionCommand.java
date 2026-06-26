/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.settlement.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2601 冲正命令（omsgid + oinfno）。 */
@Data
@Accessors(chain = true)
public class SettlementCorrectionCommand {

    private String contractBusNo;
    private String psnNo;
    /** 原交易 msgid（如 2207 的 medins_setl_id / msgid_2207）。 */
    private String omsgid;
    /** 原交易编号（2207 / 2208 / 2304 / 2305 等）。 */
    private String oinfno;
    private Long settlementRecordId;
    private Long operatorId;
    private Long tenantId;
}
