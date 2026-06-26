/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 门诊混合预结算命令（含医保+自费全量 chargeItemIds，Port 内部分组）。 */
@Data
@Accessors(chain = true)
public class OpInsurancePreSettleBatchCommand {

    /** 就诊 id。 */
    private Long encounterId;
    /** 本次收银全部收费项（医保+自费）。 */
    private List<Long> chargeItemIds;
    /** 主医保费用性质 id（分组内各 account 可不同）。 */
    private Long contractId;
    /** 收银草稿 id。 */
    private Long paymentDraftId;
    /** 就诊凭证类型。 */
    private String mdtrtCertType;
    /** 就诊凭证号码。 */
    private String mdtrtCertNo;
    /** 人员编号。 */
    private String psnNo;
    /** 参保地医保区划。 */
    private String insuplcAdmdvs;
    /** 险种类型。 */
    private String insutype;
}
