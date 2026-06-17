/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 门诊医保混合预结算命令（含医保+自费全量 chargeItemIds）。 */
@Data
@Accessors(chain = true)
public class OpInsurancePreSettleBatchCommand {

    private Long encounterId;
    private List<Long> chargeItemIds;
    private Long contractId;
    private Long paymentDraftId;
    private String mdtrtCertType;
    private String mdtrtCertNo;
    private String psnNo;
    private String insuplcAdmdvs;
    private String insutype;
}
