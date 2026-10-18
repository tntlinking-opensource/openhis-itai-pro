/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 2302 住院费用明细撤销命令 */
@Data
@Accessors(chain = true)
public class IpRevokeFeeDetailCommand {

    private Long encounterId;
    private Long contractId;
    private String contractBusNo;
    private String mdtrtId;
    private String psnNo;
    private String insuplcAdmdvs;
    private List<Long> chargeItemIds;
    private Long operatorId;
    private Long tenantId;
}
