/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.client;

import com.openhis.whale.chs.jilin.contracts.command.IpInsuranceAdmissionCommand;
import com.openhis.whale.chs.jilin.contracts.result.IpInsuranceAdmissionResult;
import com.openhis.whale.chs.jilin.contracts.command.IpInsuranceConfirmCommand;
import com.openhis.whale.chs.jilin.contracts.result.IpInsuranceConfirmResult;
import com.openhis.whale.chs.jilin.contracts.command.IpInsuranceEncounterCommand;
import com.openhis.whale.chs.jilin.contracts.command.IpInsurancePreSettleCommand;
import com.openhis.whale.chs.jilin.contracts.result.IpInsurancePreSettleResult;
import com.openhis.whale.chs.jilin.contracts.command.IpInsuranceRevokeFeeDetailCommand;
import com.openhis.whale.chs.jilin.contracts.result.IpInsuranceRevokeFeeDetailResult;
import com.openhis.whale.chs.jilin.contracts.command.IpInsuranceRevokeSettleCommand;
import com.openhis.whale.chs.jilin.contracts.result.IpInsurancePlatformAdmissionSnapshot;
import com.openhis.whale.chs.jilin.contracts.result.IpInsuranceRevokeSettleResult;

import java.util.List;

/**
 * 住院收银 ↔ 吉林医保结算 Port（chs-jilin-adapter 实现）。
 *
 * <p>Wave 1：2401 入院 → 2301 费用上传 → 2303 预结算 → 2304 正式结算。</p>
 * <p>Wave 2：2302/2305 撤销、2403 信息变更、2404/2405 入出院撤销。</p>
 */
public interface ChsInpatientSettlementClient {

    /** 判断费用性质是否为医保契约。 */
    boolean isInsuranceContract(Long contractId);

    /** 【2401】医保入院登记，返回 mdtrt_id。 */
    IpInsuranceAdmissionResult ensureAdmission(IpInsuranceAdmissionCommand command);

    /** 【2301→(2402)→2303】住院预结算。 */
    IpInsurancePreSettleResult preSettle(IpInsurancePreSettleCommand command);

    /** 【2304】本地落账前正式结算。 */
    IpInsuranceConfirmResult confirmBeforeLocalSettle(IpInsuranceConfirmCommand command);

    /** 列出草稿下 ACTIVE 预结算。 */
    List<IpInsurancePreSettleResult> listActivePreSettlesByPaymentDraft(Long paymentDraftId);

    /**
     * 按成功支付单查最近一条 SETTLED 预结算快照（取消结算/退费定位用）。
     *
     * @param paymentId {@code fin_payment.id}
     */
    IpInsurancePreSettleResult findSettledPreSettleByPayment(Long paymentId);

    /** 【2305】撤销住院正式结算。 */
    IpInsuranceRevokeSettleResult revokeSettle(IpInsuranceRevokeSettleCommand command);

    /** 【2302】撤销已上传未结算的费用明细。 */
    IpInsuranceRevokeFeeDetailResult revokeFeeDetails(IpInsuranceRevokeFeeDetailCommand command);

    /** 批量查询平台入院登记快照（2401 映射）。 */
    List<IpInsurancePlatformAdmissionSnapshot> listPlatformAdmissionSnapshots(List<Long> encounterIds);

    /** 【2404】撤销入院登记。 */
    void revokeAdmission(IpInsuranceEncounterCommand command);

    /** 【2405】撤销出院登记。 */
    void revokeDischarge(IpInsuranceEncounterCommand command);

    /** 【2403】变更住院医保登记信息。 */
    void changeInpatientInfo(IpInsuranceAdmissionCommand command);
}
