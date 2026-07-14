/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 门诊混合预结算命令（bridge enrich 后含 enrichedGroups + selfPayRollup） */
@Data
@Accessors(chain = true)
public class OpPreSettleBatchCommand {

    private Long encounterId;
    private List<Long> chargeItemIds;
    private Long contractId;
    private Long paymentDraftId;
    private String mdtrtCertType;
    private String mdtrtCertNo;
    private String psnNo;
    private String insuplcAdmdvs;
    private String insutype;
    private String acctUsedFlag;

    /** bridge 分组 enrich 后的医保组（remote 模式 HTTP body 传递） */
    private List<OpPreSettleCommand> enrichedGroups;
    /** 纯自费项汇总（不调平台） */
    private OpSelfPayRollup selfPayRollup;
}
