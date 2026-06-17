/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port;

import com.openhis.whale.chs.jilin.port.model.IpInsuranceAdmissionCommand;
import com.openhis.whale.chs.jilin.port.model.IpInsuranceAdmissionResult;
import com.openhis.whale.chs.jilin.port.model.IpInsuranceConfirmCommand;
import com.openhis.whale.chs.jilin.port.model.IpInsuranceConfirmResult;
import com.openhis.whale.chs.jilin.port.model.IpInsuranceEncounterCommand;
import com.openhis.whale.chs.jilin.port.model.IpInsurancePreSettleCommand;
import com.openhis.whale.chs.jilin.port.model.IpInsurancePreSettleResult;
import com.openhis.whale.chs.jilin.port.model.IpInsuranceRevokeFeeDetailCommand;
import com.openhis.whale.chs.jilin.port.model.IpInsuranceRevokeFeeDetailResult;
import com.openhis.whale.chs.jilin.port.model.IpInsuranceRevokeSettleCommand;
import com.openhis.whale.chs.jilin.port.model.IpInsuranceRevokeSettleResult;

import java.util.List;

/** 住院结算 ↔ 医保 Port（Wave 1：2301→2303→2304；Wave 2：2302/2305/2403–2405）。 */
public interface InpatientInsuranceSettlementPort {

    boolean isInsuranceContract(Long contractId);

    IpInsuranceAdmissionResult ensureAdmission(IpInsuranceAdmissionCommand command);

    IpInsurancePreSettleResult preSettle(IpInsurancePreSettleCommand command);

    IpInsuranceConfirmResult confirmBeforeLocalSettle(IpInsuranceConfirmCommand command);

    List<IpInsurancePreSettleResult> listActivePreSettlesByPaymentDraft(Long paymentDraftId);

    /** 按成功支付单查最近一条 SETTLED 预结算快照（取消结算用）。 */
    IpInsurancePreSettleResult findSettledPreSettleByPayment(Long paymentId);

    IpInsuranceRevokeSettleResult revokeSettle(IpInsuranceRevokeSettleCommand command);

    IpInsuranceRevokeFeeDetailResult revokeFeeDetails(IpInsuranceRevokeFeeDetailCommand command);

    void revokeAdmission(IpInsuranceEncounterCommand command);

    void revokeDischarge(IpInsuranceEncounterCommand command);

    void changeInpatientInfo(IpInsuranceAdmissionCommand command);
}
