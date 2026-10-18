/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 。301→303】住院预结算命令 */
@Data
@Accessors(chain = true)
public class IpPreSettleCommand {

    private Long encounterId;
    private List<Long> chargeItemIds;
    private Long contractId;
    private Long paymentDraftId;
    private String mdtrtCertType;
    private String mdtrtCertNo;
    private String psnNo;
    private String insuplcAdmdvs;
    private String insutype;
    private String medType;
    private String settleMode;
    private String acctUsedFlag;
    private String psnSetlway;
    /**
     * 医护站无卡试算。为 true 时预结算不要求就诊凭证号，人员信息从入院资料回填。
     */
    private Boolean cardlessTrial;
}
