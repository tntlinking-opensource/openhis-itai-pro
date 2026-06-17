/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 门诊医保预结算命令（health-application → adapter）。 */
@Data
@Accessors(chain = true)
public class OpInsurancePreSettleCommand {

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
}
