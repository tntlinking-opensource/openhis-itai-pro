/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.client;

import com.openhis.whale.chs.jilin.contracts.command.OpInsuranceRegistrationCommand;
import com.openhis.whale.chs.jilin.contracts.command.OpInsuranceRevokeRegistrationCommand;
import com.openhis.whale.chs.jilin.contracts.result.OpInsuranceRevokeRegistrationResult;
import com.openhis.whale.chs.jilin.contracts.result.OpInsurancePlatformRegistrationSnapshot;
import com.openhis.whale.chs.jilin.contracts.result.OpInsuranceRegistrationResult;
import com.openhis.whale.chs.jilin.contracts.command.OpInsuranceConfirmCommand;
import com.openhis.whale.chs.jilin.contracts.result.OpInsuranceConfirmResult;
import com.openhis.whale.chs.jilin.contracts.command.OpInsuranceCorrectSettleCommand;
import com.openhis.whale.chs.jilin.contracts.command.OpInsurancePreSettleBatchCommand;
import com.openhis.whale.chs.jilin.contracts.result.OpInsurancePreSettleBatchResult;
import com.openhis.whale.chs.jilin.contracts.command.OpInsurancePreSettleCommand;
import com.openhis.whale.chs.jilin.contracts.result.OpInsurancePreSettleResult;
import com.openhis.whale.chs.jilin.contracts.command.OpInsuranceRevokeFeeDetailCommand;
import com.openhis.whale.chs.jilin.contracts.result.OpInsuranceRevokeFeeDetailResult;
import com.openhis.whale.chs.jilin.contracts.command.OpInsuranceRevokeSettleCommand;
import com.openhis.whale.chs.jilin.contracts.result.OpInsuranceRevokeSettleResult;
import com.openhis.whale.chs.jilin.contracts.result.OpInsuranceSettleRecordResult;

import java.util.List;

/**
 * 门诊收费 ↔ 吉林医保结算 Port（chs-jilin-adapter 实现，health 通过 Extension 间接调用）。
 *
 * <p>分层：Extension → 本 Port → ContextLoader + {@code ChsOutpatientSettleOperations}。
 * health-application 仅 optional 依赖 contracts，不感知 domain 包结构。</p>
 *
 * <p>平台 infno 对照见 {@code chs-jilin-domain/README.md §4}。</p>
 */
public interface ChsOutpatientSettlementClient {

    /**
     * 判断费用性质是否为医保契约（yb_flag 或 category=省医保）。
     *
     * @param contractId {@code fin_contract.id}
     */
    boolean isInsuranceContract(Long contractId);

    /**
     * 【2201】确保门诊平台挂号登记；已有同 med_type 的 mdtrt_id 映射时跳过。
     *
     * @param command 挂号登记命令（读卡字段、encounterId）
     * @return 平台就诊 ID
     */
    OpInsuranceRegistrationResult ensurePlatformRegistration(OpInsuranceRegistrationCommand command);

    /**
     * 【2202】撤销门诊平台挂号登记。
     */
    OpInsuranceRevokeRegistrationResult revokePlatformRegistration(OpInsuranceRevokeRegistrationCommand command);

    /**
     * 批量查询平台挂号登记与预结算摘要（列表 enrichment）。
     */
    java.util.List<OpInsurancePlatformRegistrationSnapshot> listPlatformRegistrationSnapshots(
        java.util.List<Long> encounterIds,
        java.util.Map<Long, Long> paymentDraftIdByEncounter);

    /**
     * 【2203→2204→2206】单组门诊预结算。
     *
     * @param command 含 chargeItemIds、读卡字段
     */
    OpInsurancePreSettleResult preSettle(OpInsurancePreSettleCommand command);

    /**
     * 慢病多科室混合预结算：按 account + med_type + dise_codg 分组，串行多次 preSettle。
     *
     * <p>详见 {@code whale-doc/chs-jilin/20-场景-慢病多科室混合结算}。</p>
     *
     * @param command 全部 chargeItemIds
     * @return 各组预结算结果 + 自费汇总
     */
    OpInsurancePreSettleBatchResult preSettleBatch(OpInsurancePreSettleBatchCommand command);

    /**
     * 【2207】单组正式结算；须在 health {@code settleOutpatientPayment} 本地落账之前调用。
     *
     * @param command 含 paymentDraftId、chrg_bchno
     */
    OpInsuranceConfirmResult confirmBeforeLocalSettle(OpInsuranceConfirmCommand command);

    /**
     * 列出支付草稿下仍有效的 ACTIVE 预结算批次（撤销/重算前检查）。
     *
     * @param paymentDraftId {@code fin_payment_draft.id}
     */
    List<OpInsurancePreSettleResult> listActivePreSettlesByPaymentDraft(Long paymentDraftId);

    /**
     * 列出支付单下未撤销的医保正式结算回执（混合结算可能多条 chrg_bchno）。
     *
     * @param paymentId {@code fin_payment.id}
     */
    List<OpInsuranceSettleRecordResult> listSettlementsByPayment(Long paymentId);

    /**
     * 【2208】撤销门诊正式结算。
     *
     * @param command 含 settlementRecordId / paymentId / setlId 之一
     */
    OpInsuranceRevokeSettleResult revokeSettle(OpInsuranceRevokeSettleCommand command);

    /**
     * 【2601】冲正原 2207 交易（2208 失败或超时补偿）。
     *
     * @param command 含 settlementRecordId、originalInfno
     */
    void correctSettleTransaction(OpInsuranceCorrectSettleCommand command);

    /**
     * 【2205】撤销未正式结算的费用明细（预结算回滚 / 批量失败补偿）。
     *
     * @param command 含 chrg_bchno 或 paymentDraftId
     */
    OpInsuranceRevokeFeeDetailResult revokeFeeDetails(OpInsuranceRevokeFeeDetailCommand command);
}
