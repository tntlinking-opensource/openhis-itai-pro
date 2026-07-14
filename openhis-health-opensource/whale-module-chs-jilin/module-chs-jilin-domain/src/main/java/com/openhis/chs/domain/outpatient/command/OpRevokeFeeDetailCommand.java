/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2205 门诊费用明细撤销命令 */
@Data
@Accessors(chain = true)
public class OpRevokeFeeDetailCommand {

    private String chrgBchno;
    private Long paymentDraftId;
    private String mdtrtId;
    private String psnNo;
    private String contractBusNo;
    private String insuplcAdmdvs;
    private Long operatorId;
    private Long tenantId;
}
